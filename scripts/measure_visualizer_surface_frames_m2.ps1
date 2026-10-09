param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory = '.verification',
    [int]$MaximumSeconds = 1300,
    [switch]$ObserveCurrentPhase
)
$ErrorActionPreference = 'Stop'
$taskOutput = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($taskOutput) | Out-Null
$taskStarted = [Diagnostics.Stopwatch]::StartNew()
$taskPhase = ''
$taskSample = 0
$taskPhaseAge = [Diagnostics.Stopwatch]::new()
$taskInitialMark = @(& adb -s $Serial logcat -d -s 'VisualizerM2Phase:I' '*:S') | Select-Object -Last 1
$taskInitialCompletion = @(& adb -s $Serial logcat -d -s 'VisualizerM2Test:I' '*:S') | Where-Object { $_ -match 'COMPLETE durationMs=' } | Select-Object -Last 1

function Save-TargetSurfaceStats([string]$Phase) {
    if (!$Phase) { return }
    $taskDump = @(& adb -s $Serial shell dumpsys SurfaceFlinger --timestats -dump)
    # Preserve display-wide summary plus only the test Activity's layer sections.
    $taskSelected = [Collections.Generic.List[string]]::new()
    $taskInLayer = $false
    $taskIncludeLayer = $false
    foreach ($taskLine in $taskDump) {
        if ($taskLine -match '^layerName\s*=') {
            $taskInLayer = $true
            $taskIncludeLayer = $taskLine -match 'com\.bileizhen\.currentmusic\.verification.*MainActivity'
        }
        if (!$taskInLayer -or $taskIncludeLayer) { $taskSelected.Add($taskLine) }
    }
    $taskSelected | Set-Content -LiteralPath (Join-Path $taskOutput "visualizer-m2-surface-$Phase.txt") -Encoding utf8
}

try {
    while ($taskStarted.Elapsed.TotalSeconds -lt $MaximumSeconds) {
        $taskMarks = @(& adb -s $Serial logcat -d -s 'VisualizerM2Phase:I' '*:S')
        $taskMark = $taskMarks | Where-Object { $_ -match 'begin (preset-(NEON_PULSE|ORBIT_SPECTRUM|BASS_IMPACT|DARK_GLITCH)|soak|lifecycle)' } | Select-Object -Last 1
        if (!$ObserveCurrentPhase -and !$taskPhase -and $taskMark -eq $taskInitialMark) { Start-Sleep -Seconds 5; continue }
        if ($taskMark -match 'begin (preset-(NEON_PULSE|ORBIT_SPECTRUM|BASS_IMPACT|DARK_GLITCH)|soak|lifecycle)') {
            $taskNextPhase = $Matches[1]
        } else {
            # Vendor log spam can evict a one-off phase marker during a fifteen-minute run.
            # Periodic test records preserve the phase; otherwise retain the last known phase.
            $taskLatestRecord = @(& adb -s $Serial logcat -d -s 'VisualizerM2Test:I' '*:S') | Select-Object -Last 1
            $taskNextPhase = $taskPhase
            if ($taskLatestRecord -match '"phase":"soak"') { $taskNextPhase = 'soak' }
            elseif ($taskLatestRecord -match '"phase":"(seek|track|paused|background|closed)"') { $taskNextPhase = 'lifecycle' }
            elseif (!$taskPhase -and $taskLatestRecord -match '"phase":"(preset-(NEON_PULSE|ORBIT_SPECTRUM|BASS_IMPACT|DARK_GLITCH))"') { $taskNextPhase = $Matches[1] }
        }
        if ($taskNextPhase) {
            if ($taskNextPhase -ne $taskPhase) {
                Save-TargetSurfaceStats $taskPhase
                & adb -s $Serial shell dumpsys SurfaceFlinger --timestats -clear -enable | Out-Null
                $taskPhase = $taskNextPhase
                $taskPhaseAge.Restart()
                Write-Output "SurfaceFlinger phase: $taskPhase"
            }
            # The latency ring still contains the preceding FPS mode at a transition.
            # Wait five seconds before measuring this phase (126 frames take up to 4.2s at 30).
            if ($taskPhaseAge.Elapsed.TotalSeconds -ge 5) {
            $taskLayers = @(& adb -s $Serial shell dumpsys SurfaceFlinger --list)
            $taskBuffers = @($taskLayers | Where-Object {
                # A hex-prefixed window container has the same activity name but no buffers.
                # SurfaceFlinger list order is unspecified: select the exact buffer layer.
                $_ -match '^(RequestedLayerState\{|Surface\(name=)?com\.bileizhen\.currentmusic\.verification/io\.github\.currencortex\.music\.MainActivity#\d+'
            })
            # A Compose Dialog and its Activity share the same title. Bind the buffer
            # to the first (topmost) owned window, not SurfaceFlinger list ordering.
            $taskOwnedWindow = @(& adb -s $Serial shell dumpsys window windows) |
                Where-Object { $_ -match '^\s*Window #\d+ Window\{[a-f0-9]+ u\d+ com\.bileizhen\.currentmusic\.verification/io\.github\.currencortex\.music\.MainActivity\}:' } |
                Select-Object -First 1
            $taskLayer = $null
            if ($taskOwnedWindow -match 'Window\{([a-f0-9]+) u\d+') {
                $taskWindowHash = $Matches[1]
                $taskContainer = $taskLayers | Where-Object { $_ -match ('^RequestedLayerState\{' + $taskWindowHash + ' com\.bileizhen\.currentmusic\.verification/.+?#\d+ parentId=') } | Select-Object -First 1
                if ($taskContainer -match '#(\d+) parentId=') {
                    $taskParentId = $Matches[1]
                    $taskLayer = $taskBuffers | Where-Object { $_ -match (' parentId=' + $taskParentId + '(\s|\})') } | Select-Object -First 1
                }
            }
            # Older SurfaceFlinger formats expose only one exact buffer. Never guess
            # when multiple owned windows exist and the parent cannot be resolved.
            if (!$taskLayer -and $taskBuffers.Count -eq 1) { $taskLayer = $taskBuffers[0] }
            if ($taskLayer) {
                # Shell-safe quoting on the Android side; never evaluate a SurfaceFlinger name as code.
                if ($taskLayer -match 'Surface\(name=([^)]*)\)') { $taskLayer = $Matches[1] }
                elseif ($taskLayer -match '^RequestedLayerState\{(.+?) parentId=\d+\}') { $taskLayer = $Matches[1] }
                $taskQuotedLayer = "'" + $taskLayer.Replace("'", "'\''") + "'"
                & adb -s $Serial shell ("dumpsys SurfaceFlinger --latency " + $taskQuotedLayer) |
                    Set-Content -LiteralPath (Join-Path $taskOutput "visualizer-m2-latency-$taskPhase-$taskSample.txt") -Encoding utf8
                $taskLayer | Set-Content -LiteralPath (Join-Path $taskOutput "visualizer-m2-layer-$taskPhase.txt") -Encoding utf8
            }
            & adb -s $Serial shell dumpsys display | Select-String 'modeId .*renderFrameRate|mActiveModeId=|mActiveRenderFrameRate=' |
                ForEach-Object { $_.Line } | Set-Content -LiteralPath (Join-Path $taskOutput "visualizer-m2-display-$taskPhase.txt") -Encoding utf8
            & adb -s $Serial shell dumpsys thermalservice | Select-String 'Thermal Status|mStatus=|mValue=' |
                ForEach-Object { $_.Line } | Set-Content -LiteralPath (Join-Path $taskOutput "visualizer-m2-thermal-$taskPhase-$taskSample.txt") -Encoding utf8
            & adb -s $Serial shell dumpsys battery | Select-String 'level:|temperature:|powered:' |
                ForEach-Object { $_.Line } | Set-Content -LiteralPath (Join-Path $taskOutput "visualizer-m2-battery-$taskPhase-$taskSample.txt") -Encoding utf8
            $taskSample++
            }
        }
        $taskCompletion = @(& adb -s $Serial logcat -d -s 'VisualizerM2Test:I' '*:S') |
            Where-Object { $_ -match 'COMPLETE durationMs=' } | Select-Object -Last 1
        if ($taskCompletion -match 'COMPLETE durationMs=' -and $taskCompletion -ne $taskInitialCompletion) { break }
        Start-Sleep -Seconds 5
    }
} finally {
    Save-TargetSurfaceStats $taskPhase
    & adb -s $Serial shell dumpsys SurfaceFlinger --timestats -disable | Out-Null
}
