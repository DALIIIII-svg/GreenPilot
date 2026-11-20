# Deploying GreenPilot on a Linux server

Everything runs on one Ubuntu 24.04 VM: WildFly (front-ends, API, IAM), MongoDB and the Mosquitto broker.
The gateways (Raspberry Pi, or Node-RED on a PC for the simulation) connect to the broker from outside.

```
            GitHub Actions (Deploy)                     the VM
  build + tests -> release.tgz -- scp/ssh -->  deploy.sh: front-ends, WARs, setup/wildfly/*.cli (prod)
                                               WildFly :80/:443   MongoDB 127.0.0.1:27017   Mosquitto :8883
  gateway (pi-01) ---- MQTT v5 / TLS ----------------------------------------------------^
```

| File | Role |
|---|---|
| `linux/install.sh` | once: Java 25, WildFly 41 (systemd service), MongoDB 8, Mosquitto, certbot, swap on small VMs |
| `linux/greenpilot.conf.example` | the server settings (domain, passwords); copied to `/etc/greenpilot/greenpilot.conf` |
| `linux/configure.sh` | applies the settings: MQTT CA and accounts, WildFly environment, Let's Encrypt certificate |
| `linux/certbot-deploy-hook.sh` | turns each new certificate into the keystore used by `https.cli` |
| `linux/deploy.sh` | deploys a release; `--hosts` also runs the WildFly host scripts with the production properties |
| `linux/wildfly.service` | systemd unit (WildFly as the `wildfly` user, allowed to use ports 80 and 443) |
| `../.github/workflows/deploy.yml` | builds, tests, copies the release to the VM and runs `deploy.sh` |

The production values (domain, passwords) only exist on the server, in `/etc/greenpilot/`. Nothing secret is in git.

## 1. Create the VM (Azure)

- Ubuntu Server 24.04 LTS, a region in Europe. Size: B2s (4 GB) is comfortable; the free B1s/B2ats v2 (1 GB) works with the swap
  that `install.sh` adds and `WILDFLY_HEAP=1g`.
- Authentication: SSH public key. Note the admin user name (e.g. `azureuser`).
- Static public IP (Networking), so the address survives a stop/start.
- Inbound port rules: 22 from your own IP only, 80, 443, 8883 from anywhere.
- Turn on Auto-shutdown and a budget alert (Cost Management).

## 2. Domain names

With DuckDNS (free): create a name, e.g. `greenpilot`, and set its IP to the VM's public IP.
Every sub-name (`user.`, `admin.`, `api.`, `iam.`, `mqtt.greenpilot.duckdns.org`) then points to the VM.

## 3. Install and configure (once, on the VM)

From the repository root on your PC, copy the scripts:

```sh
scp -r deploy/linux azureuser@<vm-ip>:~/greenpilot-setup
ssh azureuser@<vm-ip>
sudo bash ~/greenpilot-setup/install.sh
sudo nano /etc/greenpilot/greenpilot.conf      # domain, e-mail, passwords (long random values)
sudo bash ~/greenpilot-setup/configure.sh
```

`configure.sh` can be run again after changing the settings. Let's Encrypt renews the certificate by itself (certbot timer).

## 4. GitHub secrets (repository Settings > Secrets and variables > Actions)

| Secret | Value |
|---|---|
| `VM_HOST` | the domain (or IP) of the VM |
| `VM_USER` | the admin user, e.g. `azureuser` |
| `VM_SSH_KEY` | a private key allowed on the VM (make a dedicated one: `ssh-keygen -t ed25519 -f gp-deploy`, add `gp-deploy.pub` to `~/.ssh/authorized_keys` on the VM) |
| `VM_KNOWN_HOSTS` | optional: the output of `ssh-keyscan <vm-host>`, checked against the VM instead of trusting the first answer |

The workflow uses the `production` environment: it can be set to require an approval before each deployment.

## 5. Deploy

Actions > Deploy > Run workflow. Tick **hosts** the first time (and after changing `setup/wildfly`).
The run ends with the answer of `https://api.<domain>/api/health`.

Then:
- `https://admin.<domain>`: sign in with `IAM_ADMIN_USERNAME` / `IAM_ADMIN_PASSWORD`, create the greenhouse and the grower accounts.
- Gateway on the PC: copy `/etc/greenpilot/mqtt/ca.crt` from the VM, then
  `powershell -ExecutionPolicy Bypass -File setup\start-gateway.ps1 -MqttHost mqtt.<domain> -CaFile <path>\ca.crt -Password <pi-01 password>`

To deploy on every merge into `main`, add `push: branches: [main]` under `on:` in `deploy.yml`.

## Useful commands on the VM

```sh
sudo systemctl status wildfly mongod mosquitto
sudo journalctl -u wildfly -f                # WildFly log
sudo tail -f /var/log/mosquitto/mosquitto.log
sudo certbot renew --dry-run                  # checks the automatic renewal
```

WildFly's management interface stays on `127.0.0.1:9990`: the host scripts are run on the VM through SSH,
so no administration port is open on the Internet.
