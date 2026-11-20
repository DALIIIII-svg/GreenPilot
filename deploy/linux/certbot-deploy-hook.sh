#!/usr/bin/env bash
# Called by certbot after each new or renewed certificate: converts it to the PKCS#12 keystore
# used by WildFly's HTTPS listener (setup/wildfly/https.cli). WildFly is restarted by certbot's post-hook.
set -euo pipefail
. /etc/greenpilot/greenpilot.conf
live=${RENEWED_LINEAGE:-/etc/letsencrypt/live/greenpilot}
out=/etc/greenpilot/tls/web.p12
openssl pkcs12 -export -name web -in "$live/fullchain.pem" -inkey "$live/privkey.pem" \
    -out "$out.new" -passout "pass:$KEYSTORE_PASSWORD"
chown root:wildfly "$out.new"
chmod 640 "$out.new"
mv "$out.new" "$out"
