param([string]$Test = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/test-services.ps1"
$services = $null
try {
    $services = Start-TestServices $root
    $env:MEDICAL_TEST_AUDIT_DIRECTORY = Join-Path ([IO.Path]::GetTempPath()) ('medical-test-audit-' + [guid]::NewGuid().ToString('N'))
    $maven = if ($env:MEDICAL_MAVEN) { $env:MEDICAL_MAVEN } elseif (Get-Command mvn -ErrorAction SilentlyContinue) { (Get-Command mvn).Source } else { 'D:\apache-maven-3.6.3\bin\mvn.cmd' }
    if ($IsWindows -and (Test-Path 'D:\jdk\jdk-25')) { $env:JAVA_HOME = 'D:\jdk\jdk-25' }
    if ($IsWindows -and -not $env:MEDICAL_BROWSER_CHANNEL -and (Test-Path 'C:\Program Files\Google\Chrome\Application\chrome.exe')) { $env:MEDICAL_BROWSER_CHANNEL = 'chrome' }
    $repo = if ($env:MEDICAL_MAVEN_REPO) { $env:MEDICAL_MAVEN_REPO } elseif (Test-Path "$env:USERPROFILE/.m2/repository") { "$env:USERPROFILE/.m2/repository" } else { "$root/.tools/m2" }
    [string[]]$selection = @()
    if ($Test) { $selection += "-Dtest=$Test" }
    & $maven -gs "$PSScriptRoot/maven-settings.xml" -s "$PSScriptRoot/maven-settings.xml" "-Dmaven.repo.local=$repo" -f "$root/backend/pom.xml" @selection test
    if ($LASTEXITCODE -ne 0) { throw "Backend tests failed ($LASTEXITCODE)." }
} finally { Stop-TestServices $services }
