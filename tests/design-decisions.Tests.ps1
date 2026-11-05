$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$design = Get-Content (Join-Path $root 'docs/design/mvp-decisions.md') -Raw

# 每轮已确认决策都要可检索，避免后续轮次覆盖先前约束。
foreach ($round in @('Q1–Q6', 'Q7–Q12', 'Q13–Q18', 'Q19–Q24')) {
    if (-not $design.Contains($round)) { throw "Missing decision round: $round" }
}

# ADR 链接不能指向不存在的决策。
foreach ($reference in [regex]::Matches($design, 'ADR-(\d{4})')) {
    $number = $reference.Groups[1].Value
    $matches = @(Get-ChildItem (Join-Path $root 'docs/adr') -Filter "$number-*.md")
    if ($matches.Count -ne 1) { throw "ADR reference must resolve uniquely: $number" }
}

# 同步核对 AI 任务状态清单，防止范围文档与架构记录漂移。
$boundary = Get-Content (Join-Path $root 'docs/adr/0008-explicit-external-ai-boundary.md') -Raw
foreach ($state in @('pending', 'running', 'succeeded', 'failed')) {
    $token = '`' + $state + '`'
    if (-not $design.Contains($token) -or -not $boundary.Contains($token)) {
        throw "Inconsistent AI task state: $state"
    }
}

Write-Output 'Design decision consistency validation passed.'
