param(
  [ValidateSet('all', 'backend', 'frontend', 'security-static', 'security-deps', 'frontend-functional', 'frontend-perf', 'api-functional', 'api-perf-smoke', 'api-perf-load')]
  [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$qualityDir = Split-Path -Parent $scriptDir
Set-Location $qualityDir

function Invoke-Step([string]$name, [string]$cmd) {
  Write-Host "`n=== $name ===" -ForegroundColor Cyan
  Invoke-Expression $cmd
}

if (-not (Test-Path (Join-Path $qualityDir 'node_modules'))) {
  Write-Host 'Installing quality tool dependencies...' -ForegroundColor Yellow
  npm install
}

switch ($Target) {
  'all' {
    Invoke-Step 'Backend Quality' 'npm run quality:backend'
    Invoke-Step 'Frontend Quality' 'npm run quality:frontend'
    Invoke-Step 'Static Security' 'npm run security:static'
    Invoke-Step 'Dependency Security' 'npm run security:deps'
    Invoke-Step 'Frontend Functional (Post Build)' 'npm run frontend:test:postbuild'
    Invoke-Step 'API Functional' 'npm run api:functional'
    Invoke-Step 'API Performance (Smoke)' 'npm run api:perf:smoke'
  }
  'backend' { Invoke-Step 'Backend Quality' 'npm run quality:backend' }
  'frontend' { Invoke-Step 'Frontend Quality' 'npm run quality:frontend' }
  'security-static' { Invoke-Step 'Static Security' 'npm run security:static' }
  'security-deps' { Invoke-Step 'Dependency Security' 'npm run security:deps' }
  'frontend-functional' { Invoke-Step 'Frontend Functional (Post Build)' 'npm run frontend:test:postbuild' }
  'frontend-perf' { Invoke-Step 'Frontend Performance' 'npm run frontend:perf' }
  'api-functional' { Invoke-Step 'API Functional' 'npm run api:functional' }
  'api-perf-smoke' { Invoke-Step 'API Performance Smoke' 'npm run api:perf:smoke' }
  'api-perf-load' { Invoke-Step 'API Performance Load' 'npm run api:perf:load' }
}

Write-Host "`nQuality pipeline completed for target: $Target" -ForegroundColor Green
