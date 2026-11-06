$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$maven = if ($env:MEDICAL_MAVEN) { $env:MEDICAL_MAVEN } elseif (Get-Command mvn -ErrorAction SilentlyContinue) { (Get-Command mvn).Source } else { 'D:\apache-maven-3.6.3\bin\mvn.cmd' }
if ($IsWindows -and (Test-Path 'D:\jdk\jdk-25')) { $env:JAVA_HOME = 'D:\jdk\jdk-25' }
$repo = if ($env:MEDICAL_MAVEN_REPO) { $env:MEDICAL_MAVEN_REPO } elseif (Test-Path "$env:USERPROFILE/.m2/repository") { "$env:USERPROFILE/.m2/repository" } else { "$root/.tools/m2" }
& $maven -gs "$PSScriptRoot/maven-settings.xml" -s "$PSScriptRoot/maven-settings.xml" "-Dmaven.repo.local=$repo" -f "$root/backend/pom.xml" spring-boot:run
exit $LASTEXITCODE
