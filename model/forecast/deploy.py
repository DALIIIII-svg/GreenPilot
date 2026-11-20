"""Installs a forecast release on the gateway and keeps the previous one for rollback.

    python deploy.py <version>       # e.g. forecast-20261120-1730-linear
    python deploy.py --rollback      # back to the previous release

The forecast service reloads the model within a minute; no restart needed.
"""
import argparse
import hashlib
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
GATEWAY_MODELS = os.path.join(HERE, "..", "..", "iot", "edge-ml", "models", "forecast")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("version", nargs="?")
    p.add_argument("--rollback", action="store_true")
    p.add_argument("--target", default=GATEWAY_MODELS)
    args = p.parse_args()

    pointer = os.path.join(args.target, "active.json")
    with open(pointer, encoding="utf-8") as f:
        active = json.load(f)

    if args.rollback:
        if not active.get("previous"):
            raise SystemExit("no previous release to roll back to")
        new = {"active": active["previous"], "previous": active["active"]}
    else:
        if not args.version:
            raise SystemExit("give a release version, or --rollback")
        folder = os.path.join(HERE, "releases", args.version)
        with open(os.path.join(folder, "manifest.json"), encoding="utf-8") as f:
            manifest = json.load(f)
        source = os.path.join(folder, "model.json")
        with open(source, "rb") as f:
            if hashlib.sha256(f.read()).hexdigest() != manifest["modelSha256"]:
                raise SystemExit("model.json does not match its manifest checksum")
        shutil.copyfile(source, os.path.join(args.target, f"{args.version}.json"))
        new = {"active": args.version, "previous": active["active"]}

    # smoke test before switching: the release loads and predicts a plausible value
    import sys
    sys.path.insert(0, os.path.join(args.target, "..", ".."))
    from forecast_model import ForecastModel, features
    model = ForecastModel.load(os.path.join(args.target, f"{new['active']}.json"))
    value = model.predict(features(40.0, 40.5, 41.0, 24.0, 60.0, 600))
    if not 0 <= value <= 100:
        raise SystemExit(f"smoke test failed: predicted {value}")

    tmp = pointer + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(new, f, indent=2)
    os.replace(tmp, pointer)
    print(f"active: {new['active']} (previous: {new['previous']}); smoke test 40 % -> {value:.2f} %")


if __name__ == "__main__":
    main()
