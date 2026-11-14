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
4. Open the PWA: the readings update, and *Water now* switches the pump on.

## ESP32 <-> gateway messages (JSON text frames)

| Direction | `type` | Fields |
|---|---|---|
| ESP32 -> gateway | `hello` | `deviceId`, `firmware` |
| ESP32 -> gateway | `telemetry` | `deviceId`, `ts` (epoch ms, 0 if no clock), `pump`, `readings[{type, zoneId, value, unit, quality}]` |
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

## Local API of the gateway (W3C Web of Things)

| Request | Result |
|---|---|
| `GET /status` | gateway state (cloud link, field node, buffer, configuration, last readings) |
| `GET /things/esp32-node` | Thing Description (`application/td+json`) |
| `GET /things/esp32-node/properties/{name}` | `soilMoistureA`, `soilMoistureB`, `airTemperature`, `airHumidity`, `waterAvailable`, `pumpState` |
| `POST /things/esp32-node/actions/irrigate` | `{"durationSec": 5}`, same checks as the cloud commands |
| `ws://…/ws/things/esp32-node/events/lowWater` | reservoir events |

## Not done yet

- The local link uses `ws://` and `http://`; the specification asks for WSS/HTTPS with provisioned certificates.
- Camera loop, soil-moisture forecast and YOLO detection (`DEPLOY_MODEL` is answered with REJECTED for now).
