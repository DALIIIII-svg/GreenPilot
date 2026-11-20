# GreenPilot – edge side (simulated hardware)

Without the physical greenhouse, the edge runs on the PC: the ESP32 field node in the Wokwi simulator
(or a small Node.js simulator) and Node-RED playing the Raspberry Pi gateway. The cloud side is unchanged.

```
ESP32 field node  --WebSocket/JSON-->  Node-RED gateway  --MQTT v5/TLS-->  Mosquitto  -->  WildFly API  -->  PWA
(Wokwi or simulator)  :1880/ws/esp32   (plays the Pi)       pi-01, :8883
```

| Folder | Content |
|---|---|
| `esp32-node/` | ESP32 firmware (PlatformIO, Arduino) + Wokwi circuit (`diagram.json`) |
| `gateway/` | Node-RED gateway: `flows.json`, `settings.js`, W3C WoT Thing Description of the field node |
| `simulator/` | `esp32-sim.js`: same protocol as the firmware, controlled from the keyboard |
| `edge-ml/` | gateway ML services (Python, standard library only): `forecast_service.py` + the installed model releases |

## Run it

1. Start the backend as usual (MongoDB, `start-mosquitto.ps1`, `run-wildfly.ps1`). The site `greenhouse-01`
   with gateway `pi-01` must exist (`samples\test-flow.ps1` creates it).
2. Gateway: `powershell -ExecutionPolicy Bypass -File setup\start-gateway.ps1`
   (first run installs Node-RED). Node-RED editor: <http://localhost:1880>, gateway state: <http://localhost:1880/status>.
3. Field node, one of the two:
   - **Wokwi (VS Code)**: install the *PlatformIO IDE* and *Wokwi Simulator* extensions, open the `iot/esp32-node`
     folder, run *PlatformIO: Build*, then F1 → *Wokwi: Request a New License* (once) and F1 → *Wokwi: Start Simulator*.
     Turn the potentiometers (soil A / B), flip the switch (reservoir), watch the blue LED (pump).
     The firmware reaches the PC through `host.wokwi.internal`.
   - **Simulator**: `powershell -ExecutionPolicy Bypass -File setup\start-esp32-sim.ps1`.
4. Forecast service: `powershell -ExecutionPolicy Bypass -File setup\start-forecast.ps1` (needs Python 3).
   It collects 20 minutes of readings, then forecasts each zone 30 minutes ahead every minute.
5. Open the PWA: the readings update, *Water now* switches the pump on, and each soil card shows the forecast.

## ESP32 <-> gateway messages (JSON text frames)

| Direction | `type` | Fields |
|---|---|---|
| ESP32 -> gateway | `hello` | `deviceId`, `firmware` |
| ESP32 -> gateway | `telemetry` | `deviceId`, `ts` (epoch ms, 0 if no clock; the gateway stamps the uplink with its own clock), `pump`, `readings[{type, zoneId, value, unit, quality}]` |
| ESP32 -> gateway | `ack` | `commandId`, `status` (ACCEPTED, REJECTED, COMPLETED, FAILED), `reason` |
| ESP32 -> gateway | `event` | `name` (`lowWater`), `value` |
| gateway -> ESP32 | `command` | `commandId`, `action` (IRRIGATE), `durationSec`, `expiresAt` (epoch ms) |
| gateway -> ESP32 | `config` | `maxDurationSec`, `now` (epoch ms: the field node takes its clock from the gateway) |

## What the gateway does

- Turns ESP32 telemetry into `greenpilot/pi-01/up/telemetry` messages (`msgId` = gateway + device + timestamp).
- **F03**: while the broker is unreachable, uplink messages wait in the persistent context store and are sent again
  in order on reconnection; the backend ignores duplicates thanks to the `msgId`.
- **F02**: checks every cloud command before the pump (duplicate, expiry, field node online, reservoir, duration)
  and acknowledges on `up/cmd-ack` with the MQTT 5 correlation data. `UPDATE_CONFIG` updates the local limits.
- Local rule: when a zone is below the moisture threshold with water available, it waters for 10 s, then waits
  for the cooldown. Disable with `GW_AUTO_IRRIGATION=false`.
- The firmware keeps its own protections: pump off at start, bounded duration, stops if the reservoir empties.

## Soil-moisture forecast on the gateway (`edge-ml/`)

```
Node-RED  --GET /status every 15 s-->  forecast_service.py  --POST /edge/forecast every 60 s-->  Node-RED  --up/forecast-->  cloud
```

- The service keeps 2 h of readings per zone (`edge-ml/state/forecast-history.json`, so a restart keeps them).
- Inputs: moisture now and 10 / 20 min ago, air temperature and humidity, minutes since the last watering
  (from `lastWateringAt`, recorded by the gateway on every completed watering). A missing, stale (> 2 min) or
  incomplete input suppresses the forecast; `/status` then shows `warming-up` or `stale-input`.
- Model: the release named in `edge-ml/models/forecast/active.json` (trained in `model/forecast`), reloaded within a
  minute when it changes; `active.json` also keeps the previous release for rollback (`model/forecast/deploy.py`).
- Node-RED publishes one `up/forecast` message per zone (`msgId` = gateway + zone + reading time) through the same
  offline buffer as the telemetry, and shows the last forecasts in `GET /status` → `forecast`.
- Every forecast is logged in `edge-ml/state/forecast-log.csv` (time, zone, model, current, predicted, minutes since
  watering) for the later review of forecast errors.

## Local API of the gateway (W3C Web of Things)

| Request | Result |
|---|---|
| `GET /status` | gateway state (cloud link, field node, buffer, configuration, last readings, last watering, forecast) |
| `POST /edge/forecast` | used by the forecast service (local only) |
| `GET /things/esp32-node` | Thing Description (`application/td+json`) |
| `GET /things/esp32-node/properties/{name}` | `soilMoistureA`, `soilMoistureB`, `airTemperature`, `airHumidity`, `waterAvailable`, `pumpState` |
| `POST /things/esp32-node/actions/irrigate` | `{"durationSec": 5}`, same checks as the cloud commands |
| `ws://…/ws/things/esp32-node/events/lowWater` | reservoir events |

## Not done yet

- The local link uses `ws://` and `http://`; the specification asks for WSS/HTTPS with provisioned certificates.
- Camera loop and YOLO detection (Mahdi); `DEPLOY_MODEL` from the cloud is answered with REJECTED for now
  (forecast releases are installed with `model/forecast/deploy.py`).
