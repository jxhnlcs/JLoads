# Gera o pacote distribuível no Windows: jar único (API + interface) + scripts de inicialização.
# Uso: .\scripts\build-release.ps1 -Version 1.0.0
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'
$bundle = Join-Path $dist "jloads-$Version"

Write-Host '==> Construindo a interface (Angular)'
Push-Location (Join-Path $root 'frontend')
try {
    npm ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'npm ci falhou' }
    npx ng build --configuration production
    if ($LASTEXITCODE -ne 0) { throw 'Build do frontend falhou' }
} finally {
    Pop-Location
}

Write-Host '==> Construindo o jar com a interface embutida'
Push-Location (Join-Path $root 'backend')
try {
    .\mvnw.cmd -B -ntp -Pbundle "-Drevision=$Version" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Build do backend falhou' }
} finally {
    Pop-Location
}

Write-Host '==> Montando o pacote'
if (Test-Path $bundle) { Remove-Item -Recurse -Force $bundle }
New-Item -ItemType Directory -Force (Join-Path $bundle 'config') | Out-Null
$jar = Join-Path $root "backend\target\jloads-$Version.jar"
Copy-Item $jar $bundle
Copy-Item $jar $dist
Copy-Item (Join-Path $root 'scripts\jloads.cmd'), (Join-Path $root 'scripts\jloads.sh'), (Join-Path $root 'README.md'), (Join-Path $root 'LICENSE') $bundle
Copy-Item (Join-Path $root 'scripts\application.example.yml') (Join-Path $bundle 'config\application.yml.example')

$zip = Join-Path $dist "jloads-$Version.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $bundle -DestinationPath $zip
Write-Host "Pacote gerado: $zip"
