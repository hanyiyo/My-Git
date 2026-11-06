$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    foreach ($test in Get-ChildItem "$root/tests" -Filter '*.Tests.ps1') {
        & pwsh -NoProfile -File $test.FullName
        if ($LASTEXITCODE -ne 0) { throw "Documentation or architecture check failed: $($test.Name)" }
    }
    & npm --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend typecheck/build failed.' }
    & pwsh -NoProfile -File "$PSScriptRoot/test-backend.ps1"
    if ($LASTEXITCODE -ne 0) { throw 'HTTP/dependency/browser acceptance failed.' }
    Write-Output 'All required verification passed.'
} finally { Pop-Location }
