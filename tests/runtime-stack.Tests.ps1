$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pom = [xml](Get-Content "$root/backend/pom.xml" -Raw)
$artifacts = @($pom.project.dependencies.dependency | ForEach-Object { $_.artifactId })
foreach ($dependency in @('spring-boot-starter-web','spring-boot-starter-security','spring-boot-starter-data-redis','mybatis-spring-boot-starter','flyway-mysql','mysql-connector-j')) {
    if ($dependency -notin $artifacts) { throw "Missing real runtime dependency: $dependency" }
}
$manifest = Get-Content "$root/package.json" -Raw | ConvertFrom-Json
if ($manifest.scripts.start -notmatch 'start-backend.ps1' -or $manifest.scripts.test -notmatch 'verify.ps1') { throw 'Default commands must use the approved runtime and complete verification gate.' }
$frontend = Get-Content "$root/frontend/package.json" -Raw | ConvertFrom-Json
foreach ($dependency in @('vue','vue-router','pinia')) { if (-not $frontend.dependencies.$dependency) { throw "Missing frontend runtime dependency: $dependency" } }
$source = @(Get-ChildItem "$root/backend/src/main" -Recurse -File; Get-ChildItem "$root/frontend/src" -Recurse -File)
foreach ($file in $source) {
    $body = Get-Content $file.FullName -Raw
    if ($body -match '(?:localStorage|sessionStorage)\.setItem\s*\(') { throw "Browser storage must not persist authentication tokens: $($file.FullName)" }
    if ($body -match 'MemoryRedis|exposeCaptchaCode') { throw "Test adapter leaked into shipped runtime: $($file.FullName)" }
}
if (-not (Test-Path "$root/.github/workflows/verify.yml")) { throw 'CI verification is required.' }
Write-Output 'Approved runtime and authentication storage checks passed.'
