$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$glossary = Get-Content (Join-Path $root "GLOSSARY.md") -Raw
foreach ($term in @("Account", "Schedule", "Slot", "Appointment", "Consultation", "Shared health record", "Schedule change request")) {
  if ($glossary -notmatch [regex]::Escape($term)) { throw "Missing glossary term: $term" }
}
foreach ($adr in @("docs/adr/0003-schedule-slot-appointment-lifecycle.md", "docs/adr/0004-minimum-necessary-medical-visibility.md", "docs/adr/0005-defer-real-payment.md")) {
  if (-not (Test-Path (Join-Path $root $adr))) { throw "Missing ADR: $adr" }
}
$adr3 = Get-Content (Join-Path $root "docs/adr/0003-schedule-slot-appointment-lifecycle.md") -Raw
foreach ($phrase in @("同一时段只能产生一个有效 Appointment", "MVP 不做候补队列")) {
  if ($adr3 -notmatch [regex]::Escape($phrase)) { throw "Missing appointment rule: $phrase" }
}
Write-Output "Domain model validation passed."
