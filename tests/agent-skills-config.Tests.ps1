$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$required = @(
  (Join-Path $root "docs/agents/issue-tracker.md"),
  (Join-Path $root "docs/agents/triage-labels.md"),
  (Join-Path $root "docs/agents/domain.md"),
  (Join-Path $root "AGENTS.md")
)
foreach ($path in $required) {
  if (-not (Test-Path $path)) { throw "Missing required file: $path" }
}
$agents = Get-Content (Join-Path $root "AGENTS.md") -Raw
foreach ($label in @("needs-triage", "needs-info", "ready-for-agent", "ready-for-human", "wontfix")) {
  if ($agents -notmatch [regex]::Escape($label)) { throw "Missing label in AGENTS.md: $label" }
}
$tracker = Get-Content (Join-Path $root "docs/agents/issue-tracker.md") -Raw
if ($tracker -notmatch "GitHub") { throw "Issue tracker must be GitHub" }
$domain = Get-Content (Join-Path $root "docs/agents/domain.md") -Raw
if ($domain -notmatch "Single-context repo") { throw "Domain docs must describe single-context layout" }
Write-Output "Agent skills configuration validation passed."
