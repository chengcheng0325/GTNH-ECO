Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)
$outDir = Join-Path (Resolve-Path 'tools\vision').Path 'dump'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Code([System.Drawing.Color]$c) {
    $r = $c.R; $g = $c.G; $b = $c.B
    $avg = ($r + $g + $b) / 3
    if ($avg -lt 40) { return '.' }          # near-black bg
    if ($avg -lt 75) { return ':' }          # dark gray
    if ($b -gt $r + 25 -and $b -gt 110) { if ($g -gt $r + 5) { return 'C' } else { return 'B' } }
    if ($r -gt $b + 40 -and $r -gt 110) { if ($g -gt $b + 10) { return 'Y' } else { return 'O' } }
    if ($g -gt $r + 20 -and $g -gt $b + 20) { return 'N' }
    if ($avg -lt 110) { return 'm' }
    if ($avg -lt 160) { return 'g' }
    if ($avg -lt 205) { return 'L' }
    return 'W'
}

# Region dumps: each = list of (name, x0, x1, y0, y1)
$regions = @(
    @('top-rows-band1', 0, 649, 0, 148),
    @('mid-rows-band2', 0, 649, 148, 300),
    @('bottom-darkband', 0, 649, 295, 465),
    @('vstrip-x437', 433, 460, 0, 300),
    @('vstrip-x485', 481, 508, 0, 300),
    @('vstrip-x597', 593, 620, 0, 300)
)
foreach ($reg in $regions) {
    $name = $reg[0]; $x0 = $reg[1]; $x1 = $reg[2]; $y0 = $reg[3]; $y1 = $reg[4]
    $sb = New-Object System.Text.StringBuilder
    for ($y = $y0; $y -le $y1; $y++) {
        $line = ''
        for ($x = $x0; $x -le $x1; $x++) {
            $line += Code ($bmp.GetPixel($x, $y))
        }
        [void]$sb.AppendLine($line)
    }
    $file = Join-Path $outDir ($name + '.txt')
    [System.IO.File]::WriteAllText($file, $sb.ToString())
    "wrote $file ($(($y1-$y0+1)) rows x $(($x1-$x0+1)) cols)"
}
$bmp.Dispose()
