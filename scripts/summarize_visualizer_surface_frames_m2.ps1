param(
    [Parameter(Mandatory = $true)][string]$InputDirectory,
    [string]$OutputPath = 'visualizer-m2-presented.csv'
)
$ErrorActionPreference = 'Stop'
$taskSeen = [Collections.Generic.HashSet[string]]::new()
$taskRows = foreach ($taskFile in Get-ChildItem -LiteralPath $InputDirectory -Filter 'visualizer-m2-latency-*.txt') {
    if ($taskFile.BaseName -notmatch '^visualizer-m2-latency-(.+)-(\d+)$') { continue }
    $taskPhase = $Matches[1]
    $taskSample = [int]$Matches[2]
    # --latency columns are desired-present, actual-present, frame-ready. Only the second
    # column establishes physical presentation; exclude zero and pending-fence sentinels.
    $taskPresented = @(Get-Content -LiteralPath $taskFile.FullName | ForEach-Object {
        if ($_ -match '^\s*\d+\s+(\d+)\s+\d+\s*$') {
            $taskTimestamp = [long]$Matches[1]
            if ($taskTimestamp -gt 0 -and $taskTimestamp -lt [long]::MaxValue) { $taskTimestamp }
        }
    } | Sort-Object -Unique)
    if ($taskPresented.Count -lt 4) { continue }
    # An idle/closed surface can retain its previous ring; repeated reads are not new frames.
    if (!$taskSeen.Add("${taskPhase}:$($taskPresented[-1])")) { continue }
    $taskIntervals = @(for ($taskIndex = 1; $taskIndex -lt $taskPresented.Count; $taskIndex++) {
        ($taskPresented[$taskIndex] - $taskPresented[$taskIndex - 1]) / 1e6
    })
    $taskSorted = @($taskIntervals | Sort-Object)
    $taskMean = ($taskIntervals | Measure-Object -Average).Average
    [pscustomobject]@{
        phase = $taskPhase; sample = $taskSample; frames = $taskPresented.Count
        actualPresentedFps = 1000 / $taskMean; averageMs = $taskMean
        p95Ms = $taskSorted[[math]::Ceiling($taskSorted.Count * .95) - 1]
        p99Ms = $taskSorted[[math]::Ceiling($taskSorted.Count * .99) - 1]
        firstPresentNs = $taskPresented[0]; lastPresentNs = $taskPresented[-1]
    }
}
if (!$taskRows) { throw 'No valid actual-present timestamps; do not infer presented FPS from refresh rate.' }
$taskRows | Sort-Object phase,sample | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
$taskRows | Group-Object phase | ForEach-Object {
    $taskDuration = 0.0; $taskIntervalsCount = 0
    foreach ($taskRow in $_.Group) {
        $taskDuration += ($taskRow.frames - 1) * $taskRow.averageMs
        $taskIntervalsCount += $taskRow.frames - 1
    }
    [pscustomobject]@{ phase = $_.Name; windows = $_.Count; intervals = $taskIntervalsCount
        sampledPresentedFps = $taskIntervalsCount * 1000 / $taskDuration }
} | Format-Table -AutoSize
