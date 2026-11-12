$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$design = Get-Content (Join-Path $root 'docs/design/mvp-decisions.md') -Raw

# 每轮已确认决策都要可检索，避免后续轮次覆盖先前约束。
foreach ($round in @('Q1–Q6', 'Q7–Q12', 'Q13–Q18', 'Q19–Q24', 'Q25–Q30', 'Q31–Q36', 'Q37–Q42', 'Q43–Q48', 'Q49–Q54', 'Q55–Q60', 'Q61–Q66')) {
    if (-not $design.Contains($round)) { throw "Missing decision round: $round" }
}

$revisions = Get-Content (Join-Path $root 'docs/design/mvp-decisions.md') -Raw
foreach ($revision in @('R1', 'R2')) {
    if (-not $revisions.Contains($revision)) { throw "Missing design revision: $revision" }
}

$login = Get-Content (Join-Path $root 'docs/design/login-security.md') -Raw
foreach ($phrase in @('图形验证码', 'Redis', '一次', '不发送短信或邮件')) {
    if (-not $login.Contains($phrase)) { throw "Missing login security rule: $phrase" }
}

$architecture = Get-Content (Join-Path $root 'docs/design/architecture.md') -Raw
if (-not $architecture.Contains('Redis') -or -not $architecture.Contains('登录图形验证码')) {
    throw 'Architecture must include Redis and login captcha'
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

# 医生端访谈保留已确认轮次，并明确区分后续演示目标和已交付能力。
$doctorWorkspace = Get-Content (Join-Path $root 'docs/design/doctor-workspace-prototype.md') -Raw
foreach ($number in 1..17) {
    if ([regex]::Matches($doctorWorkspace, "(?m)^## Q${number}：").Count -ne 1) {
        throw "Doctor workspace decision must have one section: Q$number"
    }
}
foreach ($boundary in @('Q1–Q8', '共享演示数据', '真实接口接入属于后续阶段', '线下面诊不创建医生聊天会话', '医疗结果提交即保存')) {
    if (-not $doctorWorkspace.Contains($boundary)) {
        throw "Missing doctor workspace design boundary: $boundary"
    }
}
if (-not $doctorWorkspace.Contains('总体模块为：首页、接诊工作台、我的排班、申请处理、站内通知、个人中心。')) {
    throw 'Doctor workspace navigation must preserve the confirmed application handling name'
}
foreach ($boundary in @('两种视图均属于接诊工作台', '点击患者卡片只预览资料', '切换保留草稿', '权限内历史记录', '尚未开始第二阶段页面实现', '只允许叫当前排第一位的患者', '主诉、诊断、处理建议必填', '每次挂号自动保存独立草稿', '本轮 AI 摘要明确标为模拟演示', '图片上传和真实 AI 接口放到后续阶段')) {
    if (-not $doctorWorkspace.Contains($boundary)) {
        throw "Missing confirmed reception workspace boundary: $boundary"
    }
}

Write-Output 'Design decision consistency validation passed.'
