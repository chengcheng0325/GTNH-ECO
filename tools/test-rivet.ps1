Add-Type -AssemblyName System.Drawing
$P = @{
    border    = [System.Drawing.Color]::FromArgb(255, 28, 32, 38)
    edgeHi    = [System.Drawing.Color]::FromArgb(255, 82, 90, 102)
    rivet     = [System.Drawing.Color]::FromArgb(255, 112, 122, 136) # 铆钉
}
Write-Host "rivet value: [$($P.rivet)]"
function New-Canvas([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h) }
function Set-Px($bmp, [int]$x, [int]$y, $c) { if ($null -eq $c) { Write-Host "NULL at ($x,$y)"; return }; $bmp.SetPixel($x, $y, $c) }
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) { for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y $c } } }
function Draw-RectBorder($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y0 $c; Set-Px $bmp $x $y1 $c }; for ($y = $y0; $y -le $y1; $y++) { Set-Px $bmp $x0 $y $c; Set-Px $bmp $x1 $y $c } }
function New-Panel16 {
    $b = New-Canvas 16 16
    Draw-RectBorder $b 0 0 15 15 $P.border
    Draw-RectBorder $b 1 1 14 14 $P.edgeHi
    foreach ($p in @(@(2,2),@(13,2),@(2,13),@(13,13))) { Fill-Rect $b $p[0] $p[1] ($p[0]+1) ($p[1]+1) $P.rivet }
    return $b
}
$b = New-Panel16
Write-Host "panel OK"