# GreenPilot frontend

React 18, TypeScript, Vite, Tailwind, TanStack Query, Axios, Recharts, Leaflet, and Lucide frontend for the existing GreenPilot middleware.

## Run locally

From `frontend/`:

```powershell
npm install
npm run dev
```

Vite serves on `http://localhost:5500` and proxies `/greenpilot` and `/iam` to the WildFly backend on port 8080. The default OAuth callback is `http://localhost:5500/callback.html`; this URI is in the repository's IAM redirect allowlist. Copy `.env.example` to `.env` to override API, WebSocket, client, scope, or redirect settings. Any deployed callback URI must be added exactly to the IAM PWA tenant configuration.

Build with `npm run build`; preview the output with `npm run preview`. The Nginx sample in `deploy/nginx.conf` serves `dist/` at the origin root and proxies `/greenpilot` and `/iam` to the backend. Set certificate paths and the backend upstream for the deployment. Set `VITE_OAUTH_REDIRECT_URI` at build time and add that exact HTTPS URI to IAM before production sign-in.

## Backend integration notes

- OAuth is authorization code + PKCE against `/iam/authorize` and `/iam/token`. Tokens are scoped to the browser tab in `sessionStorage`; refresh requests serialize because IAM rotates opaque refresh tokens. The backend has no logout endpoint, so the frontend clears its session and returns to the sign-in page.
- REST calls use the contracts in the middleware resources. Conditional site, incident, and identity changes preserve ETags where the API supplies them. WebSocket auth sends the token in the first message (never in the URL) and invalidates only affected TanStack Query data.
- Site and zone edits are represented by the backend's embedded site request schema. JSON fields for zones/plants, irrigation limits, and thresholds are editable in the site form. The backend has no site DELETE route.
- Irrigation currently accepts `{siteId, durationSec}`; the backend does not accept a zone ID. Cooldown duration is configured on the site, but remaining cooldown time is not exposed. Command state is `202 Accepted`, then updated through WebSocket/history.
- Device records expose configuration and Thing Description, but no online/offline or last-communication fields. Dashboard weather, water-consumption totals, deployment status aggregation, and device online counts likewise have no current REST response and are shown as unavailable.
- User site assignments use `PUT /greenpilot/api/users/{subject}/sites`. IAM role changes use the existing `/iam/identities/{id}` conditional update. Accounts are not created here because the user request focused on access management and the IAM create contract requires a password.
- Measurements, forecasts, and inspections are loaded from their documented endpoints. History export downloads the selected real measurements as JSON. Server-side filters support site, zone, type, and time range; the current UI limits histories to the returned API page size.
- `GET /audit` supports site and limit filters only; the UI shows the current bounded result set. Backend pagination and action/status filtering are not available.

The UI never substitutes demonstration records for missing backend data. Unavailable capabilities are labeled in context.
