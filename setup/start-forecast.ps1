# Starts the gateway's soil-moisture forecasting service (iot/edge-ml/forecast_service.py).
# Start the gateway first (start-gateway.ps1). Needs Python 3 (standard library only).
# Options are passed through, e.g.: start-forecast.ps1 --every 30
$Root = Split-Path -Parent $PSScriptRoot
$service = Join-Path $Root "iot\edge-ml\forecast_service.py"
if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 $service @args
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python $service @args
} else {
    throw "Python 3 not found. Install it from https://www.python.org/downloads/ (tick 'Add python.exe to PATH')."
}
