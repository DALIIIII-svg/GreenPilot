# Compiles core + iam + api and hot-deploys iam.war (/iam) and greenpilot.war (/greenpilot).
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
Push-Location "$Root\middleware"
try {
    mvn -B package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    Copy-Item "iam\target\iam.war" "$env:JBOSS_HOME\standalone\deployments\" -Force
    Copy-Item "api\target\greenpilot.war" "$env:JBOSS_HOME\standalone\deployments\" -Force
    Remove-Item "$env:JBOSS_HOME\standalone\deployments\greenpilot.war.failed","$env:JBOSS_HOME\standalone\deployments\iam.war.failed" -ErrorAction SilentlyContinue
    Write-Host "Deployed:" -ForegroundColor Green
    Write-Host "  IAM   http://localhost:8080/iam/jwks"
    Write-Host "  API   http://localhost:8080/greenpilot/api/health"
    Write-Host "  Live  http://localhost:8080/greenpilot/live-demo.html"
} finally { Pop-Location }
