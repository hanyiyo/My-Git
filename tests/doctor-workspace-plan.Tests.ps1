$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$body = Get-Content -LiteralPath (Join-Path $root 'docs/design/doctor-workspace-integration.md') -Raw

# Validate the plan graph and advertised existing HTTP seams, not future implementation.
$tasks = @{}
foreach ($row in [regex]::Matches($body, '(?m)^\| (D\d+) \| [^|]+ \| ([^|]+) \| ([^|]+) \|\s*$')) {
    $id = $row.Groups[1].Value
    if ($tasks.ContainsKey($id)) { throw "Duplicate doctor task: $id" }
    $tasks[$id] = @([regex]::Matches($row.Groups[2].Value, 'D\d+') | ForEach-Object Value)
}
if ($tasks.Count -eq 0) { throw 'Doctor plan has no tasks' }
foreach ($id in $tasks.Keys) {
    foreach ($dependency in $tasks[$id]) {
        if (-not $tasks.ContainsKey($dependency)) { throw "Unknown blocker $dependency for $id" }
    }
}
$completed = [System.Collections.Generic.HashSet[string]]::new()
do {
    $before = $completed.Count
    foreach ($id in $tasks.Keys) {
        if (@($tasks[$id] | Where-Object { -not $completed.Contains($_) }).Count -eq 0) {
            [void]$completed.Add($id)
        }
    }
} while ($completed.Count -gt $before)
if ($completed.Count -ne $tasks.Count) { throw 'Doctor task dependencies contain a cycle' }

$requirements = @([regex]::Matches($body, '(?m)^\| (R\d+) \| [^|]+ \| ([^|]+) \|\s*$'))
$expected = @(1..12 | ForEach-Object { 'R{0:00}' -f $_ })
$actual = @($requirements | ForEach-Object { $_.Groups[1].Value })
if (@(Compare-Object $expected $actual).Count -gt 0) { throw 'Doctor acceptance requirements must cover R01-R12 exactly once' }
foreach ($requirement in $requirements) {
    $owners = @([regex]::Matches($requirement.Groups[2].Value, 'D\d+') | ForEach-Object Value)
    if ($owners.Count -eq 0) { throw "Requirement without delivery task: $($requirement.Groups[1].Value)" }
    foreach ($owner in $owners) {
        if (-not $tasks.ContainsKey($owner)) { throw "Requirement points to missing task: $owner" }
    }
}

$apis = [regex]::Matches($body, '(?m)^\| (GET|POST|PUT|PATCH|DELETE) \| (/[^| ]+) \| ([a-z]+) \|\s*$')
if ($apis.Count -eq 0) { throw 'Doctor plan has no verified HTTP seams' }
foreach ($api in $apis) {
    $verb = $api.Groups[1].Value
    $path = $api.Groups[2].Value
    $module = $api.Groups[3].Value
    $controllers = Get-ChildItem -LiteralPath (Join-Path $root "backend/src/main/java/cn/medical/$module") -Filter '*Controller.java'
    $mapping = $verb.Substring(0,1) + $verb.Substring(1).ToLowerInvariant() + 'Mapping'
    $found = $false
    foreach ($controller in $controllers) {
        $source = Get-Content -LiteralPath $controller.FullName -Raw
        foreach ($annotation in [regex]::Matches($source, "@$mapping\(([^)]*)\)")) {
            if (@([regex]::Matches($annotation.Groups[1].Value, '"([^"]+)"') | Where-Object { $_.Groups[1].Value -eq $path }).Count -gt 0) { $found = $true }
        }
    }
    if (-not $found) { throw "Advertised existing API not found: $verb $path ($module)" }
}
$d8 = Get-Content -LiteralPath (Join-Path $root 'docs/acceptance/doctor-workspace-d8.md') -Raw
foreach ($seam in @(
    'WorkflowHttpTest\.clinicalMessagesResultsAndCorrectionsRespectOwnershipAndReadOnlyBoundary',
    'WorkflowHttpTest\.structuredClinicalResultPersistsRequiredFieldsAndPharmacySnapshot',
    'doctorReceptionKeepsCheckedInCareAfterEndButRejectsMiss',
    'WorkflowHttpTest\.doctorSummaryRequiresExactPatientScopeKeepsVersionsAndHonorsRevocation',
    'WorkflowHttpTest\.substitutionApprovalMigratesOrderAndPatientMayCancelFromOrders',
    'WorkflowHttpTest\.notificationCursorPagesAndAuthenticatedSseReplayCatchUpMoreThanOneHundred',
    'npm --prefix frontend run build',
    'pwsh -NoProfile -File scripts/verify\.ps1'
)) {
    if ($d8 -notmatch $seam) { throw "D8 acceptance record is missing reproducible seam: $seam" }
}
Write-Output "Doctor workspace plan validation passed: $($tasks.Count) tasks, $($requirements.Count) requirements, $($apis.Count) existing HTTP seams."
