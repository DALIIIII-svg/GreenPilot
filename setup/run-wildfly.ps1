# Starts WildFly in the foreground (Ctrl+C to stop). The GP_* variables of env.ps1 reach both applications.
. "$PSScriptRoot\env.ps1"
# Standard ports (80/443), as the host scripts expect; the JVM trusts the GreenPilot development CA.
& "$env:JBOSS_HOME\bin\standalone.bat" "-Djboss.http.port=80" "-Djboss.https.port=443" `
    "-Djavax.net.ssl.trustStore=$Certs\jvm-truststore.p12" "-Djavax.net.ssl.trustStorePassword=changeit" "-Djavax.net.ssl.trustStoreType=PKCS12"
