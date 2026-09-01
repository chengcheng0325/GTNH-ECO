Add-Type -AssemblyName System.Drawing

function Code([System.Drawing.Color]$c) {
    if ($c.A -lt 128) { return '.' }
    $r = $c.R; $g = $c.G; $b = $c.B
    $avg = ($r + $g + $b) / 3
    if ($avg -lt 35) { return 'K' }
    if ($b -gt $r + 25 -and $b -gt 110) { if ($g -gt $r + 10) { return 'C' } else { return 'B' } }
    if ($r -gt $b + 40 -and $r -gt 110) { if ($g -gt $b + 10) { return 'Y' } else { return 'O' } }
    if ($g -gt $r + 20 -and $g -gt $b + 20) { return 'N' }
    if ($avg -lt 70) { return 'D' }
    if ($avg -lt 110) { return 'M' }
    if ($avg -lt 160) { return 'G' }
    if ($avg -lt 205) { return 'L' }
    return 'W'
}

$files = @(
  'blocks\ecal_casing.png','blocks\ecal_parallel_proc.png','blocks\ecal_thread_core.png',
  'blocks\ecal_cell_drive.png','blocks\ecal_me_channel.png','blocks\ecal_transmitter_bus.png',
  'blocks\ecal_controller_front.png','blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png',
  'items\ecal_cell_c4.png'
)
$base = 'src\main\resources\assets\ecoaegtnh\textures'
foreach ($f in $files) {
    $path = Join-Path $base $f
    $bmp = [System.Drawing.Bitmap]::new((Resolve-Path $path).Path)
    "`n=== $f  $($bmp.Width)x$($bmp.Height) ==="
    for ($gy = 0; $gy -lt 16; $gy++) {
        $line = ''
        for ($gx = 0; $gx -lt 16; $gx++) { $line += Code ($bmp.GetPixel($gx, $gy)) }
        $line
    }
    $bmp.Dispose()
}
