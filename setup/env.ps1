# Environment for the current PowerShell window (dot-sourced by the other scripts).
# MicroProfile Config reads environment variables: gp.mqtt.url <-> GP_MQTT_URL
$Root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME  = "$Root\tools\jdk"
$env:MAVEN_HOME = "$Root\tools\maven"
$env:JBOSS_HOME = "$Root\tools\wildfly"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"

$Certs = "$Root\tools\certs"
$MqttBackendPassword = "backend-dev-password"   # DEVELOPMENT values - change for a real deployment
$MqttGatewayUser     = "pi-01"
$MqttGatewayPassword = "pi01-dev-password"

# ---- overrides of microprofile-config.properties (both WARs) ----
$env:JNOSQL_MONGODB_URL           = "mongodb://localhost:27017"
$env:GP_MQTT_URL                  = "ssl://localhost:8883"
$env:GP_MQTT_USERNAME             = "backend"
$env:GP_MQTT_PASSWORD             = $MqttBackendPassword
$env:GP_MQTT_TRUSTSTORE_PATH      = "$Certs\truststore.p12"
$env:GP_MQTT_TRUSTSTORE_PASSWORD  = "changeit"
$env:GP_IAM_ADMIN_USERNAME        = "admin@greenpilot.local"
$env:GP_IAM_ADMIN_PASSWORD        = "admin123"
