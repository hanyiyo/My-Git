$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root '开题报告/余蕊艳_智能医疗问诊预约系统_任务书与开题报告_近五年文献修订版.docx'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Get-TextHash([string]$value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value))).ToLowerInvariant() } finally { $sha.Dispose() }
}
function Assert-True($condition, [string]$message) { if (-not $condition) { throw $message } }
$expectedProtected = @'
{
  "//w:sectPr": "e61e77365feeeeca33ead7dcd740aafa2964afdb526ffaf69583519d5245398f",
  "//w:body/w:tbl[2]/w:tr[6]": "dfc5942a872fb29416d03bc20c75a45438a1be89b1f806b487031d910dcbed59",
  "//w:body/w:tbl[3]/w:tr[8]": "10e93f8bb10518ea981f0f06bf87550145fe4ca1a91bcf00fc5304a175dbedf5",
  "//w:body/w:tbl[3]/w:tr[9]": "a0c2f02be67d1ac51976a569284519c650638fd2fc16752a1e5d197a7e6eed53",
  "//w:body/w:tbl[3]/w:tr[4]": "b23b90623c252002c781ecfe3c7a61a6a81937ee1f42274b18a449a50cc24c29",
  "//w:body/w:p[contains(.,\"说   明\")]": "36dd602522f61290564000e4d4ef56f60d78dd6369265af60e0c9a069ccf71d6",
  "//w:body/w:p[starts-with(.,\"1.任务书\")]": "db5074c2f73788a82aa02080e99d42d807d87f8427e6e3407bfc0a928e0efbbb",
  "//w:body/w:p[starts-with(.,\"2.学生\")]": "c9cd3bff9e48266dc7fb0323ad71c73b78d113b3a7344fdff8a79083cbe46955",
  "//w:body/w:p[starts-with(.,\"3.本手册\")]": "6b38db4d9420e6379a0f81f67d871d5182d3ffe2eb9e045b313569e1f33454c7",
  "//w:body/w:p[starts-with(.,\"4.本手册\")]": "5e70a09cfb391a092f0daba732547c8700fa723b53e814cf38eccb7d0e87bb8d",
  "//w:body/w:p[starts-with(.,\"5.本手册\")]": "290c5f4027705406fbe8c0bafc9129f3db5d1cb6b6c2cfafe0ba4419754bff49",
  "//w:body/w:tbl[2]/w:tr[3]/w:tc[2]/w:p[position()<=8]": "9136b9653e01001e28ec5d7ed3f0aa84efe9d134efb10a096607a8ecac3ea06b",
  "//w:body/w:tbl[3]/w:tr[7]/w:tc[2]/w:p[contains(.,\"学生签名\")]": "c8c6d9ef751576b396c16569e5c6287c6f9a0daf6b73d4029907bb36c61397d3"
}
'@ | ConvertFrom-Json -AsHashtable
$expectedParts = @'
{
  "[Content_Types].xml": "13bb3bff4c55869236d5524e1a2a05529f7f8777e1239c240ce28bf2cca4e92f",
  "_rels/.rels": "514e1846381cb6bcfff105a4b0a17d666cf0ed1f0c120bff73b82226ab7ed977",
  "docProps/app.xml": "3bcdd1c88077890fb2b01e612679340b33afccd823b641696ca51d4e707c2235",
  "docProps/core.xml": "a497ce8c010205bbab177c5648f7fafc86659aa5c8ee719805bc9648f143f533",
  "docProps/custom.xml": "3c31b43638f9d3e15e681b02aa41119b6f59e99b25b72f574524999ac0797cc5",
  "word/_rels/document.xml.rels": "185056719076c0e22ec17480e17c4b41b3eca189daae446d0b193412dff0d12e",
  "word/fontTable.xml": "e2806d590562c4fd47f5963a458e228249ddbec3e6cc53e75b2e1b3425d3f6a9",
  "word/footer1.xml": "d8a7ed2a0925d204f306079978c65705ba8a0252e4afc700307683288a2a5d47",
  "word/media/image1.png": "b3baa2cc0486221c85ad6d88395605de1ee6d214817a445479d2242e24d9553b",
  "word/media/image2.png": "522222c41fea20ab2208e1c942ebe020481df9e6e441706285be7cc26714a422",
  "word/media/image3.png": "a3dc46575a38791fb4f9111c286887fddb052687badba8fa83eb659fc8737344",
  "word/media/image4.png": "5f861069937b9a422f4587a2603001690473ef781cb1d79a922682f80ce8cdca",
  "word/styles.xml": "cb742a7e5b153291bc62cd5a2fd27bf68eea4ebbcc7b341d0da065c8efb99e62",
  "word/theme/theme1.xml": "877930300f41fcf60d3edea108678078177c4c20b65ced288432470652e6b4a1"
}
'@ | ConvertFrom-Json -AsHashtable
$stream = [IO.FileStream]::new($path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
$archive = [IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Read)
try {
    $reader = [IO.StreamReader]::new($archive.GetEntry('word/document.xml').Open())
    try { [xml]$document = $reader.ReadToEnd() } finally { $reader.Dispose() }
    $ns = [Xml.XmlNamespaceManager]::new($document.NameTable)
    $ns.AddNamespace('w','http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    $text = ($document.SelectNodes('//w:t',$ns) | ForEach-Object { $_.InnerText }) -join "`n"
    $cn = '基于SpringBoot+Vue与多模态AI辅助的智能医疗问诊预约系统设计与实现'
    $en = 'Design and Implementation of an Intelligent Medical Inquiry and Appointment System Assisted by Multimodal AI Based on SpringBoot and Vue'
    foreach ($item in @(@($cn,3),@($en,2),@('余蕊艳',3),@('8120123161',3))) {
        Assert-True (([regex]::Matches($text,[regex]::Escape($item[0]))).Count -eq $item[1]) ('Incorrect metadata count: '+$item[0])
    }
    foreach ($phrase in @('计算机科学与技术2023级3班','2023级3班','患者端','医生端','管理员端','症状图片','仅医生可见','授权撤回','模拟缴费','限时占号','库存','SpringBoot 3','Redis','材料版本','不静默切换','本地模拟')) {
        Assert-True ($text.Contains($phrase)) ('Required project content missing: '+$phrase)
    }
    foreach ($old in @('基于网络行为分析','温键','8120122043','2022级天融信班','错误！未定义书签。','TODO','待补充')) {
        Assert-True (-not $text.Contains($old)) ('Unexpected old or unfinished content: '+$old)
    }
    $tables = @($document.SelectNodes('//w:body/w:tbl',$ns))
    Assert-True ($tables.Count -eq 3) 'Keep cover, task and proposal table structure.'
    $taskRefs = @($tables[1].SelectNodes('./w:tr[4]/w:tc[2]/w:p[starts-with(.,"[")]',$ns))
    $reportRefs = @($tables[2].SelectNodes('./w:tr[7]/w:tc[2]/w:p[starts-with(.,"[")]',$ns))
    Assert-True ($taskRefs.Count -eq 15 -and $reportRefs.Count -eq 15) 'Both documents need all 15 references.'
    for ($i=0;$i -lt 15;$i++) {
        Assert-True ($taskRefs[$i].InnerText.StartsWith('['+($i+1)+']')) 'Reference sequence is incomplete.'
        Assert-True ($taskRefs[$i].InnerText -eq $reportRefs[$i].InnerText) 'Task and proposal bibliography must match.'
    }
    $english = @($taskRefs | Where-Object { $_.InnerText -match '^\[\d+\][A-Z]' })
    Assert-True ($english.Count -eq 6) 'Expected nine Chinese and six English references.'
    foreach ($reference in $taskRefs) {
        $year = [regex]::Match($reference.InnerText, '[,.]\s*(20\d{2})[,:]')
        Assert-True ($year.Success) ('Reference publication year missing: '+$reference.InnerText)
        Assert-True ([int]$year.Groups[1].Value -ge 2022 -and [int]$year.Groups[1].Value -le 2026) 'References must be published in 2022-2026.'
    }
    foreach ($outdated in @('ZHAO P','VERREYDT','张锦红','石秀金','CTM模型','UCON')) {
        Assert-True (-not $text.Contains($outdated)) ('Outdated reference or review claim remains: '+$outdated)
    }
    foreach ($current in @('巫朝霞','吴性丽','MANARTE L','SCHMIDT R','Manarte','Schmidt')) {
        Assert-True ($text.Contains($current)) ('Replacement reference or supporting discussion missing: '+$current)
    }
    Assert-True ($taskRefs[8].InnerText.Contains('17(10):69-72')) 'Use verified reference 9 pagination.'
    $plan = $tables[1].SelectSingleNode('./w:tr[5]/w:tc[2]',$ns).InnerText
    foreach ($date in @('2026年10月8日～2026年10月30日','2026年10月31日～2026年11月18日','2026年11月19日～2027年1月9日','2027年1月10日～2027年1月15日','2027年1月16日～2027年4月30日','2027年5月1日～2027年5月10日','2027年7月  12 日')) {
        Assert-True ($plan.Contains($date)) ('Task schedule month/day or shifted year changed: '+$date)
    }
    $proposal = $tables[2].SelectSingleNode('./w:tr[7]/w:tc[2]',$ns).InnerText
    foreach ($date in @('2026 年 10 月 30 日前','2026 年 12 月 18 日前','2027 年 4 月 30 日前','2027 年 5 月份','2027 年 5 月 20 日前')) {
        Assert-True ($proposal.Contains($date)) ('Proposal schedule must keep month/day: '+$date)
    }
    foreach ($xpath in $expectedProtected.Keys) {
        $nodes = @($document.SelectNodes($xpath,$ns))
        Assert-True ($nodes.Count -gt 0) ('Protected section missing: '+$xpath)
        $xml = ($nodes | ForEach-Object { $_.OuterXml }) -join ''
        Assert-True ((Get-TextHash $xml) -eq $expectedProtected[$xpath]) ('Protected source XML changed: '+$xpath)
    }
    foreach ($part in $expectedParts.Keys) {
        $entry = $archive.GetEntry($part)
        Assert-True ($null -ne $entry) ('Preserved package part missing: '+$part)
        $partStream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
        try { $hash=[Convert]::ToHexString($sha.ComputeHash($partStream)).ToLowerInvariant() } finally { $partStream.Dispose();$sha.Dispose() }
        Assert-True ($hash -eq $expectedParts[$part]) ('Unrelated template part or signature image changed: '+$part)
    }
    $starts=@($document.SelectNodes('//w:bookmarkStart',$ns));$ends=@($document.SelectNodes('//w:bookmarkEnd',$ns))
    Assert-True ($starts.Count -eq $ends.Count) 'Bookmark endpoints must balance.'
    $names=@($starts | ForEach-Object { $_.GetAttribute('name',$ns.LookupNamespace('w')) })
    foreach ($name in @('_Toc26165','_Toc9711')) { Assert-True ($names -contains $name) ('Missing TOC bookmark: '+$name) }
    $target=$document.SelectSingleNode('//w:body/w:p[w:bookmarkStart[@w:name="_Toc26165"]]',$ns)
    Assert-True ($target.InnerText -eq '南昌大学共青学院本科生毕业设计（论文）任务书') 'TOC target must be the real task heading.'
    foreach ($field in $document.SelectNodes('//w:instrText',$ns)) {
        if ($field.InnerText -match '(?:PAGEREF\s+|HYPERLINK\s+\\l\s+")(_Toc\d+)') {
            Assert-True ($names -contains $Matches[1]) ('Dangling TOC reference: '+$Matches[1])
        }
    }
    Write-Output 'Task/proposal metadata, bibliography ratio, schedule, bookmarks and protected template checks passed.'
} finally { $archive.Dispose();$stream.Dispose() }
