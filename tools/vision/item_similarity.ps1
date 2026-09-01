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

$item = Get-Grid 'src\main\resources\assets\ecoaegtnh\textures\items\ecal_cell_c4.png'
$itemDir = 'src\main\resources\assets\ecoaegtnh\textures\items'
$estorage = Get-ChildItem (Join-Path (Get-Location) $itemDir) -Filter 'estorage_cell_item_*.png' | ForEach-Object { $_.FullName }
"item vs E-Storage cell items (same-pixel %):"
$mx = 0; $mxN = ''
foreach ($f in $estorage) {
    $g = Get-Grid $f
    $sim = Compare-Grids $item $g
    if ($sim -gt $mx) { $mx = $sim; $mxN = (Split-Path $f -Leaf) }
    "  {0,-32} {1,6}%" -f (Split-Path $f -Leaf), $sim
}
"  MAX vs E-Storage items: $mx% ($mxN)"

# vs our own 9 blocks + vs original repo textures
$blockDir = 'src\main\resources\assets\ecoaegtnh\textures\blocks'
$ourBlocks = Get-ChildItem (Join-Path (Get-Location) $blockDir) -Filter 'ecal_*.png' | ForEach-Object { $_.FullName }
"`nitem vs our own block textures:"
foreach ($f in $ourBlocks) {
    $g = Get-Grid $f
    $sim = Compare-Grids $item $g
    "  {0,-34} {1,6}%" -f (Split-Path $f -Leaf), $sim
}

$origBase = '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures'
$origs = @(
  'blocks\data_bus.png',
  'blocks\storage_array_mebus.png',
  'blocks\ec_modular_synthetic_memory\bloom\module_parallel_unit\on\l6_module_parallel_unit.png',
  'blocks\ec_modular_synthetic_memory\bloom\l6_controller.png',
  'blocks\ec_modular_synthetic_memory\bloom\ec_line.png',
  'blocks\compute_units\led\blue_run.png',
  'blocks\carbon_fiber_chassis.png'
)
"`nitem vs original 1.12.2 repo textures:"
foreach ($f in $origs) {
    $path = Join-Path $origBase $f
    if (Test-Path $path) {
        $g = Get-Grid $path
        $sim = Compare-Grids $item $g
        "  {0,-60} {1,6}%" -f $f, $sim
    }
}
