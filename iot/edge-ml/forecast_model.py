"""Soil-moisture forecast at t + 30 min: features and inference, standard library only.

The training code (model/forecast) imports this module, so the gateway computes the inputs
exactly as they were computed for training. A model release is one JSON file:
    {"version", "kind": "persistence" | "linear" | "trees", "features", "horizonMin", ...}
Every model predicts the change of moisture over the horizon; the forecast is current + change.
"""
import json
import math
import struct

HORIZON_MIN = 30
LAGS_MIN = (10, 20)            # moisture history the features need
MAX_SINCE_WATERING_MIN = 24 * 60

FEATURE_NAMES = (
    "moisture",                # relative soil moisture now (%)
    "change_10min",            # moisture now - 10 min ago
    "change_20min",            # moisture now - 20 min ago
    "air_temperature",         # °C
    "air_humidity",            # %
    "log_minutes_since_watering",
)


def features(moisture, moisture_10min_ago, moisture_20min_ago, air_temperature, air_humidity,
             minutes_since_watering):
    """Feature vector (dict) from raw inputs. Returns None when an input is missing."""
    raw = (moisture, moisture_10min_ago, moisture_20min_ago, air_temperature, air_humidity)
    if any(v is None or not math.isfinite(v) for v in raw):
        return None
    if minutes_since_watering is None or minutes_since_watering < 0:
        minutes_since_watering = MAX_SINCE_WATERING_MIN
    since = min(float(minutes_since_watering), MAX_SINCE_WATERING_MIN)
    return {
        "moisture": float(moisture),
        "change_10min": float(moisture - moisture_10min_ago),
        "change_20min": float(moisture - moisture_20min_ago),
        "air_temperature": float(air_temperature),
        "air_humidity": float(air_humidity),
        "log_minutes_since_watering": math.log1p(since),
    }


def _float32(v):
    # scikit-learn compares features as 32-bit floats; do the same so that values on a threshold go the same way
    return struct.unpack("f", struct.pack("f", v))[0]


def _tree_value(tree, x):
    node = 0
    while tree["left"][node] != -1:
        if x[tree["feature"][node]] <= tree["threshold"][node]:
            node = tree["left"][node]
        else:
            node = tree["right"][node]
    return tree["value"][node]


class ForecastModel:
    def __init__(self, release):
        self.release = release
        self.version = release["version"]
        self.kind = release["kind"]
        self.names = release.get("features", list(FEATURE_NAMES))
        self.horizon_min = release.get("horizonMin", HORIZON_MIN)
        self.clip = release.get("clip", [0.0, 100.0])

    @classmethod
    def load(cls, path):
        with open(path, encoding="utf-8") as f:
            return cls(json.load(f))

    def change(self, feats):
        """Predicted change of moisture over the horizon. Changes smaller than the release's dead-band
        (chosen on validation data) are reported as no change: most 30-min windows do not move."""
        if self.kind == "persistence":
            return 0.0
        value = self._raw_change(feats)
        return value if abs(value) >= self.release.get("deadband", 0.0) else 0.0

    def _raw_change(self, feats):
        x = [feats[n] for n in self.names]
        if self.kind == "linear":
            mean, scale = self.release["mean"], self.release["scale"]
            z = [(v - m) / s for v, m, s in zip(x, mean, scale)]
            return self.release["intercept"] + sum(c * v for c, v in zip(self.release["coef"], z))
        if self.kind == "trees":
            total = self.release["init"]
            rate = self.release["learningRate"]
            x = [_float32(v) for v in x]
            for tree in self.release["trees"]:
                total += rate * _tree_value(tree, x)
            return total
        raise ValueError(f"unknown model kind {self.kind}")

    def predict(self, feats):
        low, high = self.clip
        return max(low, min(high, feats["moisture"] + self.change(feats)))
