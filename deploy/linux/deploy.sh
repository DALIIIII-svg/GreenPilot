#!/usr/bin/env bash
# Deploys a release on the server: front-ends, WARs and, with --hosts, the WildFly host configuration
# (the course's CI/CD scripts in setup/wildfly with the production properties).
# Usage (as root): deploy.sh [--hosts] RELEASE_DIR
#   RELEASE_DIR holds api-1.0.war, iam-1.0.war, frontend/{frontoffice,backoffice}, setup/wildfly
set -euo pipefail

HOSTS=false
if [ "${1:-}" = "--hosts" ]; then HOSTS=true; shift; fi
REL=${1:?usage: deploy.sh [--hosts] RELEASE_DIR}
REL=$(cd "$REL" && pwd)

JBOSS_HOME=/opt/wildfly
DEPLOYMENTS=$JBOSS_HOME/standalone/deployments
WWW=/var/opt/wildfly/www
PROPS=/etc/greenpilot/wildfly
CLI="$JBOSS_HOME/bin/jboss-cli.sh --connect --timeout=120000"

[ "$(id -u)" -eq 0 ] || { echo "Run as root" >&2; exit 1; }
. /etc/greenpilot/greenpilot.conf
for f in api-1.0.war iam-1.0.war frontend/frontoffice/index.html frontend/backoffice/index.html setup/wildfly/default-host.cli; do
    [ -e "$REL/$f" ] || { echo "Release incomplete: missing $f" >&2; exit 1; }
done

wildfly_up() { $CLI --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q running; }

echo "== front-ends"
rsync -a --delete "$REL/frontend/frontoffice/" "$WWW/frontoffice/"
rsync -a --delete "$REL/frontend/backoffice/" "$WWW/backoffice/"
chown -R wildfly:wildfly "$WWW"

echo "== WARs"
stamp=$(mktemp)
staging=$(mktemp -d "$JBOSS_HOME/standalone/release.XXXXXX")
for war in iam-1.0.war api-1.0.war; do
    # copied next to deployments/ first, so the scanner only ever sees a complete file
    install -o wildfly -g wildfly -m 644 "$REL/$war" "$staging/$war"
    rm -f "$DEPLOYMENTS/$war.failed"
    mv "$staging/$war" "$DEPLOYMENTS/$war"
done
rmdir "$staging"

if systemctl is-active --quiet wildfly; then
    for war in iam-1.0.war api-1.0.war; do
        for _ in $(seq 1 90); do
            [ -f "$DEPLOYMENTS/$war.failed" ] && { echo "$war failed to deploy:" >&2; cat "$DEPLOYMENTS/$war.failed" >&2; exit 1; }
            [ "$DEPLOYMENTS/$war.deployed" -nt "$stamp" ] && break
            sleep 2
        done
        [ "$DEPLOYMENTS/$war.deployed" -nt "$stamp" ] || { echo "$war not deployed after 3 minutes" >&2; exit 1; }
        echo "   $war deployed"
    done
else
    echo "   WildFly is stopped: the WARs deploy at its next start"
fi
rm -f "$stamp"

if $HOSTS; then
    echo "== WildFly hosts for $DOMAIN"
    systemctl is-active --quiet wildfly || systemctl start wildfly
    for _ in $(seq 1 60); do wildfly_up && break; sleep 3; done
    wildfly_up || { echo "WildFly does not answer on its management port" >&2; exit 1; }

    for f in "$REL"/setup/wildfly/properties/*-prod.properties.example; do
        name=$(basename "$f" .example)
        sed -e "s/your-domain\.tld/$DOMAIN/g" \
            -e "s|^tls\.keystore=.*|tls.keystore=/etc/greenpilot/tls/web.p12|" \
            -e "s|^tls\.password=.*|tls.password=$KEYSTORE_PASSWORD|" \
            "$f" > "$PROPS/$name"
        chmod 600 "$PROPS/$name"
    done

    config=$JBOSS_HOME/standalone/configuration
    [ -f "$config/standalone.xml.before-hosts" ] || cp -p "$config/standalone.xml" "$config/standalone.xml.before-hosts"

    run_cli() {
        echo ">>> $1 ${2:-}"
        if [ -n "${2:-}" ]; then
            $CLI --file="$REL/setup/wildfly/$1" --properties="$PROPS/$2-prod.properties"
        else
            $CLI --file="$REL/setup/wildfly/$1"
        fi
    }
    run_cli reset-hosts.cli > /dev/null
    run_cli https.cli https
    run_cli default-host.cli default-host
    run_cli add-host.cli admin-host
    run_cli add-api.cli api
    run_cli add-api.cli iam
    $CLI --command=reload
    for _ in $(seq 1 60); do wildfly_up && break; sleep 3; done
fi

echo "== health"
healthy=false
for _ in $(seq 1 30); do
    if curl --noproxy "*" -fsS "https://api.$DOMAIN/api/health"; then echo; healthy=true; break; fi
    sleep 5
done
$healthy || echo "Warning: https://api.$DOMAIN/api/health does not answer yet, see: journalctl -u wildfly" >&2
echo "Front Office https://user.$DOMAIN   Back Office https://admin.$DOMAIN"
