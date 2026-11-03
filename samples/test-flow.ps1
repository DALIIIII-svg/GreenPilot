# =====================================================================
#  GreenPilot - end-to-end test WITHOUT hardware (Windows PowerShell 5.1 or 7)
#  Plays the PWA (OAuth 2.0 + PKCE login, REST calls) and the Raspberry Pi
#  (MQTT v5 over TLS with mosquitto_pub, as user pi-01).
#  Prerequisites: Mosquitto + MongoDB + WildFly running, both WARs deployed.
#  Watch MQTT traffic in another window:
#    & "C:\Program Files\mosquitto\mosquitto_sub.exe" -h localhost -p 8883 --cafile tools\certs\ca.crt `
#        -u backend -P backend-dev-password -V mqttv5 -t "greenpilot/+/up/#" -v
# =====================================================================
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\..\setup\env.ps1"
Add-Type -AssemblyName System.Net.Http

$Iam = "http://localhost:8080/iam"
$Api = "http://localhost:8080/greenpilot/api"
$ClientId = "greenpilot-pwa"
$Redirect = "http://localhost:8080/greenpilot/live-demo.html"
$Pub = "C:\Program Files\mosquitto\mosquitto_pub.exe"
$Here = $PSScriptRoot
$Gw = $MqttGatewayUser

function Step($t) { Write-Host "`n=== $t" -ForegroundColor Cyan }
function Show($o) { $o | ConvertTo-Json -Depth 8 | Write-Host }
function NowIso { (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }
function NewId { [guid]::NewGuid().ToString() }
function B64Url([byte[]]$b) { [Convert]::ToBase64String($b).TrimEnd('=').Replace('+', '-').Replace('/', '_') }

# ---------------------------------------------------------------- OAuth 2.0 Authorization Code + PKCE
function Get-Token([string]$User, [string]$Password, [string]$Scope) {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $verifier = B64Url $bytes                                   # stays secret on the client
    $challenge = B64Url ([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier)))
    $state = NewId

    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false                         # we want to read the redirect ourselves
    $http = New-Object System.Net.Http.HttpClient($handler)

    # step 1: the login form is POSTed to /authorize (a browser would show the HTML page first)
    $form = New-Object 'System.Collections.Generic.Dictionary[string,string]'
    $form["client_id"] = $ClientId; $form["redirect_uri"] = $Redirect; $form["response_type"] = "code"
    $form["scope"] = $Scope; $form["state"] = $state
    $form["code_challenge"] = $challenge; $form["code_challenge_method"] = "S256"
    $form["username"] = $User; $form["password"] = $Password
    $res = $http.PostAsync("$Iam/authorize", (New-Object System.Net.Http.FormUrlEncodedContent($form))).Result
    if ([int]$res.StatusCode -ne 303) { throw "Login failed for $User (HTTP $([int]$res.StatusCode))" }
    $query = $res.Headers.Location.Query.TrimStart('?')
    $params = @{}
    foreach ($kv in $query.Split('&')) { $p = $kv.Split('=', 2); $params[$p[0]] = [Uri]::UnescapeDataString($p[1]) }
    if ($params["error"]) { throw "Authorization error: $($params['error']) - $($params['error_description'])" }
    if ($params["state"] -ne $state) { throw "state mismatch (CSRF protection)" }

    # step 2: exchange the code + verifier for tokens
    $t = New-Object 'System.Collections.Generic.Dictionary[string,string]'
    $t["grant_type"] = "authorization_code"; $t["code"] = $params["code"]; $t["redirect_uri"] = $Redirect
    $t["client_id"] = $ClientId; $t["code_verifier"] = $verifier
    $res = $http.PostAsync("$Iam/token", (New-Object System.Net.Http.FormUrlEncodedContent($t))).Result
    $body = $res.Content.ReadAsStringAsync().Result
    if (-not $res.IsSuccessStatusCode) { throw "Token request failed: $body" }
    return ($body | ConvertFrom-Json)
}

# ---------------------------------------------------------------- REST helpers
function Call($Method, $Url, $Body = $null, $Token = $null, $Headers = @{}) {
    $h = @{} + $Headers
    if ($Token) { $h["Authorization"] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = $Url; Headers = $h; ContentType = "application/json"; UseBasicParsing = $true }
    if ($Body -ne $null) { $params["Body"] = $(if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 10 }) }
    try {
        $r = Invoke-WebRequest @params
        $etag = [string]($r.Headers["ETag"] | Select-Object -First 1)
        $json = if ($r.Content) { $r.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; ETag = $etag; Body = $json }
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Host "  -> HTTP $code $($_.ErrorDetails.Message)" -ForegroundColor Yellow
        return [pscustomobject]@{ Status = $code; ETag = $null; Body = $null }
    }
}

# Publishes as the Raspberry Pi: MQTT v5, TLS, authenticated as pi-01 (temp file avoids quoting problems)
function PiPublish($Kind, $Obj, [string[]]$Extra = @()) {
    $f = [IO.Path]::GetTempFileName()
    [IO.File]::WriteAllText($f, ($Obj | ConvertTo-Json -Depth 10 -Compress))
    & $Pub -h localhost -p 8883 --cafile "$Certs\ca.crt" -u $Gw -P $MqttGatewayPassword -V mqttv5 -q 1 `
        -t "greenpilot/$Gw/up/$Kind" -f $f @Extra
    Remove-Item $f
}

Step "1. Health (public)"
Show (Call GET "$Api/health").Body

Step "2. Admin login: OAuth 2.0 Authorization Code + PKCE against the IAM"
$admin = Get-Token "admin@greenpilot.local" "admin123" "greenhouse:read greenhouse:control incidents:manage admin"
$T = $admin.access_token
Write-Host "  access token (EdDSA JWT, $($admin.expires_in) s): $($T.Substring(0,30))...  scopes: $($admin.scope)"
Write-Host "  public keys: $Iam/jwks"

Step "3. Create the site (409 = already exists)"
Call POST "$Api/sites" ([IO.File]::ReadAllText("$Here\site.json")) $T | Out-Null

Step "4. Register the ESP32 node + its W3C WoT Thing Description"
Call PUT "$Api/devices/esp32-node" ([IO.File]::ReadAllText("$Here\device-esp32-node.json")) $T | Out-Null
$td = Invoke-WebRequest "$Api/devices/esp32-node/td" -Headers @{ Authorization = "Bearer $T" } -UseBasicParsing
Write-Host "  Content-Type: $($td.Headers['Content-Type'])"

Step "5. ETag / conditional requests"
$s = Call GET "$Api/sites/greenhouse-01" $null $T
Write-Host "  ETag: $($s.ETag)"
$c = Call GET "$Api/sites/greenhouse-01" $null $T @{ "If-None-Match" = $s.ETag }
Write-Host "  GET with If-None-Match -> $($c.Status) (304 = not modified, no body)"
$u = Call PUT "$Api/sites/greenhouse-01" @{ address = "SUP'COM, Ariana" } $T @{ "If-Match" = $s.ETag }
Write-Host "  PUT with the current ETag -> $($u.Status), new ETag $($u.ETag)"
Write-Host "  PUT again with the OLD ETag (simulates a stale page) -> expect 412:"
Call PUT "$Api/sites/greenhouse-01" @{ address = "lost update" } $T @{ "If-Match" = $s.ETag } | Out-Null

Step "6. [Pi] telemetry + forecast over MQTT v5/TLS"
PiPublish "telemetry" @{
    msgId = NewId; deviceId = "esp32-node"; ts = NowIso
    readings = @(
        @{ type = "soil_moisture"; zoneId = "A"; value = 41.5; unit = "%" },
        @{ type = "soil_moisture"; zoneId = "B"; value = 38.2; unit = "%" },
        @{ type = "air_temperature"; value = 24.3; unit = "C" },
        @{ type = "air_humidity"; value = 61; unit = "%" },
        @{ type = "reservoir"; value = 1 }
    )
}
PiPublish "forecast" @{ msgId = NewId; zoneId = "A"; ts = NowIso; horizonMin = 30; predicted = 39.8; baseline = 41.5; modelVersion = "persistence-0" }
Write-Host "  invalid message (missing readings) -> rejected by Bean Validation, see the WildFly log"
PiPublish "telemetry" @{ msgId = NewId; ts = NowIso }
Start-Sleep -Seconds 2

Step "7. Dashboard state"
Show (Call GET "$Api/sites/greenhouse-01/state" $null $T).Body.readings

Step "8. Irrigation 10 s (scope greenhouse:control)"
$cmd = (Call POST "$Api/irrigation" @{ siteId = "greenhouse-01"; durationSec = 10 } $T).Body
if ($cmd) {
    Write-Host "  commandId $($cmd.id) status $($cmd.status) - sent with MQTT5 expiry + correlation data"
    Step "9. [Pi] ack via the response topic: commandId only in the MQTT5 correlation data"
    PiPublish "cmd-ack" @{ status = "ACCEPTED" } @("-D", "publish", "correlation-data", $cmd.id)
    PiPublish "cmd-ack" @{ commandId = $cmd.id; status = "COMPLETED" }
    Start-Sleep -Seconds 2
    Show ((Call GET "$Api/irrigation?siteId=greenhouse-01&limit=1" $null $T).Body | Select-Object id, status, history)
}
Step "9b. Immediately again -> 409 cooldown;  9c. 999 s -> 400"
Call POST "$Api/irrigation" @{ siteId = "greenhouse-01"; durationSec = 10 } $T | Out-Null
Call POST "$Api/irrigation" @{ siteId = "greenhouse-01"; durationSec = 999 } $T | Out-Null

Step "10. [Pi] confirmed disease event + JPEG evidence"
$imgKey = "img-" + (NewId)
$det = @(@{ cls = "bacterial_spot"; score = 0.81; bbox = @(30, 20, 130, 100) })
PiPublish "inspection" @{ msgId = NewId; plantId = "pepper-1"; zoneId = "A"; ts = NowIso; status = "DISEASE_SUSPECTED"; imageW = 160; imageH = 120; modelVersion = "yolo-leaf-0.1.0"; imageKey = $imgKey; detections = $det }
PiPublish "incident" @{ eventId = NewId; plantId = "pepper-1"; zoneId = "A"; ts = NowIso; score = 0.81; modelVersion = "yolo-leaf-0.1.0"; imageKey = $imgKey; imageW = 160; imageH = 120; detections = $det }
# binary JPEG: published with a small Java + Paho helper (mosquitto_pub on Windows cannot read binary files)
$Paho = "$env:USERPROFILE\.m2\repository\org\eclipse\paho\org.eclipse.paho.mqttv5.client\1.2.5\org.eclipse.paho.mqttv5.client-1.2.5.jar"
& "$env:JAVA_HOME\bin\java.exe" -cp $Paho "$Here\PublishFile.java" "ssl://localhost:8883" "greenpilot/$Gw/up/image/$imgKey" `
    "$Here\leaf.jpg" $Gw $MqttGatewayPassword "$Certs\truststore.p12" "changeit"
Start-Sleep -Seconds 2

Step "11. Grower workflow on the incident (with If-Match)"
$open = @((Call GET "$Api/incidents?siteId=greenhouse-01&status=OPEN" $null $T).Body)
$inc = $open[0]
if ($inc) {
    $d = Call GET "$Api/incidents/$($inc.id)" $null $T
    Write-Host "  evidence events: $(@($d.Body.events).Count), ETag $($d.ETag)"
    try {
        Invoke-WebRequest "$Api/images/$imgKey" -Headers @{ Authorization = "Bearer $T" } -OutFile "$env:TEMP\evidence.jpg" -UseBasicParsing
        Write-Host "  image saved to $env:TEMP\evidence.jpg"
    } catch { Write-Host "  -> image not found (was it published?)" -ForegroundColor Yellow }
    Write-Host "  close before ack -> 409:"; Call POST "$Api/incidents/$($inc.id)/close" "{}" $T | Out-Null
    $a = Call POST "$Api/incidents/$($inc.id)/ack" "{}" $T @{ "If-Match" = $d.ETag }
    $r = Call POST "$Api/incidents/$($inc.id)/review" @{ verdict = "CONFIRMED"; note = "spots on 2 leaves" } $T @{ "If-Match" = $a.ETag }
    Show (Call POST "$Api/incidents/$($inc.id)/close" @{ note = "leaves removed" } $T @{ "If-Match" = $r.ETag }).Body.status
}

Step "12. Location-based search: sites within 20 km of Tunis"
Show (Call GET "$Api/sites/nearby?lat=36.8065&lng=10.1815&radiusKm=20" $null $T).Body

Step "13. Grower account in the IAM, site access in the API, least privilege"
$g = Call POST "$Iam/identities" @{ username = "grower@greenpilot.local"; password = "grower123"; displayName = "Demo grower"; roles = @("GROWER") } $T
$growerId = if ($g.Body) { $g.Body.id } else { (@((Call GET "$Iam/identities" $null $T).Body) | Where-Object { $_.username -eq "grower@greenpilot.local" }).id }
Call PUT "$Api/users/$growerId/sites" @{ siteIds = @("greenhouse-01") } $T | Out-Null
$grower = Get-Token "grower@greenpilot.local" "grower123" "greenhouse:read greenhouse:control incidents:manage admin"
Write-Host "  grower asked for 'admin' too, IAM granted only: $($grower.scope)"
Show (Call GET "$Api/users/me" $null $grower.access_token).Body
Write-Host "  grower -> /audit (expect 403):"; Call GET "$Api/audit" $null $grower.access_token | Out-Null
Write-Host "  no token -> 401:"; Call GET "$Api/sites" | Out-Null

Step "14. Audit log (last 5)"
Show ((Call GET "$Api/audit?limit=5" $null $T).Body | Select-Object ts, action, username)
Write-Host "`nAll steps executed. Open http://localhost:8080/greenpilot/live-demo.html for the WebSocket live view." -ForegroundColor Green
