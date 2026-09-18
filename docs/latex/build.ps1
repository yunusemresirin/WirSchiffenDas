# Builds the LaTeX documents with Tectonic inside the "latex" conda environment.
#
#   ./build.ps1              # builds architecture.tex and presentation.tex
#   ./build.ps1 architecture # builds only architecture.tex
#   ./build.ps1 presentation # builds only presentation.tex
#
# The first run downloads the required LaTeX packages from the Tectonic
# bundle (network access required). Later runs are fully offline.

param(
    [ValidateSet('all', 'architecture', 'presentation')]
    [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'

$envName = 'latex'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $scriptDir
try {
    $documents = switch ($Target) {
        'architecture' { @('architecture.tex') }
        'presentation' { @('presentation.tex') }
        default        { @('architecture.tex', 'presentation.tex') }
    }

    foreach ($document in $documents) {
        Write-Host "Building $document ..."
        conda run -n $envName tectonic -X compile $document --keep-logs
        if ($LASTEXITCODE -ne 0) {
            throw "Tectonic build failed for $document with exit code $LASTEXITCODE"
        }
        Write-Host "Build finished: $scriptDir\$([System.IO.Path]::GetFileNameWithoutExtension($document)).pdf"
    }
}
finally {
    Pop-Location
}
