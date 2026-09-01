Add-Type -AssemblyName System.Drawing
$base = 'src\main\resources\assets\ecoaegtnh\textures'
function Near([int]$r,[int]$g,[int]$b,[int]$tr,[int]$tg,[int]$tb){ return ([math]::Abs($r-$tr)+[math]::Abs($g-$tg)+[math]::Abs($b-$tb)) -le 40 }
function ClassifyAccent([System.Drawing.Color]$c){
    $r=$c.R;$g=$c.G;$b=$c.B
    # tier colors first
    if(Near $r $g $b 255 147 0){return 'C6gold'}; if(Near $r $g $b 255 198 0){return 'C6gold'}; if(Near $r $g $b 255 224 174){return 'C6gold'}
    if(Near $r $g $b 136 21 216){return 'C9purple'}; if(Near $r $g $b 176 111 221){return 'C9purple'}; if(Near $r $g $b 221 168 245){return 'C9purple'}
    if(Near $r $g $b 77 191 212){return 'famCyan'}; if(Near $r $g $b 128 225 255){return 'famCyan'}; if(Near $r $g $b 204 253 255){return 'famCyan'}
    if(Near $r $g $b 222 200 68){return 'famGold'}; if(Near $r $g $b 242 233 181){return 'famGold'}; if(Near $r $g $b 255 229 76){return 'famGold'}; if(Near $r $g $b 196 173 35){return 'famGold'}
    $avg=($r+$g+$b)/3
    if($avg -lt 60){return 'dark'}
    if($avg -ge 150){return 'light'}
    return 'mid'
}
$files = @(
  'blocks\ecal_controller_front.png','blocks\ecal_controller_front_off.png','blocks\ecal_controller_side.png',
  'blocks\ecal_parallel_proc_c6.png','blocks\ecal_parallel_proc_c9.png',
  'blocks\ecal_thread_core_c6.png','blocks\ecal_thread_core_c9.png',
  'blocks\ecal_thread_core_hyper.png','blocks\ecal_thread_core_hyper_c6.png','blocks\ecal_thread_core_hyper_c9.png',
  'blocks\ecal_controller_c6_front.png','blocks\ecal_controller_c6_front_off.png','blocks\ecal_controller_c6_side.png',
  'blocks\ecal_controller_c9_front.png','blocks\ecal_controller_c9_front_off.png','blocks\ecal_controller_c9_side.png',
  'items\ecal_cell_c6.png','items\ecal_cell_c9.png'
)
foreach($f in $files){
    $p = Join-Path $base $f
    if(-not (Test-Path $p)){ "=== $f MISSING ==="; continue }
    $b=[System.Drawing.Bitmap]::new((Resolve-Path $p).Path)
    $acc=@{}; $uniq=@{}; $tot=0
    for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
        $c=$b.GetPixel($x,$y)
        $cls=ClassifyAccent $c
        if($acc.ContainsKey($cls)){$acc[$cls]++}else{$acc[$cls]=1}
        $key="{0:X2}{1:X2}{2:X2}" -f $c.R,$c.G,$c.B
        if($uniq.ContainsKey($key)){$uniq[$key]++}else{$uniq[$key]=1}
        $tot++
    } }
    $b.Dispose()
    $pct = {}
    $parts = @()
    foreach($k in 'C6gold','C9purple','famCyan','famGold','dark','light','mid'){
        if($acc.ContainsKey($k)){ $parts += ("{0}={1}%" -f $k, [math]::Round(100.0*$acc[$k]/$tot,1)) }
    }
    "{0,-42} uniq={1,2}  {2}" -f $f, $uniq.Count, ($parts -join '  ')
}
