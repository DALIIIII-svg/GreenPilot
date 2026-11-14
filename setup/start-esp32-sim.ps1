# Simulated ESP32 field node, for when the Wokwi simulator is not available. Start the gateway first.
# Keys: a/z zone A +/-, s/x zone B +/-, r reservoir empty/full, t send now, q quit.
$Iot = Join-Path (Split-Path -Parent $PSScriptRoot) "iot"
Set-Location $Iot
if (-not (Test-Path "node_modules")) { npm install }
node simulator\esp32-sim.js
