#!/usr/bin/env bash
# Applies /etc/greenpilot/greenpilot.conf: local name resolution, MQTT certificates and accounts,
# WildFly environment, HTTPS certificate (Let's Encrypt). Safe to run again after changing the settings.
# Run as root: sudo bash configure.sh
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
CONF=/etc/greenpilot/greenpilot.conf
MQTT_DIR=/etc/greenpilot/mqtt

[ "$(id -u)" -eq 0 ] || { echo "Run as root: sudo bash $0" >&2; exit 1; }
[ -f "$CONF" ] || { echo "Missing $CONF (run install.sh first)" >&2; exit 1; }
. "$CONF"
for v in DOMAIN LETSENCRYPT_EMAIL IAM_ADMIN_USERNAME IAM_ADMIN_PASSWORD MQTT_BACKEND_PASSWORD MQTT_GATEWAYS KEYSTORE_PASSWORD; do
    [ -n "${!v:-}" ] || { echo "Set $v in $CONF" >&2; exit 1; }
done
for gw in $MQTT_GATEWAYS; do
    [ -n "${gw#*:}" ] && [ "${gw#*:}" != "$gw" ] || { echo "MQTT_GATEWAYS: '$gw' has no password (expected user:password)" >&2; exit 1; }
done
NAMES="user.$DOMAIN admin.$DOMAIN api.$DOMAIN iam.$DOMAIN mqtt.$DOMAIN"

echo "== the server reaches its own names locally (API -> IAM keys, API -> broker)"
sed -i '/# greenpilot$/d' /etc/hosts
echo "127.0.0.1 $NAMES # greenpilot" >> /etc/hosts

echo "== MQTT certificates ($MQTT_DIR)"
if [ ! -f "$MQTT_DIR/ca.key" ]; then
    openssl req -x509 -new -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -sha384 -days 825 \
        -subj "/O=GreenPilot/CN=GreenPilot MQTT CA" -keyout "$MQTT_DIR/ca.key" -out "$MQTT_DIR/ca.crt"
fi
if [ ! -f "$MQTT_DIR/server.crt" ] || ! openssl x509 -in "$MQTT_DIR/server.crt" -noout -ext subjectAltName | grep -q "DNS:mqtt.$DOMAIN"; then
    openssl req -new -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes \
        -subj "/O=GreenPilot/CN=mqtt.$DOMAIN" -keyout "$MQTT_DIR/server.key" -out "$MQTT_DIR/server.csr"
    openssl x509 -req -in "$MQTT_DIR/server.csr" -CA "$MQTT_DIR/ca.crt" -CAkey "$MQTT_DIR/ca.key" -CAcreateserial \
        -days 825 -sha384 -out "$MQTT_DIR/server.crt" \
        -extfile <(printf 'subjectAltName=DNS:mqtt.%s,DNS:localhost\nextendedKeyUsage=serverAuth\n' "$DOMAIN")
    rm -f "$MQTT_DIR/server.csr"
fi
rm -f "$MQTT_DIR/truststore.p12"
keytool -importcert -noprompt -alias greenpilot-mqtt-ca -file "$MQTT_DIR/ca.crt" \
    -keystore "$MQTT_DIR/truststore.p12" -storetype PKCS12 -storepass "$KEYSTORE_PASSWORD" >/dev/null
chown root:mosquitto "$MQTT_DIR/server.key" "$MQTT_DIR/server.crt" "$MQTT_DIR/ca.crt"
chmod 640 "$MQTT_DIR/server.key"
chmod 644 "$MQTT_DIR/server.crt" "$MQTT_DIR/ca.crt"
chown root:wildfly "$MQTT_DIR/truststore.p12"
chmod 640 "$MQTT_DIR/truststore.p12"
chmod 600 "$MQTT_DIR/ca.key"
chmod 751 /etc/greenpilot
chgrp mosquitto "$MQTT_DIR"
chmod 755 "$MQTT_DIR"

echo "== Mosquitto: MQTT v5 over TLS on 8883, accounts and ACL"
passwd_file=/etc/mosquitto/greenpilot.passwd
: > "$passwd_file"
mosquitto_passwd -b "$passwd_file" backend "$MQTT_BACKEND_PASSWORD"
for gw in $MQTT_GATEWAYS; do
    mosquitto_passwd -b "$passwd_file" "${gw%%:*}" "${gw#*:}"
done
chown root:mosquitto "$passwd_file"
chmod 640 "$passwd_file"
cat > /etc/mosquitto/greenpilot.acl <<ACL
# backend: reads every gateway's uplink (also through the shared subscription), writes downlinks
user backend
topic read greenpilot/+/up/#
topic read \$share/greenpilot-backend/greenpilot/+/up/#
topic write greenpilot/+/down/#

# every gateway (username = gatewayId): only its own topics
pattern write greenpilot/%u/up/#
pattern read greenpilot/%u/down/#
ACL
cat > /etc/mosquitto/conf.d/greenpilot.conf <<MOSQ
per_listener_settings false
allow_anonymous false
password_file $passwd_file
acl_file /etc/mosquitto/greenpilot.acl

listener 8883
protocol mqtt
cafile $MQTT_DIR/ca.crt
certfile $MQTT_DIR/server.crt
keyfile $MQTT_DIR/server.key
tls_version tlsv1.3
MOSQ
systemctl enable mosquitto
systemctl restart mosquitto

echo "== WildFly environment (/etc/greenpilot/wildfly.env)"
umask 027
cat > /etc/greenpilot/wildfly.env <<ENV
JAVA_OPTS=-Xms256m -Xmx${WILDFLY_HEAP:-1g} -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=320m -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true
JNOSQL_MONGODB_URL=mongodb://localhost:27017
GP_JWT_ISSUER=https://iam.$DOMAIN
GP_IAM_JWKS_URL=https://iam.$DOMAIN/jwks
GP_IAM_PWA_REDIRECT_URIS=https://user.$DOMAIN/callback.html,https://admin.$DOMAIN/callback.html,https://api.$DOMAIN/live-demo.html
GP_IAM_ADMIN_USERNAME=$IAM_ADMIN_USERNAME
GP_IAM_ADMIN_PASSWORD=$IAM_ADMIN_PASSWORD
GP_MQTT_URL=ssl://mqtt.$DOMAIN:8883
GP_MQTT_USERNAME=backend
GP_MQTT_PASSWORD=$MQTT_BACKEND_PASSWORD
GP_MQTT_TRUSTSTORE_PATH=$MQTT_DIR/truststore.p12
GP_MQTT_TRUSTSTORE_PASSWORD=$KEYSTORE_PASSWORD
ENV
chown root:wildfly /etc/greenpilot/wildfly.env
umask 022

echo "== HTTPS certificate (Let's Encrypt, port 80 must be open)"
hook=/etc/letsencrypt/renewal-hooks/deploy/greenpilot.sh
install -D -m 755 "$HERE/certbot-deploy-hook.sh" "$hook"
domains=()
for n in user admin api iam; do domains+=(-d "$n.$DOMAIN"); done
certbot certonly --standalone --non-interactive --agree-tos -m "$LETSENCRYPT_EMAIL" \
    --cert-name greenpilot --expand --keep-until-expiring "${domains[@]}" \
    --pre-hook "systemctl stop wildfly" --post-hook "systemctl start wildfly"
# the hook directory only runs on renewals; convert the current certificate now
RENEWED_LINEAGE=/etc/letsencrypt/live/greenpilot bash "$hook"

systemctl restart wildfly
echo
echo "Configured for $DOMAIN. Gateways need $MQTT_DIR/ca.crt and connect to mqtt.$DOMAIN:8883."
echo "Next: deploy the application (GitHub Actions > Deploy, with 'hosts' checked the first time)."
