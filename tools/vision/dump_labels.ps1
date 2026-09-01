Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)
$outDir = Join-Path (Resolve-Path 'tools\vision').Path 'dump'

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

# Label strips with ruler: 4 strips x3 rows
$strips = @(
    @('L1-row1', 0, 649, 136, 148),
    @('L1-row2', 0, 649, 296, 308),
    @('L1-row3', 0, 649, 456, 470)
)
foreach ($s in $strips) {
    $x0 = $s[1]; $x1 = $s[2]; $y0 = $s[3]; $y1 = $s[4]
    $sb = New-Object System.Text.StringBuilder
    $ruler = ''
    for ($x = $x0; $x -le $x1; $x++) { if ($x % 10 -eq 0) { $ruler += [string]$x } else { $ruler += ' ' } }
    [void]$sb.AppendLine('x: ' + $ruler)
    for ($y = $y0; $y -le $y1; $y++) {
        $line = ''
        for ($x = $x0; $x -le $x1; $x++) { $line += Code ($bmp.GetPixel($x, $y)) }
        [void]$sb.AppendLine(('{0,3}: ' -f $y) + $line)
    }
    $file = Join-Path $outDir ($s[0] + '.txt')
    [System.IO.File]::WriteAllText($file, $sb.ToString())
    "wrote $file"
}
$bmp.Dispose()
