Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
function Code([System.Drawing.Color]$c) {
    if ($c.A -lt 128) { return '.' }
    $r=$c.R;$g=$c.G;$b=$c.B; $avg=($r+$g+$b)/3
    if($avg -lt 35){return 'K'}
    if($b -gt $r+25 -and $b -gt 110){if($g -gt $r+10){return 'C'}else{return 'B'}}
    if($r -gt $b+40 -and $r -gt 110){if($g -gt $b+10){return 'Y'}else{return 'O'}}
    if($g -gt $r+20 -and $g -gt $b+20){return 'N'}
    if($avg -lt 70){return 'D'}; if($avg -lt 110){return 'M'}; if($avg -lt 160){return 'G'}; if($avg -lt 205){return 'L'}; return 'W'
}
# --- 1) preview slot consistency ---
$bmp=[System.Drawing.Bitmap]::new((Resolve-Path 'tools\ecal-textures-preview.png').Path)
"preview: $($bmp.Width)x$($bmp.Height)  ($(Get-Item 'tools\ecal-textures-preview.png' | Select-Object -ExpandProperty LastWriteTime))"
$slots=@(
  @{f='blocks\ecal_casing.png';x=5;y=5},@{f='blocks\ecal_parallel_proc.png';x=165;y=5},
  @{f='blocks\ecal_thread_core.png';x=325;y=5},@{f='blocks\ecal_cell_drive.png';x=485;y=5},
  @{f='blocks\ecal_me_channel.png';x=5;y=165},@{f='blocks\ecal_transmitter_bus.png';x=165;y=165},
  @{f='blocks\ecal_controller_front.png';x=325;y=165},@{f='blocks\ecal_controller_front_off.png';x=485;y=165},
  @{f='blocks\ecal_controller_side.png';x=5;y=325},@{f='items\ecal_cell_c4.png';x=165;y=325},
  @{f='blocks\ecal_cell_drive_front.png';x=325;y=325}
)
"slot consistency:"
foreach($s in $slots){
  $fb=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $s.f)).Path)
  $diff=0; $opaque=0
  for($gy=0;$gy -lt 16;$gy++){ for($gx=0;$gx -lt 16;$gx++){
    $cf=Code ($fb.GetPixel($gx,$gy)); $cp=Code ($bmp.GetPixel($s.x+$gx*8,$s.y+$gy*8))
    if($cf -ne '.'){ $opaque++; if($cf -ne $cp){ $diff++ } }
  } }
  $fb.Dispose()
  "{0,-34} opaque={1} diff={2}" -f $s.f, $opaque, $diff
}
$bmp.Dispose()
# --- 2) originality check for changed/new textures ---
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
$ours = @('ecal_controller_front.png','ecal_controller_front_off.png','ecal_controller_side.png','ecal_cell_drive_front.png')
$blockDir = 'src\main\resources\assets\ecoaegtnh\textures\blocks'
$origBase = '.research\NovaEngineering-ECOAEExtension-main\src\main\resources\assets\ecoaeextension\textures\blocks'
$origs = @('data_bus.png','storage_array_mebus.png','carbon_fiber_chassis.png','ec_modular_synthetic_memory\bloom\l6_controller.png','ec_modular_synthetic_memory\bloom\module_parallel_unit\on\l6_module_parallel_unit.png','ec_modular_synthetic_memory\bloom\ec_line.png','ec_modular_synthetic_memory\bloom\l9_controller.png')
$og = @{}
foreach($o in $origs){ $path = Join-Path $origBase $o; if(Test-Path $path){ $og[(Split-Path $o -Leaf)] = Get-Grid $path } }
"`nsimilarity vs originals (%):"
foreach($f in $ours){
  $g = Get-Grid (Join-Path $blockDir $f)
  $row = "{0,-30}" -f $f
  foreach($k in $og.Keys){ $row += "  {0}={1}%" -f $k.Replace('.png',''), (Compare-Grids $g $og[$k]) }
  $row
}
