Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)

function Code([System.Drawing.Color]$c) {
    $r = $c.R; $g = $c.G; $b = $c.B
    $avg = ($r + $g + $b) / 3
    if ($avg -lt 40) { return '.' }
    if ($avg -lt 75) { return ':' }
    if ($b -gt $r + 25 -and $b -gt 110) { if ($g -gt $r + 5) { return 'C' } else { return 'B' } }
    if ($r -gt $b + 40 -and $r -gt 110) { if ($g -gt $b + 10) { return 'Y' } else { return 'O' } }
    if ($g -gt $r + 20 -and $g -gt $b + 20) { return 'N' }
    if ($avg -lt 110) { return 'm' }
    if ($avg -lt 160) { return 'g' }
    if ($avg -lt 205) { return 'L' }
    return 'W'
}

# 1) detect panel geometry: rows y-runs and col x-runs where light panel face (avg 120..215) dominates
function Get-Runs([int]$yy) {
    $runs = New-Object System.Collections.ArrayList
    $in = $false; $s = 0
    for ($xx = 0; $xx -lt 650; $xx++) {
        $c = $bmp.GetPixel($xx, $yy); $avg = ($c.R + $c.G + $c.B) / 3
        $isP = ($avg -ge 120 -and $avg -le 215)
        if ($isP -and -not $in) { $in = $true; $s = $xx }
        elseif (-not $isP -and $in) { $in = $false; [void]$runs.Add(@($s, ($xx - 1))) }
    }
    if ($in) { [void]$runs.Add(@($s, 649)) }
    return $runs
}
"Panel spans at selected rows:"
foreach ($yy in 20, 70, 120, 180, 230, 280, 335, 380, 420, 445) {
    $d = (Get-Runs $yy | ForEach-Object { "$($_[0])-$($_[1])" }) -join ','
    "y=$yy : $d"
}

# 2) for each 128x128 slot candidate (x=5,165,325,485 x y=5,165,326), majority grid + non-bg fraction
$cols = @(5, 165, 325, 485)
$rows = @(5, 165, 326)
$idx = 0
$slotGrids = @{}
foreach ($ry in $rows) {
    foreach ($cx in $cols) {
        $idx++
        # non-background fraction (not rgb(60,66,76)-ish)
        $nonBg = 0; $tot = 0
        for ($y = $ry + 2; $y -lt $ry + 126; $y += 4) {
            for ($x = $cx + 2; $x -lt $cx + 126; $x += 4) {
                $c = $bmp.GetPixel($x, $y)
                if ([math]::Abs($c.R-60) + [math]::Abs($c.G-66) + [math]::Abs($c.B-76) -gt 40) { $nonBg++ }
                $tot++
            }
        }
        $pct = [math]::Round(100.0 * $nonBg / $tot, 1)
        if ($pct -lt 3) {
            "SLOT $idx (x=$cx y=$ry): EMPTY ($pct% non-bg)"
            continue
        }
        $grid = @()
        for ($gy = 0; $gy -lt 16; $gy++) {
            $line = ''
            for ($gx = 0; $gx -lt 16; $gx++) {
                $hist = @{}
                for ($dy = 0; $dy -lt 8; $dy++) { for ($dx = 0; $dx -lt 8; $dx++) {
                    $c = $bmp.GetPixel($cx + $gx*8 + $dx, $ry + $gy*8 + $dy)
                    $cd = Code $c
                    if ($hist.ContainsKey($cd)) { $hist[$cd]++ } else { $hist[$cd] = 1 }
                } }
                $top = ($hist.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 1)
                $line += $top.Key
            }
            $grid += $line
        }
        $slotGrids[$idx] = $grid
        "SLOT $idx (x=$cx y=$ry): $pct% non-bg"
    }
}

# 3) compare slots vs actual files
$base = 'src\main\resources\assets\ecoaegtnh\textures'
$files = @{
  1='blocks\ecal_casing.png'; 2='blocks\ecal_parallel_proc.png'; 3='blocks\ecal_thread_core.png';
  4='blocks\ecal_cell_drive.png'; 5='blocks\ecal_me_channel.png'; 6='blocks\ecal_transmitter_bus.png';
  7='blocks\ecal_controller_front.png'; 8='blocks\ecal_controller_front_off.png';
  9='blocks\ecal_controller_side.png'; 10='items\ecal_cell_c4.png'
}
function GridFromFile([string]$rel) {
    $b = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $rel)).Path)
    $g = @()
    for ($y = 0; $y -lt 16; $y++) {
        $l = ''
        for ($x = 0; $x -lt 16; $x++) { $l += Code ($b.GetPixel($x, $y)) }
        $g += $l
    }
    $b.Dispose()
    return $g
}
$fileGrids = @{}
foreach ($k in $files.Keys) { $fileGrids[$k] = GridFromFile $files[$k] }

"`nSlot->file match (exact grid match = same 16x16 code grid):"
foreach ($idx in ($slotGrids.Keys | Sort-Object)) {
    $best = ''; $bestN = 0
    foreach ($k in $fileGrids.Keys) {
        $n = 0
        for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
            if ($slotGrids[$idx][$y][$x] -eq $fileGrids[$k][$y][$x]) { $n++ }
        } }
        if ($n -gt $bestN) { $bestN = $n; $best = $k }
    }
    $pct = [math]::Round(100.0 * $bestN / 256, 1)
    $name = $files[$best]
    $allSame = ($bestN -eq 256)
    "slot $idx -> best match: file#$best $name ($pct% identical, exact=$allSame)"
}
$bmp.Dispose()
