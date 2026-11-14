# Configures WildFly with the course's CI/CD scripts (setup/wildfly/*.cli):
#   default host = Front Office, admin host = Back Office, api and iam hosts = api-1.0.war and iam-1.0.war, HTTPS.
# WildFly must be running. Safe to re-run: reset-hosts.cli first removes what the scripts added.
# --timeout gives WildFly time to come back after each reload (about 15 s with both WARs deployed).
#   dev  (default): properties written from the *-dev.properties.example files with the path of this checkout
#   prod          : uses setup/wildfly/properties/*-prod.properties, e.g. -Environment prod -Controller remote+https://host:9993
param([string]$Environment = "dev", [string]$Controller = "localhost:9990")
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"

$Cli = Join-Path $env:JBOSS_HOME "bin\jboss-cli.bat"
$Scripts = Join-Path $PSScriptRoot "wildfly"
$Properties = Join-Path $Scripts "properties"
$env:NOPAUSE = "true"
if (-not (Test-Path $Cli)) { throw "WildFly CLI not found at '$Cli'. Run setup-windows.ps1 first." }

if ($Environment -eq "dev") {
    if (-not (Test-Path "$Certs\wildfly.p12")) {
        throw "Missing $Certs\wildfly.p12. Run: powershell -ExecutionPolicy Bypass -File .\setup\make-certs.ps1 -Force"
    }
    $repo = $Root -replace '\\', '/'
    Get-ChildItem "$Properties\*-dev.properties.example" | ForEach-Object {
        $text = (Get-Content $_.FullName -Raw) -replace 'C:/path/to/cot-real', $repo
        Set-Content -Encoding ASCII -Path ($_.FullName -replace '\.example$', '') -Value $text
    }
}

$config = Join-Path $env:JBOSS_HOME "standalone\configuration"
if (-not (Test-Path "$config\standalone.xml.before-hosts")) {
    Copy-Item "$config\standalone.xml" "$config\standalone.xml.before-hosts"
}

function Invoke-Cli([string]$Script, [string]$PropertiesName) {
    $arguments = @("--connect", "--controller=$Controller", "--timeout=120000", "--file=$Scripts\$Script")
    if ($PropertiesName) {
        $file = "$Properties\$PropertiesName-$Environment.properties"
        if (-not (Test-Path $file)) { throw "Missing $file (copy it from its .example file)." }
        $arguments += "--properties=$file"
    }
    Write-Host ">>> $Script $PropertiesName" -ForegroundColor Cyan
    & $Cli @arguments
    if ($LASTEXITCODE -ne 0) { throw "WildFly CLI failed on $Script (exit code $LASTEXITCODE)." }
}

Invoke-Cli "reset-hosts.cli"
Invoke-Cli "https.cli" "https"
Invoke-Cli "default-host.cli" "default-host"
Invoke-Cli "add-host.cli" "admin-host"
Invoke-Cli "add-api.cli" "api"
Invoke-Cli "add-api.cli" "iam"
& $Cli --connect "--controller=$Controller" --timeout=120000 --command=reload
if ($LASTEXITCODE -ne 0) { throw "WildFly reload failed." }

Write-Host "WildFly hosts configured ($Environment)." -ForegroundColor Green
if ($Environment -eq "dev") {
    Write-Host "  Front Office: https://user.greenpilot.local/"
    Write-Host "  Back Office:  https://admin.greenpilot.local/"
    Write-Host "  API:          https://api.greenpilot.local/api/health"
    Write-Host "  IAM:          https://iam.greenpilot.local/jwks"
}
