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
    if ($body -match '(?:localStorage|sessionStorage)\.setItem\s*\(') {
        # Q7/Q17 明确授权同浏览器保存虚构联动进度；只允许指定开发原型写入，认证信息仍禁止持久化。
        $demoSettings = Join-Path $root 'frontend/src/views/doctor-home-prototype/demo-settings.ts'
        $safeDemoWriter = $file.FullName -eq $demoSettings -and
            $body.Contains('if (!import.meta.env.DEV) return') -and
            $body.Contains("'medical-doctor-home-demo-v1'") -and
            $body.Contains('localStorage.setItem(demoSettingsKey, JSON.stringify({ scenario, role }))') -and
            ([regex]::Matches($body, '(?:localStorage|sessionStorage)\.setItem\s*\(').Count -eq 1) -and
            $body.Contains("scenarios.find(item => item.key === settings.scenario)?.key ?? 'current'") -and
            $body.Contains("settings.role === 'head' ? 'head' : 'doctor'")
        $receptionDemo = Join-Path $root 'frontend/src/views/doctor-home-prototype/reception-demo.ts'
        $safeReceptionWriter = $file.FullName -eq $receptionDemo -and
            $body.Contains('if (!import.meta.env.DEV) return false') -and
            $body.Contains("'medical-reception-prototype-v1'") -and
            $body.Contains('navigator.locks.request(receptionKey') -and
            $body.Contains('localStorage.setItem(receptionKey, JSON.stringify(next))') -and
            ([regex]::Matches($body, '(?:localStorage|sessionStorage)\.setItem\s*\(').Count -eq 1) -and
            ($body -notmatch 'accessToken|refreshToken|password|captcha')
        if (-not $safeDemoWriter -and -not $safeReceptionWriter) { throw "Browser storage must not persist authentication tokens: $($file.FullName)" }
    }
    if ($body -match 'MemoryRedis|exposeCaptchaCode') { throw "Test adapter leaked into shipped runtime: $($file.FullName)" }
}
if (-not (Test-Path "$root/.github/workflows/verify.yml")) { throw 'CI verification is required.' }
$testServices = Get-Content "$root/scripts/test-services.ps1" -Raw
if ($testServices -notmatch 'Get-CimInstance Win32_Process' -or $testServices -notmatch 'Remove-Item -LiteralPath \$Services\.Root') { throw 'Isolated test services must clean child processes and their generated data directory.' }
Write-Output 'Approved runtime and authentication storage checks passed.'
