Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
$p2 = (Resolve-Path 'tools\ecal-cells-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p2)
$W = $bmp.Width; $H = $bmp.Height
$rect = New-Object System.Drawing.Rectangle 0, 0, $W, $H
$data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$bytes = New-Object byte[] ($stride * $H)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
$bmp.UnlockBits($data)
function Code([System.Drawing.Color]$c) {
    if ($c.A -lt 128) { return '.' }
    $r=$c.R;$g=$c.G;$b=$c.B; $avg=($r+$g+$b)/3
    if($avg -lt 35){return 'K'}
    if($b -gt $r+25 -and $b -gt 110){if($g -gt $r+10){return 'C'}else{return 'B'}}
    if($r -gt $b+40 -and $r -gt 110){if($g -gt $b+10){return 'Y'}else{return 'O'}}
    if($g -gt $r+20 -and $g -gt $b+20){return 'N'}
    if($avg -lt 70){return 'D'}; if($avg -lt 110){return 'M'}; if($avg -lt 160){return 'G'}; if($avg -lt 205){return 'L'}; return 'W'
}
function CodeAt([int]$x, [int]$y) {
    if ($x -lt 0 -or $x -ge $W -or $y -lt 0 -or $y -ge $H) { return 'X' }
    $i = $y * $stride + $x * 4
    $b = $bytes[$i]; $g = $bytes[$i+1]; $r = $bytes[$i+2]; $a = $bytes[$i+3]
    if ($a -lt 128) { return '.' }
    $avg = ($r + $g + $b) / 3
    if ($avg -lt 35) { return 'K' }
    if ($b -gt $r + 25 -and $b -gt 110) { if ($g -gt $r + 10) { return 'C' } else { return 'B' } }
    if ($r -gt $b + 40 -and $r -gt 110) { if ($g -gt $b + 10) { return 'Y' } else { return 'O' } }
    if ($g -gt $r + 20 -and $g -gt $b + 20) { return 'N' }
    if ($avg -lt 70) { return 'D' }; if ($avg -lt 110) { return 'M' }; if ($avg -lt 160) { return 'G' }; if ($avg -lt 205) { return 'L' }
    return 'W'
}
$names = @('ecal_cell_256k','ecal_cell_1024k','ecal_cell_4096k','ecal_cell_16m','ecal_cell_64m','ecal_cell_256m','ecal_cell_1024m','ecal_cell_4096m','ecal_cell_16384m')
$yC = @(9,10,11,12,13,14,15,149,150,151,152,153,154,155,289,290,291,292,293,294,295)
$xC = @(9,10,11,12,13,14,15,149,150,151,152,153,154,155,289,290,291,292,293,294,295)
foreach ($n in $names) {
    $fb = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base ('items\' + $n + '.png'))).Path)
    $fg = @()
    for ($yy = 0; $yy -lt 16; $yy++) { $l=''; for ($xx = 0; $xx -lt 16; $xx++) { $l += Code ($fb.GetPixel($xx,$yy)) }; $fg += $l }
    $fb.Dispose()
    $best = @{sim=-1; x0=0; y0=0}
    foreach ($y0 in $yC) { foreach ($x0 in $xC) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($x0 + $gx*7) ($y0 + $gy*7)) -eq $fg[$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0} }
    } }
    "{0,-20} sim={1,4}%  ({2},{3})" -f $n, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0
}
# label strips presence between rows
$bmp2 = [System.Drawing.Bitmap]::new($p2)
$bands = @(@(125,135), @(265,275), @(405,415))
foreach($bnd in $bands){
  $n=0
  for($y=$bnd[0]; $y -le $bnd[1]; $y++){ for($x=0;$x -lt $W;$x++){
    $c=$bmp2.GetPixel($x,$y); if($c.A -gt 128){ $r=$c.R;$g=$c.G;$bl=$c.B
      if($bl -gt $r+25 -and $bl -gt 110){$n++} elseif($r -gt $bl+40 -and $r -gt 110){$n++} } } }
  "label band y=$($bnd[0])-$($bnd[1]): colored=$n"
}
$bmp2.Dispose()