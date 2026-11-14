# WildFly hosting for GreenPilot PWAs

GreenPilot's Front Office and Back Office are static PWAs. The Undertow configuration in this folder serves them from WildFly so a deployment pipeline can publish the frontend files to the server and set virtual-host paths per environment.

## Host mapping

| Host | WildFly behavior |
|---|---|
| `user.<domain>` | Default host serves `frontend/frontoffice/` at `/`. |
| `iam.greenpilot.local` | Alias of the default host; the existing `/iam` WAR remains available. |
| `admin.<domain>` | Dedicated Undertow file handler serves `frontend/backoffice/` at `/`. |
| `api.<domain>` | Dedicated virtual host routes to the deployed `greenpilot.war`; API paths remain `/greenpilot/api` and `/greenpilot/ws`. |
| `iam.<domain>` | Alias of the default host; the existing `/iam` WAR remains available. |

The host scripts use separate property files. The tracked `*.properties.example` files are templates; actual environment files ending in `.properties` are git-ignored. The WildFly service account must be able to read both static directories.

## Windows development setup

From the repository root, copy the dev templates and replace `C:/path/to/GreenPilot` with the absolute checkout path:

```powershell
Copy-Item .\setup\wildfly\properties\default-host-dev.properties.example .\setup\wildfly\properties\default-host-dev.properties
Copy-Item .\setup\wildfly\properties\admin-host-dev.properties.example .\setup\wildfly\properties\admin-host-dev.properties
Copy-Item .\setup\wildfly\properties\api-dev.properties.example .\setup\wildfly\properties\api-dev.properties
```

Add `setup/hosts.example` entries to the Windows hosts file. Start WildFly, deploy the IAM and API WARs with `setup/build.ps1`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup\configure-wildfly-hosts.ps1
```

Open `http://user.greenpilot.local:8080/` or `http://localhost:8080/` for the Front Office and `http://admin.greenpilot.local:8080/` for the Back Office. `setup/serve-front.ps1` and `setup/serve-backoffice.ps1` remain optional static development servers on ports 5500/5501; use them only when intentionally working outside the WildFly-hosted workflow.

## CI/CD and remote environments

For each target environment, create real property files from the corresponding `*-prod.properties.example` templates and set paths and DNS aliases for that environment. The pipeline must publish the static files to those paths before applying the host scripts. For example, with WildFly's remote management HTTPS interface enabled and protected:

```sh
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=remote+https://your-wildfly-host:9993 \
  --file=setup/wildfly/add-api.cli \
  --properties=setup/wildfly/properties/api-prod.properties
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=remote+https://your-wildfly-host:9993 \
  --file=setup/wildfly/add-host.cli \
  --properties=setup/wildfly/properties/admin-host-prod.properties
$JBOSS_HOME/bin/jboss-cli.sh --connect --controller=remote+https://your-wildfly-host:9993 \
  --file=setup/wildfly/default-host.cli \
  --properties=setup/wildfly/properties/default-host-prod.properties
```

The default-host script reloads WildFly after all host resources are updated. Supply management credentials through the deployment environment or the CLI's secured credential configuration; do not commit credentials or real `.properties` files.

`api.deployment` must match the runtime deployment name. This project currently packages the API as `greenpilot.war` and the IAM as `iam.war`. The API host script points to the former; IAM stays on the default host alias at `/iam`. Both frontend config files map `user.<domain>` and `admin.<domain>` to sibling `api.<domain>` and `iam.<domain>` hosts, preserving HTTPS and any explicit port.

## CORS, OAuth and HTTPS

The scripts intentionally do not inject wildcard CORS response headers. The Jakarta applications enforce CORS with `gp.cors.origin`; set `GP_CORS_ORIGIN` in each environment to the exact Front Office and Back Office origins. The IAM's `gp.iam.pwa.redirect-uris` must also contain the exact `https://<frontend-host>/callback.html` URLs. For production, configure `GP_JWT_ISSUER` consistently in IAM and API, and set `GP_IAM_JWKS_URL` in the API to the matching IAM `/jwks` endpoint. The local defaults include the WildFly-hosted origins on port 8080.

The attached examples referenced HSTS and HTTP-to-HTTPS filters by name, but a stock WildFly configuration does not necessarily define those filters. Configure a valid HTTPS listener or TLS-terminating ingress and its HTTP redirect/HSTS policy for production before advertising HTTPS URLs. Do not reuse development HTTP origins in production CORS or redirect allowlists.
