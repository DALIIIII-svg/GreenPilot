"""Soil-moisture forecasting service of the gateway (runs next to Node-RED, on the Pi or the PC).

Every 15 s it reads the latest field readings from the gateway (GET /status) and keeps a short
history per zone. Every minute, once 20 minutes of history exist, it predicts each zone's moisture
30 minutes ahead with the active model release and hands the forecasts to the gateway
(POST /edge/forecast), which publishes them on greenpilot/<gw>/up/forecast through its offline buffer.

Standard library only:  python forecast_service.py   (options: --help)
"""
import argparse
import csv
import json
import os
import signal
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

from forecast_model import HORIZON_MIN, LAGS_MIN, ForecastModel, features

HERE = os.path.dirname(os.path.abspath(__file__))
ZONES = ("A", "B")
STALE_AFTER_SEC = 120          # no inference on readings older than this
LAG_TOLERANCE_SEC = 180        # a lagged value may be this far from its target time
KEEP_HISTORY_SEC = 2 * 3600


def log(text):
    print(f"{datetime.now().strftime('%H:%M:%S')}  {text}", flush=True)


def iso(ms):
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class Gateway:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def status(self):
        with urllib.request.urlopen(f"{self.base}/status", timeout=5) as r:
            return json.load(r)

    def post(self, path, body):
        data = json.dumps(body).encode()
        req = urllib.request.Request(f"{self.base}{path}", data=data, method="POST",
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status


class History:
    """Recent samples (gateway time in ms) per zone, saved to disk so a restart keeps them."""

    def __init__(self, path):
        self.path = path
        self.samples = {"moisture": {z: [] for z in ZONES}, "air_temperature": [], "air_humidity": []}
        self.last_ts = 0
        try:
            with open(path, encoding="utf-8") as f:
                saved = json.load(f)
            self.samples.update(saved["samples"])
            self.last_ts = saved.get("lastTs", 0)
        except (OSError, ValueError, KeyError):
            pass

    def add(self, latest):
        ts = latest.get("ts") or 0
        if ts <= self.last_ts:
            return False
        self.last_ts = ts
        for r in latest.get("readings", []):
            value = r.get("value")
            if not isinstance(value, (int, float)):
                continue
            if r.get("type") == "soil_moisture" and r.get("zoneId") in ZONES:
                self.samples["moisture"][r["zoneId"]].append([ts, value])
            elif r.get("type") in ("air_temperature", "air_humidity"):
                self.samples[r["type"]].append([ts, value])
        cutoff = ts - KEEP_HISTORY_SEC * 1000
        for series in self._all_series():
            series[:] = [s for s in series if s[0] >= cutoff]
        return True

    def _all_series(self):
        return [*self.samples["moisture"].values(), self.samples["air_temperature"], self.samples["air_humidity"]]

    def save(self):
        tmp = self.path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump({"lastTs": self.last_ts, "samples": self.samples}, f)
        os.replace(tmp, self.path)

    @staticmethod
    def value_at(series, ts, tolerance_ms):
        best = None
        for t, v in series:
            gap = abs(t - ts)
            if gap <= tolerance_ms and (best is None or gap < best[0]):
                best = (gap, v)
        return None if best is None else best[1]

    def history_minutes(self, zone):
        series = self.samples["moisture"][zone]
        return 0 if not series else (series[-1][0] - series[0][0]) / 60000


class ModelStore:
    """models/forecast/active.json names the release in use; the previous one is kept for rollback."""

    def __init__(self, folder):
        self.folder = folder
        self.pointer = os.path.join(folder, "active.json")
        self.model = None
        self.mtime = None

    def current(self):
        try:
            mtime = os.path.getmtime(self.pointer)
        except OSError:
            if self.model is None:
                raise SystemExit(f"No model release: {self.pointer} is missing (see model/forecast/README.md)")
            return self.model
        if mtime != self.mtime:
            with open(self.pointer, encoding="utf-8") as f:
                active = json.load(f)["active"]
            self.model = ForecastModel.load(os.path.join(self.folder, f"{active}.json"))
            self.mtime = mtime
            log(f"model {self.model.version} ({self.model.kind}) loaded")
        return self.model


def run(args):
    os.makedirs(args.state, exist_ok=True)
    gateway = Gateway(args.gateway)
    history = History(os.path.join(args.state, "forecast-history.json"))
    models = ModelStore(args.models)
    log_path = os.path.join(args.state, "forecast-log.csv")
    new_log = not os.path.exists(log_path)
    log_file = open(log_path, "a", newline="", encoding="utf-8")
    writer = csv.writer(log_file)
    if new_log:
        writer.writerow(["ts", "zone", "model", "moisture", "predicted", "minutes_since_watering"])

    stop = []
    signal.signal(signal.SIGINT, lambda *_: stop.append(True))
    next_forecast = 0
    unreachable = False
    while not stop:
        try:
            status = gateway.status()
            if unreachable:
                log("gateway reachable again")
            unreachable = False
        except (urllib.error.URLError, OSError, ValueError) as e:
            if not unreachable:
                log(f"gateway not reachable at {args.gateway} ({e}); retrying")
            unreachable = True
            time.sleep(args.poll)
            continue

        latest = status.get("latest")
        if latest and history.add(latest):
            history.save()

        now = time.time()
        if now >= next_forecast:
            next_forecast = now + args.every
            forecast_once(status, history, models.current(), gateway, writer)
            log_file.flush()
        time.sleep(args.poll)
    log_file.close()


def forecast_once(status, history, model, gateway, writer):
    latest = status.get("latest")
    now_ms = time.time() * 1000
    body = {"modelVersion": model.version, "horizonMin": model.horizon_min, "forecasts": []}
    if not latest or now_ms - latest.get("ts", 0) > STALE_AFTER_SEC * 1000:
        body["state"] = "stale-input"
    else:
        ts = latest["ts"]
        tol = LAG_TOLERANCE_SEC * 1000
        temperature = History.value_at(history.samples["air_temperature"], ts, tol)
        humidity = History.value_at(history.samples["air_humidity"], ts, tol)
        watered = status.get("lastWateringAt")
        since = None if not watered else max(0.0, (ts - watered) / 60000)
        missing = []
        for zone in ZONES:
            series = history.samples["moisture"][zone]
            m0 = History.value_at(series, ts, tol)
            lagged = [History.value_at(series, ts - lag * 60000, tol) for lag in LAGS_MIN]
            feats = features(m0, lagged[0], lagged[1], temperature, humidity, since)
            if feats is None:
                missing.append(zone)
                continue
            predicted = round(model.predict(feats), 2)
            body["forecasts"].append({"zoneId": zone, "ts": iso(ts), "predicted": predicted,
                                      "baseline": round(m0, 2)})
            writer.writerow([iso(ts), zone, model.version, m0, predicted, "" if since is None else round(since, 1)])
        if body["forecasts"]:
            body["state"] = "forecasting"
        else:
            body["state"] = "warming-up"
            needed = max(LAGS_MIN)
            have = min(history.history_minutes(z) for z in missing) if missing else 0
            body["minutesLeft"] = max(0, round(needed - have, 1))

    try:
        gateway.post("/edge/forecast", body)
    except (urllib.error.URLError, OSError) as e:
        log(f"could not hand the forecast to the gateway: {e}")
        return
    if body["forecasts"]:
        text = ", ".join(f"{f['zoneId']} {f['baseline']} -> {f['predicted']} %" for f in body["forecasts"])
        log(f"forecast +{model.horizon_min} min ({model.version}): {text}")
    elif body["state"] == "warming-up":
        log(f"collecting history: {body['minutesLeft']} min before the first forecast")
    else:
        log("no recent field reading: forecast suppressed")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--gateway", default=os.environ.get("GW_LOCAL_URL", "http://localhost:1880"))
    p.add_argument("--models", default=os.environ.get("GW_FORECAST_MODELS", os.path.join(HERE, "models", "forecast")))
    p.add_argument("--state", default=os.environ.get("GW_FORECAST_STATE", os.path.join(HERE, "state")))
    p.add_argument("--poll", type=float, default=15, help="seconds between two reads of the gateway status")
    p.add_argument("--every", type=float, default=float(os.environ.get("GW_FORECAST_EVERY_SEC", 60)),
                   help="seconds between two forecasts")
    run(p.parse_args())


if __name__ == "__main__":
    sys.exit(main())
