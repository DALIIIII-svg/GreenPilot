# Serves the front-end on http://localhost:5500 (the redirect URI registered in the IAM).
# Start Mosquitto and WildFly first (start-mosquitto.ps1, run-wildfly.ps1).
$Root = Split-Path -Parent $PSScriptRoot
$Front = Join-Path $Root "front"

if (Get-Command npx -ErrorAction SilentlyContinue) {
    npx --yes http-server $Front -p 5500 -c-1
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    python -m http.server 5500 --directory $Front
} else {
    Write-Host "Install Node.js (winget install OpenJS.NodeJS.LTS) or Python, then run this script again."
}
