# Configure WildFly Undertow to serve the Front Office, Back Office and API host.
# Copy and edit the three setup/wildfly/properties/*-dev.properties.example files first.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"

$Cli = Join-Path $env:JBOSS_HOME "bin\jboss-cli.bat"
$Scripts = Join-Path $PSScriptRoot "wildfly"
$Properties = Join-Path $Scripts "properties"

if (-not (Test-Path $Cli)) {
    throw "WildFly CLI not found at '$Cli'. Run setup-windows.ps1 and check JBOSS_HOME."
}

$Steps = @(
    @{ Script = "add-api.cli"; Properties = "api-dev.properties" },
    @{ Script = "add-host.cli"; Properties = "admin-host-dev.properties" },
    @{ Script = "default-host.cli"; Properties = "default-host-dev.properties" }
)

foreach ($Step in $Steps) {
    $CliFile = Join-Path $Scripts $Step.Script
    $PropertiesFile = Join-Path $Properties $Step.Properties
    if (-not (Test-Path $PropertiesFile)) {
        throw "Missing '$PropertiesFile'. Copy its matching .properties.example file and set the paths/aliases."
    }

    Write-Host "Applying $($Step.Script) with $($Step.Properties)..."
    & $Cli --connect --controller=localhost:9990 "--file=$CliFile" "--properties=$PropertiesFile"
    if ($LASTEXITCODE -ne 0) {
        throw "WildFly CLI failed while applying '$($Step.Script)' (exit code $LASTEXITCODE)."
    }
}

Write-Host "WildFly host configuration applied." -ForegroundColor Green
Write-Host "  Front Office: http://user.greenpilot.local:8080/"
Write-Host "  Back Office:  http://admin.greenpilot.local:8080/"
Write-Host "  API:          http://api.greenpilot.local:8080/greenpilot/api/health"
