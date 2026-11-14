# Serves the Back Office on port 5501. With hosts.example configured, open http://admin.greenpilot.local:5501.
# Start Mosquitto and WildFly first (start-mosquitto.ps1, run-wildfly.ps1).
$Root = Split-Path -Parent $PSScriptRoot
$BackOffice = Join-Path $Root "frontend\backoffice"

if (Get-Command npx -ErrorAction SilentlyContinue) {
    npx --yes http-server $BackOffice -p 5501 -c-1
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    python -m http.server 5501 --directory $BackOffice
} else {
    Write-Host "Install Node.js (winget install OpenJS.NodeJS.LTS) or Python, then run this script again."
}
