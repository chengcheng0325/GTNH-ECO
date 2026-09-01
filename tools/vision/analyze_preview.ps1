Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)
$W = $bmp.Width; $H = $bmp.Height
"Image: ${W}x${H}"

function Is-Dark([System.Drawing.Color]$c) {
    return (($c.R + $c.G + $c.B) / 3) -lt 75
}

# --- column profile: dark fraction in middle band ---
$colDark = @()
for ($x = 0; $x -lt $W; $x++) {
    $n = 0; $tot = 0
    for ($y = 20; $y -lt $H - 20; $y += 2) {
        $c = $bmp.GetPixel($x, $y)
        if (Is-Dark $c) { $n++ }
        $tot++
    }
    $colDark += ($n / $tot)
}
# --- row profile: dark fraction in middle band ---
$rowDark = @()
for ($y = 0; $y -lt $H; $y++) {
    $n = 0; $tot = 0
    for ($x = 20; $x -lt $W - 20; $x += 2) {
        $c = $bmp.GetPixel($x, $y)
        if (Is-Dark $c) { $n++ }
        $tot++
    }
    $rowDark += ($n / $tot)
}

# --- find runs of non-dark columns (tile spans) ---
$colRuns = New-Object System.Collections.ArrayList
$inRun = $false; $start = 0
for ($x = 0; $x -lt $W; $x++) {
    $isDark = $colDark[$x] -gt 0.7
    if (-not $isDark -and -not $inRun) { $inRun = $true; $start = $x }
    elseif ($isDark -and $inRun) { $inRun = $false; [void]$colRuns.Add(@($start, ($x - 1))) }
}
if ($inRun) { [void]$colRuns.Add(@($start, ($W - 1))) }
"Column tile spans:"
foreach ($r in $colRuns) { "  x=$($r[0])..$($r[1])  w=$($r[1] - $r[0] + 1)" }

$rowRuns = New-Object System.Collections.ArrayList
$inRun = $false; $start = 0
for ($y = 0; $y -lt $H; $y++) {
    $isDark = $rowDark[$y] -gt 0.7
    if (-not $isDark -and -not $inRun) { $inRun = $true; $start = $y }
    elseif ($isDark -and $inRun) { $inRun = $false; [void]$rowRuns.Add(@($start, ($y - 1))) }
}
if ($inRun) { [void]$rowRuns.Add(@($start, ($H - 1))) }
"Row tile spans:"
foreach ($r in $rowRuns) { "  y=$($r[0])..$($r[1])  h=$($r[1] - $r[0] + 1)" }

# --- for each tile, print a 16x16 coarse grid (sampling every 8px) ---
function Color-Code([System.Drawing.Color]$c) {
    $r = $c.R; $g = $c.G; $b = $c.B
    $avg = ($r + $g + $b) / 3
    if ($avg -lt 35) { return 'K' }
    if ($b -gt $r + 25 -and $b -gt 110) {
        if ($g -gt $r + 10) { return 'C' } else { return 'B' }
    }
    if ($r -gt $b + 40 -and $r -gt 110) {
        if ($g -gt $b + 10) { return 'Y' } else { return 'O' }
    }
    if ($g -gt $r + 20 -and $g -gt $b + 20) { return 'N' }
    if ($avg -lt 70) { return 'D' }
    if ($avg -lt 110) { return 'M' }
    if ($avg -lt 160) { return 'G' }
    if ($avg -lt 205) { return 'L' }
    return 'W'
}

$tileIdx = 0
foreach ($ry in $rowRuns) {
    foreach ($cx in $colRuns) {
        $x0 = $cx[0]; $y0 = $ry[0]
        $tw = $cx[1] - $x0 + 1; $th = $ry[1] - $y0 + 1
        $tileIdx++
        "`n===== TILE $tileIdx  x=$x0 y=$y0  ${tw}x${th} ====="
        # sample 16x16 from the tile area (center-weighted)
        $grid = @()
        for ($gy = 0; $gy -lt 16; $gy++) {
            $row = ''
            for ($gx = 0; $gx -lt 16; $gx++) {
                $px = $x0 + [int](($gx + 0.5) * $tw / 16)
                $py = $y0 + [int](($gy + 0.5) * $th / 16)
                $row += Color-Code ($bmp.GetPixel($px, $py))
            }
            $grid += $row
        }
        $grid | ForEach-Object { $_ }
        # unique colors inside tile (sampled every 4px)
        $cols = @{}
        for ($y = $y0 + 4; $y -lt $y0 + $th - 4; $y += 4) {
            for ($x = $x0 + 4; $x -lt $x0 + $tw - 4; $x += 4) {
                $c = $bmp.GetPixel($x, $y)
                $key = "{0},{1},{2}" -f $c.R, $c.G, $c.B
                if ($cols.ContainsKey($key)) { $cols[$key]++ } else { $cols[$key] = 1 }
            }
        }
        "Colors:"
        $cols.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 14 | ForEach-Object { "  rgb($($_.Key)) x$($_.Value)" }
    }
}
$bmp.Dispose()
