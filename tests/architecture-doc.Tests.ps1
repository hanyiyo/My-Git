$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$body = Get-Content (Join-Path $root 'docs/design/architecture.md') -Raw

# 仅校验文档结构与状态图连通性；不把此检查当作业务实现测试。
$fences = [regex]::Matches($body, '(?m)^```[^\r\n]*$')
if ($fences.Count % 2 -ne 0) { throw 'Unbalanced architecture code fences' }
$diagrams = [regex]::Matches($body, '(?s)```mermaid\r?\nstateDiagram-v2\r?\n(.*?)```')
if ($diagrams.Count -ne 5) { throw 'Expected five independent state diagrams' }

foreach ($diagram in $diagrams) {
    $transitions = [regex]::Matches($diagram.Groups[1].Value, '(?m)^\s*(\[\*\]|[A-Z_]+)\s*-->\s*(\[\*\]|[A-Z_]+)(?:\s*:.*)?\s*$')
    if ($transitions.Count -eq 0) { throw 'State diagram without transitions' }
    $starts = @($transitions | Where-Object { $_.Groups[1].Value -eq '[*]' })
    $ends = @($transitions | Where-Object { $_.Groups[2].Value -eq '[*]' })
    if ($starts.Count -ne 1 -or $ends.Count -eq 0) { throw 'Missing unique entry or terminal state' }

    $states = @($transitions | ForEach-Object {
        $_.Groups[1].Value
        $_.Groups[2].Value
    } | Where-Object { $_ -ne '[*]' } | Sort-Object -Unique)
    $reachable = [System.Collections.Generic.HashSet[string]]::new()
    [void]$reachable.Add($starts[0].Groups[2].Value)
    do {
        $before = $reachable.Count
        foreach ($edge in $transitions) {
            $from = $edge.Groups[1].Value
            $to = $edge.Groups[2].Value
            if ($reachable.Contains($from) -and $to -ne '[*]') { [void]$reachable.Add($to) }
        }
    } while ($reachable.Count -gt $before)
    foreach ($state in $states) {
        if (-not $reachable.Contains($state)) { throw "Unreachable diagram state: $state" }
        $outgoing = @($transitions | Where-Object { $_.Groups[1].Value -eq $state })
        if ($outgoing.Count -eq 0) { throw "Diagram state without exit or terminal marker: $state" }
    }
}

Write-Output 'Architecture document structure and diagram connectivity validation passed.'
