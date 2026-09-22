param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version
)

$ErrorActionPreference = 'Stop'

$DockerHubUser = 'ysirin2s'
$DockerHubRepository = 'seka-wirschiffendas'
$ComposeFile = 'alternative_docker-compose.yml'

$Services = @(
    'configuration-service',
    'analysis-management-service',
    'fluid-analysis-service',
    'thermal-analysis-service',
    'electrical-analysis-service',
    'engine-management-analysis-service'
)

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Get-DockerHubAccessToken {
    if ([string]::IsNullOrWhiteSpace($env:DOCKERHUB_PAT)) {
        return $null
    }

    $body = @{
        identifier = $DockerHubUser
        secret = $env:DOCKERHUB_PAT
    } | ConvertTo-Json

    $response = Invoke-RestMethod `
        -Method Post `
        -Uri 'https://hub.docker.com/v2/auth/token' `
        -ContentType 'application/json' `
        -Body $body

    return $response.access_token
}

function Get-RepositoryTags([string]$AccessToken) {
    $headers = @{ Authorization = "Bearer $AccessToken" }
    $url = "https://hub.docker.com/v2/namespaces/$DockerHubUser/repositories/$DockerHubRepository/tags?page_size=100"
    $tags = @()

    while ($url) {
        $response = Invoke-RestMethod -Method Get -Uri $url -Headers $headers
        $tags += $response.results.name
        $url = $response.next
    }

    return $tags
}

function Remove-DockerHubTag([string]$AccessToken, [string]$Tag) {
    $headers = @{ Authorization = "Bearer $AccessToken" }

    # Docker Hub has used both URL forms for tag management over time.
    # Try the current namespace-based endpoint first and fall back to the
    # legacy repository endpoint if the first one is not accepted.
    $urls = @(
        "https://hub.docker.com/v2/namespaces/$DockerHubUser/repositories/$DockerHubRepository/tags/$Tag",
        "https://hub.docker.com/v2/repositories/$DockerHubUser/$DockerHubRepository/tags/$Tag/"
    )

    foreach ($url in $urls) {
        try {
            Invoke-WebRequest -Method Delete -Uri $url -Headers $headers | Out-Null
            return $true
        }
        catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -notin @(404, 405)) {
                throw
            }
        }
    }

    return $false
}

Write-Host ''
Write-Host '=============================================='
Write-Host " WirSchiffenDas Docker Release v$Version"
Write-Host '=============================================='

Write-Step 'Pruefe Docker'
docker version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker ist nicht erreichbar. Bitte Docker Desktop starten.'
}

Write-Step 'Ermittle den reproduzierbaren Source-Stand'
$revision = git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Git-Revision konnte nicht ermittelt werden.' }
$workingTree = git status --porcelain
if ($LASTEXITCODE -ne 0 -or $workingTree) {
    throw 'Vor einem Release alle Source-Aenderungen committen; das Image-Label muss den gebauten Stand identifizieren.'
}
$env:VCS_REF = $revision.Trim()

Write-Step 'Baue und pushe alle sechs Backend-Images'
$env:VERSION = $Version

docker compose -f $ComposeFile build --push @Services
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose Build/Push ist fehlgeschlagen.'
}

Write-Step 'Veroeffentlichte Images'
foreach ($service in $Services) {
    Write-Host "  $DockerHubUser/${DockerHubRepository}:${service}-v$Version"
}

Write-Step 'Bereinige alte Docker-Hub-Versionen'
$accessToken = Get-DockerHubAccessToken

if (-not $accessToken) {
    Write-Warning 'DOCKERHUB_PAT ist nicht gesetzt. Build und Push sind erfolgreich, die Remote-Bereinigung wird uebersprungen.'
    Write-Host 'Einmalig fuer automatische Retention setzen:'
    Write-Host '  $env:DOCKERHUB_PAT = "<dein Docker-Hub-PAT>"'
}
else {
    $allTags = Get-RepositoryTags $accessToken

    $versionPattern = '^(?<service>.+)-v(?<version>\d+\.\d+\.\d+)$'
    $versions = $allTags |
        ForEach-Object {
            if ($_ -match $versionPattern -and $Services -contains $Matches.service) {
                [version]$Matches.version
            }
        } |
        Where-Object { $_ -ne $null } |
        Sort-Object -Descending -Unique

    $keepVersions = @($versions | Select-Object -First 2)
    $removeVersions = @($versions | Select-Object -Skip 2)

    if ($removeVersions.Count -eq 0) {
        Write-Host 'Keine aelteren versionierten Releases zu loeschen.'
    }
    else {
        foreach ($oldVersion in $removeVersions) {
            foreach ($service in $Services) {
                $tag = "$service-v$oldVersion"
                if ($allTags -contains $tag) {
                    Write-Host "  Loesche $tag ..."
                    $deleted = Remove-DockerHubTag -AccessToken $accessToken -Tag $tag
                    if (-not $deleted) {
                        Write-Warning "Tag $tag konnte nicht automatisch geloescht werden. Bitte im Docker-Hub-Repository unter Tags entfernen."
                    }
                }
            }
        }
    }

    if ($keepVersions.Count -gt 0) {
        Write-Host "Behaltene Releases: $($keepVersions -join ', ')"
    }
}

Write-Step 'Release abgeschlossen'
Write-Host "Version v$Version wurde aus Git-Revision $env:VCS_REF gebaut und gepusht."

