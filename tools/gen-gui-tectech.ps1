# =============================================================
# ECO-GTNH TecTech-style GUI texture (per research spec §4)
# base #000020, 2px gray border #808080, flat data areas,
# neon accents #428AFF/#03DEFF, minimal decoration
# =============================================================
Add-Type -AssemblyName System.Drawing
$gui = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\gui"
New-Item -ItemType Directory -Force -Path $gui | Out-Null

$navy   = [System.Drawing.Color]::FromArgb(255, 0, 0, 32)     # #000020
$gray   = [System.Drawing.Color]::FromArgb(255, 128, 128, 128) # #808080
$blue   = [System.Drawing.Color]::FromArgb(255, 66, 138, 255)  # #428AFF
$blueDim= [System.Drawing.Color]::FromArgb(120, 66, 138, 255)
$cyan   = [System.Drawing.Color]::FromArgb(255, 3, 222, 255)   # #03DEFF
$gold   = [System.Drawing.Color]::FromArgb(255, 255, 170, 0)   # #FFAA00
$black  = [System.Drawing.Color]::FromArgb(255, 0, 0, 0)
$white  = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)

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

# 2px gray border (TecTech screen_blue style)
Draw-Border $g 0 0 175 127 $gray
Draw-Border $g 1 1 174 126 $gray

# title separator line (blue neon) under title band
Fill-Rect $g 4 19 171 19 $blue
Fill-Rect $g 4 20 171 20 $blueDim

# left neon vertical line (decor)
Fill-Rect $g 4 24 4 102 $blue
Fill-Rect $g 5 24 5 102 $blueDim

# right neon vertical line (decor)
Fill-Rect $g 171 24 171 102 $blue
Fill-Rect $g 170 24 170 102 $blueDim

# top-right circuit nodes (decor, clear of title text area x>120)
Fill-Rect $g 158 26 160 28 $blueDim
Fill-Rect $g 163 24 165 26 $blue
Fill-Rect $g 168 28 170 30 $blueDim
Set-Px $g 161 27 $blue; Set-Px $g 166 25 $cyan; Set-Px $g 167 29 $cyan

# row separators (subtle, data area y24-84, 8% gray)
$sep = [System.Drawing.Color]::FromArgb(20, 128, 128, 128)
for ($sy = 36; $sy -le 72; $sy += 12) { Fill-Rect $g 8 $sy 168 $sy $sep }

# bottom energy track area (y104-122): black track + gray border
Fill-Rect $g 8 106 168 118 $black
Draw-Border $g 8 106 168 118 $gray
# track segment ticks (blue)
for ($tx = 12; $tx -le 164; $tx += 12) { Set-Px $g $tx 105 $blueDim; Set-Px $g $tx 119 $blueDim }

# bottom accent line
Fill-Rect $g 4 122 171 123 $gray
Fill-Rect $g 4 124 171 124 $blueDim

Save-Png $g (Join-Path $gui "estorage_controller.png")
Write-Host "TecTech GUI texture written:"
Get-Item (Join-Path $gui "estorage_controller.png") | Select-Object Name, Length