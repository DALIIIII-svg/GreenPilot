import os
import sys
import unittest

import numpy as np
import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)

import synthetic  # noqa: E402
import train  # noqa: E402
from forecast_model import FEATURE_NAMES, ForecastModel, features  # noqa: E402


def dataset():
    frames = []
    for probe, seed in (("A", 1), ("B", 2)):
        w = train.windows(synthetic.series(probe, seed=seed))
        w["probe"] = probe
        frames.append(w)
    return pd.concat(frames, ignore_index=True)


class Windows(unittest.TestCase):
    def test_features_are_computed_from_the_lagged_readings(self):
        s = synthetic.series().set_index("ts")
        df = train.windows(synthetic.series())
        row = df.iloc[50]
        ts = row["ts"]
        m = s["moisture"]
        self.assertAlmostEqual(row["moisture"], m[ts])
        self.assertAlmostEqual(row["change_10min"], m[ts] - m[ts - pd.Timedelta(minutes=10)])
        self.assertAlmostEqual(row["change_20min"], m[ts] - m[ts - pd.Timedelta(minutes=20)])
        self.assertAlmostEqual(row["target"], m[ts + pd.Timedelta(minutes=30)] - m[ts])
        self.assertTrue(set(FEATURE_NAMES) <= set(df.columns))

    def test_no_watering_inside_a_target_window(self):
        s = synthetic.series()
        df = train.windows(s)
        watered = set(s.loc[s["watering"] > 0, "ts"])
        for ts in df["ts"].head(2000):
            ahead = {ts + pd.Timedelta(minutes=m) for m in (10, 20, 30)}
            self.assertFalse(ahead & watered)

    def test_split_keeps_cycles_together_and_test_is_latest(self):
        parts = train.split_by_cycle(dataset())
        for probe in ("A", "B"):
            cycles = {k: set(v.loc[v["probe"] == probe, "cycle"]) for k, v in parts.items()}
            self.assertFalse(cycles["train"] & cycles["test"])
            self.assertFalse(cycles["validation"] & cycles["test"])
            self.assertLess(max(cycles["train"]), min(cycles["test"]))


class Export(unittest.TestCase):
    def check(self, kind, make):
        df = dataset()
        model, scaler = train.fit(kind, make, df)
        release = train.export(kind, model, scaler, "test")
        exported = ForecastModel(release)
        sample = df.sample(200, random_state=0)
        mine = np.array([exported.change({n: r[n] for n in FEATURE_NAMES}) for _, r in sample.iterrows()])
        self.assertTrue(np.allclose(mine, train.predict(kind, model, scaler, sample), atol=1e-6))

    def test_linear_export_matches_scikit_learn(self):
        from sklearn.linear_model import Ridge
        self.check("linear", lambda: Ridge(alpha=1.0))

    def test_trees_export_matches_scikit_learn(self):
        from sklearn.ensemble import GradientBoostingRegressor
        self.check("trees", lambda: GradientBoostingRegressor(n_estimators=30, max_depth=3, random_state=0))


class Gateway(unittest.TestCase):
    def test_missing_input_suppresses_inference(self):
        self.assertIsNone(features(40, None, 41, 22, 60, 10))
        self.assertIsNone(features(40, 41, 42, float("nan"), 60, 10))

    def test_prediction_is_clipped(self):
        model = ForecastModel({"version": "t", "kind": "linear", "features": list(FEATURE_NAMES),
                               "mean": [0] * 6, "scale": [1] * 6, "intercept": -500, "coef": [0] * 6})
        self.assertEqual(model.predict(features(40, 41, 42, 22, 60, 10)), 0.0)

    def test_persistence_returns_current_moisture(self):
        model = ForecastModel({"version": "p", "kind": "persistence"})
        self.assertEqual(model.predict(features(42.5, 43, 44, 22, 60, None)), 42.5)


if __name__ == "__main__":
    unittest.main()
