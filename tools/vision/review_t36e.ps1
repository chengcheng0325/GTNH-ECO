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
$files = @(
  'items\ecal_parallel_core_1.png','items\ecal_parallel_core_4.png','items\ecal_parallel_core_16.png',
  'items\ecal_parallel_core_64.png','items\ecal_parallel_core_256.png','items\ecal_parallel_core_1024.png',
  'items\ecal_parallel_core_4096.png','items\ecal_parallel_core_16384.png','items\ecal_parallel_core_65536.png',
  'items\ecal_thread_core_1.png','items\ecal_thread_core_4.png','items\ecal_thread_core_16.png',
  'items\ecal_thread_core_hyper_2.png','items\ecal_thread_core_hyper_4.png','items\ecal_thread_core_hyper_8.png'
)
$yC = @(153,154,155,156,157,158,159,160,303,304,305,306,307,308,309,310,453,454,455,456,457,458,459,460)
$xC = @(3,4,5,6,7,8,143,144,145,146,147,148,283,284,285,286,287,288,423,424,425,426,427,428,563,564,565,566,567,568,703,704,705,706,707,708)
foreach ($f in $files) {
    $fb = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $f)).Path)
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
    "{0,-42} sim={1,4}%  ({2},{3})" -f $f, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0
}
