"""Trains and evaluates the soil-moisture forecast (t + 30 min), then writes a versioned release.

    python train.py                     # uses data/raw/*.zip (see README.md)

Protocol (specification, section 4.1):
  - models: persistence baseline (moisture stays the same), ridge linear regression, small gradient-boosted trees;
  - every model predicts the change of moisture over 30 min; the inputs are the ones the gateway has
    (iot/edge-ml/forecast_model.py is imported so training and gateway compute them the same way);
  - windows are grouped by drying cycle (watering to watering); the latest cycles are held out for the test,
    the cycles just before them select the hyper-parameters; preprocessing is fitted on training data only;
  - windows whose next 30 min contain a watering are excluded: the model forecasts the soil left alone,
    and the gateway records any watering made after a forecast;
  - hyper-parameters and the model family are chosen on validation RMSE: about 90 % of 30-min windows show no change
    at the probe's resolution, so MAE alone rewards "predict no change"; RMSE weighs the drying and post-watering
    moves that matter for irrigation decisions;
  - a dead-band, also chosen on validation (MAE), turns predicted changes smaller than it into "no change";
  - release gate: a model is released only if its test MAE beats persistence, otherwise persistence is released.
"""
import argparse
import hashlib
import json
import math
import os
import platform
import sys
from datetime import datetime, timezone

import numpy as np
import pandas as pd
import sklearn
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.linear_model import Ridge
from sklearn.preprocessing import StandardScaler

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "iot", "edge-ml"))
from forecast_model import FEATURE_NAMES, HORIZON_MIN, LAGS_MIN, ForecastModel, features  # noqa: E402

import datasets  # noqa: E402

STEP_MIN = 10
TEST_SHARE = 0.2
VALIDATION_SHARE = 0.15


def windows(series):
    """One row per time step with complete inputs and target. series: one probe on a STEP_MIN grid."""
    s = series.sort_values("ts").set_index("ts")
    s = s[~s.index.duplicated()]
    grid = s.asfreq(f"{STEP_MIN}min")
    m = grid["moisture"]
    watered = grid["watering"].fillna(0) > 0

    # minutes since the last watering, and drying-cycle number (a new cycle starts at each watering)
    last = pd.Series(np.where(watered, grid.index.view("int64"), np.nan), index=grid.index).ffill()
    since = (grid.index.view("int64") - last) / 6e10
    cycle = watered.cumsum()

    steps = HORIZON_MIN // STEP_MIN
    future = m.shift(-steps)
    water_ahead = watered[::-1].rolling(steps, min_periods=1).max()[::-1].shift(-1).fillna(1) > 0

    rows = []
    lag1, lag2 = (m.shift(lag // STEP_MIN) for lag in LAGS_MIN)
    for i, ts in enumerate(grid.index):
        if water_ahead.iloc[i] or math.isnan(future.iloc[i]):
            continue
        f = features(m.iloc[i], lag1.iloc[i], lag2.iloc[i], grid["air_temperature"].iloc[i],
                     grid["air_humidity"].iloc[i], None if math.isnan(since.iloc[i]) else since.iloc[i])
        if f is None:
            continue
        f.update(ts=ts, cycle=int(cycle.iloc[i]), target=future.iloc[i] - m.iloc[i], actual=future.iloc[i])
        rows.append(f)
    return pd.DataFrame(rows)


def split_by_cycle(df):
    """Chronological split of whole cycles, per probe: train | validation | test.
    The latest cycles go to the test set until it holds TEST_SHARE of the probe's windows,
    the cycles just before them to the validation set until it holds VALIDATION_SHARE."""
    parts = {"train": [], "validation": [], "test": []}
    for _, g in df.groupby("probe"):
        sizes = g.groupby("cycle").size().sort_index()
        total = sizes.sum()
        test, val, filled = set(), set(), 0
        for cycle, size in sizes[::-1].items():
            if filled < TEST_SHARE * total:
                test.add(cycle)
            elif filled < (TEST_SHARE + VALIDATION_SHARE) * total:
                val.add(cycle)
            else:
                break
            filled += size
        parts["test"].append(g[g["cycle"].isin(test)])
        parts["validation"].append(g[g["cycle"].isin(val)])
        parts["train"].append(g[~g["cycle"].isin(test | val)])
    return {k: pd.concat(v) for k, v in parts.items()}


def X(df):
    return df[list(FEATURE_NAMES)].to_numpy()


MOVING = 1.0   # windows whose moisture changes by at least this much over the horizon
DEADBANDS = (0.0, 0.25, 0.5, 0.75, 1.0)


def scores(df, predicted_change):
    err = (df["moisture"].to_numpy() + predicted_change) - df["actual"].to_numpy()
    moving = np.abs(df["target"].to_numpy()) >= MOVING
    return {"mae": float(np.mean(np.abs(err))), "rmse": float(np.sqrt(np.mean(err ** 2))), "n": int(len(df)),
            "maeMoving": float(np.mean(np.abs(err[moving]))) if moving.any() else None, "nMoving": int(moving.sum())}


def by_probe(df, predicted_change):
    out = {}
    for probe, idx in df.groupby("probe").groups.items():
        pos = df.index.get_indexer(idx)
        out[probe] = scores(df.iloc[pos], predicted_change[pos])
    return out


def candidates():
    for alpha in (0.1, 10.0, 1000.0):
        yield f"ridge(alpha={alpha})", "linear", lambda a=alpha: Ridge(alpha=a)
    for loss in ("squared_error", "huber"):
        for depth in (2, 3):
            for n in (100, 200):
                yield (f"trees({loss},depth={depth},n={n})", "trees",
                       lambda l=loss, d=depth, k=n: GradientBoostingRegressor(
                           loss=l, n_estimators=k, max_depth=d, learning_rate=0.05, subsample=0.8,
                           min_samples_leaf=20, random_state=42))


def fit(kind, make, df):
    model = make()
    if kind == "linear":
        scaler = StandardScaler().fit(X(df))
        model.fit(scaler.transform(X(df)), df["target"])
        return model, scaler
    model.fit(X(df), df["target"])
    return model, None


def predict(kind, model, scaler, df, deadband=0.0):
    change = model.predict(scaler.transform(X(df)) if kind == "linear" else X(df))
    return np.where(np.abs(change) >= deadband, change, 0.0)


def choose_deadband(change, df):
    """Smallest dead-band within 1 % of the best validation MAE."""
    maes = {d: scores(df, np.where(np.abs(change) >= d, change, 0.0))["mae"] for d in DEADBANDS}
    best = min(maes.values())
    return min(d for d, m in maes.items() if m <= best * 1.01), maes


def export(kind, model, scaler, version):
    release = {"version": version, "kind": kind, "features": list(FEATURE_NAMES), "horizonMin": HORIZON_MIN,
               "clip": [0.0, 100.0]}
    if kind == "linear":
        release.update(mean=scaler.mean_.tolist(), scale=scaler.scale_.tolist(),
                       intercept=float(model.intercept_), coef=model.coef_.tolist())
    elif kind == "trees":
        trees = []
        for est in model.estimators_[:, 0]:
            t = est.tree_
            trees.append({"feature": t.feature.tolist(), "threshold": t.threshold.tolist(),
                          "left": t.children_left.tolist(), "right": t.children_right.tolist(),
                          "value": t.value[:, 0, 0].tolist()})
        release.update(init=float(model.init_.constant_[0][0]), learningRate=model.learning_rate, trees=trees)
    return release


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--data", default=os.path.join(HERE, "data", "raw"))
    p.add_argument("--out", default=os.path.join(HERE, "releases"))
    args = p.parse_args()

    raw, source = datasets.load(args.data)
    frames = []
    for probe, g in raw.groupby("probe"):
        w = windows(g)
        w["probe"] = probe
        frames.append(w)
    df = pd.concat(frames, ignore_index=True)
    parts = split_by_cycle(df)
    for k in parts:
        parts[k] = parts[k].reset_index(drop=True)
    train, val, test = parts["train"], parts["validation"], parts["test"]
    print(f"windows: train {len(train)}, validation {len(val)}, test {len(test)}; "
          f"cycles: {df.groupby('probe')['cycle'].nunique().sum()}")

    # 1. choose each family's hyper-parameters on the validation cycles
    persistence_val = scores(val, np.zeros(len(val)))
    best = {}
    print(f"\nvalidation                         MAE    RMSE   MAE on moving windows")
    print(f"  {'persistence':40s} {persistence_val['mae']:.3f}  {persistence_val['rmse']:.3f}  {persistence_val['maeMoving']:.3f}")
    for name, kind, make in candidates():
        model, scaler = fit(kind, make, train)
        s = scores(val, predict(kind, model, scaler, val))
        print(f"  {name:40s} {s['mae']:.3f}  {s['rmse']:.3f}  {s['maeMoving']:.3f}")
        if kind not in best or s["rmse"] < best[kind][2]["rmse"]:
            best[kind] = (name, make, s, predict(kind, model, scaler, val))

    deadbands = {}
    for kind, (name, _, _, change) in best.items():
        deadbands[kind], maes = choose_deadband(change, val)
        print(f"  dead-band for {name}: {deadbands[kind]}  "
              f"(validation MAE {', '.join(f'{d}: {m:.3f}' for d, m in maes.items())})")

    # 2. refit on train + validation, evaluate once on the held-out test cycles
    train_val = pd.concat([train, val], ignore_index=True)
    results = {"persistence": {"name": "persistence", "test": scores(test, np.zeros(len(test))),
                               "byProbe": by_probe(test, np.zeros(len(test))), "validation": persistence_val}}
    fitted = {}
    for kind, (name, make, _, _) in best.items():
        model, scaler = fit(kind, make, train_val)
        change = predict(kind, model, scaler, test, deadbands[kind])
        results[kind] = {"name": name, "deadband": deadbands[kind], "test": scores(test, change),
                         "byProbe": by_probe(test, change), "validation": best[kind][2]}
        fitted[kind] = (model, scaler)

    print("\ntest (held-out later cycles)      MAE    RMSE   MAE on moving windows")
    for kind, r in results.items():
        t = r["test"]
        name = r["name"] + (f" dead-band {r['deadband']}" if r.get("deadband") else "")
        print(f"  {name:40s} {t['mae']:.3f}  {t['rmse']:.3f}  {t['maeMoving']:.3f}")

    # 3. release gate: the candidate chosen on validation must beat persistence on the test cycles
    chosen = min(best, key=lambda k: best[k][2]["rmse"])
    beats = results[chosen]["test"]["mae"] < results["persistence"]["test"]["mae"]
    kind = chosen if beats else "persistence"
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M")
    version = f"forecast-{stamp}-{kind}"
    if kind == "persistence":
        release = {"version": version, "kind": "persistence", "features": list(FEATURE_NAMES),
                   "horizonMin": HORIZON_MIN, "clip": [0.0, 100.0]}
    else:
        release = export(kind, *fitted[kind], version)
        release["deadband"] = deadbands[kind]

    # the exported JSON must give the same answers as scikit-learn
    exported = ForecastModel(release)
    check = test.head(500)
    mine = np.array([exported.change({n: r[n] for n in FEATURE_NAMES}) for _, r in check.iterrows()])
    ref = np.zeros(len(check)) if kind == "persistence" else predict(kind, *fitted[kind], check, deadbands[kind])
    assert np.allclose(mine, ref, atol=1e-6), "exported model differs from scikit-learn"

    folder = os.path.join(args.out, version)
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "model.json"), "w", encoding="utf-8") as f:
        json.dump(release, f)
    manifest = {
        "version": version, "kind": kind, "createdAt": datetime.now(timezone.utc).isoformat(),
        "horizonMin": HORIZON_MIN, "stepMin": STEP_MIN, "lagsMin": list(LAGS_MIN), "features": list(FEATURE_NAMES),
        "target": "moisture(t + 30 min) - moisture(t), relative soil moisture (0-100) per probe",
        "dataset": {**source, "files": {os.path.basename(f): sha256(f) for f in source["files"]}},
        "split": {"rule": "per probe, chronological by drying cycle",
                  "testShare": TEST_SHARE, "validationShare": VALIDATION_SHARE,
                  "windows": {"train": len(train), "validation": len(val), "test": len(test)}},
        "selection": "family and hyper-parameters with the lowest validation RMSE",
        "releaseGate": {"rule": "test MAE < persistence test MAE", "chosenOnValidation": results[chosen]["name"],
                        "beatsPersistence": bool(beats)},
        "movingWindow": f"|moisture(t+30) - moisture(t)| >= {MOVING}",
        "results": results,
        "environment": {"python": platform.python_version(), "scikit-learn": sklearn.__version__,
                        "pandas": pd.__version__, "numpy": np.__version__},
        "modelSha256": sha256(os.path.join(folder, "model.json")),
    }
    with open(os.path.join(folder, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, default=str)
    print(f"\nrelease {version}: {'beats' if beats else 'does not beat'} persistence -> {folder}")
    print(f"deploy it to the gateway:  python deploy.py {version}")


if __name__ == "__main__":
    main()
