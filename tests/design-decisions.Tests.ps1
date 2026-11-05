$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$design = Get-Content (Join-Path $root 'docs/design/mvp-decisions.md') -Raw

# 每轮已确认决策都要可检索，避免后续轮次覆盖先前约束。
foreach ($round in @('Q1–Q6', 'Q7–Q12', 'Q13–Q18', 'Q19–Q24', 'Q25–Q30', 'Q31–Q36', 'Q37–Q42', 'Q43–Q48', 'Q49–Q54', 'Q55–Q60', 'Q61–Q66')) {
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

# 检查设计文档与 ADR 中的本地链接，新增跨文档覆盖说明不可断链。
$documentation = @(
    Get-ChildItem (Join-Path $root 'docs/design') -Filter '*.md'
    Get-ChildItem (Join-Path $root 'docs/adr') -Filter '*.md'
)
foreach ($document in $documentation) {
    $body = Get-Content -LiteralPath $document.FullName -Raw
    # 同一文档重复标题会令锚点及决策入口含糊，访谈追加章节时需要避免。
    $headings = @([regex]::Matches($body, '(?m)^## ([^\r\n]+)') | ForEach-Object { $_.Groups[1].Value })
    $duplicateHeadings = @($headings | Group-Object | Where-Object { $_.Count -gt 1 })
    if ($duplicateHeadings.Count -gt 0) {
        throw "Duplicate section headings in $($document.Name): $($duplicateHeadings.Name -join ', ')"
    }
    foreach ($link in [regex]::Matches($body, '\[[^\]]+\]\(([^)]+\.md)\)')) {
        $target = Join-Path $document.DirectoryName $link.Groups[1].Value
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw "Broken document reference in $($document.Name): $target"
        }
    }
}

# 术语重命名后不能留下两个相同英文词条。
$glossary = Get-Content (Join-Path $root 'GLOSSARY.md') -Raw
$terms = @([regex]::Matches($glossary, '(?m)^\*\*([^（\r\n]+)（') | ForEach-Object { $_.Groups[1].Value })
$duplicates = @($terms | Group-Object | Where-Object { $_.Count -gt 1 })
if ($duplicates.Count -gt 0) { throw "Duplicate glossary terms: $($duplicates.Name -join ', ')" }

# 运行规则按已确认题号完整收录，防止用户的修订落在历史摘要里却遗漏正文。
$reception = Get-Content (Join-Path $root 'docs/design/reception-rules.md') -Raw
$questions = @([regex]::Matches($reception, '(?m)^## Q(\d+)：') | ForEach-Object { [int]$_.Groups[1].Value })
foreach ($number in 37..66) {
    if (@($questions | Where-Object { $_ -eq $number }).Count -ne 1) {
        throw "Reception decision must have one section: Q$number"
    }
}

Write-Output 'Design decision consistency validation passed.'
