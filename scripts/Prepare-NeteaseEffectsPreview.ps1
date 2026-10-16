param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$NdkPath
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOutput = Join-Path $taskRoot '.verification/sound-effects/port/runtime-libs/arm64-v8a'
$taskCompiler = Join-Path $NdkPath 'toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android26-clang.cmd'
if (!(Test-Path -LiteralPath $taskCompiler)) { throw 'Android NDK Windows arm64 compiler is missing' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath))
try {
    $taskLibraries = @{}
    foreach ($taskName in @('libneaudioeffects.so','libc++_shared.so')) {
        $taskEntry = $taskArchive.GetEntry("lib/arm64-v8a/$taskName")
        if (!$taskEntry -or $taskEntry.Length -gt 16MB) { throw "Missing or invalid $taskName" }
        $taskStream = $taskEntry.Open()
        $taskMemory = [IO.MemoryStream]::new()
        try { $taskStream.CopyTo($taskMemory); $taskLibraries[$taskName] = $taskMemory.ToArray() }
        finally { $taskStream.Dispose(); $taskMemory.Dispose() }
    }
    $taskSmartEntry = $taskArchive.GetEntry('assets/audioeffect/preinstall/smartae/-110#6#81f3b6dfa9b6052500be01120678bbeb')
    if (!$taskSmartEntry -or $taskSmartEntry.Length -gt 256KB) { throw 'Compatible smart preset is missing' }
    $taskSmartStream = $taskSmartEntry.Open()
    $taskSmartMemory = [IO.MemoryStream]::new()
    try { $taskSmartStream.CopyTo($taskSmartMemory); $taskSmartBytes = $taskSmartMemory.ToArray() }
    finally { $taskSmartStream.Dispose(); $taskSmartMemory.Dispose() }
} finally { $taskArchive.Dispose() }
$taskHash = [Security.Cryptography.SHA256]::HashData($taskLibraries['libneaudioeffects.so'])
$taskDigest = [Convert]::ToHexString($taskHash).ToLowerInvariant()
if ($taskDigest -ne '3f8e2eec659080cb8e90bbfc672d327a180e4e0b6c051818e38625c5f01fcbd1') {
    throw 'This adapter only supports the verified NetEase 9.5.30 arm64 engine'
}
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
foreach ($taskName in $taskLibraries.Keys) { [IO.File]::WriteAllBytes((Join-Path $taskOutput $taskName), $taskLibraries[$taskName]) }
$taskBridge = Join-Path $taskOutput 'libcurrentmusic_netease_bridge.so'
& $taskCompiler (Join-Path $taskRoot 'app/src/main/cpp/netease_pcm_bridge.c') -shared -fPIC -O2 -o $taskBridge '-Wl,-z,max-page-size=16384' '-Wl,-soname,libcurrentmusic_netease_bridge.so'
if ($LASTEXITCODE -ne 0) { throw 'Native PCM bridge compilation failed' }
$taskOwnAsset = Join-Path $taskRoot 'app/src/main/assets/audiofx/arm64-v8a'
New-Item -ItemType Directory -Force -Path $taskOwnAsset | Out-Null
Copy-Item -LiteralPath $taskBridge -Destination (Join-Path $taskOwnAsset 'libcurrentmusic_netease_bridge.so') -Force
$taskSmartAsset = Join-Path $taskRoot '.verification/sound-effects/port/runtime-assets/original-effects'
New-Item -ItemType Directory -Force -Path $taskSmartAsset | Out-Null
[IO.File]::WriteAllBytes((Join-Path $taskSmartAsset 'smartae.ncae'), $taskSmartBytes)
Write-Output "Prepared local preview libraries in $taskOutput"
Write-Output 'Build with -PneteaseOriginalEffects=true. Ordinary builds can import the compatible engine from a user-selected APK.'
