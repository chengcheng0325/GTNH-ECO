Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures\blocks'
$files = @(
  'ecal_casing.png',
  'ecal_parallel_proc.png',
  'ecal_thread_core.png',
  'ecal_cell_drive.png',
  'ecal_me_channel.png',
  'ecal_transmitter_bus.png',
  'ecal_controller_front.png',
  'ecal_controller_front_off.png',
  'ecal_controller_side.png'
)
function Color-Code([System.Drawing.Color]$c) {
    $r = $c.R; $g = $c.G; $b = $c.B
    if ($c.A -lt 128) { return '.' }
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
foreach ($f in $files) {
    $path = Join-Path $base $f
    if (-not (Test-Path $path)) { "=== $f : MISSING ==="; continue }
    $bmp = [System.Drawing.Bitmap]::new((Resolve-Path $path).Path)
    "`n=== $f  $($bmp.Width)x$($bmp.Height) ==="
    for ($gy = 0; $gy -lt 16; $gy++) {
        $line = ''
        for ($gx = 0; $gx -lt 16; $gx++) {
            $c = $bmp.GetPixel($gx, $gy)
            $line += Color-Code $c
        }
        $line
    }
    $bmp.Dispose()
}
