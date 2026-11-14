# Starts the Node-RED gateway that plays the Raspberry Pi: editor and local API on http://localhost:1880.
# Start Mosquitto first (start-mosquitto.ps1). Needs Node.js.
. "$PSScriptRoot\env.ps1"
$env:GW_ID            = $MqttGatewayUser
$env:GW_MQTT_USER     = $MqttGatewayUser
$env:GW_MQTT_PASSWORD = $MqttGatewayPassword
$env:GW_MQTT_HOST     = "localhost"
$env:GW_MQTT_PORT     = "8883"
$env:GW_CA_FILE       = "$Certs\ca.crt"

Set-Location "$Root\iot"
if (-not (Test-Path "node_modules")) { npm install }
npm run gateway
