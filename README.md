# GreenPilot

Cloud of Things project (SUP'COM): connected greenhouse, bounded irrigation, visual disease tracking.

```
GreenPilot/
├── middleware/                  Maven multi-module build
│   ├── core/                    shared jar: NosqlEntity, UUIDv7, ETag, EdDSA JWT verification, @Secured, error mappers
│   ├── iam/   -> iam.war        OAuth 2.0 authorization server (Authorization Code + PKCE), /iam
│   └── api/   -> greenpilot.war REST + WebSocket + MQTT v5 middleware, /greenpilot
├── front/                       PWA in plain HTML/CSS/JavaScript (no framework, no build step)
├── setup/                       Windows scripts: install, certificates, MQTT ACL, build, run
├── samples/                     test data + end-to-end test (simulates the Pi and the PWA)
└── tools/                       JDK 25, Maven, WildFly 41, certificates, broker config (generated, not in git)
```

Each module follows **Boundary – Control – Entity**: `boundaries` (REST/WebSocket/MQTT contracts),
`controllers` (managers = business logic, repositories = Jakarta Data), `entities` (Jakarta NoSQL).

## Quick start (PowerShell, from the repository root)

```powershell
powershell -ExecutionPolicy Bypass -File .\setup\setup-windows.ps1     # once
powershell -ExecutionPolicy Bypass -File .\setup\start-mosquitto.ps1   # window 1
powershell -ExecutionPolicy Bypass -File .\setup\run-wildfly.ps1       # window 2 (MongoDB must be running)
powershell -ExecutionPolicy Bypass -File .\setup\build.ps1             # window 3: build and deploy the WARs
powershell -ExecutionPolicy Bypass -File .\samples\test-flow.ps1       # optional: sample site, readings and an incident
powershell -ExecutionPolicy Bypass -File .\setup\serve-front.ps1       # window 4: the PWA
```

Open <http://localhost:5500> and sign in with `admin@greenpilot.local` / `admin123`.
Use `localhost` (not `127.0.0.1`) and port 5500: the IAM only accepts the registered redirect URI
`http://localhost:5500/callback.html`. `serve-front.ps1` needs Node.js (`npx`) or Python.

A minimal test page is also served by WildFly at <http://localhost:8080/greenpilot/live-demo.html>.

## Front-end (PWA)

`front/` is a single-page app written in plain HTML, CSS and JavaScript modules, served as static files.
It talks to the middleware on `http://localhost:8080` (CORS is enabled for development).

| Page | Who | What |
|---|---|---|
| Dashboard | grower, admin | latest readings, soil moisture forecast, 24 h chart, irrigation |
| Incidents / incident detail | grower, admin | disease alerts, photo with detection boxes, acknowledge → review → close |
| History | grower, admin | measurements, forecast vs measured, camera inspections, CSV export |
| Nearby | grower, admin | map of the greenhouses, search around the phone's location |
| Sites & devices | admin | greenhouses, zones, limits, devices and their Thing Descriptions |
| Users & access | admin | accounts, roles and the greenhouses each person can see |
| Models & audit | admin | model releases, deployment and rollback per greenhouse, audit log |

```
front/
├── index.html, callback.html    app shell and OAuth redirect page
├── manifest.webmanifest, sw.js  installable app + offline cache of the app files
├── css/style.css
├── icons/
├── vendor/leaflet/              map library (Leaflet 1.9.4, BSD-2-Clause)
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
| Tokens | 15-min JWT signed **EdDSA/Ed25519**; API verifies with the IAM public keys (`/iam/jwks`), checks `iss`, `aud`, `exp`, `alg` |
| Authorization | roles (`groups`: GROWER/ADMIN) + scopes (`greenhouse:read`, `greenhouse:control`, `incidents:manage`, `admin`) + per-user site list |
| Refresh | opaque, stored hashed, rotated on every use |
| MQTT | TLS 1.3 only (port 8883), no anonymous, one user per gateway, ACL: a gateway only writes `greenpilot/<itself>/up/#` |
| Concurrency | `version` + ETag: `If-None-Match` → 304, `If-Match` → 412, atomic version claim in MongoDB |

## REST API (`http://localhost:8080/greenpilot/api`, Bearer token)
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

IAM (`http://localhost:8080/iam`): `GET /authorize`, `POST /token`, `GET /jwks`, `GET/POST/PUT /identities` (admin).

WebSocket `ws://localhost:8080/greenpilot/ws`: send `{"type":"auth","token":"…"}` first, then receive
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
- Dev only: self-signed CA, signing key stored in MongoDB, default passwords in `setup/env.ps1`.
- `Instant` fields are stored by JNoSQL as ISO-8601 text (sortable); two values inside the same second may sort slightly off.
- The PWA and the API run on two origins (5500 and 8080) in development; in production, Nginx with HTTPS should serve both on one origin.
- Automated tests (JUnit + Testcontainers) and CI.
