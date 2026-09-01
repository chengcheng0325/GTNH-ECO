Add-Type -AssemblyName System.Drawing
$p2 = (Resolve-Path 'tools\ecal-textures-preview-v2.png').Path
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

$base = 'src\main\resources\assets\ecoaegtnh\textures'
$slotFiles = @(
  'blocks\ecal_me_channel.png','blocks\ecal_transmitter_bus.png','blocks\ecal_controller_front.png',
  'blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png','items\ecal_cell_c4.png'
)
# expected origins: row2 (y=165) cols 5,165,325,485 ; row3 (y=325) cols 5,165
$origins = @(165, 325, 485, 5, 165) # me_channel(165,5?) wait -- order: me_channel=row2col1 => x=5 y=165
# rebuild properly:
$expect = @(
  @{f='blocks\ecal_me_channel.png'; x=5;   y=165},
  @{f='blocks\ecal_transmitter_bus.png'; x=165; y=165},
  @{f='blocks\ecal_controller_front.png'; x=325; y=165},
  @{f='blocks\ecal_controller_front_off.png'; x=485; y=165},
  @{f='blocks\ecal_controller_side.png'; x=5;   y=325},
  @{f='items\ecal_cell_c4.png'; x=165; y=325}
)
foreach ($e in $expect) {
    $fb = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $e.f)).Path)
    $fg = @()
    for ($yy = 0; $yy -lt 16; $yy++) { $l=''; for ($xx = 0; $xx -lt 16; $xx++) { $l += Code ($fb.GetPixel($xx,$yy)) }; $fg += $l }
    $fb.Dispose()
    # search y0 in +-4 around expected y, x0 in +-4 around expected x, ox/oy = 0 only
    $best = @{sim=-1; x0=0; y0=0}
    foreach ($y0 in (($e.y-4)..($e.y+4))) { foreach ($x0 in (($e.x-4)..($e.x+4))) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($x0 + $gx*8) ($y0 + $gy*8)) -eq $fg[$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0} }
    } }
    "{0,-32} sim={1,3}% x0={2} y0={3}" -f $e.f, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0
}
