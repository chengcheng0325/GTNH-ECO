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
# locate the 12 slots: try all grid origins
$yCands = @(3,4,5,6,7,163,164,165,166,167,323,324,325,326,327)
$xCands = @(3,4,5,6,7,163,164,165,166,167,323,324,325,326,327,483,484,485,486,487)
$files = @(
  'blocks\ecal_casing.png','blocks\ecal_parallel_proc.png','blocks\ecal_thread_core.png',
  'blocks\ecal_cell_drive.png','blocks\ecal_cell_drive_front.png','blocks\ecal_cell_drive_front_filled.png',
  'blocks\ecal_me_channel.png','blocks\ecal_transmitter_bus.png','blocks\ecal_controller_front.png',
  'blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png','items\ecal_cell_c4.png'
)
"slot location (12 files):"
$found = @{}
foreach ($f in $files) {
    $fb = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $f)).Path)
    $fg = @()
    for ($yy = 0; $yy -lt 16; $yy++) { $l=''; for ($xx = 0; $xx -lt 16; $xx++) { $l += Code ($fb.GetPixel($xx,$yy)) }; $fg += $l }
    $fb.Dispose()
    $best = @{sim=-1; x0=0; y0=0}
    foreach ($y0 in $yCands) { foreach ($x0 in $xCands) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            if ((CodeAt ($x0 + $gx*8) ($y0 + $gy*8)) -eq $fg[$gy][$gx]) { $same++ }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0} }
    } }
    $found[$f] = $best
    "{0,-40} sim={1,4}%  ({2},{3})" -f $f, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0
}
$bmp.Dispose()

# originality: filled vs original repo + vs our E-Storage filled reference
function Get-Grid([string]$path) {
    $bmp = [System.Drawing.Bitmap]::new((Resolve-Path $path).Path)
    $grid = @()
    for ($y = 0; $y -lt 16; $y++) { $row = @(); for ($x = 0; $x -lt 16; $x++) { $c = $bmp.GetPixel($x, $y); $row += ,@($c.R, $c.G, $c.B, $c.A) }; $grid += ,$row }
    $bmp.Dispose(); return $grid
}
function Compare-Grids($g1, $g2) {
    $same = 0; $tot = 0
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $p1 = $g1[$y][$x]; $p2 = $g2[$y][$x]
        if ($p1[3] -lt 128 -or $p2[3] -lt 128) { continue }
        $tot++
        $d = [math]::Abs($p1[0]-$p2[0]) + [math]::Abs($p1[1]-$p2[1]) + [math]::Abs($p1[2]-$p2[2])
        if ($d -le 60) { $same++ }
    } }
    if ($tot -eq 0) { return 0 }
    return [math]::Round(100.0 * $same / $tot, 1)
}
$filled = Get-Grid 'src\main\resources\assets\ecoaegtnh\textures\blocks\ecal_cell_drive_front_filled.png'
"`nsimilarity of filled vs references:"
$refs = @(
  'src\main\resources\assets\ecoaegtnh\textures\blocks\storage_array_drives_front_filled.png',
  'src\main\resources\assets\ecoaegtnh\textures\blocks\storage_array_drives_front.png',
  'src\main\resources\assets\ecoaegtnh\textures\blocks\ecal_cell_drive_front.png',
  'src\main\resources\assets\ecoaegtnh\textures\blocks\ecal_cell_drive.png',
  '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks\data_bus.png',
  '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks\storage_array_mebus.png',
  '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks\ec_modular_synthetic_memory\bloom\l6_controller.png',
  '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks\ec_modular_synthetic_memory\bloom\module_parallel_unit\on\l6_module_parallel_unit.png',
  '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks\ec_modular_synthetic_memory\bloom\ec_line.png'
)
foreach ($r in $refs) {
    if (Test-Path $r) {
        $g = Get-Grid $r
        "{0,-70} {1}%" -f $r, (Compare-Grids $filled $g)
    }
}
