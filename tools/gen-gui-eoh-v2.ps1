# =============================================================
# ECO-GTNH EOH GUI texture v2 - follows engineer t17 layout spec:
#   title band   y4-18  full width (flat dark)
#   left labels  x8-60  y24-84 (flat dark)
#   right values x110-168 y24-84 (flat dark)
#   bottom bar   y102-126 (energy track area)
#   decorations  only at corners / edges
# =============================================================
Add-Type -AssemblyName System.Drawing
$gui = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\gui"
New-Item -ItemType Directory -Force -Path $gui | Out-Null

$navy   = [System.Drawing.Color]::FromArgb(255, 10, 14, 20)
$navy2  = [System.Drawing.Color]::FromArgb(255, 14, 19, 28)
$navy3  = [System.Drawing.Color]::FromArgb(255, 22, 30, 44)
$cyan   = [System.Drawing.Color]::FromArgb(255, 77, 195, 255)
$cyanD  = [System.Drawing.Color]::FromArgb(255, 36, 84, 122)
$cyanGlow = [System.Drawing.Color]::FromArgb(90, 77, 195, 255)
$border = [System.Drawing.Color]::FromArgb(255, 30, 40, 56)
$edge   = [System.Drawing.Color]::FromArgb(255, 64, 84, 112)

function New-Canvas([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h) }
function Set-Px($bmp, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $x -lt $bmp.Width -and $y -ge 0 -and $y -lt $bmp.Height -and $null -ne $c) { $bmp.SetPixel($x, $y, $c) }
}
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y $c } }
}
function Draw-Border($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y0 $c; Set-Px $bmp $x $y1 $c }
    for ($y = $y0; $y -le $y1; $y++) { Set-Px $bmp $x0 $y $c; Set-Px $bmp $x1 $y $c }
}
function Save-Png($bmp, [string]$path) { $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose() }

$g = New-Canvas 176 128
Fill-Rect $g 0 0 175 127 $navy

# outer frame
Draw-Border $g 0 0 175 127 $border
Draw-Border $g 1 1 174 126 $cyanD

# title band (y2-19) - flat dark with neon underline at y18-19
Fill-Rect $g 2 2 173 17 $navy2
Fill-Rect $g 2 18 173 19 $cyan          # neon underline
Fill-Rect $g 2 17 173 17 $cyanGlow       # glow layer above underline

# title corner brackets (top-left / top-right)
Draw-Border $g 4 4 6 4 $cyan; Draw-Border $g 4 4 4 6 $cyan
Draw-Border $g 169 4 171 4 $cyan; Draw-Border $g 171 4 171 6 $cyan

# left neon edge line (x2-5, y22-100) - decoration only
Fill-Rect $g 2 22 2 100 $cyanD
Fill-Rect $g 3 22 3 100 $cyanGlow
# bottom-left corner glow dot
Fill-Rect $g 2 100 4 100 $cyan

# main text area - flat dark (left labels x8-60, right values x110-168, y24-84)
Fill-Rect $g 6 22 173 84 $navy2
Draw-Border $g 6 22 173 84 $cyanD
# subtle row separators (y 36/48/60/72, x 8-170) at 8% alpha
$sep = [System.Drawing.Color]::FromArgb(20, 168, 196, 220)
for ($sy = 36; $sy -le 72; $sy += 12) { Fill-Rect $g 8 $sy 170 $sy $sep }

# top-right circuit node decorations (x158-172, y26-40) - corner only
Fill-Rect $g 158 26 160 28 $cyanD
Fill-Rect $g 163 24 165 26 $cyan
Fill-Rect $g 168 28 170 30 $cyanD
Fill-Rect $g 165 26 165 28 $cyanGlow
Set-Px $g 161 27 $cyan; Set-Px $g 166 25 $cyan; Set-Px $g 167 29 $cyan

# right edge neon line (x171-174, y22-100)
Fill-Rect $g 173 22 173 100 $cyanD
Fill-Rect $g 172 22 172 100 $cyanGlow

# bottom energy track area (y104-124) - flat dark with track
Fill-Rect $g 8 104 168 124 $navy3
Draw-Border $g 8 104 168 124 $cyanD
# track inner
Fill-Rect $g 10 106 166 122 $navy
# track segment ticks (decorative)
for ($tx = 14; $tx -le 162; $tx += 12) { Set-Px $g $tx 105 $cyanD; Set-Px $g $tx 123 $cyanD }

# bottom-right corner glow
Fill-Rect $g 168 122 170 124 $cyanGlow
Fill-Rect $g 168 124 170 124 $cyan

# angled accent lines (bottom-left corner only, faint)
for ($i = 0; $i -lt 10; $i++) { Set-Px $g (10 + $i) (118 - $i) $cyanGlow }

Save-Png $g (Join-Path $gui "estorage_controller.png")
Write-Host "EOH GUI v2 written:"
Get-Item (Join-Path $gui "estorage_controller.png") | Select-Object Name, Length