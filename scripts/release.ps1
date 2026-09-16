param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)+$')]
    [string]$ImageRepository
)

$ErrorActionPreference = 'Stop'
$composePath = Join-Path (Split-Path $PSScriptRoot -Parent) 'docker-compose.yml'
$services = @(
    'configuration-service', 'analysis-management-service', 'fluid-analysis-service',
    'thermal-analysis-service', 'electrical-analysis-service', 'engine-management-analysis-service'
)
$previousVersion = $env:VERSION
try {
    $env:VERSION = $Version
    docker compose -f $composePath build
    if ($LASTEXITCODE -ne 0) { throw 'Local build failed.' }
    foreach ($service in $services) {
        $localImage = "wirschiffendas/${service}:$Version"
        $publishedImage = "${ImageRepository}:${service}-v$Version"
        docker tag $localImage $publishedImage
        if ($LASTEXITCODE -ne 0) { throw "Tagging failed: $service" }
        docker push $publishedImage
        if ($LASTEXITCODE -ne 0) { throw "Publishing failed: $service" }
    }
    Write-Host "Published six backend images to $ImageRepository, version $Version."
    Write-Host 'For compose.images.yml, set IMAGE_REPOSITORY and VERSION accordingly.'
} finally {
    $env:VERSION = $previousVersion
}
