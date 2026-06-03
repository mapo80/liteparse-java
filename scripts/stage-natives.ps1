<#
.SYNOPSIS
  Collect the freshly built Windows native files for one platform into a staging
  directory that the Gradle `nativeJar-<classifier>` task packs into a jar.

.DESCRIPTION
  Bundles, side by side (so PDFium's self_dir() probe finds everything):
    - the JNI library (liteparse_jni.dll)
    - pdfium.dll (downloaded prebuilt by liteparse-pdfium-sys; never compiled here)
    - eng.traineddata for the bundled Tesseract OCR (prebuilt, downloaded)

.PARAMETER Target        Rust target triple (e.g. x86_64-pc-windows-msvc)
.PARAMETER Classifier    Platform classifier (e.g. windows-x86_64)
.PARAMETER OutDir        Staging output directory
.PARAMETER WithTessdata  1 (default) to bundle eng.traineddata, 0 to skip
#>
param(
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$Classifier,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [int]$WithTessdata = 1
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path "$PSScriptRoot\..").Path
$releaseDir = Join-Path $repo "rust\target\$Target\release"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# 1) JNI library
$jni = "liteparse_jni.dll"
$jniSrc = Join-Path $releaseDir $jni
if (-not (Test-Path $jniSrc)) {
    throw "JNI library not found at $jniSrc (did 'cargo build --release' run?)"
}
Copy-Item $jniSrc (Join-Path $OutDir $jni) -Force

# 2) pdfium.dll — prefer the copy build.rs places in deps\, then env, then cache.
$pdfium = $null
$cand = Join-Path $releaseDir "deps\pdfium.dll"
if (Test-Path $cand) { $pdfium = $cand }
if (-not $pdfium -and $env:PDFIUM_LIB_PATH) {
    $c = Join-Path $env:PDFIUM_LIB_PATH "pdfium.dll"
    if (Test-Path $c) { $pdfium = $c }
}
if (-not $pdfium) {
    foreach ($base in @("$env:LOCALAPPDATA\pdfium-rs", "$env:USERPROFILE\.cache\pdfium-rs")) {
        if (Test-Path $base) {
            $f = Get-ChildItem -Path $base -Recurse -Filter "pdfium.dll" -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($f) { $pdfium = $f.FullName; break }
        }
    }
}
if (-not $pdfium) { throw "Could not locate pdfium.dll. Set PDFIUM_LIB_PATH." }
Copy-Item $pdfium (Join-Path $OutDir "pdfium.dll") -Force

# NOTE: the MSVC C++ runtime (vcruntime140.dll / msvcp140.dll) is normally
# present on target machines via the VC++ Redistributable. If a fully
# self-contained jar is required, copy them here from the toolchain.

# 3) Tesseract language data (prebuilt, downloaded — never generated here).
if ($WithTessdata -eq 1) {
    $url = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata"
    Invoke-WebRequest -Uri $url -OutFile (Join-Path $OutDir "eng.traineddata")
}

Write-Host "Staged native files for $Classifier in ${OutDir}:"
Get-ChildItem $OutDir | Format-Table -AutoSize
