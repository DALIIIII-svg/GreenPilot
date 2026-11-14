# Compiles core + iam + api and hot-deploys iam-1.0.war and api-1.0.war (served at the root of iam.* and api.*,
# see configure-wildfly-hosts.ps1).
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
Push-Location "$Root\middleware"
try {
    mvn -B package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    $deployments = "$env:JBOSS_HOME\standalone\deployments"
    Remove-Item "$deployments\greenpilot.war*", "$deployments\iam.war*", "$deployments\*.war.failed" -ErrorAction SilentlyContinue
    Copy-Item "iam\target\iam-1.0.war" $deployments -Force
    Copy-Item "api\target\api-1.0.war" $deployments -Force
    Write-Host "Deployed:" -ForegroundColor Green
    Write-Host "  IAM   https://iam.greenpilot.local/jwks"
    Write-Host "  API   https://api.greenpilot.local/api/health"
    Write-Host "  Live  https://api.greenpilot.local/live-demo.html"
} finally { Pop-Location }
