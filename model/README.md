# Machine learning

| Folder | Model | Runs on | Status |
|---|---|---|---|
| [`forecast/`](forecast/README.md) | soil moisture at t + 30 min (gradient-boosted trees vs persistence) | gateway, `iot/edge-ml/forecast_service.py` | trained, released, integrated |
| [`vision/`](vision/README.md) | bell pepper leaves: healthy / bacterial spot (YOLO) | gateway (to be integrated) | classification model trained; detector and gateway integration to do |

Both follow the specification's MLOps rules (§4.4): training on the PC, separate from the live services; versioned
releases with their data references, metrics and checksums; deployment to the gateway with a smoke test and the
previous release kept for rollback; incoming observations never change a deployed model.
