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

# wide search for side & item slots: y0 in 320..340, x0 around 5/165, offset -4..4
$tests = @(
  @{n='side'; xc=5;   f='blocks\ecal_controller_side.png'},
  @{n='item'; xc=165; f='items\ecal_cell_c4.png'}
)
foreach ($t in $tests) {
    $fg = Get-FileGrid $t.f
    $best = @{sim=-1; x0=0; y0=0; ox=0; oy=0}
    foreach ($y0 in 318..344) { foreach ($x0 in ($t.xc-8)..($t.xc+8)) { foreach ($ox in 0..7) { foreach ($oy in 0..7) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            $px = $x0 + $gx*8 + $ox; $py = $y0 + $gy*8 + $oy
            if ($px -ge 0 -and $px -lt 650 -and $py -ge 0 -and $py -lt 490) {
                if ((Code ($bmp.GetPixel($px,$py))) -eq $fg[$gy][$gx]) { $same++ }
            }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0; ox=$ox; oy=$oy} }
    } } } }
    "{0}: best sim={1}% x0={2} y0={3} ox={4} oy={5}" -f $t.n, [math]::Round(100.0*$best.sim/256,1), $best.x0, $best.y0, $best.ox, $best.oy
}

# print diff grids at best position for both
foreach ($t in $tests) {
    $fg = Get-FileGrid $t.f
    # re-run search to get best coords (cheap enough)
    $best = @{sim=-1; x0=0; y0=0; ox=0; oy=0}
    foreach ($y0 in 318..344) { foreach ($x0 in ($t.xc-8)..($t.xc+8)) { foreach ($ox in 0..7) { foreach ($oy in 0..7) {
        $same = 0
        for ($gy = 0; $gy -lt 16; $gy++) { for ($gx = 0; $gx -lt 16; $gx++) {
            $px = $x0 + $gx*8 + $ox; $py = $y0 + $gy*8 + $oy
            if ($px -ge 0 -and $px -lt 650 -and $py -ge 0 -and $py -lt 490) {
                if ((Code ($bmp.GetPixel($px,$py))) -eq $fg[$gy][$gx]) { $same++ }
            }
        } }
        if ($same -gt $best.sim) { $best = @{sim=$same; x0=$x0; y0=$y0; ox=$ox; oy=$oy} }
    } } } }
    "`n== $($t.n) best position x0=$($best.x0) y0=$($best.y0) ox=$($best.ox) oy=$($best.oy) =="
    "preview grid:"
    for ($gy = 0; $gy -lt 16; $gy++) { $l=''; for ($gx = 0; $gx -lt 16; $gx++) { $l += Code ($bmp.GetPixel($best.x0 + $gx*8 + $best.ox, $best.y0 + $gy*8 + $best.oy)) }; $l }
    "file grid:"
    $fg
}
$bmp.Dispose()
