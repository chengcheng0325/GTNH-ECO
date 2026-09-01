Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
$p2 = (Resolve-Path 'tools\ecal-cores-drives-preview.png').Path
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
"preview: ${W}x${H}  ts=$(Get-Item 'tools\ecal-cores-drives-preview.png' | Select-Object -ExpandProperty LastWriteTime)"
# detect layout: col runs at several rows
function ColsAt([int]$yy){
  $runs=New-Object System.Collections.ArrayList; $in=$false;$s=0
  for($xx=0;$xx -lt $W;$xx++){ $c=$bmp.GetPixel($xx,$yy); $avg=($c.R+$c.G+$c.B)/3
    $isP=$avg -ge 110
    if($isP -and -not $in){$in=$true;$s=$xx} elseif(-not $isP -and $in){$in=$false;[void]$runs.Add(@($s,($xx-1)))}
  }
  if($in){[void]$runs.Add(@($s,($W-1)))}
  return $runs
}
"cols y=30: " + ((ColsAt 30 | ForEach-Object { "$($_[0])-$($_[1])" }) -join ', ')
$files = @(
  'blocks\ecal_parallel_drive.png','blocks\ecal_parallel_drive_front.png','blocks\ecal_parallel_drive_front_filled.png',
  'blocks\ecal_thread_drive.png','blocks\ecal_thread_drive_front.png','blocks\ecal_thread_drive_front_filled.png',
  'items\ecal_parallel_core_1.png','items\ecal_parallel_core_4.png','items\ecal_parallel_core_16.png',
  'items\ecal_parallel_core_64.png','items\ecal_parallel_core_256.png','items\ecal_parallel_core_1024.png',
  'items\ecal_parallel_core_4096.png','items\ecal_parallel_core_16384.png','items\ecal_parallel_core_65536.png',
  'items\ecal_thread_core_1.png','items\ecal_thread_core_4.png','items\ecal_thread_core_16.png',
  'items\ecal_thread_core_hyper_2.png','items\ecal_thread_core_hyper_4.png','items\ecal_thread_core_hyper_8.png'
)
# candidate origins: assume 7x slots, 6 cols; rows 4; find from col runs
$yC = @(); for($i=0;$i -lt 40;$i++){ $yC += (5 + $i*37) }  # generous: rows every ~37px? will refine below
# better: detect bands
$bands=New-Object System.Collections.ArrayList; $in=$false;$s=0
for($yy=0;$yy -lt $H;$yy++){
  $n=0; for($xx=0;$xx -lt $W;$xx+=2){ $c=$bmp.GetPixel($xx,$yy); if(($c.R+$c.G+$c.B)/3 -ge 110){$n++} }
  $light=$n -gt 15
  if($light -and -not $in){$in=$true;$s=$yy} elseif(-not $light -and $in){$in=$false;[void]$bands.Add(@($s,($yy-1)))}
}
if($in){[void]$bands.Add(@($s,($H-1)))}
"bands: " + (($bands | ForEach-Object { "$($_[0])-$($_[1])" }) -join ', ')
# take band starts as y candidates, col starts from y=30 run starts
$colStarts = @()
$r30 = ColsAt 30
foreach($r in $r30){ $colStarts += $r[0] }
$yStarts = @()
foreach($bnd in $bands){ if(($bnd[1]-$bnd[0]+1) -gt 50){ $yStarts += $bnd[0] } }
"yStarts: $($yStarts -join ',')  colStarts: $($colStarts -join ',')"
$yC2 = @(); foreach($ys in $yStarts){ for($d=-8; $d -le 4; $d++){ $yC2 += ($ys+$d) } }
$xC2 = @(); foreach($cs in $colStarts){ for($d=0;$d -le 6;$d++){ $xC2 += ($cs+$d) } }
foreach ($f in $files) {
    $fb = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $f)).Path)
    $fg = @()
    for ($yy = 0; $yy -lt 16; $yy++) { $l=''; for ($xx = 0; $xx -lt 16; $xx++) { $l += Code ($fb.GetPixel($xx,$yy)) }; $fg += $l }
    $fb.Dispose()
    $best = @{sim=-1; x0=0; y0=0}
    foreach ($y0 in $yC2) { foreach ($x0 in $xC2) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($x0 + $gx*7) ($y0 + $gy*7)) -eq $fg[$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0} }
    } }
    "{0,-42} sim={1,4}%  ({2},{3})" -f $f, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0
}

