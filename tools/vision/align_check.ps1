Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)

function Code([System.Drawing.Color]$c) {
    $r=$c.R;$g=$c.G;$b=$c.B; $avg=($r+$g+$b)/3
    if($avg -lt 35){return 'K'}
    if($b -gt $r+25 -and $b -gt 110){if($g -gt $r+10){return 'C'}else{return 'B'}}
    if($r -gt $b+40 -and $r -gt 110){if($g -gt $b+10){return 'Y'}else{return 'O'}}
    if($g -gt $r+20 -and $g -gt $b+20){return 'N'}
    if($avg -lt 70){return 'D'}; if($avg -lt 110){return 'M'}; if($avg -lt 160){return 'G'}; if($avg -lt 205){return 'L'}; return 'W'
}

function Get-FileGrid([string]$rel) {
    $b = [System.Drawing.Bitmap]::new((Resolve-Path (Join-Path 'src\main\resources\assets\ecoaegtnh\textures' $rel)).Path)
    $g = @()
    for ($y = 0; $y -lt 16; $y++) { $l=''; for ($x = 0; $x -lt 16; $x++) { $l += Code ($b.GetPixel($x,$y)) }; $g += $l }
    $b.Dispose()
    return $g
}

$slots = @(
  @{n='casing';        x=5;   y=5;   f='blocks\ecal_casing.png'},
  @{n='parallel_proc'; x=165; y=5;   f='blocks\ecal_parallel_proc.png'},
  @{n='thread_core';   x=325; y=5;   f='blocks\ecal_thread_core.png'},
  @{n='cell_drive';    x=485; y=5;   f='blocks\ecal_cell_drive.png'},
  @{n='me_channel';    x=5;   y=165; f='blocks\ecal_me_channel.png'},
  @{n='trans_bus';     x=165; y=165; f='blocks\ecal_transmitter_bus.png'},
  @{n='front';         x=325; y=165; f='blocks\ecal_controller_front.png'},
  @{n='front_off';     x=485; y=165; f='blocks\ecal_controller_front_off.png'},
  @{n='side';          x=5;   y=326; f='blocks\ecal_controller_side.png'},
  @{n='cell_c4_item';  x=165; y=326; f='items\ecal_cell_c4.png'}
)

foreach ($s in $slots) {
    $fg = Get-FileGrid $s.f
    $best = @{sim = -1; ox = 0; oy = 0; mode = ''}
    # mode A: 16 texels stride 8, sub-pixel offset ox/oy in 0..7
    foreach ($ox in 0..7) { foreach ($oy in 0..7) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            $px = $s.x + $gx*8 + $ox; $py = $s.y + $gy*8 + $oy
            if ($px -lt 650 -and $py -lt 490) {
                if ((Code ($bmp.GetPixel($px,$py))) -eq $fg[$gy][$gx]) { $same++ }
            }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; ox=$ox; oy=$oy; mode='A16'} }
    } }
    # mode B: 15 texels stride 8 offset 4 (inset 4px), center texel map
    foreach ($ox in 0..7) { foreach ($oy in 0..7) {
        $same = 0
        for ($gy = 0; $gy -lt 15; $gy++) { for ($gx = 0; $gx -lt 15; $gx++) {
            $px = $s.x + 4 + $gx*8 + $ox; $py = $s.y + 4 + $gy*8 + $oy
            if ($px -lt 650 -and $py -lt 490) {
                if ((Code ($bmp.GetPixel($px,$py))) -eq $fg[$gy][$gx]) { $same++ }
            }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; ox=$ox; oy=$oy; mode='B15'} }
    } }
    $pct = [math]::Round(100.0 * $best.sim / 256, 1)
    "{0,-14} best {1,-4} sim={2,5}% (ox={3} oy={4})" -f $s.n, $best.mode, $pct, $best.ox, $best.oy
}
$bmp.Dispose()
