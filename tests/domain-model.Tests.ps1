$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$glossary = Get-Content (Join-Path $root "GLOSSARY.md") -Raw
foreach ($term in @("Patient", "Doctor", "Appointment", "Consultation", "AI-assisted result", "Risk alert", "AI gateway")) {
  if ($glossary -notmatch [regex]::Escape($term)) { throw "Missing glossary term: $term" }
}
foreach ($adr in @("docs/adr/0001-modular-monolith-mvp.md", "docs/adr/0002-ai-advisory-human-final.md")) {
  if (-not (Test-Path (Join-Path $root $adr))) { throw "Missing ADR: $adr" }
}
$adr2 = Get-Content (Join-Path $root "docs/adr/0002-ai-advisory-human-final.md") -Raw
foreach ($phrase in @("医生必须", "不能自动形成诊断", "AI gateway")) {
  if ($adr2 -notmatch [regex]::Escape($phrase)) { throw "Missing safety boundary: $phrase" }
}
Write-Output "Domain model validation passed."
