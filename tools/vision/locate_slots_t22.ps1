Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
$p2 = (Resolve-Path 'tools\ecal-textures-preview.png').Path
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
$files = @(
  'blocks\ecal_casing.png','blocks\ecal_parallel_proc.png','blocks\ecal_thread_core.png',
  'blocks\ecal_cell_drive.png','blocks\ecal_cell_drive_front.png','blocks\ecal_cell_drive_front_filled.png',
  'blocks\ecal_me_channel.png','blocks\ecal_transmitter_bus.png','blocks\ecal_controller_front.png',
  'blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png','items\ecal_cell_c4.png',
  'blocks\ecal_parallel_proc_c6.png','blocks\ecal_parallel_proc_c9.png',
  'blocks\ecal_thread_core_c6.png','blocks\ecal_thread_core_c9.png',
  'blocks\ecal_thread_core_hyper.png','blocks\ecal_thread_core_hyper_c6.png','blocks\ecal_thread_core_hyper_c9.png',
  'blocks\ecal_controller_c6_front.png','blocks\ecal_controller_c6_front_off.png','blocks\ecal_controller_c6_side.png',
  'blocks\ecal_controller_c9_front.png','blocks\ecal_controller_c9_front_off.png','blocks\ecal_controller_c9_side.png',
  'items\ecal_cell_c6.png','items\ecal_cell_c9.png'
)
# candidate origins (7x slots): rows y in {5,155,305,455,612} +-3, cols x in {5,145,285,425,565,705} +-3
$yC = @(2,3,4,5,6,7,8,152,153,154,155,156,157,158,302,303,304,305,306,307,308,452,453,454,455,456,457,458,609,610,611,612,613,614,615)
$xC = @(2,3,4,5,6,7,8,142,143,144,145,146,147,148,282,283,284,285,286,287,288,422,423,424,425,426,427,428,562,563,564,565,566,567,568,702,703,704,705,706,707,708)
$gridCache = @{}
foreach ($f in $files) {
    $fb = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $f)).Path)
    $fg = @()
    for ($yy = 0; $yy -lt 16; $yy++) { $l=''; for ($xx = 0; $xx -lt 16; $xx++) { $l += Code ($fb.GetPixel($xx,$yy)) }; $fg += $l }
    $fb.Dispose()
    $gridCache[$f] = $fg
    $best = @{sim=-1; x0=0; y0=0}
    foreach ($y0 in $yC) { foreach ($x0 in $xC) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($x0 + $gx*7) ($y0 + $gy*7)) -eq $fg[$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0} }
    } }
    "{0,-42} sim={1,4}%  ({2},{3})" -f $f, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0
}
