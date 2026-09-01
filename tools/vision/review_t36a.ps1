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
$files = @(
  'blocks\ecal_parallel_drive.png','blocks\ecal_parallel_drive_front.png','blocks\ecal_parallel_drive_front_filled.png',
  'blocks\ecal_thread_drive.png','blocks\ecal_thread_drive_front.png','blocks\ecal_thread_drive_front_filled.png',
  'items\ecal_parallel_core_1.png','items\ecal_parallel_core_65536.png',
  'items\ecal_thread_core_1.png','items\ecal_thread_core_hyper_4.png'
)
foreach($f in $files){
  $b=[System.Drawing.Bitmap]::new((Resolve-Path (Join-Path $base $f)).Path)
  "`n=== $f ==="
  for($y=0;$y -lt 16;$y++){ $l=''; for($x=0;$x -lt 16;$x++){ $l+=Code ($b.GetPixel($x,$y)) }; $l }
  $b.Dispose()
}
