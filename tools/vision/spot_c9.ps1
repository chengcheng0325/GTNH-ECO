Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
# 1) spot check hex of "C6gold-tagged" pixels in C9 files + C9 variants structure
$spot = @(
  'blocks\ecal_thread_core_c9.png',
  'blocks\ecal_thread_core_hyper_c9.png',
  'items\ecal_cell_c9.png',
  'blocks\ecal_controller_c9_side.png',
  'blocks\ecal_thread_core_c6.png',
  'blocks\ecal_controller_c6_front.png',
  'blocks\ecal_cell_c6.png'
)
foreach($f in $spot){
  $b=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $f)).Path)
  $goldish = New-Object System.Collections.ArrayList; $purplish = New-Object System.Collections.ArrayList
  for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    $c=$b.GetPixel($x,$y); $r=$c.R;$g=$c.G;$bl=$c.B
    if($r -gt 200 -and $g -gt 120 -and $g -lt 240 -and $bl -lt 140){ [void]$goldish.Add("({0},{1})#{2:X2}{3:X2}{4:X2}" -f $x,$y,$r,$g,$bl) }
    if($bl -gt 150 -and $r -lt 200 -and $bl -gt $r+40){ [void]$purplish.Add("({0},{1})#{2:X2}{3:X2}{4:X2}" -f $x,$y,$r,$g,$bl) }
  } }
  $b.Dispose()
  "`n$f : warm(200+,120-240,<140) n=$($goldish.Count)  violet(b>150,r<200,b>r+40) n=$($purplish.Count)"
  $shown=0; foreach($p in $goldish){ if($shown -lt 10){ "  warm $p" }; $shown++ }
  $shown=0; foreach($p in $purplish){ if($shown -lt 8){ "  viol $p" }; $shown++ }
}
