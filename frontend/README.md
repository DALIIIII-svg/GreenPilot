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

The `js/` and `js/pages/` layout is the same in both apps.

## 3. Front Office

The Front Office is for growers. It provides the greenhouse dashboard, incident list and detail, measurement history and export, nearby greenhouse map, OAuth sign-in, and session controls. It contains no administrative pages or administration navigation. It requests only grower scopes.

Technologies are native HTML, CSS, JavaScript ES modules, browser APIs, and Leaflet 1.9.4. The app entry and route table are in `js/app.js`; API and OAuth modules are in `js/api.js` and `js/auth.js`; individual screens are in `js/pages/`.

## 4. Back Office

The Back Office provides administrative sign-in, site and device management, user and site access management, model deployment and audit views. Its application shell registers only administrative routes. After `/users/me` is loaded, the app requires both the `ADMIN` role and `admin` scope before opening the console. Non-administrators are signed out of the interface.

It uses the same existing HTML, CSS, native JavaScript modules, and browser APIs. The independent entry point, auth and API modules, router, and admin pages live in the Back Office directory.

## 5. Technology Stack

Both apps preserve the existing stack: plain HTML5, CSS, JavaScript ES modules, native `fetch`, Web Crypto for PKCE, WebSocket for live updates, and Service Workers for static app-shell caching. The Front Office uses Leaflet 1.9.4 for maps. There is no frontend framework, UI component library, CSS framework, state library, bundler, npm dependency, or package manager. The backend remains Java/Jakarta EE, Maven, MongoDB, and the existing OAuth 2.0 IAM/API services.

## 6. Installation

No frontend dependency installation is required. Install the backend prerequisites as documented in the repository root README. In the standard workflow, WildFly serves the static frontend files through Undertow; no separate frontend server is required.

## 7. Configuration

The frontend has no `.env` files: browser environment values are public, and each app's `js/config.js` derives API, IAM, WebSocket and callback origins from the current host. No credentials or signing keys belong in frontend files.

WildFly serves each part on its own virtual host, over HTTPS on the standard ports:

| URL | Responsibility | WildFly host |
|---|---|---|
| `https://user.greenpilot.local` | Grower Front Office | default host, file handler for `frontend/frontoffice/` |
| `https://admin.greenpilot.local` | Administrator Back Office | `admin-host`, file handler for `frontend/backoffice/` |
| `https://iam.greenpilot.local` | OAuth authorization, tokens, identities and JWKS | `iam-host`, `iam-1.0.war` at the root |
| `https://api.greenpilot.local/api` | GreenPilot REST API (WebSocket at `/ws`) | `api-host`, `api-1.0.war` at the root |

`js/config.js` takes the domain from the page's host name (`user.<domain>` or `admin.<domain>`) and calls `api.<domain>` and `iam.<domain>`.
The four names are in [`../setup/hosts.example`](../setup/hosts.example). The scripts and dev/prod property files are in
[`../setup/wildfly/`](../setup/wildfly/README.md): WildFly adds the CORS headers for `https://` origins of the domain, and the OAuth
callbacks are listed in `gp.iam.pwa.redirect-uris` of the IAM. HTTPS is required: sign-in (PKCE) and the service worker only work in a secure context.

## 8. Running the Applications

Follow the quick start of the root README (certificates, hosts entries, `build.ps1`, then
[`setup/configure-wildfly-hosts.ps1`](../setup/configure-wildfly-hosts.ps1)). Then open <https://user.greenpilot.local> for growers and
<https://admin.greenpilot.local> for administrators. Restart or redeploy IAM and API after changing their MicroProfile configuration.

## 9. Build and Production

There is no frontend build step. Publish `frontend/frontoffice/` and `frontend/backoffice/` to readable static directories on the WildFly host, then run the Undertow CLI scripts with the production property files. The root `setup/build.ps1` builds and deploys the Java backend WAR files; the frontend files are served directly from their configured filesystem paths. Configure HTTPS, DNS, OAuth callbacks, CORS and WebSocket access for the production origins. See [`../setup/wildfly/README.md`](../setup/wildfly/README.md) for commands and property templates.

## 10. Security Architecture

Front Office and Back Office have separate static roots, route tables, page modules, and service worker caches. The Front Office omits admin pages and requests no `admin` scope. The Back Office checks role and scope after authentication and renders only administrator routes. The local domain names do not replace this backend authorization. OAuth Authorization Code with PKCE, token refresh, storage, and API calls retain the existing implementation.

Client-side checks improve separation and navigation but do not authorize operations. IAM identity endpoints and administrative API operations are protected by backend `@Secured` role/scope checks. Do not remove or weaken those server controls. API responses are not cached by either service worker. For production, serve over HTTPS and keep secrets on the server.

## 11. Development Guidelines

Keep user screens and user-facing navigation in `frontoffice/`; keep administrative screens and navigation in `backoffice/`. Put page modules in each app's `js/pages/`, shared-per-app API/auth/state/router behavior in `js/`, and styles in `css/`. The apps are deployed independently; avoid importing modules across application roots. Update the corresponding service worker precache list when adding or removing app-shell files. Keep API contracts aligned with the existing middleware and enforce permissions in the backend.

## 12. Troubleshooting

- **OAuth reports an unregistered redirect URI:** confirm the exact origin and callback path are present in `gp.iam.pwa.redirect-uris`, then restart/redeploy IAM.
- **The page loads but API calls fail:** confirm WildFly and MongoDB are running and that `configure-wildfly-hosts.ps1` was applied (it adds the CORS headers); open the page with `https://` and without a port.
- **Live status keeps reconnecting:** check that `wss://api.greenpilot.local/ws` is reachable and that the session is still valid.
- **A stale interface appears offline:** clear the site's service worker/cache or reload while online; Front Office and Back Office use distinct cache names.
- **The Back Office denies access:** authenticate with an identity carrying both `ADMIN` and the `admin` scope. API authorization remains enforced independently by the server.
- **The browser warns about the certificate:** run `setup/trust-dev-ca.ps1` and restart the browser.
- **Sign-in does nothing:** the page must be opened over HTTPS; PKCE needs Web Crypto, which browsers only offer in a secure context.
