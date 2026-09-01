# =============================================================
# ECO-GTNH storage cell AE-style proposals (t94 - preview only)
# Style F: AE Basic/Advanced (translucent gray diamond chip + central type block)
# Style G: AE Extreme (dark glossy diamond shell + glowing type core)
# Type colours fixed: item gold / fluid blue / essentia purple.
# Outputs:
#   tools/texture-style-preview-ae.png  contact sheet (2 rows F/G x 3 cols)
#   tools/texture-styles/style-f-*.png, style-g-*.png  (6 singles)
# Run: pwsh -File tools/gen-texture-style-ae.ps1   (keep ASCII-only!)
# =============================================================
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$outDir = "D:\DeepSeek\GTNH-ECO\tools\texture-styles"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$TYPES = [ordered]@{
    item     = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)   # gold
    fluid    = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)   # blue
    essentia = [System.Drawing.Color]::FromArgb(255, 176, 108, 255)  # purple
}

# ---------- helpers ----------
function New-Canvas([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h) }
function Set-Px($bmp, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $x -lt $bmp.Width -and $y -ge 0 -and $y -lt $bmp.Height) { $bmp.SetPixel($x, $y, $c) }
}
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y $c } }
}
function Shade($c, [int]$d) {
    return [System.Drawing.Color]::FromArgb(255,
        [Math]::Max(0, [Math]::Min(255, $c.R + $d)),
        [Math]::Max(0, [Math]::Min(255, $c.G + $d)),
        [Math]::Max(0, [Math]::Min(255, $c.B + $d)))
}
function Dim($c, [double]$f) {
    return [System.Drawing.Color]::FromArgb(255,
        [Math]::Max(0, [Math]::Min(255, [int]($c.R * $f))),
        [Math]::Max(0, [Math]::Min(255, [int]($c.G * $f))),
        [Math]::Max(0, [Math]::Min(255, [int]($c.B * $f))))
}
# AE diamond mask: per-row inclusive x range (rows 0..15)
$DIAMOND = @(@(7,8), @(6,9), @(5,10), @(4,11), @(3,12), @(2,13), @(1,14), @(1,14), @(1,14), @(1,14), @(2,13), @(3,12), @(4,11), @(5,10), @(6,9), @(7,8))
function In-Diamond([int]$x, [int]$y) {
    if ($y -lt 0 -or $y -gt 15) { return $false }
    return ($x -ge $DIAMOND[$y][0] -and $x -le $DIAMOND[$y][1])
}
# contour of the diamond = diamond pixels having a non-diamond 8-neighbour
function Diamond-Contour($bmp, $color) {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            if (-not (In-Diamond $x $y)) { continue }
            $edge = $false
            foreach ($dy in @(-1,0,1)) { foreach ($dx in @(-1,0,1)) {
                if ($dx -eq 0 -and $dy -eq 0) { continue }
                if (-not (In-Diamond ($x+$dx) ($y+$dy))) { $edge = $true }
            } }
            if ($edge) { Set-Px $bmp $x $y $color }
        }
    }
}

# =============================================================
# Style F: AE Basic/Advanced - translucent gray diamond + central type block
# =============================================================
function New-StyleF($tc) {
    $b = New-Canvas 16 16
    $border = [System.Drawing.Color]::FromArgb(255, 55, 49, 39)   # AE 373127
    $body   = [System.Drawing.Color]::FromArgb(255, 122, 129, 137) # translucent gray
    $bodyD  = [System.Drawing.Color]::FromArgb(255, 92, 98, 105)
    $dark   = [System.Drawing.Color]::FromArgb(255, 64, 64, 64)    # 404040 texture
    $edgeHi = [System.Drawing.Color]::FromArgb(255, 186, 194, 202) # light edge strip
    $led    = [System.Drawing.Color]::FromArgb(255, 25, 255, 0)    # AE green LED
    # body
    for ($y = 0; $y -lt 16; $y++) { for ($x = $DIAMOND[$y][0]; $x -le $DIAMOND[$y][1]; $x++) { Set-Px $b $x $y $body } }
    # contour border
    Diamond-Contour $b $border
    # edge strips: brighten the top-left and bottom-right diagonal edges (machined strip)
    foreach ($y in 0..15) {
        $x0 = $DIAMOND[$y][0]; $x1 = $DIAMOND[$y][1]
        if (In-Diamond ($x0-1) ($y-1)) { Set-Px $b $x0 $y $edgeHi }   # top-left edge
        if (In-Diamond ($x1+1) ($y+1)) { Set-Px $b $x1 $y $edgeHi }   # bottom-right edge
    }
    # inner texture dots (AE 404040 pattern around the core)
    foreach ($p in @(@(7,3), @(10,3), @(4,5), @(4,9), @(13,5), @(13,9), @(7,12), @(10,12))) { Set-Px $b $p[0] $p[1] $dark }
    Set-Px $b 7 4 $dark; Set-Px $b 10 4 $dark; Set-Px $b 7 11 $dark; Set-Px $b 10 11 $dark
    # central type block (4x4 with dark core, type rim)
    Fill-Rect $b 6 5 9 8 $tc
    Fill-Rect $b 7 6 8 7 $dark
    Set-Px $b 6 5 $tc; Set-Px $b 9 5 $tc; Set-Px $b 6 8 $tc; Set-Px $b 9 8 $tc
    # green status LED
    Fill-Rect $b 10 12 10 13 $led
    return $b
}

# =============================================================
# Style G: AE Extreme - dark glossy diamond shell + glowing type core
# =============================================================
function New-StyleG($tc) {
    $b = New-Canvas 16 16
    $border = [System.Drawing.Color]::FromArgb(255, 26, 27, 31)   # dark shell border
    $ring1  = [System.Drawing.Color]::FromArgb(255, 46, 50, 57)   # outer ring
    $ring2  = [System.Drawing.Color]::FromArgb(255, 58, 63, 71)   # mid ring
    $ring3  = [System.Drawing.Color]::FromArgb(255, 74, 80, 89)   # inner ring
    $gloss  = [System.Drawing.Color]::FromArgb(255, 154, 161, 171) # gloss streak
    $led    = [System.Drawing.Color]::FromArgb(255, 25, 255, 0)
    # nested diamond rings by manhattan distance from center (7.5,7.5)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = $DIAMOND[$y][0]; $x -le $DIAMOND[$y][1]; $x++) {
            $md = [Math]::Abs($x - 7.5) + [Math]::Abs($y - 7.5)
            $c = $ring1
            if ($md -le 5.5) { $c = $ring2 }
            if ($md -le 3.5) { $c = $ring3 }
            Set-Px $b $x $y $c
        }
    }
    Diamond-Contour $b $border
    # gloss: diagonal light streak top-left -> bottom-right
    foreach ($p in @(@(3,2), @(4,3), @(5,4), @(12,11), @(13,12), @(14,13), @(2,3), @(3,4), @(11,12), @(12,13))) { Set-Px $b $p[0] $p[1] $gloss }
    # glowing type core: bright 2x2 + halo ring
    Fill-Rect $b 6 6 9 9 (Dim $tc 0.5)     # halo
    Fill-Rect $b 7 7 8 8 (Shade $tc 55)    # bright core
    Set-Px $b 6 6 $tc; Set-Px $b 9 6 $tc; Set-Px $b 6 9 $tc; Set-Px $b 9 9 $tc
    # type accents at diamond corners (extreme-style scattered accents)
    Set-Px $b 3 5 $tc; Set-Px $b 5 3 $tc; Set-Px $b 12 5 $tc; Set-Px $b 10 3 $tc
    Set-Px $b 3 10 $tc; Set-Px $b 5 12 $tc; Set-Px $b 12 10 $tc; Set-Px $b 10 12 $tc
    # green LED
    Fill-Rect $b 10 13 10 13 $led
    return $b
}

# ---------- contact sheet (2 rows x 3 cols) ----------
$styles = [ordered]@{ F = "AE Basic/Adv"; G = "AE Extreme" }
$painters = [ordered]@{ F = "New-StyleF"; G = "New-StyleG" }

$cell = 96; $pad = 26; $labelW = 150; $headerH = 46
$cols = @("item", "fluid", "essentia")
$sheetW = $labelW + ($cols.Count * $cell) + (($cols.Count + 1) * $pad)
$sheetH = $headerH + ($styles.Count * $cell) + (($styles.Count + 1) * $pad) + 10
$sheet = New-Canvas $sheetW $sheetH
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(255, 18, 19, 22))
$font  = New-Object System.Drawing.Font("Arial", 13, [System.Drawing.FontStyle]::Bold)
$white = [System.Drawing.Brushes]::White
$ci = 0
foreach ($cname in $cols) {
    $cx = $labelW + $pad + $ci * ($cell + $pad)
    $g.DrawString($cname, $font, $white, $cx + 22, 12)
    $ci++
}
$styleIdx = 0
foreach ($sname in $styles.Keys) {
    $ry = $headerH + $pad + $styleIdx * ($cell + $pad)
    $g.DrawString(("$sname " + $styles[$sname]), $font, $white, 10, $ry + 36)
    $ci = 0
    foreach ($cname in $cols) {
        $cx = $labelW + $pad + $ci * ($cell + $pad)
        $tc = $TYPES[$cname]
        $icon = & $painters[$sname] $tc
        $singlePath = Join-Path $outDir ("style-{0}-{1}.png" -f $sname.ToLower(), $cname)
        $icon.Save($singlePath, [System.Drawing.Imaging.ImageFormat]::Png)
        $dst = New-Object System.Drawing.Rectangle($cx, $ry, $cell, $cell)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $g.DrawImage($icon, $dst, 0, 0, 16, 16, [System.Drawing.GraphicsUnit]::Pixel)
        $icon.Dispose()
        $ci++
    }
    $styleIdx++
}
$sheetPath = "D:\DeepSeek\GTNH-ECO\tools\texture-style-preview-ae.png"
$sheet.Save($sheetPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $sheet.Dispose()
Write-Output ("contact sheet: {0} ({1}x{2})" -f $sheetPath, $sheetW, $sheetH)
Write-Output ("6 singles saved to: {0}" -f $outDir)
