# =============================================================
# ECO-GTNH storage COMPONENT textures in AE2U storage-component style (t101-coop)
# Replaces the 27 src textures (storage_component_<type>_<size>.png).
# AE style: dark #373127 border + #999999 edge + #A4A4A4 gray-white body + dark slot.
# Type colour = small accent: 3 capacity cores (1-3 lit, within group) + 3 group pips
#   on the right edge (k=1 pip, 16M..256M=2, 1024M+=3) - all 27 combos unique.
# Type colours fixed: item gold / fluid blue / essentia purple.
# Outputs:
#   src/main/resources/assets/ecoaegtnh/textures/items/storage_component_*.png (27, overwrite)
#   tools/component-style-preview.png  contact sheet (3 types x 9 tiers)
# Run: pwsh -File tools/gen-storage-components-ae.ps1   (keep ASCII-only!)
# =============================================================
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$itm = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\items"

# chip silhouette (same family as the current ECO component / AE cell part)
$CHIP = @(@(7,8), @(6,9), @(5,10), @(4,11), @(3,12), @(2,13), @(1,14), @(1,14), @(1,14), @(1,14), @(2,13), @(3,12), @(4,11), @(5,10), @(6,9), @(7,8))
function In-Chip([int]$x, [int]$y) {
    if ($y -lt 0 -or $y -gt 15) { return $false }
    return ($x -ge $CHIP[$y][0] -and $x -le $CHIP[$y][1])
}

$TYPES = [ordered]@{
    item     = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)   # gold
    fluid    = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)   # blue
    essentia = [System.Drawing.Color]::FromArgb(255, 176, 108, 255)  # purple
}
$TIERS = @(
    @("256k", 0, 0),    @("1024k", 0, 1),  @("4096k", 0, 2),
    @("16m", 1, 0),     @("64m", 1, 1),    @("256m", 1, 2),
    @("1024m", 2, 0),   @("4096m", 2, 1),  @("16384m", 2, 2)
)

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

# group pips -> (cores lit, group pips lit) for a tier entry
function New-Component($tc, [int]$cores, [int]$pips) {
    $b = New-Canvas 16 16
    $border = [System.Drawing.Color]::FromArgb(255, 55, 49, 39)    # AE 373127
    $rim    = [System.Drawing.Color]::FromArgb(255, 153, 153, 153) # AE 999999 edge highlight
    $body   = [System.Drawing.Color]::FromArgb(255, 164, 164, 164) # AE a4a4a4 body
    $shade  = [System.Drawing.Color]::FromArgb(255, 128, 128, 128) # AE 808080
    $slot   = [System.Drawing.Color]::FromArgb(255, 38, 41, 46)    # dark slot backing
    $slotIn = [System.Drawing.Color]::FromArgb(255, 58, 62, 68)    # unlit core
    # body
    for ($y = 0; $y -lt 16; $y++) { for ($x = $CHIP[$y][0]; $x -le $CHIP[$y][1]; $x++) { Set-Px $b $x $y $body } }
    # contour: outer edge = rim; second layer on diagonals = shade (machined bevel)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = $CHIP[$y][0]; $x -le $CHIP[$y][1]; $x++) {
            $edge = $false
            foreach ($dy in @(-1,0,1)) { foreach ($dx in @(-1,0,1)) {
                if ($dx -eq 0 -and $dy -eq 0) { continue }
                if (-not (In-Chip ($x+$dx) ($y+$dy))) { $edge = $true }
            } }
            if ($edge) { Set-Px $b $x $y $rim; continue }
            # bevel: second layer inside the contour on the 4 diagonal directions
            if ((In-Chip ($x-1) ($y-1)) -and -not (In-Chip ($x-2) ($y-2))) { Set-Px $b $x $y $shade; continue }
            if ((In-Chip ($x+1) ($y+1)) -and -not (In-Chip ($x+2) ($y+2))) { Set-Px $b $x $y $shade; continue }
        }
    }
    # dark slot zone in the centre (the "deep slot" of the AE component)
    Fill-Rect $b 5 5 10 10 $slot
    Set-Px $b 5 5 $border; Set-Px $b 10 5 $border; Set-Px $b 5 10 $border; Set-Px $b 10 10 $border
    # 3 capacity cores (2x2 each) - cores lit 1..3
    $coreXs = @(@(5,6), @(7,8), @(9,10))
    for ($i = 0; $i -lt 3; $i++) {
        $c = $slotIn
        if ($i -lt $cores) { $c = $tc }
        Fill-Rect $b $coreXs[$i][0] 6 $coreXs[$i][1] 7 $c
    }
    # group pips on the right edge (inside the chip): 1 = k, 2 = 16M, 3 = bigM
    $pipYs = @(4, 8, 11)
    for ($i = 0; $i -lt 3; $i++) {
        if ($i -lt $pips) { Set-Px $b 12 $pipYs[$i] $tc }
    }
    return $b
}

# ---------- generate 27 + contact sheet ----------
$rows = @()
foreach ($tname in $TYPES.Keys) {
    foreach ($tier in $TIERS) {
        $size = $tier[0]; $grp = $tier[1]; $idx = $tier[2]
        $cores = $idx + 1
        $pips = $grp + 1
        $icon = New-Component $TYPES[$tname] $cores $pips
        $path = Join-Path $itm ("storage_component_{0}_{1}.png" -f $tname, $size)
        $icon.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $icon.Dispose()
        $rows += ,@($tname, $size, $cores, $pips)
    }
}

# contact sheet: 3 type rows x 9 tier cols
$cell = 64; $pad = 14; $labelW = 96; $headerH = 40
$cols = @()
foreach ($tier in $TIERS) { $cols += $tier[0] }
$sheetW = $labelW + ($cols.Count * $cell) + (($cols.Count + 1) * $pad)
$sheetH = $headerH + (3 * $cell) + (4 * $pad)
$sheet = New-Canvas $sheetW $sheetH
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(255, 18, 19, 22))
$font = New-Object System.Drawing.Font("Arial", 11, [System.Drawing.FontStyle]::Bold)
$white = [System.Drawing.Brushes]::White
for ($i = 0; $i -lt $cols.Count; $i++) {
    $cx = $labelW + $pad + $i * ($cell + $pad)
    $g.DrawString($cols[$i], $font, $white, $cx + 6, 10)
}
$typeIdx = 0
foreach ($tname in $TYPES.Keys) {
    $ry = $headerH + $pad + $typeIdx * ($cell + $pad)
    $g.DrawString($tname, $font, $white, 12, $ry + 22)
    for ($i = 0; $i -lt 9; $i++) {
        $size = $TIERS[$i][0]; $grp = $TIERS[$i][1]; $idx = $TIERS[$i][2]
        $icon = New-Component $TYPES[$tname] ($idx + 1) ($grp + 1)
        $cx = $labelW + $pad + $i * ($cell + $pad)
        $dst = New-Object System.Drawing.Rectangle($cx, $ry, $cell, $cell)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $g.DrawImage($icon, $dst, 0, 0, 16, 16, [System.Drawing.GraphicsUnit]::Pixel)
        $icon.Dispose()
    }
    $typeIdx++
}
$sheetPath = "D:\DeepSeek\GTNH-ECO\tools\component-style-preview.png"
$sheet.Save($sheetPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $sheet.Dispose()
Write-Output ("27 textures written to: {0}" -f $itm)
Write-Output ("contact sheet: {0} ({1}x{2})" -f $sheetPath, $sheetW, $sheetH)
foreach ($r in $rows) { Write-Output ("  {0,-8} {1,-8} cores={2} pips={3}" -f $r[0], $r[1], $r[2], $r[3]) }
