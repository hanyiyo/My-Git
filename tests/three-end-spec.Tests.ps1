$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$spec = Get-Content -LiteralPath (Join-Path $root 'docs/design/three-end-requirements.md') -Raw
foreach ($phrase in @('R3-Q1–Q8', '原预约/当日队尾', '不因患者未回复而取消', '只关闭药费单', '已有订单金额', '普通AI调用日志仅元数据')) {
    if (-not $spec.Contains($phrase)) { throw "Confirmed three-end rule missing: $phrase" }
}
foreach ($name in @('mvp-spec.md', 'current-mvp.md', 'reception-rules.md', 'architecture.md')) {
    $body = Get-Content -LiteralPath (Join-Path $root "docs/design/$name") -Raw
    if (-not $body.Contains('(three-end-requirements.md)')) { throw "Historical entry must point to current specification: $name" }
}
Write-Output 'Three-end confirmed specification consistency passed.'
