# Frontend Architecture

## 1. Overview

GreenPilot now has two independent static web applications. The Front Office serves growers and other regular users. The Back Office serves administrators and contains administrative pages only. This reduces the public interface's attack surface and keeps administrative navigation and code out of the user application. Backend authorization remains the security boundary for every protected operation.

The project uses the existing no-build PWA approach. There are no frontend package manifests, package manager lock files, or frontend build commands because the applications use native browser JavaScript modules and are served as static files.

## 2. Architecture

```text
frontend/
├── frontoffice/
│   ├── index.html, callback.html
│   ├── js/                 app, auth, API, state, router, live updates, UI
│   │   └── pages/          dashboard, incidents, history, nearby
│   ├── css/style.css
│   ├── icons/, vendor/     app icons and Leaflet map library
│   ├── sw.js               Front Office offline shell
│   └── manifest.webmanifest
├── backoffice/
│   ├── index.html, callback.html
│   ├── js/                 app, auth, API, state, router, live updates, UI
│   │   └── pages/          sites, users, models and audit
│   ├── css/style.css
│   ├── icons/              app icons
│   ├── sw.js               Back Office offline shell
│   └── manifest.webmanifest
└── README.md
```

The `js/` and `js/pages/` layout follows the existing PWA conventions. No component, hook, or utility framework is used by this project, so empty framework-style folders were not added.

## 3. Front Office

The Front Office is for growers. It provides the greenhouse dashboard, incident list and detail, measurement history and export, nearby greenhouse map, OAuth sign-in, and session controls. It contains no administrative pages or administration navigation. It requests only grower scopes.

Technologies are native HTML, CSS, JavaScript ES modules, browser APIs, and Leaflet 1.9.4. The app entry and route table are in `js/app.js`; API and OAuth modules are in `js/api.js` and `js/auth.js`; individual screens are in `js/pages/`.

## 4. Back Office

The Back Office provides administrative sign-in, site and device management, user and site access management, model deployment and audit views. Its application shell registers only administrative routes. After `/users/me` is loaded, the app requires both the `ADMIN` role and `admin` scope before opening the console. Non-administrators are signed out of the interface.

It uses the same existing HTML, CSS, native JavaScript modules, and browser APIs. The independent entry point, auth and API modules, router, and admin pages live in the Back Office directory.

## 5. Technology Stack

Both apps preserve the existing stack: plain HTML5, CSS, JavaScript ES modules, native `fetch`, Web Crypto for PKCE, WebSocket for live updates, and Service Workers for static app-shell caching. The Front Office uses Leaflet 1.9.4 for maps. There is no frontend framework, UI component library, CSS framework, state library, bundler, npm dependency, or package manager. The backend remains Java/Jakarta EE, Maven, MongoDB, and the existing OAuth 2.0 IAM/API services.

## 6. Installation

No frontend dependency installation is required. Install the backend prerequisites as documented in the repository root README. The static server can use Node.js (`npx`) or Python; the existing PowerShell scripts choose the available server.

## 7. Configuration

The frontend has no `.env` files: browser environment values are public, and the current setup uses explicit development configuration in each app's `js/config.js`. No credentials or signing keys belong in frontend files.

The local domain plan uses separate names while keeping the existing service ports and WildFly deployment:

| URL | Responsibility | Local target |
|---|---|---|
| `http://user.greenpilot.local:5500` | Grower Front Office | Static server for `frontend/frontoffice/` |
| `http://admin.greenpilot.local:5501` | Administrator Back Office | Static server for `frontend/backoffice/` |
| `http://iam.greenpilot.local:8080/iam` | OAuth authorization, tokens, identities and JWKS | Existing IAM WAR on WildFly |
| `http://api.greenpilot.local:8080/greenpilot/api` | GreenPilot REST API | Existing API WAR on WildFly |

The example hosts entries are in [`../setup/hosts.example`](../setup/hosts.example). Add them to `C:\Windows\System32\drivers\etc\hosts` from an elevated editor. They map all four names to `127.0.0.1`. A hosts file resolves names only; it cannot map ports. Therefore the `:5500`, `:5501`, and `:8080` ports remain in the development URLs.

This gives the browser separate app origins and distinct IAM/API hostnames, but IAM and API still run in the same WildFly server process and port. The API and IAM CORS allowlist is configured by `gp.cors.origin` in their MicroProfile config files. The token issuer and API JWKS URL must use the same IAM hostname; these are set in the IAM and API configs. The OAuth public client is `greenpilot-pwa`. Its exact callbacks include `http://user.greenpilot.local:5500/callback.html` and `http://admin.greenpilot.local:5501/callback.html` as well as the localhost callbacks.

For a different host name, update `js/config.js`, the IAM redirect allowlist, the API and IAM CORS origins, and the API's expected JWT issuer/JWKS URL together. In production, configure real DNS and HTTPS and use server-side configuration.

## 8. Running the Applications

Start MongoDB, then from the repository root start the IAM/API and broker using the existing setup scripts. Run each frontend in its own PowerShell window:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup\serve-front.ps1
powershell -ExecutionPolicy Bypass -File .\setup\serve-backoffice.ps1
```

With the hosts entries configured, use <http://user.greenpilot.local:5500> for growers and <http://admin.greenpilot.local:5501> for administrators. The localhost frontend URLs also remain supported when the hosts entries are present, because the API uses the IAM hostname to load JWKS keys. Restart/redeploy IAM and API after changing their MicroProfile configuration. Use an appropriate account for the selected application.

## 9. Build and Production

There is no frontend build step. Deploy each directory's static files independently to its own static host or origin. Configure HTTPS, the backend/API origin, IAM callback allowlists, CORS, and WebSocket routing for the deployment. The root `setup/build.ps1` builds and deploys the Java backend WAR files; it does not build the frontends.

## 10. Security Architecture

Front Office and Back Office have separate static roots, route tables, page modules, and service worker caches. The Front Office omits admin pages and requests no `admin` scope. The Back Office checks role and scope after authentication and renders only administrator routes. The local domain names do not replace this backend authorization. OAuth Authorization Code with PKCE, token refresh, storage, and API calls retain the existing implementation.

Client-side checks improve separation and navigation but do not authorize operations. IAM identity endpoints and administrative API operations are protected by backend `@Secured` role/scope checks. Do not remove or weaken those server controls. API responses are not cached by either service worker. For production, serve over HTTPS and keep secrets on the server.

## 11. Development Guidelines

Keep user screens and user-facing navigation in `frontoffice/`; keep administrative screens and navigation in `backoffice/`. Put page modules in each app's `js/pages/`, shared-per-app API/auth/state/router behavior in `js/`, and styles in `css/`. The apps are deployed independently; avoid importing modules across application roots. Update the corresponding service worker precache list when adding or removing app-shell files. Keep API contracts aligned with the existing middleware and enforce permissions in the backend.

## 12. Troubleshooting

- **OAuth reports an unregistered redirect URI:** confirm the exact origin and callback path are present in `gp.iam.pwa.redirect-uris`, then restart/redeploy IAM.
- **The page loads but API calls fail:** confirm WildFly and MongoDB are running, check the backend URL in `js/config.js`, and verify the backend CORS origin permits the frontend origin.
- **Live status keeps reconnecting:** check the backend WebSocket endpoint and its origin/security settings.
- **A stale interface appears offline:** clear the site's service worker/cache or reload while online; Front Office and Back Office use distinct cache names.
- **The Back Office denies access:** authenticate with an identity carrying both `ADMIN` and the `admin` scope. API authorization remains enforced independently by the server.
- **No static server is available:** install Node.js or Python, then rerun the matching PowerShell script.
