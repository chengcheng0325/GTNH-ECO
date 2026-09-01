Add-Type -AssemblyName System.Drawing
$base = '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks'
$files = @(
  'compute_units\compute_cluster_structure_shell.png',
  'compute_units\io.png',
  'ec_modular_synthetic_memory\bloom\ec_synthetic_storage_control_host.png',
  'ec_modular_synthetic_memory\bloom\ec_synthetic_storage_control_host_off.png',
  'ec_modular_synthetic_memory\bloom\l6_controller.png',
  'ec_modular_synthetic_memory\bloom\module_parallel_unit\on\l6_module_parallel_unit.png',
  'storage_array_mebus.png',
  'data_bus.png',
  'carbon_fiber_chassis.png',
  'compute_units\led\blue_run.png'
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
    $w = $bmp.Width; $h = $bmp.Height
    "`n=== $f  ${w}x${h} ==="
    # sample up to 16x16 from top-left (or center for non-16 textures)
    $sx0 = 0; $sy0 = 0
    if ($w -gt 16) { $sx0 = [int](($w - 16) / 2) }
    if ($h -gt 16) { $sy0 = [int](($h - 16) / 2) }
    for ($gy = 0; $gy -lt 16; $gy++) {
        $line = ''
        for ($gx = 0; $gx -lt 16; $gx++) {
            $px = $sx0 + $gx; $py = $sy0 + $gy
            if ($px -lt $w -and $py -lt $h) { $line += Color-Code ($bmp.GetPixel($px, $py)) }
            else { $line += '.' }
        }
        $line
    }
    $bmp.Dispose()
}
