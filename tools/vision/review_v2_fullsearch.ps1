Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
$files = @(
  'blocks\ecal_casing.png','blocks\ecal_parallel_proc.png','blocks\ecal_thread_core.png',
  'blocks\ecal_cell_drive.png','blocks\ecal_me_channel.png','blocks\ecal_transmitter_bus.png',
  'blocks\ecal_controller_front.png','blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png',
  'items\ecal_cell_c4.png'
)
function Code([System.Drawing.Color]$c) {
    if ($c.A -lt 128) { return '.' }
    $r=$c.R;$g=$c.G;$b=$c.B; $avg=($r+$g+$b)/3
    if($avg -lt 35){return 'K'}
    if($b -gt $r+25 -and $b -gt 110){if($g -gt $r+10){return 'C'}else{return 'B'}}
    if($r -gt $b+40 -and $r -gt 110){if($g -gt $b+10){return 'Y'}else{return 'O'}}
    if($g -gt $r+20 -and $g -gt $b+20){return 'N'}
    if($avg -lt 70){return 'D'}; if($avg -lt 110){return 'M'}; if($avg -lt 160){return 'G'}; if($avg -lt 205){return 'L'}; return 'W'
}
foreach ($f in $files) {
    $path = Join-Path $base $f
    $b = [System.Drawing.Bitmap]::new((Resolve-Path $path).Path)
    $uniq = @{}
    $grid = @()
    for ($y = 0; $y -lt 16; $y++) { $l=''; for ($x = 0; $x -lt 16; $x++) {
        $c = $b.GetPixel($x,$y)
        $l += Code $c
        $key = "{0:X2}{1:X2}{2:X2}" -f $c.R,$c.G,$c.B
        if ($uniq.ContainsKey($key)) { $uniq[$key]++ } else { $uniq[$key] = 1 }
    } ; $grid += $l }
    $b.Dispose()
    $nUniq = $uniq.Count
    $top = ($uniq.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 5 | ForEach-Object { "#$($_.Key)x$($_.Value)" }) -join ' '
    "`n=== $f  uniq=$nUniq ==="
    $grid
    "  colors: $top"
}

# ---- preview v2 slot verification ----
"`n`n########## PREVIEW V2 ##########"
$p2 = (Resolve-Path 'tools\ecal-textures-preview-v2.png').Path
$bmp = [System.Drawing.Bitmap]::new($p2)
"preview-v2: $($bmp.Width)x$($bmp.Height)"
$W=$bmp.Width; $H=$bmp.Height
$rect = New-Object System.Drawing.Rectangle 0, 0, $W, $H
$data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$bytes = New-Object byte[] ($stride * $H)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
$bmp.UnlockBits($data)
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
# find slot grid: scan row 30 for panel runs to detect columns, row band 1 y0
$runs = New-Object System.Collections.ArrayList
$in=$false;$s=0
for($x=0;$x -lt $W;$x++){ $c=$bmp.GetPixel($x,30); $avg=($c.R+$c.G+$c.B)/3; $isP=($avg -ge 150 -and $avg -le 230)
  if($isP -and -not $in){$in=$true;$s=$x} elseif(-not $isP -and $in){$in=$false; [void]$runs.Add(@($s,($x-1))) } }
if($in){[void]$runs.Add(@($s,($W-1)))}
"col runs at y=30: " + (($runs | ForEach-Object { "$($_[0])-$($_[1])" }) -join ', ')

# slot files in expected order
$slotFiles = @(
  'blocks\ecal_casing.png','blocks\ecal_parallel_proc.png','blocks\ecal_thread_core.png',
  'blocks\ecal_cell_drive.png','blocks\ecal_me_channel.png','blocks\ecal_transmitter_bus.png',
  'blocks\ecal_controller_front.png','blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png',
  'items\ecal_cell_c4.png'
)
$grids = @{}
foreach ($sf in $slotFiles) {
    $b = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $sf)).Path)
    $g = @()
    for ($y = 0; $y -lt 16; $y++) { $l=''; for ($x = 0; $x -lt 16; $x++) { $l += Code ($b.GetPixel($x,$y)) }; $g += $l }
    $b.Dispose()
    $grids[$sf] = $g
}
# try slot rects: x=5/165/325/485, y=5/165/325(+326) with single-pixel offset search
foreach ($i in 0..9) {
    $sf = $slotFiles[$i]
    $col = [math]::Floor($i / 3); $rowi = $i % 3
    $best = @{sim=-1; x0=0; y0=0; ox=0; oy=0}
    foreach ($y0 in @(3,4,5,6,7,163,164,165,166,167,323,324,325,326,327)) { foreach ($x0 in @(3,4,5,6,7)) { foreach ($ox in 0..7) { foreach ($oy in 0..7) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($x0 + $gx*8 + $ox) ($y0 + $gy*8 + $oy)) -eq $grids[$sf][$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0; ox=$ox; oy=$oy} }
    } } } }
    # also try the 4 column x positions
    foreach ($cx0 in 5,165,325,485) { foreach ($y0 in 3,4,5,6,7) { foreach ($ox in 0..7) { foreach ($oy in 0..7) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($cx0 + $gx*8 + $ox) ($y0 + $gy*8 + $oy)) -eq $grids[$sf][$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$cx0; y0=$y0; ox=$ox; oy=$oy} }
    } } } }
    "{0,-32} best={1,3}% x0={2} y0={3} ox={4} oy={5}" -f $sf, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0, $best.ox, $best.oy
}

