# Starts the MQTT broker in the foreground, verbose (every connection / subscription is printed).
. "$PSScriptRoot\env.ps1"
Stop-Service mosquitto -ErrorAction SilentlyContinue   # the Windows service would hold the port
& "C:\Program Files\mosquitto\mosquitto.exe" -c "$Root\tools\mosquitto\mosquitto.conf" -v
