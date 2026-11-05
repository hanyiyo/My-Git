$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
foreach ($adr in @("docs/adr/0006-consultation-and-ai-runtime-rules.md", "docs/adr/0007-controlled-mvp-attachments-and-admin.md", "docs/adr/0011-paid-booking-holds-mvp.md")) {
  if (-not (Test-Path (Join-Path $root $adr))) { throw "Missing ADR: $adr" }
}
$adr6 = Get-Content (Join-Path $root "docs/adr/0006-consultation-and-ai-runtime-rules.md") -Raw
foreach ($phrase in @('释放 Slot', 'AI 在患者首次提交后自动运行', '不阻塞问诊提交')) {
  if ($adr6 -notmatch [regex]::Escape($phrase)) { throw "Missing runtime rule: $phrase" }
}

# 旧“无支付/立即确认”文档必须显式指向后续决策，不能继续被当作全部有效规则。
$replacement = '0011-paid-booking-holds-mvp.md'
foreach ($historical in @('0005-defer-real-payment.md', '0006-consultation-and-ai-runtime-rules.md')) {
  $body = Get-Content (Join-Path $root "docs/adr/$historical") -Raw
  $status = [regex]::Match($body, '(?m)^状态：([^\r\n]+)').Groups[1].Value
  if (-not $status.Contains('部分替代') -or -not $status.Contains($replacement)) {
    throw "Historical ADR must identify replacement in its status: $historical"
  }
}

# 范围入口与历史记录之间应双向可达，避免新增范围成为孤立文档。
$current = Get-Content (Join-Path $root 'docs/design/current-mvp.md') -Raw
$history = Get-Content (Join-Path $root 'docs/design/mvp-decisions.md') -Raw
if (-not $current.Contains('(mvp-decisions.md)') -or -not $history.Contains('(current-mvp.md)')) {
  throw 'Current MVP and decision history must link to each other'
}
$adr7 = Get-Content (Join-Path $root "docs/adr/0007-controlled-mvp-attachments-and-admin.md") -Raw
foreach ($phrase in @('图片和 PDF/常见文档', '语音输入延后', 'AI 调用日志')) {
  if ($adr7 -notmatch [regex]::Escape($phrase)) { throw "Missing MVP scope: $phrase" }
}
Write-Output "Runtime and MVP scope validation passed."
