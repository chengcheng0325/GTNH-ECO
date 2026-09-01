Add-Type -AssemblyName System.Drawing
$dir = 'src\main\resources\assets\ecoaegtnh\textures\items'
function IsCyan($r,$g,$b){ return (([math]::Abs($r-77)+[math]::Abs($g-191)+[math]::Abs($b-212) -le 45) -or ([math]::Abs($r-128)+[math]::Abs($g-225)+[math]::Abs($b-255) -le 45) -or ([math]::Abs($r-204)+[math]::Abs($g-253)+[math]::Abs($b-255) -le 45)) }
function IsGold($r,$g,$b){ return (([math]::Abs($r-255)+[math]::Abs($g-147)+[math]::Abs($b-0) -le 45) -or ([math]::Abs($r-255)+[math]::Abs($g-198)+[math]::Abs($b-0) -le 45) -or ([math]::Abs($r-255)+[math]::Abs($g-224)+[math]::Abs($b-174) -le 45)) }
function IsPurple($r,$g,$b){ return (([math]::Abs($r-136)+[math]::Abs($g-21)+[math]::Abs($b-216) -le 45) -or ([math]::Abs($r-176)+[math]::Abs($g-111)+[math]::Abs($b-221) -le 45) -or ([math]::Abs($r-221)+[math]::Abs($g-168)+[math]::Abs($b-245) -le 45)) }

$parCores = @('ecal_parallel_core_1','ecal_parallel_core_4','ecal_parallel_core_16','ecal_parallel_core_64','ecal_parallel_core_256','ecal_parallel_core_1024','ecal_parallel_core_4096','ecal_parallel_core_16384','ecal_parallel_core_65536')
"== parallel cores: lit-cell area C px (window cols 4-10 rows 5-11), right bar segs (col13), bottom ticks (row13 cols 4/7/10) =="
foreach($n in $parCores){
  $b=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $dir ($n+'.png'))).Path)
  $lit=0; $barSegs=0; $ticks=0
  for($y=5;$y -le 11;$y++){ for($x=4;$x -le 10;$x++){ $c=$b.GetPixel($x,$y); if(IsCyan $c.R $c.G $c.B){$lit++} } }
  # right bar col 13: segments at row pairs (6,7),(9,10),(12,13) — any cyan in the pair
  foreach($pair in @(@(6,7),@(9,10),@(12,13))){ $ok=$false; foreach($yy in $pair){ $c=$b.GetPixel(13,$yy); if(IsCyan $c.R $c.G $c.B){$ok=$true} }; if($ok){$barSegs++} }
  foreach($xx in 4,7,10){ $c=$b.GetPixel($xx,13); if(IsCyan $c.R $c.G $c.B){$ticks++} }
  $b.Dispose()
  "{0,-24} lit={1,2} (cells~{2})  barSegs={3}  ticks={4}" -f $n, $lit, [math]::Round($lit/4,1), $barSegs, $ticks
}
"`n== thread cores: right bar + ticks + cyan/purple =="
foreach($n in @('ecal_thread_core_1','ecal_thread_core_4','ecal_thread_core_16')){
  $b=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $dir ($n+'.png'))).Path)
  $barSegs=0;$ticks=0;$cyan=0
  foreach($pair in @(@(6,7),@(9,10),@(12,13))){ $ok=$false; foreach($yy in $pair){ $c=$b.GetPixel(13,$yy); if(IsCyan $c.R $c.G $c.B){$ok=$true} }; if($ok){$barSegs++} }
  foreach($xx in 4,7,10){ $c=$b.GetPixel($xx,13); if(IsCyan $c.R $c.G $c.B){$ticks++} }
  for($y=4;$y -le 13;$y++){ for($x=3;$x -le 13;$x++){ $c=$b.GetPixel($x,$y); if(IsCyan $c.R $c.G $c.B){$cyan++} } }
  $b.Dispose()
  "{0,-24} barSegs={1}  ticks={2}  cyan={3}" -f $n, $barSegs, $ticks, $cyan
}
"`n== hyper cores: purple% + cyan count =="
foreach($n in @('ecal_thread_core_hyper_2','ecal_thread_core_hyper_4','ecal_thread_core_hyper_8')){
  $b=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $dir ($n+'.png'))).Path)
  $purple=0;$cyan=0;$gold=0;$tot=0
  for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){ $c=$b.GetPixel($x,$y)
    if($c.A -gt 128){ $tot++
      if(IsPurple $c.R $c.G $c.B){$purple++}
      if(IsCyan $c.R $c.G $c.B){$cyan++}
      if(IsGold $c.R $c.G $c.B){$gold++}
    } } }
  $b.Dispose()
  "{0,-24} purple={1}% ({2}px)  cyan={3}  gold={4}%" -f $n, [math]::Round(100.0*$purple/196,1), $purple, $cyan, [math]::Round(100.0*$gold/196,1)
}
# drive blocks light/dark
"`n== drive blocks light/dark =="
foreach($n in 'ecal_parallel_drive','ecal_parallel_drive_front','ecal_parallel_drive_front_filled','ecal_thread_drive','ecal_thread_drive_front','ecal_thread_drive_front_filled'){
  $b=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path 'src\main\resources\assets\ecoaegtnh\textures\blocks' ($n+'.png'))).Path)
  $light=0;$dark=0;$cyan=0;$tot=0
  for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){ $c=$b.GetPixel($x,$y); $avg=($c.R+$c.G+$c.B)/3
    if($avg -ge 150){$light++} elseif($avg -lt 80){$dark++}
    if(IsCyan $c.R $c.G $c.B){$cyan++}
    $tot++ } }
  $b.Dispose()
  "{0,-36} light={1}% dark={2}% cyan={3}" -f $n, [math]::Round(100.0*$light/$tot,1), [math]::Round(100.0*$dark/$tot,1), [math]::Round(100.0*$cyan/$tot,1)
}
