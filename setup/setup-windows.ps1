# =====================================================================
#  GreenPilot - one-time installation of the development environment (Windows)
#
#  Portable tools (JDK 25, Maven, WildFly 41) are unzipped into ..\tools :
#  nothing changes system-wide, no admin rights needed for them.
#  MongoDB and Mosquitto are installed with winget only if missing.
#  Then TLS certificates and MQTT users/ACL are generated.
#
#     powershell -ExecutionPolicy Bypass -File .\setup\setup-windows.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root  = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root "tools"
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

function Get-Portable([string]$Url, [string]$Name) {
    $zip = Join-Path $env:TEMP "greenpilot-$Name.zip"
    $tmp = Join-Path $Tools "_tmp_$Name"
    $dest = Join-Path $Tools $Name
    Write-Host "    downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $zip -DestinationPath $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
    Move-Item $inner.FullName $dest
    Remove-Item -Recurse -Force $tmp
    Remove-Item -Force $zip
}

Write-Host "[1/6] JDK 25 (Eclipse Temurin, current LTS)"
if ((Test-Path "$Tools\jdk\bin\java.exe") -and ((& "$Tools\jdk\bin\java.exe" -version 2>&1 | Out-String) -match '"25')) { Write-Host "    already installed" }
else { Get-Portable "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse" "jdk" }

Write-Host "[2/6] Apache Maven 3.9.11"
if (Test-Path "$Tools\maven\bin\mvn.cmd") { Write-Host "    already installed" }
else { Get-Portable "https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip" "maven" }

Write-Host "[3/6] WildFly 41.0.0.Final (Jakarta EE 11, MicroProfile 7.1, Jakarta Data)"
if (Test-Path "$Tools\wildfly\jboss-modules.jar") {
    if (-not (Test-Path "$Tools\wildfly\modules\system\layers\base\jakarta\data")) {
        Write-Host "    an older WildFly is installed - replacing it" ; Remove-Item -Recurse -Force "$Tools\wildfly"
    } else { Write-Host "    already installed" }
}
if (-not (Test-Path "$Tools\wildfly\jboss-modules.jar")) {
    Get-Portable "https://github.com/wildfly/wildfly/releases/download/41.0.0.Final/wildfly-41.0.0.Final.zip" "wildfly"
}

Write-Host "[4/6] MongoDB"
$mongoInstalled = (Get-Service -Name "MongoDB" -ErrorAction SilentlyContinue) -or (Get-Command mongod -ErrorAction SilentlyContinue) -or (Test-Path "C:\Program Files\MongoDB\Server")
if ($mongoInstalled) { Write-Host "    already installed" } else { winget install --id MongoDB.Server -e --source winget }

Write-Host "[5/6] Eclipse Mosquitto (MQTT v5 broker)"
if (Test-Path "C:\Program Files\mosquitto\mosquitto.exe") { Write-Host "    already installed" } else { winget install --id EclipseFoundation.Mosquitto -e --source winget }

Write-Host "[6/6] TLS certificates + MQTT users and ACL"
& "$PSScriptRoot\make-certs.ps1"
& "$PSScriptRoot\make-mqtt-auth.ps1"

Write-Host ""
Write-Host "Done. Next (each in its own PowerShell window):" -ForegroundColor Green
Write-Host "  .\setup\start-mosquitto.ps1     MQTT broker on mqtts://localhost:8883"
Write-Host "  .\setup\run-wildfly.ps1         application server on http://localhost:8080"
Write-Host "  .\setup\build.ps1               compile core+iam+api and deploy iam.war + greenpilot.war"
Write-Host "  .\samples\test-flow.ps1         end-to-end test (OAuth PKCE login, MQTT v5, ETag...)"
