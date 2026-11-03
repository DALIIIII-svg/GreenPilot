# Development PKI for MQTT over TLS, made with the JDK's keytool only (no OpenSSL needed):
#   ca.crt            GreenPilot development CA  (give it to the Pi / mosquitto_pub: --cafile)
#   server.crt/.key   broker certificate for localhost / 127.0.0.1 (Mosquitto)
#   truststore.p12    the CA, for the Java backend (gp.mqtt.truststore.path)
# Re-run with -Force to regenerate. Add your PC's LAN name/IP with -ExtraSan "dns:mypc,ip:192.168.1.20"
param([switch]$Force, [string]$ExtraSan = "")
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$kt = "$env:JAVA_HOME\bin\keytool.exe"
New-Item -ItemType Directory -Force -Path $Certs | Out-Null
if ((Test-Path "$Certs\server.key") -and -not $Force) { Write-Host "    certificates already exist ($Certs)"; return }
Get-ChildItem $Certs -File | Remove-Item -Force

$pw = "changeit"
$ks = "$Certs\_work.p12"
$san = "dns:localhost,ip:127.0.0.1"
if ($ExtraSan) { $san = "$san,$ExtraSan" }

# 1. CA key pair (self-signed, basicConstraints CA:true)
& $kt -genkeypair -alias ca -keyalg EC -groupname secp256r1 -dname "CN=GreenPilot Dev CA,O=SUPCOM" -ext bc:c -validity 825 -keystore $ks -storetype PKCS12 -storepass $pw | Out-Null
# 2. server key pair, CSR, certificate signed by the CA
& $kt -genkeypair -alias server -keyalg EC -groupname secp256r1 -dname "CN=localhost,O=SUPCOM" -validity 825 -keystore $ks -storetype PKCS12 -storepass $pw | Out-Null
& $kt -certreq -alias server -keystore $ks -storepass $pw -file "$Certs\_server.csr" | Out-Null
& $kt -gencert -alias ca -keystore $ks -storepass $pw -infile "$Certs\_server.csr" -outfile "$Certs\server.crt" -rfc -validity 825 -ext "san=$san" -ext "eku=serverAuth" | Out-Null
& $kt -exportcert -alias ca -keystore $ks -storepass $pw -rfc -file "$Certs\ca.crt" | Out-Null
# 3. truststore for Java = only the CA
& $kt -importcert -noprompt -alias greenpilot-ca -file "$Certs\ca.crt" -keystore "$Certs\truststore.p12" -storetype PKCS12 -storepass $pw | Out-Null
# 4. private key of the server as PEM (PKCS#8) for Mosquitto - tiny Java program, JDK only
& "$env:JAVA_HOME\bin\java.exe" "$PSScriptRoot\ExportKey.java" $ks $pw server "$Certs\server.key"
Remove-Item "$Certs\_server.csr", $ks -Force
Write-Host "    certificates written to $Certs"
