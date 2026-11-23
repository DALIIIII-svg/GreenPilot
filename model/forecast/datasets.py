"""Training data loaders. load() returns (frame, source) where frame has one row per probe and 10-min step:
    probe, ts, moisture (relative 0-100), air_temperature (°C), air_humidity (%), watering (> 0: water reached the soil)
and source describes the dataset for the release manifest.
"""
import glob
import io
import os
import re
import zipfile

import numpy as np
import pandas as pd

ARNESANO = {
    "name": "Soil Moisture, Irrigation Actuator and Weather Dataset from a Multi-Sector Precision-Irrigation "
            "Field Trial (Arnesano, Apulia, Italy, 2025)",
    "url": "https://data.mendeley.com/datasets/c837v6p8ph/2",
    "doi": "10.17632/c837v6p8ph.2",
    "license": "CC BY 4.0 (weather: Open-Meteo / ERA5, CC BY 4.0)",
    "sectors": {1: "tomato, open field", 2: "tomato, open field", 3: "tomato, pots", 4: "zucchini", 5: "blueberry"},
}

RAIN_MM = 0.1            # rain in a 10-min bin counts as water reaching the soil (open-field sectors)
SPIKE_POINTS = 15        # same despiking rule as the dataset's own preprocessing
FLATLINE_STEPS = 144     # 24 h without any change: the probe is stalled (soil dries every day)


def clean_moisture(m):
    """Range filter, despike and stalled-probe removal. Gaps stay gaps (no interpolation):
    a window with a missing input is dropped, as the gateway suppresses inference on missing inputs."""
    m = m.where(m.between(0, 115) & (m != 0)).clip(upper=100)   # exactly 0 = probe disconnected
    median = m.rolling(9, center=True, min_periods=5).median()
    m = m.where((m - median).abs() <= SPIKE_POINTS)
    valid = m.dropna()
    run = (valid != valid.shift()).cumsum()
    stalled = valid.groupby(run).transform("size") >= FLATLINE_STEPS
    return m.where(~m.index.isin(valid.index[stalled]))


def load_arnesano(path):
    frames = []
    with zipfile.ZipFile(path) as z:
        names = [n for n in z.namelist() if re.search(r"02_processed_data/merged/dataset_zone_\d\.csv$", n)]
        if not names:
            raise SystemExit(f"{os.path.basename(path)}: no 02_processed_data/merged/dataset_zone_*.csv inside")
        for name in sorted(names):
            sector = int(re.search(r"zone_(\d)", name).group(1))
            raw = pd.read_csv(io.BytesIO(z.read(name)), parse_dates=["ts"])
            watering = (raw["irrigation_duration_minutes"].fillna(0) > 0) | (raw["weather_rain"].fillna(0) >= RAIN_MM)
            frames.append(pd.DataFrame({
                "probe": f"sector-{sector}",
                "ts": raw["ts"],
                "moisture": clean_moisture(raw["soil_moisture"]),
                "air_temperature": raw["weather_temp"],
                "air_humidity": raw["weather_humidity"],
                "watering": watering.astype(float),
            }))
    source = {k: v for k, v in ARNESANO.items() if k != "sectors"}
    source["probes"] = {f"sector-{k}": v for k, v in ARNESANO["sectors"].items()}
    source["files"] = [path]
    source["inputs"] = ("merged 10-min tables; moisture = soil_moisture (cleaned, not interpolated); "
                        "air = weather_temp / weather_humidity (ERA5); watering = valve open or rain >= 0.1 mm")
    return pd.concat(frames, ignore_index=True), source


def load(folder):
    archives = sorted(glob.glob(os.path.join(folder, "*.zip")))
    if not archives:
        raise SystemExit(f"No dataset in {folder}: download it as described in README.md")
    return load_arnesano(archives[0])
