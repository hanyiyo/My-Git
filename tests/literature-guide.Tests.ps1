$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root '开题报告/智能医疗问诊预约系统开题报告文献检索指南.docx'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fileStream = [System.IO.FileStream]::new($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
$archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Read)
try {
    $entry = $archive.GetEntry('word/document.xml')
    if (-not $entry) { throw 'The guide must be a readable Word document.' }
    $reader = [System.IO.StreamReader]::new($entry.Open())
    try { [xml]$document = $reader.ReadToEnd() } finally { $reader.Dispose() }
    $namespace = [System.Xml.XmlNamespaceManager]::new($document.NameTable)
    $namespace.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    $text = ($document.SelectNodes('//w:t', $namespace) | ForEach-Object { $_.InnerText }) -join "`n"
    foreach ($phrase in @(
        '患者端', '医生端', '管理员端',
        '医疗预约与门诊服务流程', '智能导诊与科室推荐',
        '在线问诊与医生辅助摘要', '医疗数据隐私与访问控制',
        '系统架构与业务可靠性', '医疗系统可用性与效果评价',
        '中文高级检索组合', '英文关键词组合',
        '筛选文献并用于开题报告', '国内外研究现状',
        'LOCAL_MOCK 本地规则模拟', '尚未接入真实外部大模型',
        '文献检索应提前覆盖规划功能', '支付为模拟支付',
        '数量为工作建议', '不列未经逐篇核验的论文题录'
    )) {
        if (-not $text.Contains($phrase)) { throw "Required guide content missing: $phrase" }
    }
    if ($text.Contains('待补充') -or $text.Contains('TODO')) { throw 'Unfinished placeholder in guide.' }
    $directions = $document.SelectNodes('//w:p[w:pPr/w:pStyle[@w:val="Heading2"]]', $namespace) |
        Where-Object { $_.InnerText.StartsWith('方向') }
    if (@($directions).Count -ne 6) { throw 'Expected six literature research directions.' }
    $stylesReader = [System.IO.StreamReader]::new($archive.GetEntry('word/styles.xml').Open())
    try { [xml]$styles = $stylesReader.ReadToEnd() } finally { $stylesReader.Dispose() }
    $stylesNamespace = [System.Xml.XmlNamespaceManager]::new($styles.NameTable)
    $stylesNamespace.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    if ($document.SelectNodes('//w:pBdr', $namespace).Count -gt 0 -or
        $styles.SelectNodes('//w:style[@w:styleId="Title"]//w:pBdr', $stylesNamespace).Count -gt 0) {
        throw 'The guide title must not inherit a decorative paragraph border.'
    }
    Write-Output 'Literature guide content and scope checks passed.'
} finally { $archive.Dispose(); $fileStream.Dispose() }
