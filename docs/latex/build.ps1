# Builds architecture.tex with Tectonic inside the "latex" conda environment.
#
#   ./build.ps1
#
# The first run downloads the required LaTeX packages from the Tectonic
# bundle (network access required). Later runs are fully offline.

$ErrorActionPreference = 'Stop'

$envName = 'latex'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $scriptDir
try {
    conda run -n $envName tectonic -X compile architecture.tex --keep-logs
    if ($LASTEXITCODE -ne 0) {
        throw "Tectonic build failed with exit code $LASTEXITCODE"
    }
    Write-Host "Build finished: $scriptDir\architecture.pdf"
}
finally {
    Pop-Location
}
