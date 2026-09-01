Add-Type -AssemblyName System.Drawing

function Get-Grid([string]$path) {
    $bmp = [System.Drawing.Bitmap]::new((Resolve-Path $path).Path)
    $grid = @()
    for ($y = 0; $y -lt 16; $y++) {
        $row = @()
        for ($x = 0; $x -lt 16; $x++) {
            $c = $bmp.GetPixel($x, $y)
            $row += ,@($c.R, $c.G, $c.B, $c.A)
        }
        $grid += ,$row
    }
    $bmp.Dispose()
    return $grid
}

# similarity: fraction of pixels where |dR|+|dG|+|dB| <= 60 (ignoring alpha<128)
function Compare-Grids($g1, $g2) {
    $same = 0; $tot = 0
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $p1 = $g1[$y][$x]; $p2 = $g2[$y][$x]
            if ($p1[3] -lt 128 -or $p2[3] -lt 128) { continue }
            $tot++
            $d = [math]::Abs($p1[0]-$p2[0]) + [math]::Abs($p1[1]-$p2[1]) + [math]::Abs($p1[2]-$p2[2])
            if ($d -le 60) { $same++ }
        }
    }
    if ($tot -eq 0) { return 0 }
    return [math]::Round(100.0 * $same / $tot, 1)
}

$ours = @(
  'ecal_casing.png','ecal_parallel_proc.png','ecal_thread_core.png','ecal_cell_drive.png',
  'ecal_me_channel.png','ecal_transmitter_bus.png','ecal_controller_front.png',
  'ecal_controller_front_off.png','ecal_controller_side.png'
)
$origBase = '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks'
$orig16 = @(
  'data_bus.png',
  'storage_array_mebus.png',
  'ec_modular_synthetic_memory\bloom\l6_controller.png',
  'carbon_fiber_chassis.png',
  'ec_modular_synthetic_memory\bloom\module_parallel_unit\on\l6_module_parallel_unit.png',
  'compute_units\led\off.png',
  'ec_modular_synthetic_memory\bloom\l9_controller.png',
  'ec_modular_synthetic_memory\bloom\ec_line.png'
)

$ourGrids = @{}
foreach ($f in $ours) {
    $ourGrids[$f] = Get-Grid (Join-Path 'src\main\resources\assets\ecoaegtnh\textures\blocks' $f)
}
$origGrids = @{}
foreach ($f in $orig16) {
    $path = Join-Path $origBase $f
    if (Test-Path $path) { $origGrids[$f] = Get-Grid $path }
}

"Similarity matrix (% same pixels, ours vs original 16x16):"
$header = ('{0,-32}' -f '') + ($origGrids.Keys | ForEach-Object { ('{0,-28}' -f ($_.Split('\')[-1] -replace '\.png$','')) }) -join ''
$header
foreach ($o in $ours) {
    $row = ('{0,-32}' -f $o)
    foreach ($og in $origGrids.Keys) {
        $row += ('{0,-28}' -f (Compare-Grids $ourGrids[$o] $origGrids[$og]))
    }
    $row
}

# palette check: dominant non-gray colors per actual texture
"`nPalette (top non-gray colors per texture):"
function Is-Grayish($r,$g,$b) {
    $mx = [math]::Max($r,[math]::Max($g,$b)); $mn = [math]::Min($r,[math]::Min($g,$b))
    return ($mx - $mn) -lt 25
}
foreach ($o in $ours) {
    $bmp = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path 'src\main\resources\assets\ecoaegtnh\textures\blocks' $o)).Path)
    $cols = @{}
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $c = $bmp.GetPixel($x, $y)
        if (-not (Is-Grayish $c.R $c.G $c.B)) {
            $key = "{0},{1},{2}" -f $c.R, $c.G, $c.B
            if ($cols.ContainsKey($key)) { $cols[$key]++ } else { $cols[$key] = 1 }
        }
    } }
    $bmp.Dispose()
    $top = ($cols.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 6 | ForEach-Object { "rgb($($_.Key))x$($_.Value)" }) -join ' '
    "$o : $top"
}
