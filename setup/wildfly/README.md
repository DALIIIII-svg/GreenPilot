# WildFly hosting for GreenPilot

The course's CI/CD scripts configure WildFly so that it serves the whole application, one Undertow virtual host per part:

| Script | Properties | Result |
|---|---|---|
| `default-host.cli` | `default-host-<env>.properties` | Front Office on the default host (`user.<domain>`); defines the shared filters; reloads |
| `add-host.cli` | `admin-host-<env>.properties` | Back Office on `admin-host` (`admin.<domain>`) |
| `add-api.cli` | `api-<env>.properties` | `api-1.0.war` at the root of `api-host` (`api.<domain>`) |
| `add-api.cli` | `iam-<env>.properties` | `iam-1.0.war` at the root of `iam-host` (`iam.<domain>`) |
| `https.cli` | `https-<env>.properties` | HTTPS listener with the PKCS#12 certificate |
| `reset-hosts.cli` | - | removes what the scripts above added, so they can be applied again |

`add-api.cli` serves `${api.name}-${api.version}.war`, so the Maven `finalName` of the two WARs is `api-1.0` and `iam-1.0`.

Every host gets the same filters: HSTS on port 443, HTTP to HTTPS redirection on port 80, the SPA router (a 404 returns
`index.html`, front-ends only), `Cache-Control: private,no-cache`, `X-Content-Type-Options: nosniff`, and the CORS headers, sent
only when the `Origin` is an `https://` origin of `*.domains`. The rule does not accept an explicit port, so WildFly listens
on the standard ports 80 and 443, in development too (`setup/run-wildfly.ps1`).

## Development (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File .\setup\make-certs.ps1 -Force     # once: CA + tools/certs/wildfly.p12 + jvm-truststore.p12
powershell -ExecutionPolicy Bypass -File .\setup\trust-dev-ca.ps1          # once: Windows trusts the CA
powershell -ExecutionPolicy Bypass -File .\setup\configure-wildfly-hosts.ps1
```

`configure-wildfly-hosts.ps1` writes the `*-dev.properties` files from their `.example` with the path of the checkout,
keeps a copy of the original `standalone.xml` (`standalone.xml.before-hosts`), then runs, against the running server:
`reset-hosts.cli`, `https.cli`, `default-host.cli`, `add-host.cli`, `add-api.cli` (api), `add-api.cli` (iam), and a reload.

## Other environments (CI/CD)

Create the `*-prod.properties` files from their `.example` (paths, domain, certificate), publish `frontend/frontoffice` and
`frontend/backoffice` to the configured paths, deploy the two WARs, then either run
`configure-wildfly-hosts.ps1 -Environment prod -Controller remote+https://your-wildfly-host:9993`, or each script directly:

```sh
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=remote+https://your-wildfly-host:9993 --timeout=120000 \
  --file=setup/wildfly/default-host.cli --properties=setup/wildfly/properties/default-host-prod.properties
```

Real `.properties` files are not committed (`.gitignore`). Set `GP_JWT_ISSUER` (IAM and API), `GP_IAM_JWKS_URL` (API) and
`GP_IAM_PWA_REDIRECT_URIS` (IAM) for the production domain.
