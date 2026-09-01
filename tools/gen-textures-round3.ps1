# =============================================================
# ECO-GTNH round-3 original textures (ASCII-only comments)
# 1) storage_array_controller.png  - custom MTE controller face
# 2) estorage_controller.png       - TecTech EOH-style GUI (176x128)
# =============================================================
Add-Type -AssemblyName System.Drawing

$root = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures"
$blk  = Join-Path $root "blocks"
$gui  = Join-Path $root "gui"
New-Item -ItemType Directory -Force -Path $blk, $gui | Out-Null

# ---------- palette ----------
$C = @{
    navy    = [System.Drawing.Color]::FromArgb(255, 10, 14, 20)     # deep navy bg
    navy2   = [System.Drawing.Color]::FromArgb(255, 16, 22, 32)     # panel bg
    navy3   = [System.Drawing.Color]::FromArgb(255, 24, 32, 46)     # lighter panel
    cyan    = [System.Drawing.Color]::FromArgb(255, 77, 195, 255)   # neon cyan
    cyanDim = [System.Drawing.Color]::FromArgb(255, 40, 90, 130)    # dim cyan
    border  = [System.Drawing.Color]::FromArgb(255, 28, 38, 54)     # dark border
    edgeHi  = [System.Drawing.Color]::FromArgb(255, 60, 78, 104)    # edge highlight
    text    = [System.Drawing.Color]::FromArgb(255, 176, 196, 220)  # pale text
    green   = [System.Drawing.Color]::FromArgb(255, 61, 220, 132)
    gold    = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)
    inset   = [System.Drawing.Color]::FromArgb(255, 6, 9, 14)       # deep inset
}

function New-Canvas([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h) }
function Set-Px($bmp, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $x -lt $bmp.Width -and $y -ge 0 -and $y -lt $bmp.Height -and $null -ne $c) {
        $bmp.SetPixel($x, $y, $c)
    }
}
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y $c } }
}
function Draw-Border($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y0 $c; Set-Px $bmp $x $y1 $c }
    for ($y = $y0; $y -le $y1; $y++) { Set-Px $bmp $x0 $y $c; Set-Px $bmp $x1 $y $c }
}
function Save-Png($bmp, [string]$path) { $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose() }

# ---------- 1) controller face 16x16 ----------
# Design: dark navy-graphite panel, cyan neon ring around a core,
# 3 LED status dots, corner bolts. Distinct from casing/drive.
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $C.navy2
Draw-Border $b 0 0 15 15 $C.border
Draw-Border $b 1 1 14 14 $C.edgeHi
# corner bolts
foreach ($corner in @(@(2,2),@(13,2),@(2,13),@(13,13))) {
    Fill-Rect $b $corner[0] $corner[1] ($corner[0]+1) ($corner[1]+1) $C.cyanDim
}
# neon ring (2px, rounded corners approximated)
Draw-Border $b 4 4 11 11 $C.cyan
Draw-Border $b 5 5 10 10 $C.cyanDim
# core chip
Fill-Rect $b 6 6 9 9 $C.inset
Draw-Border $b 6 6 9 9 $C.cyan
# core center glow
Fill-Rect $b 7 7 8 8 $C.green
# LED status dots (top strip)
Set-Px $b 6 2 $C.green; Set-Px $b 8 2 $C.cyan; Set-Px $b 10 2 $C.gold
# side notches (energy arcs)
Fill-Rect $b 2 6 3 9 $C.cyanDim
Fill-Rect $b 12 6 13 9 $C.cyanDim
Set-Px $b 3 7 $C.cyan; Set-Px $b 12 8 $C.cyan
Save-Png $b (Join-Path $blk "storage_array_controller.png")
Write-Host "controller texture written"

# ---------- 2) TecTech EOH-style GUI 176x128 ----------
$g = New-Canvas 176 128
Fill-Rect $g 0 0 175 127 $C.navy
# outer frame
Draw-Border $g 0 0 175 127 $C.border
Draw-Border $g 1 1 174 126 $C.cyanDim
# title band with neon underline + angled accents
Fill-Rect $g 2 2 173 17 $C.navy2
Draw-Border $g 2 2 173 17 $C.cyanDim
Fill-Rect $g 2 17 173 18 $C.cyan                      # neon underline
# angled slash accents in title
for ($i = 0; $i -lt 12; $i++) { Set-Px $g (10 + $i) (12 - $i) $C.cyanDim }
for ($i = 0; $i -lt 12; $i++) { Set-Px $g (154 + $i) (5 + $i) $C.cyanDim }
# title corner marks
Fill-Rect $g 4 4 6 6 $C.cyan
Fill-Rect $g 169 4 171 6 $C.cyan
# main info area
Fill-Rect $g 4 22 173 84 $C.navy2
Draw-Border $g 4 22 173 84 $C.cyanDim
# left label column ticks
for ($y = 26; $y -le 78; $y += 13) {
    Fill-Rect $g 7 $y 10 $y $C.cyan
    Fill-Rect $g 11 $y 98 $y $C.navy3
}
# right value panels (neon-bordered)
for ($y = 24; $y -le 76; $y += 13) {
    Draw-Border $g 102 $y 170 ($y + 9) $C.cyanDim
    Fill-Rect $g 103 ($y + 1) 169 ($y + 8) $C.inset
}
# energy bar decoration inside first value panel
Fill-Rect $g 104 26 120 32 $C.cyan
Fill-Rect $g 104 40 120 40 $C.green
Fill-Rect $g 104 53 120 53 $C.gold
# angled energy arcs (EOH flavor) in the middle
for ($i = 0; $i -lt 14; $i++) { Set-Px $g (30 + $i) (60 + [int]($i / 2)) $C.cyanDim }
for ($i = 0; $i -lt 14; $i++) { Set-Px $g (140 + $i) (70 - [int]($i / 2)) $C.cyanDim }
# bottom status strip
Fill-Rect $g 4 88 173 124 $C.navy2
Draw-Border $g 4 88 173 124 $C.cyanDim
Fill-Rect $g 4 88 173 89 $C.cyan
# status segments
Draw-Border $g 8 94 48 106 $C.cyanDim
Fill-Rect $g 9 95 47 105 $C.inset
for ($i = 0; $i -lt 5; $i++) { Set-Px $g (12 + $i * 8) 100 $C.green }
# right status text area
Draw-Border $g 54 94 168 106 $C.cyanDim
Fill-Rect $g 55 95 167 105 $C.inset
for ($x = 58; $x -le 164; $x += 8) { Set-Px $g $x 99 $C.cyanDim }
# decorative corner brackets
Draw-Border $g 2 20 2 22 $C.cyan; Draw-Border $g 2 20 4 20 $C.cyan
Draw-Border $g 173 20 173 22 $C.cyan; Draw-Border $g 171 20 173 20 $C.cyan
Draw-Border $g 2 86 2 88 $C.cyan; Draw-Border $g 2 86 4 86 $C.cyan
Draw-Border $g 173 86 173 88 $C.cyan; Draw-Border $g 171 86 173 86 $C.cyan
Save-Png $g (Join-Path $gui "estorage_controller.png")
Write-Host "EOH gui texture written"

Write-Host "=== round-3 textures done ==="
Get-Item (Join-Path $blk "storage_array_controller.png"), (Join-Path $gui "estorage_controller.png") | Select-Object Name, Length