# GreenPilot

Cloud of Things project (SUP'COM): connected greenhouse, bounded irrigation, visual disease tracking.

```
GreenPilot/
├── middleware/                  Maven multi-module build
│   ├── core/                    shared jar: NosqlEntity, UUIDv7, ETag, EdDSA JWT verification, @Secured, error mappers
│   ├── iam/   -> iam-1.0.war    OAuth 2.0 authorization server (Authorization Code + PKCE), https://iam.<domain>/
│   └── api/   -> api-1.0.war    REST + WebSocket + MQTT v5 middleware, https://api.<domain>/
├── frontend/                    Front Office and Back Office PWAs (plain HTML/CSS/JavaScript)
├── iot/                         edge side: ESP32 firmware (Wokwi), Node-RED gateway, field node simulator
├── setup/                       Windows scripts: install, certificates, MQTT ACL, build, run, WildFly hosts (wildfly/)
├── deploy/                      Linux server: installation, configuration and deployment scripts (see deploy/README.md)
├── .github/workflows/           CI (build + tests on every pull request) and Deploy (to the Linux server)
├── samples/                     test data + end-to-end test (simulates the Pi and the PWA)
└── tools/                       JDK 25, Maven, WildFly 41, certificates, broker config (generated, not in git)
```

Each module follows **Boundary – Control – Entity**: `boundaries` (REST/WebSocket/MQTT contracts),
`controllers` (managers = business logic, repositories = Jakarta Data), `entities` (Jakarta NoSQL).

## Quick start (PowerShell, from the repository root)

WildFly serves everything, one virtual host per part, over HTTPS on the standard ports (80 redirects to 443):

| Host | Served by WildFly | URL |
|---|---|---|
| `user.greenpilot.local` | Front Office (default host, `frontend/frontoffice`) | <https://user.greenpilot.local> |
| `admin.greenpilot.local` | Back Office (`frontend/backoffice`) | <https://admin.greenpilot.local> |
| `api.greenpilot.local` | `api-1.0.war`: REST `/api`, WebSocket `/ws` | <https://api.greenpilot.local/api/health> |
| `iam.greenpilot.local` | `iam-1.0.war`: `/authorize`, `/token`, `/jwks`, `/identities` | <https://iam.greenpilot.local/jwks> |

Once, in this order:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup\setup-windows.ps1      # JDK, Maven, WildFly, MongoDB, Mosquitto
powershell -ExecutionPolicy Bypass -File .\setup\make-certs.ps1 -Force  # CA, broker certificate, WildFly HTTPS certificate
powershell -ExecutionPolicy Bypass -File .\setup\make-mqtt-auth.ps1     # broker users, ACL, configuration
powershell -ExecutionPolicy Bypass -File .\setup\trust-dev-ca.ps1       # Windows (Chrome, Edge) trusts the development CA
```

Add the four names of [`setup/hosts.example`](setup/hosts.example) to `C:\Windows\System32\drivers\etc\hosts` (administrator).
Ports 80 and 443 must be free (`netstat -ano | findstr ":443 "`).

Each session:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup\start-mosquitto.ps1   # window 1
powershell -ExecutionPolicy Bypass -File .\setup\run-wildfly.ps1       # window 2 (MongoDB must be running)
powershell -ExecutionPolicy Bypass -File .\setup\build.ps1             # window 3: build and deploy the WARs (after code changes)
powershell -ExecutionPolicy Bypass -File .\setup\configure-wildfly-hosts.ps1   # window 3: once, or after changing setup/wildfly
powershell -ExecutionPolicy Bypass -File .\samples\test-flow.ps1       # optional: sample site, readings and an incident
powershell -ExecutionPolicy Bypass -File .\setup\start-gateway.ps1     # window 4: Node-RED gateway (plays the Raspberry Pi)
powershell -ExecutionPolicy Bypass -File .\setup\start-esp32-sim.ps1   # window 5: simulated ESP32 (or Wokwi, see iot/README.md)
```

Open <https://admin.greenpilot.local> with the development administrator `admin@greenpilot.local` / `admin123`,
create a grower account with access to a greenhouse, and use it on <https://user.greenpilot.local>.
A minimal test page is served by the API at <https://api.greenpilot.local/live-demo.html>.

## Edge side (simulated hardware)

Until the greenhouse hardware is available, the ESP32 field node runs in the Wokwi simulator (or `iot/simulator`) and Node-RED plays the Raspberry Pi gateway between the ESP32 and the MQTT broker. Details, messages and the gateway's W3C WoT API are in [`iot/README.md`](iot/README.md).

## WildFly hosting (CI/CD)

The host configuration uses the CLI scripts and per-environment property files of [`setup/wildfly/`](setup/wildfly/README.md):
`default-host.cli` (Front Office), `add-host.cli` (Back Office), `add-api.cli` (API and IAM, each WAR at the root of its host),
plus `https.cli` (certificate) and `reset-hosts.cli` (so the configuration can be applied again). They also add HSTS,
HTTP to HTTPS redirection, the SPA router, `Cache-Control`, `X-Content-Type-Options` and the CORS headers, which are sent only to
`https://` origins of the configured domain. The applications therefore no longer add CORS headers themselves.

## CI/CD pipeline (GitHub Actions)

- **CI** (`.github/workflows/ci.yml`): on every pull request to `main` and every push to `main`, GitHub compiles the three
  modules with Java 25, runs the JUnit tests (`mvn verify`), keeps the two WARs as artifacts, and checks the gateway files.
  Run the same build locally with `mvn -f middleware/pom.xml verify`.
- **Deploy** (`.github/workflows/deploy.yml`): builds and tests, then copies the release (WARs, front-ends, `setup/wildfly`)
  to the Linux server over SSH and runs `deploy/linux/deploy.sh`, which applies the WildFly host scripts with the production
  properties. Started by hand for now; server setup and secrets in [`deploy/README.md`](deploy/README.md).

## Front-end (PWA)

`frontend/frontoffice/` and `frontend/backoffice/` are independent single-page apps written in plain HTML, CSS and JavaScript modules. WildFly serves them from file handlers on the `user` and `admin` virtual hosts; both call the API and the IAM on their sibling hosts `api.<domain>` and `iam.<domain>`.

| Page | Who | What |
|---|---|---|
| Dashboard | grower | latest readings, soil moisture forecast, 24 h chart, irrigation |
| Incidents / incident detail | grower | disease alerts, photo with detection boxes, acknowledge → review → close |
| History | grower | measurements, forecast vs measured, camera inspections, CSV export |
| Nearby | grower | map of the greenhouses, search around the phone's location |
| Sites & devices | admin | greenhouses, zones, limits, devices and their Thing Descriptions |
| Users & access | admin | accounts, roles and the greenhouses each person can see |
| Models & audit | admin | model releases, deployment and rollback per greenhouse, audit log |

```
frontend/frontoffice/
├── index.html, callback.html    user app shell and OAuth redirect page
├── manifest.webmanifest, sw.js  installable app + offline cache of the user app
├── css/style.css
├── icons/, vendor/leaflet/      icons and map library (Leaflet 1.9.4, BSD-2-Clause)
└── js/
    ├── config.js, auth.js       endpoints, sign-in with Authorization Code + PKCE, token refresh
    ├── api.js, live.js          REST client (Bearer, ETag / If-Match), WebSocket with reconnect
    ├── router.js, state.js      hash router, current user and selected greenhouse
    ├── ui.js                    DOM helpers, tables, dialogs, SVG line chart
    └── pages/                   one module per page
```

Sign-in uses the public client `greenpilot-pwa`. Tokens are kept in `localStorage`; the API calls are
never cached by the service worker. The live updates come from the WebSocket below.

## Security model
| Layer | Mechanism |
|---|---|
| Users | IAM: OAuth 2.0 Authorization Code + PKCE (S256), public PWA client, exact redirect URIs, `state` |
| Tokens | 15-min JWT signed **EdDSA/Ed25519**; API verifies with the IAM public keys (`https://iam.<domain>/jwks`), checks `iss`, `aud`, `exp`, `alg` |
| Authorization | roles (`groups`: GROWER/ADMIN) + scopes (`greenhouse:read`, `greenhouse:control`, `incidents:manage`, `admin`) + per-user site list |
| Refresh | opaque, stored hashed, rotated on every use |
| MQTT | TLS 1.3 only (port 8883), no anonymous, one user per gateway, ACL: a gateway only writes `greenpilot/<itself>/up/#` |
| Concurrency | `version` + ETag: `If-None-Match` → 304, `If-Match` → 412, atomic version claim in MongoDB |

## REST API (`https://api.greenpilot.local/api`, Bearer token)
| Method | Path | Needs |
|---|---|---|
| GET | /health | public |
| GET | /users/me | any token |
| GET | /sites, /sites/{id} (ETag), /sites/{id}/state, /sites/nearby?lat&lng&radiusKm | read |
| POST/PUT | /sites, /sites/{id} (If-Match) | admin |
| GET | /devices?siteId, /devices/{id}, /devices/{id}/td (`application/td+json`) | read |
| PUT | /devices/{id} | admin |
| GET | /measurements, /forecasts, /inspections (?siteId&…&from&to&limit) | read |
| POST | /irrigation `{siteId,durationSec}` → 202 | read + control |
| GET | /incidents?siteId&status, /incidents/{id} | read |
| POST | /incidents/{id}/ack, /review, /close (If-Match) | read + incidents:manage |
| GET | /images/{imageKey} | read |
| GET/POST | /models, POST /models/{id}/deploy | read / admin |
| PUT | /users/{subject}/sites | admin |
| GET | /audit | admin |

IAM (`https://iam.greenpilot.local`): `GET /authorize`, `POST /token`, `GET /jwks`, `GET/POST/PUT /identities` (admin).

WebSocket `wss://api.greenpilot.local/ws`: send `{"type":"auth","token":"…"}` first, then receive
`{"siteId","type":"telemetry|forecast|inspection|command|incident.opened|incident.updated","data":{…}}`.

## MQTT v5 contract (TLS, QoS 1, never retained, `{gw}` = gatewayId = MQTT username)
| Topic | Dir | Payload |
|---|---|---|
| `greenpilot/{gw}/up/telemetry` | Pi→cloud | `{"msgId","deviceId","ts","readings":[{"type","zoneId","value","unit","quality"}]}` |
| `greenpilot/{gw}/up/forecast` | Pi→cloud | `{"msgId","zoneId","ts","horizonMin","predicted","baseline","modelVersion"}` |
| `greenpilot/{gw}/up/inspection` | Pi→cloud | `{"msgId","plantId","zoneId","ts","status","detections":[{"cls","score","bbox"}],"imageW","imageH","modelVersion","imageKey"}` |
| `greenpilot/{gw}/up/incident` | Pi→cloud | `{"eventId","plantId","zoneId","ts","score","modelVersion","imageKey","detections",…}` |
| `greenpilot/{gw}/up/image/{imageKey}` | Pi→cloud | raw JPEG |
| `greenpilot/{gw}/up/cmd-ack` | Pi→cloud | `{"commandId"?,"status":"ACCEPTED|REJECTED|COMPLETED|FAILED","reason"}` (commandId may come from correlation data) |
| `greenpilot/{gw}/down/cmd` | cloud→Pi | `{"commandId","action","issuedAt","expiresAt",…}` + MQTT5 *message expiry*, *response topic*, *correlation data* |
| `greenpilot/{gw}/down/event-ack` | cloud→Pi | `{"eventId","status":"STORED"}` once event **and** image are stored |

Messages are validated (JSON-B + Bean Validation); ids are deterministic so a re-sent buffer creates no duplicates.

## Known limitations / next steps
- Dev only: signing key stored in MongoDB, default passwords in `setup/env.ps1`.
- `Instant` fields are stored by JNoSQL as ISO-8601 text (sortable); two values inside the same second may sort slightly off.
- Development HTTPS uses a self-signed CA (`setup/make-certs.ps1`); production needs a real certificate in `https-prod.properties`.
- Unit tests cover tokens, PKCE, passwords, ids, ETags and MQTT topics; no integration tests against MongoDB/MQTT yet.
