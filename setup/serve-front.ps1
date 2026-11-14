# Serves the Front Office on port 5500. With hosts.example configured, open http://user.greenpilot.local:5500.
# Start Mosquitto and WildFly first (start-mosquitto.ps1, run-wildfly.ps1).
$Root = Split-Path -Parent $PSScriptRoot
$Front = Join-Path $Root "frontend\frontoffice"

if (Get-Command npx -ErrorAction SilentlyContinue) {
    npx --yes http-server $Front -p 5500 -c-1
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    python -m http.server 5500 --directory $Front
} else {
    Write-Host "Install Node.js (winget install OpenJS.NodeJS.LTS) or Python, then run this script again."
}
