<#
.SYNOPSIS
  Chaos engineering test suite for the Stock Brokerage app using Toxiproxy.

.DESCRIPTION
  Runs fault-injection scenarios against the running Docker Compose chaos stack
  and reports baseline vs under-chaos response times and HTTP statuses.

  Requires the chaos stack to be running first:
    docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d

.PARAMETER Scenario
  Which scenario to run. Default: All
  Options: DbLatency, DbOutage, DbBandwidthThrottle, RedisLatency, RedisOutage, All

.PARAMETER AppUrl
  Base URL of the application. Default: http://localhost

.PARAMETER ToxiproxyUrl
  Toxiproxy admin API base URL. Default: http://localhost:8474

.PARAMETER Username / Password
  Credentials for API calls. Default: client5 / pass1234

.PARAMETER Samples
  Number of HTTP requests per measurement point. Default: 3

.EXAMPLE
  # Run all chaos scenarios
  .\chaos-tests.ps1

  # Run only the DB latency scenario
  .\chaos-tests.ps1 -Scenario DbLatency

  # Run against a remote host
  .\chaos-tests.ps1 -AppUrl https://myapp.example.com -ToxiproxyUrl http://myapp.example.com:8474
#>

[CmdletBinding()]
param(
    [ValidateSet('DbLatency', 'DbOutage', 'DbBandwidthThrottle', 'RedisLatency', 'RedisOutage', 'All')]
    [string]$Scenario     = 'All',

    [string]$AppUrl       = 'http://localhost',
    [string]$ToxiproxyUrl = 'http://localhost:8474',
    [string]$Username     = 'client5',
    [string]$Password     = 'pass1234',
    [int]   $Samples      = 3
)

$ErrorActionPreference = 'Stop'

# ─── Auth header ─────────────────────────────────────────────────────────────
$AuthBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:${Password}"))
$AuthHeader = @{ Authorization = "Basic $AuthBase64" }

# ─── Result accumulator ──────────────────────────────────────────────────────
$script:Results = [System.Collections.Generic.List[PSObject]]::new()

# ─────────────────────────────────────────────────────────────────────────────
# Toxiproxy REST helpers
# ─────────────────────────────────────────────────────────────────────────────

function Invoke-Toxiproxy {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Body = $null
    )
    # Toxiproxy 2.9+ rejects requests without a User-Agent header
    $params = @{
        Method      = $Method
        Uri         = "$ToxiproxyUrl$Path"
        ContentType = 'application/json'
        Headers     = @{ 'User-Agent' = 'chaos-tests/1.0' }
        ErrorAction = 'Stop'
    }
    if ($Body) { $params.Body = ($Body | ConvertTo-Json -Depth 5) }
    Invoke-RestMethod @params
}

function Add-Toxic {
    param(
        [string]$Proxy,
        [string]$Name,
        [string]$Type,
        [hashtable]$Attributes,
        [string]$Stream   = 'downstream',
        [double]$Toxicity = 1.0
    )
    $body = @{
        name       = $Name
        type       = $Type
        stream     = $Stream
        toxicity   = $Toxicity
        attributes = $Attributes
    }
    Invoke-Toxiproxy -Method POST -Path "/proxies/$Proxy/toxics" -Body $body | Out-Null
    Write-Host "  [+] Toxic '$Name' ($Type) → proxy '$Proxy'" -ForegroundColor DarkYellow
}

function Remove-Toxic {
    param([string]$Proxy, [string]$Name)
    try {
        Invoke-Toxiproxy -Method DELETE -Path "/proxies/$Proxy/toxics/$Name" | Out-Null
        Write-Host "  [-] Removed toxic '$Name' from proxy '$Proxy'" -ForegroundColor DarkGray
    } catch {
        Write-Host "  [!] Could not remove toxic '$Name': $($_.Exception.Message)" -ForegroundColor DarkRed
    }
}

function Set-ProxyEnabled {
    param([string]$Proxy, [bool]$Enabled)
    Invoke-Toxiproxy -Method POST -Path "/proxies/$Proxy" -Body @{ enabled = $Enabled } | Out-Null
    $state = if ($Enabled) { 'ENABLED' } else { 'DISABLED' }
    $color = if ($Enabled) { 'DarkGray' } else { 'DarkYellow' }
    Write-Host "  [proxy] '$Proxy' → $state" -ForegroundColor $color
}

function Reset-AllToxics {
    try {
        $proxies = Invoke-Toxiproxy -Method GET -Path '/proxies'
        foreach ($proxyName in $proxies.PSObject.Properties.Name) {
            # Re-enable any disabled proxy
            Set-ProxyEnabled -Proxy $proxyName -Enabled $true

            $toxics = Invoke-Toxiproxy -Method GET -Path "/proxies/$proxyName/toxics"
            foreach ($toxic in $toxics) {
                Remove-Toxic -Proxy $proxyName -Name $toxic.name
            }
        }
    } catch {
        Write-Host "  [!] Reset-AllToxics error: $($_.Exception.Message)" -ForegroundColor DarkRed
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# Measurement helper
# ─────────────────────────────────────────────────────────────────────────────

function Measure-Endpoint {
    param(
        [string]   $Url,
        [hashtable]$Headers  = $AuthHeader,
        [int]      $Count    = $Samples,
        [int]      $TimeoutSec = 30
    )
    $times    = [System.Collections.Generic.List[double]]::new()
    $statuses = [System.Collections.Generic.List[string]]::new()

    for ($i = 0; $i -lt $Count; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $resp = Invoke-WebRequest -Uri $Url -Headers $Headers `
                        -UseBasicParsing -TimeoutSec $TimeoutSec -ErrorAction Stop
            $statuses.Add([string]$resp.StatusCode)
        } catch {
            $statuses.Add('ERR')
        }
        $sw.Stop()
        $times.Add([Math]::Round($sw.Elapsed.TotalMilliseconds, 0))
        Start-Sleep -Milliseconds 300
    }

    [PSCustomObject]@{
        Avg    = [Math]::Round(($times | Measure-Object -Average).Average, 0)
        Max    = [Math]::Round(($times | Measure-Object -Maximum).Maximum, 0)
        Min    = [Math]::Round(($times | Measure-Object -Minimum).Minimum, 0)
        Status = ($statuses -join ',')
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# Reporting helpers
# ─────────────────────────────────────────────────────────────────────────────

function Write-ScenarioHeader {
    param([string]$Name, [string]$Description)
    Write-Host ""
    Write-Host ("─" * 56) -ForegroundColor Cyan
    Write-Host "  SCENARIO : $Name" -ForegroundColor Cyan
    Write-Host "  FAULT    : $Description" -ForegroundColor Gray
    Write-Host ("─" * 56) -ForegroundColor Cyan
}

function Add-Result {
    param(
        [string]$ScenarioName,
        [string]$Endpoint,
        [string]$ChaosType,
        $Baseline,
        $UnderChaos
    )
    $degradPct = if ($Baseline.Avg -gt 0) {
        [Math]::Round((($UnderChaos.Avg - $Baseline.Avg) / $Baseline.Avg) * 100, 0)
    } else { $null }

    $degradLabel = if ($null -ne $degradPct) { "${degradPct}%" } else { 'N/A' }
    $color = if ($UnderChaos.Status -match 'ERR|5\d\d') { 'Red' }
             elseif ($null -ne $degradPct -and $degradPct -gt 200) { 'Yellow' }
             else { 'Green' }

    Write-Host ("  %-30s  baseline %5d ms (%s)  chaos %5d ms (%s)  degraded %s" -f `
        $Endpoint, $Baseline.Avg, $Baseline.Status, `
        $UnderChaos.Avg, $UnderChaos.Status, $degradLabel) -ForegroundColor $color

    $script:Results.Add([PSCustomObject]@{
        Scenario       = $ScenarioName
        Endpoint       = $Endpoint
        ChaosType      = $ChaosType
        Baseline_ms    = $Baseline.Avg
        Chaos_ms       = $UnderChaos.Avg
        Degradation    = $degradLabel
        StatusBaseline = $Baseline.Status
        StatusChaos    = $UnderChaos.Status
    })
}

# ─────────────────────────────────────────────────────────────────────────────
# Chaos scenarios
# ─────────────────────────────────────────────────────────────────────────────

function Test-DbLatency {
    <#
    .SYNOPSIS Injects 2 000 ms latency on every PostgreSQL packet.
    .NOTES    Verifies that DB-heavy endpoints degrade proportionally and
              the app doesn't silently swallow errors.
    #>
    Write-ScenarioHeader 'DB Latency' '2 000 ms latency on every PostgreSQL TCP packet'

    $endpoints = @(
        [PSCustomObject]@{ Label = 'portfolio/summary';   Url = "$AppUrl/api/portfolio/client/5/summary" }
        [PSCustomObject]@{ Label = 'trades/client/5';     Url = "$AppUrl/api/trades/client/5" }
        [PSCustomObject]@{ Label = 'prices/TNA';          Url = "$AppUrl/api/prices/TNA" }
    )

    foreach ($ep in $endpoints) {
        Write-Host "  Testing: $($ep.Label)" -ForegroundColor White
        $baseline = Measure-Endpoint -Url $ep.Url

        Add-Toxic -Proxy 'stock_postgres' -Name 'db_latency' `
                  -Type 'latency' -Attributes @{ latency = 2000; jitter = 100 }
        $chaos = Measure-Endpoint -Url $ep.Url
        Remove-Toxic -Proxy 'stock_postgres' -Name 'db_latency'

        Add-Result -ScenarioName 'DbLatency' -Endpoint $ep.Label `
                   -ChaosType '2 000 ms latency' -Baseline $baseline -UnderChaos $chaos
    }
}

function Test-DbOutage {
    <#
    .SYNOPSIS Disables the PostgreSQL proxy entirely — hard DB failure.
    .NOTES    The app should return 5xx quickly; connection pool should not
              exhaust / hang. Backend Spring Boot uses HikariCP default
              connection-timeout of 30 s, so requests should fail within that.
    #>
    Write-ScenarioHeader 'DB Hard Outage' 'PostgreSQL proxy disabled — simulates complete DB failure'

    $endpoints = @(
        [PSCustomObject]@{ Label = 'portfolio/summary'; Url = "$AppUrl/api/portfolio/client/5/summary" }
        [PSCustomObject]@{ Label = 'trades/client/5';   Url = "$AppUrl/api/trades/client/5" }
    )

    foreach ($ep in $endpoints) {
        Write-Host "  Testing: $($ep.Label)" -ForegroundColor White
        $baseline = Measure-Endpoint -Url $ep.Url

        Set-ProxyEnabled -Proxy 'stock_postgres' -Enabled $false
        # Use a shorter timeout — we expect fast failure, not a 30 s hang
        $chaos = Measure-Endpoint -Url $ep.Url -TimeoutSec 35
        Set-ProxyEnabled -Proxy 'stock_postgres' -Enabled $true

        Add-Result -ScenarioName 'DbOutage' -Endpoint $ep.Label `
                   -ChaosType 'proxy disabled' -Baseline $baseline -UnderChaos $chaos
    }
}

function Test-DbBandwidthThrottle {
    <#
    .SYNOPSIS Throttles the PostgreSQL connection to 10 KB/s.
    .NOTES    Simulates a saturated network link to the DB host. Large result
              sets (ATR history: 480 bars) are most affected.
    #>
    Write-ScenarioHeader 'DB Bandwidth Throttle' 'PostgreSQL capped at 10 KB/s — saturated network link'

    $endpoints = @(
        [PSCustomObject]@{ Label = 'portfolio/summary'; Url = "$AppUrl/api/portfolio/client/5/summary" }
        [PSCustomObject]@{ Label = 'atr/TNA';           Url = "$AppUrl/api/atr/TNA" }
    )

    foreach ($ep in $endpoints) {
        Write-Host "  Testing: $($ep.Label)" -ForegroundColor White
        $baseline = Measure-Endpoint -Url $ep.Url

        Add-Toxic -Proxy 'stock_postgres' -Name 'db_bandwidth' `
                  -Type 'bandwidth' -Attributes @{ rate = 10 }
        $chaos = Measure-Endpoint -Url $ep.Url -TimeoutSec 60
        Remove-Toxic -Proxy 'stock_postgres' -Name 'db_bandwidth'

        Add-Result -ScenarioName 'DbBandwidthThrottle' -Endpoint $ep.Label `
                   -ChaosType '10 KB/s bandwidth' -Baseline $baseline -UnderChaos $chaos
    }
}

function Test-RedisLatency {
    <#
    .SYNOPSIS Injects 500 ms latency on every Redis packet.
    .NOTES    Redis is used for session storage and rate-limiting. Latency
              here adds to every authenticated request's overhead.
    #>
    Write-ScenarioHeader 'Redis Latency' '500 ms latency on every Redis TCP packet'

    $endpoints = @(
        [PSCustomObject]@{ Label = 'portfolio/summary'; Url = "$AppUrl/api/portfolio/client/5/summary" }
        [PSCustomObject]@{ Label = 'auth check';        Url = "$AppUrl/api/account/5" }
    )

    foreach ($ep in $endpoints) {
        Write-Host "  Testing: $($ep.Label)" -ForegroundColor White
        $baseline = Measure-Endpoint -Url $ep.Url

        Add-Toxic -Proxy 'stock_redis' -Name 'redis_latency' `
                  -Type 'latency' -Attributes @{ latency = 500; jitter = 50 }
        $chaos = Measure-Endpoint -Url $ep.Url
        Remove-Toxic -Proxy 'stock_redis' -Name 'redis_latency'

        Add-Result -ScenarioName 'RedisLatency' -Endpoint $ep.Label `
                   -ChaosType '500 ms latency' -Baseline $baseline -UnderChaos $chaos
    }
}

function Test-RedisOutage {
    <#
    .SYNOPSIS Disables the Redis proxy — Redis is completely unreachable.
    .NOTES    Spring Boot (Spring Session + Spring Security) should degrade
              gracefully: sessions fall back to in-memory, rate limiting
              is skipped. Functional API responses should still succeed.
    #>
    Write-ScenarioHeader 'Redis Hard Outage' 'Redis proxy disabled — simulates Redis going down'

    $endpoints = @(
        [PSCustomObject]@{ Label = 'portfolio/summary'; Url = "$AppUrl/api/portfolio/client/5/summary" }
        [PSCustomObject]@{ Label = 'auth check';        Url = "$AppUrl/api/account/5" }
    )

    foreach ($ep in $endpoints) {
        Write-Host "  Testing: $($ep.Label)" -ForegroundColor White
        $baseline = Measure-Endpoint -Url $ep.Url

        Set-ProxyEnabled -Proxy 'stock_redis' -Enabled $false
        $chaos = Measure-Endpoint -Url $ep.Url -TimeoutSec 35
        Set-ProxyEnabled -Proxy 'stock_redis' -Enabled $true

        Add-Result -ScenarioName 'RedisOutage' -Endpoint $ep.Label `
                   -ChaosType 'proxy disabled' -Baseline $baseline -UnderChaos $chaos
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════╗" -ForegroundColor Magenta
Write-Host "║    Stock Brokerage — Chaos Engineering Test Suite   ║" -ForegroundColor Magenta
Write-Host "╚══════════════════════════════════════════════════════╝" -ForegroundColor Magenta
Write-Host "  App URL:        $AppUrl"       -ForegroundColor Gray
Write-Host "  Toxiproxy:      $ToxiproxyUrl" -ForegroundColor Gray
Write-Host "  Scenario:       $Scenario"     -ForegroundColor Gray
Write-Host "  Samples/point:  $Samples"      -ForegroundColor Gray

# ── Verify Toxiproxy is reachable ──────────────────────────────────────────
Write-Host ""
Write-Host "  Verifying Toxiproxy connectivity..." -ForegroundColor Gray
try {
    $proxies = Invoke-Toxiproxy -Method GET -Path '/proxies'
    $proxyNames = $proxies.PSObject.Properties.Name -join ', '
    Write-Host "  Active proxies: $proxyNames" -ForegroundColor Gray
} catch {
    Write-Error @"
Cannot reach Toxiproxy at $ToxiproxyUrl.

Is the chaos stack running? Start it with:
  docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d

Then wait ~30 s for the backend to reconnect through Toxiproxy, then re-run this script.
"@
    exit 1
}

# ── Clean any leftover toxics from a previous interrupted run ───────────────
Write-Host "  Cleaning up any leftover toxics..." -ForegroundColor Gray
Reset-AllToxics

# ── Run selected scenario(s) ────────────────────────────────────────────────
switch ($Scenario) {
    'DbLatency'           { Test-DbLatency }
    'DbOutage'            { Test-DbOutage }
    'DbBandwidthThrottle' { Test-DbBandwidthThrottle }
    'RedisLatency'        { Test-RedisLatency }
    'RedisOutage'         { Test-RedisOutage }
    'All' {
        Test-DbLatency
        Test-DbOutage
        Test-DbBandwidthThrottle
        Test-RedisLatency
        Test-RedisOutage
    }
}

# ── Always clean up before exiting ─────────────────────────────────────────
Write-Host ""
Write-Host "  Cleaning up toxics..." -ForegroundColor Gray
Reset-AllToxics

# ── Summary report ───────────────────────────────────────────────────────────
Write-Host ""
Write-Host ("═" * 80) -ForegroundColor Cyan
Write-Host "  RESULTS SUMMARY" -ForegroundColor Cyan
Write-Host ("═" * 80) -ForegroundColor Cyan
$script:Results | Format-Table Scenario, Endpoint, ChaosType, Baseline_ms, Chaos_ms, Degradation, StatusBaseline, StatusChaos -AutoSize

$failures = $script:Results | Where-Object { $_.StatusChaos -match 'ERR|5\d\d' }
if ($failures) {
    Write-Host "  ERRORS under chaos ($($failures.Count) endpoint(s) returned 5xx/ERR):" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "    • $($_.Scenario) / $($_.Endpoint)" -ForegroundColor Red }
}
Write-Host ""
