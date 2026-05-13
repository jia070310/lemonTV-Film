# Brand assets from ICO\LOM.png (preferred) or ICO\LOM.ico. Run from repo root: npm run icons
# Outputs: public/lom.png, android mipmap, drawable-nodpi/lom.png + lom_logo.png, public/images placeholders.

param(
  [string]$SourcePng = "ICO\LOM.png",
  [string]$FallbackIco = "ICO\LOM.ico",
  # Launcher/mipmap padding; lower = bigger glyph in tab bar / recents (was 0.38 -> tiny dot)
  [double]$PadRatioLauncher = 0.22,
  [double]$PadRatioPublic = 0.02,
  [double]$PadRatioNodpi = 0.36,
  [double]$PadRatioPoster = 0.06,
  [double]$PadRatioHero = 0.04
)

$ErrorActionPreference = "Stop"
# Resolve repo root without Split-Path $PSScriptRoot -Parent (empty on some hosts)
if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
  $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
  throw "Cannot resolve script directory (PSScriptRoot empty)."
}
$base = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, ".."))
Set-Location -LiteralPath $base

$pngFull = Join-Path $base $SourcePng
$icoFull = Join-Path $base $FallbackIco
if (Test-Path -LiteralPath $pngFull) {
  $srcPath = (Resolve-Path $pngFull).Path
  $used = $SourcePng
} elseif (Test-Path -LiteralPath $icoFull) {
  $srcPath = (Resolve-Path $icoFull).Path
  $used = $FallbackIco
} else {
  throw "Missing brand source: $pngFull or $icoFull"
}

Add-Type -AssemblyName System.Drawing
$White = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)

function Open-SourceBitmap([string]$path) {
  return ,[System.Drawing.Image]::FromFile($path)
}

function Save-FitContainSquare(
  [System.Drawing.Image]$source,
  [int]$px,
  [string]$outPath,
  [System.Drawing.Color]$bg,
  [bool]$transparentBg,
  [double]$padRatio
) {
  if ([string]::IsNullOrWhiteSpace($outPath)) {
    throw "Save-FitContainSquare: outPath is empty."
  }
  if ($transparentBg) {
    $bmp = New-Object System.Drawing.Bitmap(
      $px,
      $px,
      [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
  } else {
    $bmp = New-Object System.Drawing.Bitmap($px, $px)
  }
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  if ($transparentBg) {
    $g.Clear([System.Drawing.Color]::Transparent)
  } else {
    $g.Clear($bg)
  }
  $avail = [double]$px * (1.0 - 2.0 * $padRatio)
  if ($avail -lt 4) { $avail = 4 }
  $ratio = [Math]::Min($avail / [double]$source.Width, $avail / [double]$source.Height)
  $w = [int][Math]::Round($source.Width * $ratio)
  $h = [int][Math]::Round($source.Height * $ratio)
  $x = [int][Math]::Floor(($px - $w) / 2.0)
  $y = [int][Math]::Floor(($px - $h) / 2.0)
  $g.DrawImage($source, $x, $y, $w, $h)
  $dir = Split-Path $outPath
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
  $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose()
  $bmp.Dispose()
}

function Save-FitContainRect(
  [System.Drawing.Image]$source,
  [int]$canvasW,
  [int]$canvasH,
  [string]$outPath,
  [System.Drawing.Color]$bg,
  [bool]$transparentBg,
  [double]$padRatio
) {
  if ([string]::IsNullOrWhiteSpace($outPath)) {
    throw "Save-FitContainRect: outPath is empty."
  }
  $bmp = New-Object System.Drawing.Bitmap($canvasW, $canvasH)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  if ($transparentBg) {
    $g.Clear([System.Drawing.Color]::Transparent)
  } else {
    $g.Clear($bg)
  }
  $availW = [double]$canvasW * (1.0 - 2.0 * $padRatio)
  $availH = [double]$canvasH * (1.0 - 2.0 * $padRatio)
  if ($availW -lt 4) { $availW = 4 }
  if ($availH -lt 4) { $availH = 4 }
  $ratio = [Math]::Min($availW / [double]$source.Width, $availH / [double]$source.Height)
  $w = [int][Math]::Round($source.Width * $ratio)
  $h = [int][Math]::Round($source.Height * $ratio)
  $x = [int][Math]::Floor(($canvasW - $w) / 2.0)
  $y = [int][Math]::Floor(($canvasH - $h) / 2.0)
  $g.DrawImage($source, $x, $y, $w, $h)
  $dir = Split-Path $outPath
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
  $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose()
  $bmp.Dispose()
}

$res = Join-Path $base "android\app\src\main\res"
$pubDir = Join-Path $base "public"
$imgDir = Join-Path $pubDir "images"

$src = Open-SourceBitmap $srcPath
try {
  $sizes = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
  foreach ($e in $sizes.GetEnumerator()) {
    $folder = Join-Path $res $e.Key
    $px = $e.Value
    Save-FitContainSquare $src $px (Join-Path $folder "ic_launcher.png") $White $true $PadRatioLauncher
    Save-FitContainSquare $src $px (Join-Path $folder "ic_launcher_round.png") $White $true $PadRatioLauncher
  }
  Save-FitContainSquare $src 256 (Join-Path $res "drawable-nodpi\lom_logo.png") $White $true $PadRatioNodpi
  # public lom.png + copy to drawable-nodpi for native splash (same asset as Web)
  $pubLom = Join-Path $pubDir "lom.png"
  Save-FitContainSquare $src 256 $pubLom $White $true $PadRatioPublic
  $nodpiDir = Join-Path $res "drawable-nodpi"
  if (-not (Test-Path $nodpiDir)) { New-Item -ItemType Directory -Path $nodpiDir -Force | Out-Null }
  Copy-Item -LiteralPath $pubLom -Destination (Join-Path $nodpiDir "lom.png") -Force

  for ($i = 1; $i -le 10; $i++) {
    Save-FitContainRect $src 480 720 (Join-Path $imgDir "movie-poster-$i.png") $White $false $PadRatioPoster
  }
  Save-FitContainRect $src 1280 720 (Join-Path $imgDir "hero-banner-1.png") $White $false $PadRatioHero
  Save-FitContainRect $src 1280 720 (Join-Path $imgDir "hero-banner-2.png") $White $false $PadRatioHero
} finally {
  $src.Dispose()
}

if ($used -eq $FallbackIco) {
  Copy-Item -LiteralPath $icoFull -Destination (Join-Path $pubDir "lom.ico") -Force
}

Write-Host "OK: generated from $used -> transparent mipmap + lom_logo + public/lom.png + drawable-nodpi/lom.png."
