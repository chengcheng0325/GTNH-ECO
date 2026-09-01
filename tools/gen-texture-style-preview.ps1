# =============================================================
# ECO-GTNH storage cell texture STYLE PREVIEWS (t93 - preview only, no code/texture changes)
# 5 visual styles x 3 types (item gold / fluid blue / essentia purple), 16x16 each.
# Outputs:
#   tools/texture-style-preview.png         contact sheet (5 rows x 3 cols, 96px cells)
#   tools/texture-styles/<style>-<type>.png 15 individual previews
# Run: pwsh -File tools/gen-texture-style-preview.ps1
# NOTE: keep this file ASCII-only (PS 5.1 reads non-BOM UTF-8 as ANSI -> parse errors).
# =============================================================
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$outDir = "D:\DeepSeek\GTNH-ECO\tools\texture-styles"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# ---------- type colours (user-fixed, do not change) ----------
$TYPES = [ordered]@{
    item     = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)   # item  gold
    fluid    = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)   # fluid blue
    essentia = [System.Drawing.Color]::FromArgb(255, 176, 108, 255)  # essentia purple
}

# ---------- helpers ----------
function New-Canvas([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h) }
function Set-Px($bmp, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $x -lt $bmp.Width -and $y -ge 0 -and $y -lt $bmp.Height) { $bmp.SetPixel($x, $y, $c) }
}
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y $c } }
}
function Draw-RectBorder($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y0 $c; Set-Px $bmp $x $y1 $c }
    for ($y = $y0; $y -le $y1; $y++) { Set-Px $bmp $x0 $y $c; Set-Px $bmp $x1 $y $c }
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

# =============================================================
# Style A: Classic GT Industrial (graphite + type-colour traces + chip, 2-tone bevel)
# =============================================================
function New-StyleA($tc) {
    $b = New-Canvas 16 16
    $base  = [System.Drawing.Color]::FromArgb(255, 42, 47, 56)    # graphite body
    $dark  = [System.Drawing.Color]::FromArgb(255, 28, 32, 38)    # outer frame
    $hi    = [System.Drawing.Color]::FromArgb(255, 112, 122, 136) # pins / highlight
    $edge  = [System.Drawing.Color]::FromArgb(255, 82, 90, 102)   # bright edge
    $edgeD = [System.Drawing.Color]::FromArgb(255, 52, 58, 68)    # dark edge
    $chip  = [System.Drawing.Color]::FromArgb(255, 16, 18, 22)    # chip base
    Fill-Rect $b 1 1 14 14 $base
    Draw-RectBorder $b 0 0 15 15 $dark
    Draw-RectBorder $b 1 1 14 14 $edge
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 1 $edge; Set-Px $b 1 $x $edge; Set-Px $b $x 14 $edgeD; Set-Px $b 14 $x $edgeD }
    for ($x = 3; $x -le 13; $x += 2) { Set-Px $b $x 0 $hi }
    Fill-Rect $b 2 3 2 12 $tc                                            # left type strip
    Fill-Rect $b 4 4 12 4 $tc; Fill-Rect $b 4 7 12 7 $tc; Fill-Rect $b 4 10 12 10 $tc
    Set-Px $b 4 5 $tc; Set-Px $b 4 6 $tc; Set-Px $b 12 5 $tc; Set-Px $b 12 6 $tc
    Set-Px $b 4 8 $tc; Set-Px $b 4 9 $tc; Set-Px $b 12 8 $tc; Set-Px $b 12 9 $tc
    Fill-Rect $b 6 5 9 9 $chip
    Draw-RectBorder $b 6 5 9 9 $tc
    Fill-Rect $b 7 6 8 8 (Shade $tc 45)
    Fill-Rect $b 14 4 15 5 $tc; Fill-Rect $b 14 7 15 8 $tc; Fill-Rect $b 14 10 15 11 $tc
    return $b
}

# =============================================================
# Style B: Modern Glowing Chip (near-black base + glowing type-colour traces, high contrast)
# =============================================================
function New-StyleB($tc) {
    $b = New-Canvas 16 16
    $base   = [System.Drawing.Color]::FromArgb(255, 11, 13, 17)
    $edge   = [System.Drawing.Color]::FromArgb(255, 31, 35, 41)
    $glow   = Dim $tc 0.45
    $bright = Shade $tc 60
    Fill-Rect $b 1 1 14 14 $base
    Draw-RectBorder $b 0 0 15 15 $edge
    foreach ($px in @(@(4,3), @(4,6), @(4,9), @(4,12), @(11,3), @(11,6), @(11,9), @(11,12))) {
        foreach ($off in @(@(-1,0), @(1,0), @(0,-1), @(0,1))) { Set-Px $b ($px[0]+$off[0]) ($px[1]+$off[1]) $glow }
    }
    Fill-Rect $b 4 3 4 12 $tc; Fill-Rect $b 11 3 11 12 $tc
    Fill-Rect $b 4 6 11 6 $tc; Fill-Rect $b 4 9 11 9 $tc
    Fill-Rect $b 3 3 3 12 $bright; Fill-Rect $b 12 3 12 12 $bright
    Fill-Rect $b 6 5 9 10 (Dim $tc 0.22)
    Draw-RectBorder $b 5 5 10 10 $tc
    Fill-Rect $b 6 6 9 9 $glow
    Fill-Rect $b 7 7 8 8 $bright
    Fill-Rect $b 7 13 8 14 $tc
    Set-Px $b 13 2 $bright; Set-Px $b 2 13 $bright
    return $b
}

# =============================================================
# Style C: Minimal Flat (flat colour block + simple geometric icon, modern GUI)
# =============================================================
function New-StyleC($tc) {
    $b = New-Canvas 16 16
    $bg     = [System.Drawing.Color]::FromArgb(255, 31, 33, 38)
    $bgEdge = [System.Drawing.Color]::FromArgb(255, 58, 62, 68)
    Fill-Rect $b 0 0 15 15 $bg
    Draw-RectBorder $b 0 0 15 15 $bgEdge
    Fill-Rect $b 4 3 11 12 $tc
    Set-Px $b 3 4 $tc; Set-Px $b 3 5 $tc; Set-Px $b 3 10 $tc; Set-Px $b 3 11 $tc
    Set-Px $b 12 4 $tc; Set-Px $b 12 5 $tc; Set-Px $b 12 10 $tc; Set-Px $b 12 11 $tc
    Fill-Rect $b 6 5 9 5 $bg; Fill-Rect $b 6 7 9 7 $bg; Fill-Rect $b 6 9 9 9 $bg
    Set-Px $b 13 2 $tc
    return $b
}

# =============================================================
# Style D: Neon Tech (pure black + high-saturation neon polyline + glow/nodes)
# =============================================================
function New-StyleD($tc) {
    $b = New-Canvas 16 16
    $base   = [System.Drawing.Color]::FromArgb(255, 5, 6, 10)
    $edge   = [System.Drawing.Color]::FromArgb(255, 14, 17, 22)
    $halo   = Dim $tc 0.35
    $bright = Shade $tc 55
    Fill-Rect $b 1 1 14 14 $base
    Draw-RectBorder $b 0 0 15 15 $edge
    $path = @(@(2,2),@(3,2),@(4,2),@(5,2),@(6,2), @(6,3),@(6,4),@(6,5),@(6,6),
              @(7,6),@(8,6),@(9,6),@(10,6),@(11,6), @(11,7),@(11,8),@(11,9),@(11,10),@(11,11),
              @(12,11),@(13,11),@(14,11))
    foreach ($p in $path) {
        foreach ($off in @(@(-1,0),@(1,0),@(0,-1),@(0,1),@(-1,-1),@(1,1),@(-1,1),@(1,-1))) {
            Set-Px $b ($p[0]+$off[0]) ($p[1]+$off[1]) $halo
        }
    }
    foreach ($p in $path) { Set-Px $b $p[0] $p[1] $tc }
    foreach ($n in @(@(6,2), @(6,6), @(11,6), @(11,11))) { Fill-Rect $b ($n[0]-1) ($n[1]-1) $n[0] $n[1] $bright }
    Set-Px $b 8 5 $bright; Set-Px $b 8 9 $bright; Set-Px $b 6 7 $bright; Set-Px $b 10 7 $bright
    Set-Px $b 7 6 $tc; Set-Px $b 9 6 $tc; Set-Px $b 7 8 $tc; Set-Px $b 9 8 $tc
    Set-Px $b 8 7 $bright
    return $b
}

# =============================================================
# Style E: Metallic (vertical metal gradient + brushed lines + type-colour accents)
# =============================================================
function New-StyleE($tc) {
    $b = New-Canvas 16 16
    # darker metal gradient (vision: bright metal overpowered the thin type-colour line)
    $bands = @(
        @(0,  2, 138, 146, 155),
        @(3,  5, 98, 105, 114),
        @(6,  9, 66, 71, 78),
        @(10, 12, 45, 49, 54),
        @(13, 15, 34, 37, 41)
    )
    foreach ($band in $bands) { Fill-Rect $b 0 $band[0] 15 $band[1] ([System.Drawing.Color]::FromArgb(255, $band[2], $band[3], $band[4])) }
    for ($y = 3; $y -le 13; $y += 2) { Fill-Rect $b 1 $y 14 $y ([System.Drawing.Color]::FromArgb(255, 84, 91, 100)) }
    Fill-Rect $b 0 0 15 0 ([System.Drawing.Color]::FromArgb(255, 192, 199, 207))
    Fill-Rect $b 0 0 0 15 ([System.Drawing.Color]::FromArgb(255, 192, 199, 207))
    Fill-Rect $b 15 0 15 15 ([System.Drawing.Color]::FromArgb(255, 20, 23, 26))
    Fill-Rect $b 0 15 15 15 ([System.Drawing.Color]::FromArgb(255, 20, 23, 26))
    # recessed slot
    Fill-Rect $b 4 4 11 11 ([System.Drawing.Color]::FromArgb(255, 18, 20, 23))
    Draw-RectBorder $b 4 4 11 11 ([System.Drawing.Color]::FromArgb(255, 10, 11, 13))
    Fill-Rect $b 5 4 10 4 ([System.Drawing.Color]::FromArgb(255, 150, 158, 167))
    # big type-colour chip inside the slot (vision: raise type-colour coverage >12%)
    Fill-Rect $b 5 6 10 9 $tc
    Fill-Rect $b 7 7 8 8 ([System.Drawing.Color]::FromArgb(255, 12, 13, 16))      # dark core
    Fill-Rect $b 5 9 10 9 (Dim $tc 0.55)                                          # glow under-chip
    Fill-Rect $b 5 5 10 5 (Shade $tc 45)                                           # chip top rim
    # four corner type-colour rivets
    Set-Px $b 2 2 $tc; Set-Px $b 13 2 $tc; Set-Px $b 2 13 $tc; Set-Px $b 13 13 $tc
    return $b
}

# ---------- generate 15 previews + contact sheet ----------
$styles = [ordered]@{
    A = "Classic GT"
    B = "Glow Chip"
    C = "Minimal Flat"
    D = "Neon Tech"
    E = "Metal"
}
$painters = [ordered]@{ A = "New-StyleA"; B = "New-StyleB"; C = "New-StyleC"; D = "New-StyleD"; E = "New-StyleE" }

$cell = 96
$pad  = 26
$labelW = 150
$headerH = 46
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
$sheetPath = "D:\DeepSeek\GTNH-ECO\tools\texture-style-preview.png"
$sheet.Save($sheetPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $sheet.Dispose()
Write-Output ("contact sheet: {0} ({1}x{2})" -f $sheetPath, $sheetW, $sheetH)
Write-Output ("15 singles saved to: {0}" -f $outDir)
