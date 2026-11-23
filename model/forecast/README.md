# Soil-moisture forecast (t + 30 min)

Specification §4.1: estimate each zone's relative soil moisture 30 minutes ahead, on the gateway, to support the
grower's decisions (the forecast never switches the pump). This folder trains and evaluates the model and produces
versioned releases; the gateway runs them with `iot/edge-ml/forecast_service.py`.

```
model/forecast/
├── datasets.py          dataset loader (cleaning, watering events)
├── train.py             windows, split, candidates, release gate, export, manifest
├── deploy.py            installs a release on the gateway, keeps the previous one (rollback)
├── tests/               unit tests (synthetic series, exported model = scikit-learn)
├── releases/<version>/  model.json (what the gateway loads) + manifest.json (data, split, metrics, checksums)
└── data/raw/            the downloaded dataset (not in git, see below)
```

## Dataset

Without the greenhouse hardware, the project's own watering/drying cycles (specification: from week 2) cannot be
recorded yet. The model is trained on a public dataset of the same kind of signal:

**Soil Moisture, Irrigation Actuator and Weather Dataset from a Multi-Sector Precision-Irrigation Field Trial**
(Arnesano, Apulia, Italy, Feb.–Sep. 2025), Mendeley Data, DOI [10.17632/c837v6p8ph.2](https://data.mendeley.com/datasets/c837v6p8ph/2), CC BY 4.0.
Weather from Open-Meteo / ERA5 (CC BY 4.0).

Why this one: capacitive probes reporting a **relative 0–100 moisture**, the same kind of indicator as our probes;
**measured** irrigation (valve telemetry, 789 validated events) instead of assumed watering; a 10-minute grid, so the
30-minute horizon is 3 steps; five sectors (tomato in open field ×2, tomato in pots, zucchini, blueberry).

Download *Download All* from the page above and put the zip, as it is, in `data/raw/`.
The manifest of each release records the zip's SHA-256.

What is used: the `merged/dataset_zone_{1..5}.csv` tables (10-min grid, gaps kept).
- moisture = `soil_moisture`, cleaned like the dataset's own preprocessing (0–115 range, clip at 100, 15-point
  despiking) plus: exact 0 removed (probe disconnected), runs unchanged for 24 h removed (stalled probe).
  **No interpolation**: a window with a missing input is dropped, as the gateway suppresses inference on missing inputs.
- air temperature / humidity = `weather_temp`, `weather_humidity` (ERA5, outdoor; the farm's own air sensors cover
  only the last two months).
- watering = valve open in the bin, or rain ≥ 0.1 mm (rain wets open-field soil like irrigation does).

## Method (as specified)

| | |
|---|---|
| Inputs | moisture now, change over 10 and 20 min (trend), air temperature, air humidity, log(minutes since watering). Computed by `iot/edge-ml/forecast_model.py`, the same code the gateway runs |
| Target | moisture(t + 30 min) − moisture(t); forecast = current + predicted change, clipped to 0–100 |
| Excluded | windows with a watering inside the next 30 min: the model forecasts the soil left alone; the gateway logs watering times next to its forecasts |
| Split | per probe, chronological, by whole drying cycle (watering to watering): latest cycles = test (20 % of windows), the cycles before = validation (15 %), the rest = training. Scaling is fitted on training data only |
| Candidates | persistence `m̂(t+30) = m(t)`; ridge linear regression; small gradient-boosted trees (depth 2–3, 100–200 trees, squared or Huber loss) |
| Selection | family and hyper-parameters with the lowest **validation RMSE**; then a dead-band (also on validation, by MAE): predicted changes smaller than it count as "no change" |
| Release gate | released only if its **test MAE beats persistence**; otherwise persistence is released and the evaluation documented |

Why RMSE for the selection: at the probes' resolution, about 90 % of 30-minute windows show **no change at all**.
MAE alone then rewards "predict no change"; RMSE weighs the windows where the soil does move, which are the ones
that matter for irrigation. The specification's MAE gate is kept as is.

## Results (release `forecast-20261120-1623-trees`)

Test = later held-out drying cycles, 5,908 windows, of which 332 "moving" (|change| ≥ 1 point).

| Model | Test MAE | Test RMSE | MAE on moving windows |
|---|---|---|---|
| Persistence (baseline) | 0.159 | 1.084 | 2.837 |
| Ridge regression, dead-band 0.75 | 0.161 | 0.899 | 2.260 |
| **Gradient-boosted trees (Huber, depth 3, 200 trees), dead-band 0.5** | **0.111** | **0.781** | **1.934** |

The trees beat persistence on all three measures (MAE −30 %, RMSE −28 %, −32 % on the windows that move) and pass
the gate. Ridge does not beat persistence on MAE. By sector (test MAE, persistence → trees):

| Sector | Crop | Windows | Persistence | Trees |
|---|---|---|---|---|
| 1 | tomato, open field | 1,413 | 0.195 | 0.124 |
| 2 | tomato, open field | 1,370 | 0.085 | 0.068 |
| 3 | tomato, pots | 2 | 7.50 | 6.72 |
| 4 | zucchini | 1,392 | 0.258 | 0.163 |
| 5 | blueberry | 1,731 | 0.101 | 0.083 |

Sector 3 (pots, the closest to our set-up) has almost no valid readings in its last cycles: its probe stopped working
in July, as the dataset's README also notes. Its two test windows are not meaningful.

What the model learned, in plain terms: a sudden drop of a few points over 10–20 minutes is usually a reading that
comes back (it forecasts the recovery); right after a sharp rise (watering), part of the water drains away (it
forecasts a decrease); otherwise it keeps the current value. Real drying is slow at a 30-minute horizon.

## Limitations

- Field and pot crops in southern Italy, outdoor reanalysis weather: not our greenhouse, our bell peppers or our
  probes. These results do not transfer as they are; they show that the pipeline works on real data.
- With the hardware, record at least ten watering/drying cycles per zone, normalise each probe with its reference
  conditions (dry / saturated), add a loader in `datasets.py`, retrain, and release only if the gate passes again.
- MAE is low in absolute terms because most windows do not move; compare models with the moving-window column.

## Commands

```powershell
cd model\forecast
py -3 -m pip install -r requirements.txt
py -3 -m unittest discover -s tests          # 8 tests
py -3 train.py                               # writes releases\<version>\ (about 10 s)
py -3 deploy.py <version>                     # installs it on the gateway, smoke test, keeps the previous one
py -3 deploy.py --rollback                    # back to the previous release
```

The gateway's forecast service reloads the model within a minute, without a restart.
Each release's `manifest.json` holds the dataset checksum, the split sizes, every metric above, the library versions
and the model's checksum, which `deploy.py` verifies.
