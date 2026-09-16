param(
    [string]$ConfigurationUrl = 'http://localhost:8081',
    [string]$AnalysisUrl = 'http://localhost:8082',
    [string]$FluidUrl = 'http://localhost:8083',
    [string]$ThermalUrl = 'http://localhost:8084',
    [int]$TimeoutSeconds = 60,
    [switch]$IncludeFaults,
    [switch]$IncludeWorkerLoss,
    [string]$ProjectName = ''
)

$ErrorActionPreference = 'Stop'
$composePath = Join-Path (Split-Path $PSScriptRoot -Parent) 'docker-compose.yml'
$composeArgs = @('compose', '-f', $composePath)
if ($ProjectName) { $composeArgs += @('-p', $ProjectName) }
$script:assertionCount = 0

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Assertion failed: $Message" }
    $script:assertionCount++
}

function Invoke-Api([string]$Method, [string]$Url, $Body = $null) {
    $request = @{ Method = $Method; Uri = $Url; TimeoutSec = 10; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @request
}

function Invoke-Compose([string[]]$Arguments) {
    & docker @composeArgs @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed: $Arguments" }
}

function Wait-Ready([string]$Url) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            if ((Invoke-Api GET "$Url/actuator/health/readiness").status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Service not ready: $Url"
}

function Wait-Run([string]$Id, [scriptblock]$Predicate, [string]$Description) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $run = Invoke-Api GET "$AnalysisUrl/api/analyses/$Id"
        if (& $Predicate $run) { return $run }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timeout waiting for $Description. Last response: $($run | ConvertTo-Json -Depth 12 -Compress)"
}

function Get-Algorithm($Run, [string]$Name) {
    @($Run.algorithms | Where-Object algorithm -eq $Name)[0]
}

function New-Configuration([string]$Oil = 'STANDARD') {
    Invoke-Api POST "$ConfigurationUrl/api/configurations" @{
        oilSystem = $Oil; fuelSystem = 'PREMIUM'; coolingSystem = 'STANDARD'
        electricalSystem = 'PREMIUM'; engineManagementSystem = 'ADVANCED'
    }
}

function Start-Run([string]$ConfigurationId) {
    Invoke-Api POST "$AnalysisUrl/api/analyses" @{ configurationId = $ConfigurationId }
}

function Assert-Complete($Run) {
    Assert-That ($Run.overallResult -eq 'OK') 'Overall result is OK'
    Assert-That (@($Run.algorithms | Where-Object { $_.status -eq 'READY' -and $_.result -eq 'OK' }).Count -eq 4) 'Four READY/OK algorithms'
    Assert-That ((Get-Algorithm $Run FLUID).equipmentResults.oilSystem -eq 'OK') 'Oil result persisted'
    Assert-That ((Get-Algorithm $Run FLUID).equipmentResults.fuelSystem -eq 'OK') 'Fuel result persisted'
    Assert-That ((Get-Algorithm $Run THERMAL).equipmentResults.coolingSystem -eq 'OK') 'Cooling result persisted'
    Assert-That ((Get-Algorithm $Run ELECTRICAL).equipmentResults.electricalSystem -eq 'OK') 'Electrical result persisted'
    Assert-That ((Get-Algorithm $Run ENGINE_MANAGEMENT).equipmentResults.engineManagementSystem -eq 'OK') 'Engine management result persisted'
}

function Get-Breaker([string]$Url, [string]$Name) {
    # The dedicated endpoint stays readable when the global health endpoint returns 503.
    $snapshot = Invoke-Api GET "$Url/actuator/circuitbreakers"
    $snapshot.circuitBreakers.$Name
}

function Wait-Probe([string]$Url, [string]$Name) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $state = (Get-Breaker $Url $Name).state
        if ($state -in @('HALF_OPEN', 'CLOSED')) { return }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Breaker $Name did not allow a probe"
}

foreach ($url in @($ConfigurationUrl, $AnalysisUrl, $FluidUrl, $ThermalUrl)) { Wait-Ready $url }

Write-Host '1. Successful analysis and equipment results'
$configuration = New-Configuration
$loaded = Invoke-Api GET "$ConfigurationUrl/api/configurations/$($configuration.configurationId)"
Assert-That ($loaded.oilSystem -eq 'STANDARD') 'Saved configuration can be loaded'
$started = Start-Run $configuration.configurationId
$complete = Wait-Run $started.analysisId { param($r) $r.overallResult -eq 'OK' } 'successful analysis'
Assert-Complete $complete

Write-Host '2. Negative oil result with positive fuel result'
$invalid = New-Configuration INVALID
$negativeStart = Start-Run $invalid.configurationId
$negative = Wait-Run $negativeStart.analysisId { param($r) $r.overallResult -eq 'FAILED' } 'negative analysis'
$fluid = Get-Algorithm $negative FLUID
Assert-That ($fluid.equipmentResults.oilSystem -eq 'FAILED') 'Invalid oil is identified'
Assert-That ($fluid.equipmentResults.fuelSystem -eq 'OK') 'Valid fuel remains identifiable'
Assert-That ((Get-Algorithm $negative ENGINE_MANAGEMENT).status -ne 'READY') 'Dependent engine analysis cannot succeed'

if ($IncludeFaults -or $IncludeWorkerLoss) {
    try {
        Write-Host '3. Thermal outage and isolated circuit breakers'
        Invoke-Compose @('stop', 'thermal-analysis-service')
        $outageStart = Start-Run $configuration.configurationId
        $outage = Wait-Run $outageStart.analysisId { param($r) (Get-Algorithm $r THERMAL).status -eq 'FAILED' } 'thermal outage'
        Assert-That ($outage.overallResult -eq 'FAILED') 'Outage is visible'
        Assert-That ((Get-Breaker $FluidUrl nextService).state -in @('OPEN', 'HALF_OPEN')) 'Fluid-to-thermal breaker opens'
        $oldThermalAttempt = (Get-Algorithm $outage THERMAL).attemptId
        $oldFluidAttempt = (Get-Algorithm $outage FLUID).attemptId
        Invoke-Api POST "$AnalysisUrl/api/analyses/$($outage.analysisId)/algorithms/THERMAL/retry" | Out-Null
        $outage = Wait-Run $outage.analysisId { param($r) (Get-Algorithm $r THERMAL).status -eq 'FAILED' } 'failed thermal retry'
        Assert-That ((Get-Breaker $AnalysisUrl startThermal).state -in @('OPEN', 'HALF_OPEN')) 'Management thermal breaker opens'
        Assert-That ((Get-Breaker $AnalysisUrl startFluid).state -eq 'CLOSED') 'Management fluid breaker stays closed'
        $independent = Start-Run $configuration.configurationId
        $independent = Wait-Run $independent.analysisId { param($r) (Get-Algorithm $r FLUID).status -eq 'READY' } 'independent fluid execution'
        Assert-That ((Get-Algorithm $independent FLUID).result -eq 'OK') 'Healthy fluid remains callable'

        Write-Host '4. Recovery, retry and stale callback'
        Invoke-Compose @('start', 'thermal-analysis-service')
        Wait-Ready $ThermalUrl
        Wait-Probe $AnalysisUrl startThermal
        Invoke-Api POST "$AnalysisUrl/api/analyses/$($outage.analysisId)/algorithms/THERMAL/retry" | Out-Null
        $recovered = Wait-Run $outage.analysisId { param($r) $r.overallResult -eq 'OK' } 'retry recovery'
        Assert-Complete $recovered
        Assert-That ((Get-Algorithm $recovered FLUID).attemptId -eq $oldFluidAttempt) 'Successful predecessor is retained'
        Assert-That ((Get-Algorithm $recovered THERMAL).attemptId -ne $oldThermalAttempt) 'Retry receives a new attempt ID'
        Invoke-Api PUT "$AnalysisUrl/internal/analyses/$($outage.analysisId)/algorithms/THERMAL/result" @{
            attemptId = $oldThermalAttempt; status = 'FAILED'; result = 'FAILED'
            message = 'Deliberately stale test callback'; equipmentResults = @{ coolingSystem = 'FAILED' }
        } | Out-Null
        $afterStale = Invoke-Api GET "$AnalysisUrl/api/analyses/$($outage.analysisId)"
        Assert-That ($afterStale.overallResult -eq 'OK') 'Stale result cannot overwrite the retry'
        Assert-That ((Get-Breaker $AnalysisUrl startThermal).state -eq 'CLOSED') 'Successful management probe closes its breaker'

        Write-Host '5. Probe the original fluid-to-thermal connection'
        Wait-Probe $FluidUrl nextService
        $probeStart = Start-Run $configuration.configurationId
        $probe = Wait-Run $probeStart.analysisId { param($r) $r.overallResult -eq 'OK' } 'original connection recovery'
        Assert-Complete $probe
        Assert-That ((Get-Breaker $FluidUrl nextService).state -eq 'CLOSED') 'Original breaker closes after its own successful probe'

        if ($IncludeWorkerLoss) {
            Write-Host '6. Lost worker after acceptance, deadline and retry'
            $lostStart = Start-Run $configuration.configurationId
            $running = Wait-Run $lostStart.analysisId { param($r) (Get-Algorithm $r THERMAL).status -eq 'RUNNING' } 'running thermal worker'
            Invoke-Compose @('kill', 'thermal-analysis-service')
            $timedOut = Wait-Run $running.analysisId { param($r) (Get-Algorithm $r THERMAL).status -eq 'FAILED' } 'inactivity timeout'
            Assert-That ($timedOut.overallResult -eq 'FAILED') 'Lost worker reaches a retryable error'
            Assert-That ((Get-Algorithm $timedOut THERMAL).message -match '(?i)timeout|timed out|inactiv') 'Timeout reason is visible'
            Invoke-Compose @('start', 'thermal-analysis-service')
            Wait-Ready $ThermalUrl
            Invoke-Api POST "$AnalysisUrl/api/analyses/$($running.analysisId)/algorithms/THERMAL/retry" | Out-Null
            $restarted = Wait-Run $running.analysisId { param($r) $r.overallResult -eq 'OK' } 'retry after worker loss'
            Assert-Complete $restarted
        }
    } finally {
        Invoke-Compose @('start', 'thermal-analysis-service')
    }
}

Write-Host "All $script:assertionCount assertions passed."
