$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
foreach ($adr in @("docs/adr/0006-consultation-and-ai-runtime-rules.md", "docs/adr/0007-controlled-mvp-attachments-and-admin.md")) {
  if (-not (Test-Path (Join-Path $root $adr))) { throw "Missing ADR: $adr" }
}
$adr6 = Get-Content (Join-Path $root "docs/adr/0006-consultation-and-ai-runtime-rules.md") -Raw
foreach ($phrase in @('立即进入 `confirmed`', '释放 Slot', 'AI 在患者首次提交后自动运行', '不阻塞问诊提交')) {
  if ($adr6 -notmatch [regex]::Escape($phrase)) { throw "Missing runtime rule: $phrase" }
}
$adr7 = Get-Content (Join-Path $root "docs/adr/0007-controlled-mvp-attachments-and-admin.md") -Raw
foreach ($phrase in @('图片和 PDF/常见文档', '语音输入延后', 'AI 调用日志')) {
  if ($adr7 -notmatch [regex]::Escape($phrase)) { throw "Missing MVP scope: $phrase" }
}
Write-Output "Runtime and MVP scope validation passed."
