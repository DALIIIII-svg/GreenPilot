# MQTT users (password file) + ACL + broker configuration.
# Each gateway logs in with username = its gatewayId and may only use ITS OWN topics.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$Mq = "C:\Program Files\mosquitto"
$Conf = "$Root\tools\mosquitto"
New-Item -ItemType Directory -Force -Path $Conf | Out-Null

$pwFile = "$Conf\passwords.txt"
if (Test-Path $pwFile) { Remove-Item $pwFile -Force }
& "$Mq\mosquitto_passwd.exe" -c -b $pwFile backend $MqttBackendPassword
& "$Mq\mosquitto_passwd.exe" -b $pwFile $MqttGatewayUser $MqttGatewayPassword

@"
# ---- GreenPilot MQTT access control ----
# backend: reads every gateway's uplink (also through the shared subscription), writes downlinks
user backend
topic read greenpilot/+/up/#
topic read `$share/greenpilot-backend/greenpilot/+/up/#
topic write greenpilot/+/down/#

# every gateway (username = gatewayId, %u): only its own topics
pattern write greenpilot/%u/up/#
pattern read greenpilot/%u/down/#
"@ | Set-Content -Encoding ASCII "$Conf\acl.txt"

$c = $Certs -replace '\\','/'
$m = $Conf -replace '\\','/'
@"
# ---- GreenPilot broker: MQTT v5 over TLS only, authenticated, ACL ----
per_listener_settings false
allow_anonymous false
password_file $m/passwords.txt
acl_file $m/acl.txt
persistence true
persistence_location $m/

listener 8883
protocol mqtt
cafile $c/ca.crt
certfile $c/server.crt
keyfile $c/server.key
tls_version tlsv1.3
"@ | Set-Content -Encoding ASCII "$Conf\mosquitto.conf"
Write-Host "    broker configuration written to $Conf"
