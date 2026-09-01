Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)

function Color-Code([System.Drawing.Color]$c) {
    $r = $c.R; $g = $c.G; $b = $c.B
    $avg = ($r + $g + $b) / 3
    if ($avg -lt 35) { return 'K' }
    if ($b -gt $r + 25 -and $b -gt 110) { if ($g -gt $r + 10) { return 'C' } else { return 'B' } }
    if ($r -gt $b + 40 -and $r -gt 110) { if ($g -gt $b + 10) { return 'Y' } else { return 'O' } }
    if ($g -gt $r + 20 -and $g -gt $b + 20) { return 'N' }
    if ($avg -lt 70) { return 'D' }
    if ($avg -lt 110) { return 'M' }
    if ($avg -lt 160) { return 'G' }
    if ($avg -lt 205) { return 'L' }
    return 'W'
}

# panels: row band y0..y1, columns x0..x1  (128x128 each)
$rows = @(
    @{ y0 = 5;  y1 = 132 },
    @{ y0 = 165; y1 = 292 },
    @{ y0 = 326; y1 = 453 }
)
$cols = @(5, 165, 325, 485)

$idx = 0
foreach ($r in $rows) {
    foreach ($cx in $cols) {
        $idx++
        $y0 = $r.y0; $x0 = $cx
        $grid = @()
        $mixed = @()
        for ($gy = 0; $gy -lt 16; $gy++) {
            $line = ''; $mixline = ''
            for ($gx = 0; $gx -lt 16; $gx++) {
                # majority color in 8x8 block
                $hist = @{}
                for ($dy = 0; $dy -lt 8; $dy++) {
                    for ($dx = 0; $dx -lt 8; $dx++) {
                        $c = $bmp.GetPixel($x0 + $gx * 8 + $dx, $y0 + $gy * 8 + $dy)
                        $code = Color-Code $c
                        if ($hist.ContainsKey($code)) { $hist[$code]++ } else { $hist[$code] = 1 }
                    }
                }
                $top = ($hist.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 1)
                $line += $top.Key
                if ($top.Value -lt 56) { $mixline += '?' } else { $mixline += ' ' }
            }
            $grid += $line
            $mixed += $mixline
        }
        "`n===== SLOT $idx  (row=$($rows.IndexOf($r)+1), col=$($cols.IndexOf($cx)+1), x=$x0 y=$y0) ====="
        for ($i = 0; $i -lt 16; $i++) { $grid[$i] + '  ' + $mixed[$i] }
        # unique colors
        $cols2 = @{}
        for ($y = $y0 + 2; $y -lt $y0 + 126; $y += 3) {
            for ($x = $x0 + 2; $x -lt $x0 + 126; $x += 3) {
                $c = $bmp.GetPixel($x, $y)
                $key = "{0},{1},{2}" -f $c.R, $c.G, $c.B
                if ($cols2.ContainsKey($key)) { $cols2[$key]++ } else { $cols2[$key] = 1 }
            }
        }
        "colors:"
        $cols2.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 12 | ForEach-Object { "  rgb($($_.Key)) x$($_.Value)" }
    }
}
$bmp.Dispose()
