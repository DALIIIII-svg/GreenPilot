# Starts WildFly in the foreground (Ctrl+C to stop). The GP_* variables of env.ps1 reach both applications.
. "$PSScriptRoot\env.ps1"
& "$env:JBOSS_HOME\bin\standalone.bat"
