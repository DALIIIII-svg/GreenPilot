# Development PKI, made with the JDK's keytool only (no OpenSSL needed):
#   ca.crt            GreenPilot development CA (give it to the Pi / mosquitto_pub: --cafile; trust-dev-ca.ps1 adds it to Windows)
#   ca.p12            the CA key pair, kept to sign later certificates
#   server.crt/.key   broker certificate for localhost / 127.0.0.1 (Mosquitto)
#   truststore.p12    the CA, for the Java backend (gp.mqtt.truststore.path)
#   wildfly.p12       WildFly HTTPS certificate for user/admin/api/iam.<WebDomain>
#   jvm-truststore.p12  the JDK's public CAs + the GreenPilot CA, for WildFly (the API fetches the IAM keys over HTTPS)
# Re-run with -Force to regenerate everything (then run make-mqtt-auth.ps1 and restart Mosquitto).
# Add your PC's LAN name/IP to the broker certificate with -ExtraSan "dns:mypc,ip:192.168.1.20"
param([switch]$Force, [string]$ExtraSan = "", [string]$WebDomain = "greenpilot.local")
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$kt = "$env:JAVA_HOME\bin\keytool.exe"
New-Item -ItemType Directory -Force -Path $Certs | Out-Null
if ((Test-Path "$Certs\wildfly.p12") -and -not $Force) { Write-Host "    certificates already exist ($Certs)"; return }
if ((Test-Path "$Certs\server.key") -and -not (Test-Path "$Certs\ca.p12") -and -not $Force) {
    Write-Host "    the CA key of the existing certificates was not kept: run again with -Force" -ForegroundColor Yellow
    return
}
if ($Force) { Get-ChildItem $Certs -File | Remove-Item -Force }

$pw = "changeit"
$ca = "$Certs\ca.p12"
$ks = "$Certs\_work.p12"
$web = "$Certs\wildfly.p12"
$san = "dns:localhost,ip:127.0.0.1"
if ($ExtraSan) { $san = "$san,$ExtraSan" }
$webSan = "dns:user.$WebDomain,dns:admin.$WebDomain,dns:api.$WebDomain,dns:iam.$WebDomain,dns:localhost,ip:127.0.0.1"

# 1. CA key pair (self-signed, basicConstraints CA:true), kept in ca.p12
if (-not (Test-Path $ca)) {
    & $kt -genkeypair -alias ca -keyalg EC -groupname secp256r1 -dname "CN=GreenPilot Dev CA,O=SUPCOM" -ext bc:c -validity 825 -keystore $ca -storetype PKCS12 -storepass $pw | Out-Null
}
& $kt -exportcert -alias ca -keystore $ca -storepass $pw -rfc -file "$Certs\ca.crt" | Out-Null

# 2. broker key pair, CSR, certificate signed by the CA
& $kt -genkeypair -alias server -keyalg EC -groupname secp256r1 -dname "CN=localhost,O=SUPCOM" -validity 825 -keystore $ks -storetype PKCS12 -storepass $pw | Out-Null
& $kt -certreq -alias server -keystore $ks -storepass $pw -file "$Certs\_server.csr" | Out-Null
& $kt -gencert -alias ca -keystore $ca -storepass $pw -infile "$Certs\_server.csr" -outfile "$Certs\server.crt" -rfc -validity 825 -ext "san=$san" -ext "eku=serverAuth" | Out-Null

# 3. truststore for Java = only the CA
& $kt -importcert -noprompt -alias greenpilot-ca -file "$Certs\ca.crt" -keystore "$Certs\truststore.p12" -storetype PKCS12 -storepass $pw | Out-Null

# 4. private key of the broker as PEM (PKCS#8) for Mosquitto - tiny Java program, JDK only
& "$env:JAVA_HOME\bin\java.exe" "$PSScriptRoot\ExportKey.java" $ks $pw server "$Certs\server.key"

# 5. WildFly HTTPS: key pair + certificate chain (server, CA) in wildfly.p12
& $kt -genkeypair -alias web -keyalg EC -groupname secp256r1 -dname "CN=$WebDomain,O=SUPCOM" -validity 825 -keystore $web -storetype PKCS12 -storepass $pw | Out-Null
& $kt -certreq -alias web -keystore $web -storepass $pw -file "$Certs\_web.csr" | Out-Null
& $kt -gencert -alias ca -keystore $ca -storepass $pw -infile "$Certs\_web.csr" -outfile "$Certs\_web.crt" -rfc -validity 825 -ext "san=$webSan" -ext "eku=serverAuth" | Out-Null
& $kt -importcert -noprompt -alias ca -file "$Certs\ca.crt" -keystore $web -storepass $pw | Out-Null
& $kt -importcert -noprompt -alias web -file "$Certs\_web.crt" -keystore $web -storepass $pw | Out-Null

# 6. truststore of the WildFly JVM: the JDK's CAs + the GreenPilot CA
if (Test-Path "$Certs\jvm-truststore.p12") { Remove-Item "$Certs\jvm-truststore.p12" -Force }
$ErrorActionPreference = "Continue"
& $kt -importkeystore -noprompt -srckeystore "$env:JAVA_HOME\lib\security\cacerts" -srcstorepass $pw -destkeystore "$Certs\jvm-truststore.p12" -deststoretype PKCS12 -deststorepass $pw 2>&1 | Out-Null
$ErrorActionPreference = "Stop"
if ($LASTEXITCODE -ne 0) { throw "keytool -importkeystore failed (exit $LASTEXITCODE)" }
& $kt -importcert -noprompt -alias greenpilot-ca -file "$Certs\ca.crt" -keystore "$Certs\jvm-truststore.p12" -storepass $pw | Out-Null

Remove-Item "$Certs\_server.csr", "$Certs\_web.csr", "$Certs\_web.crt", $ks -Force
Write-Host "    certificates written to $Certs"
