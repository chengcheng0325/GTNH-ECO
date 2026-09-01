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

# full bottom area with ruler: y=295..470, x=0..650, 1px per char
$sb = New-Object System.Text.StringBuilder
# ruler
$ruler1 = ''; $ruler2 = ''
for ($x = 0; $x -le 649; $x++) {
    $t = [string]$x
    if ($x % 10 -eq 0) { $ruler1 += $t; $ruler2 += ('-' * (10 - $t.Length)) }
    else { $ruler1 += ' '; $ruler2 += ' ' }
}
[void]$sb.AppendLine('x: ' + $ruler1)
[void]$sb.AppendLine('   ' + $ruler2)
for ($y = 295; $y -le 470; $y++) {
    $line = ''
    for ($x = 0; $x -le 649; $x++) { $line += Code ($bmp.GetPixel($x, $y)) }
    [void]$sb.AppendLine(('{0,3}: ' -f $y) + $line)
}
$file = Join-Path $outDir 'bottom-ruled.txt'
[System.IO.File]::WriteAllText($file, $sb.ToString())
"wrote $file"
$bmp.Dispose()
