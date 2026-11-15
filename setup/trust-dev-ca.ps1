# Adds the GreenPilot development CA to the Windows certificate store of the current user, so that
# Chrome and Edge accept https://*.greenpilot.local. Windows asks for confirmation. Restart the browser afterwards.
. "$PSScriptRoot\env.ps1"
Import-Certificate -FilePath "$Certs\ca.crt" -CertStoreLocation Cert:\CurrentUser\Root | Format-List Subject, Thumbprint, NotAfter
