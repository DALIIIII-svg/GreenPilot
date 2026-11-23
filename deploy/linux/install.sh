#!/usr/bin/env bash
# One-time installation of the GreenPilot server on Ubuntu 24.04: Java 25, WildFly 41, MongoDB 8,
# Mosquitto and certbot. Run as root: sudo bash install.sh
set -euo pipefail

WILDFLY_VERSION=41.0.0.Final
JAVA_MAJOR=25
MONGODB_SERIES=8.0
HERE=$(cd "$(dirname "$0")" && pwd)

[ "$(id -u)" -eq 0 ] || { echo "Run as root: sudo bash $0" >&2; exit 1; }
. /etc/os-release
[ "${VERSION_CODENAME:-}" = noble ] || echo "Warning: written for Ubuntu 24.04 (noble), this is ${PRETTY_NAME:-unknown}." >&2

echo "== packages"
apt-get update
apt-get install -y curl gnupg ca-certificates unzip rsync openssl mosquitto mosquitto-clients certbot

echo "== Java $JAVA_MAJOR (Eclipse Temurin)"
if ! java -version 2>&1 | grep -q "version \"$JAVA_MAJOR"; then
    curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor --yes -o /usr/share/keyrings/adoptium.gpg
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $VERSION_CODENAME main" \
        > /etc/apt/sources.list.d/adoptium.list
    apt-get update
    apt-get install -y "temurin-$JAVA_MAJOR-jdk"
fi

echo "== MongoDB $MONGODB_SERIES (listens on 127.0.0.1 only)"
if ! command -v mongod >/dev/null; then
    curl -fsSL "https://www.mongodb.org/static/pgp/server-$MONGODB_SERIES.asc" | gpg --dearmor --yes -o "/usr/share/keyrings/mongodb-server-$MONGODB_SERIES.gpg"
    echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-$MONGODB_SERIES.gpg ] https://repo.mongodb.org/apt/ubuntu $VERSION_CODENAME/mongodb-org/$MONGODB_SERIES multiverse" \
        > "/etc/apt/sources.list.d/mongodb-org-$MONGODB_SERIES.list"
    apt-get update
    apt-get install -y mongodb-org
fi
systemctl enable --now mongod

echo "== WildFly $WILDFLY_VERSION in /opt/wildfly"
id wildfly >/dev/null 2>&1 || useradd --system --home-dir /opt/wildfly --shell /usr/sbin/nologin wildfly
if [ ! -f /opt/wildfly/jboss-modules.jar ]; then
    curl -fsSL -o /tmp/wildfly.tar.gz "https://github.com/wildfly/wildfly/releases/download/$WILDFLY_VERSION/wildfly-$WILDFLY_VERSION.tar.gz"
    mkdir -p /opt/wildfly
    tar xzf /tmp/wildfly.tar.gz -C /opt/wildfly --strip-components=1
    rm /tmp/wildfly.tar.gz
fi
mkdir -p /var/opt/wildfly/www/frontoffice /var/opt/wildfly/www/backoffice
chown -R wildfly:wildfly /opt/wildfly /var/opt/wildfly
install -m 644 "$HERE/wildfly.service" /etc/systemd/system/wildfly.service
systemctl daemon-reload
systemctl enable wildfly

echo "== swap (small VMs)"
mem_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
if [ "$mem_kb" -lt 3000000 ] && [ -z "$(swapon --show --noheadings)" ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== settings"
install -d -m 750 -o root -g wildfly /etc/greenpilot /etc/greenpilot/tls /etc/greenpilot/mqtt /etc/greenpilot/wildfly
if [ ! -f /etc/greenpilot/greenpilot.conf ]; then
    install -m 600 "$HERE/greenpilot.conf.example" /etc/greenpilot/greenpilot.conf
fi

echo
echo "Installed. Next: fill in /etc/greenpilot/greenpilot.conf, then run: sudo bash $HERE/configure.sh"
