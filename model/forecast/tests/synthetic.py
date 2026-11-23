"""Synthetic drying/watering series, for the unit tests only (never for training a release)."""
import numpy as np
import pandas as pd


def series(probe="test", days=6, seed=0, step_min=10):
    rng = np.random.default_rng(seed)
    ts = pd.date_range("2026-06-01", periods=days * 24 * 60 // step_min, freq=f"{step_min}min", tz="UTC")
    hours = ts.hour + ts.minute / 60
    temperature = 22 + 6 * np.sin((hours - 9) / 24 * 2 * np.pi) + rng.normal(0, 0.3, len(ts))
    humidity = 65 - 15 * np.sin((hours - 9) / 24 * 2 * np.pi) + rng.normal(0, 1, len(ts))
    moisture, watering = [], []
    m = 70.0
    for i in range(len(ts)):
        water = m < 35 or (i > 0 and i % 150 == 0)
        if water:
            m = min(90.0, m + 30)
        rate = 0.004 * (1 + (temperature[i] - 22) / 10) * (1 + (60 - humidity[i]) / 100)
        m = m - rate * (m - 10) + rng.normal(0, 0.15)
        moisture.append(m)
        watering.append(1.0 if water else 0.0)
    return pd.DataFrame({"probe": probe, "ts": ts, "moisture": moisture, "air_temperature": temperature,
                         "air_humidity": humidity, "watering": watering})
