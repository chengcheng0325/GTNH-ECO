# =============================================================
# ECO-GTNH round-4 face textures (ASCII-only comments)
# controller: front (neon ring) vs side
# drive bay: front empty / front filled / side
# =============================================================
Add-Type -AssemblyName System.Drawing
$blk = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\blocks"
New-Item -ItemType Directory -Force -Path $blk | Out-Null

$C = @{
    base    = [System.Drawing.Color]::FromArgb(255, 52, 58, 68)
    baseD   = [System.Drawing.Color]::FromArgb(255, 42, 47, 56)
    border  = [System.Drawing.Color]::FromArgb(255, 28, 32, 38)
    edgeHi  = [System.Drawing.Color]::FromArgb(255, 82, 90, 102)
    rivet   = [System.Drawing.Color]::FromArgb(255, 112, 122, 136)
    inset   = [System.Drawing.Color]::FromArgb(255, 20, 23, 28)
    insetBd = [System.Drawing.Color]::FromArgb(255, 64, 71, 82)
    ledGreen= [System.Drawing.Color]::FromArgb(255, 61, 220, 132)
    cyan    = [System.Drawing.Color]::FromArgb(255, 77, 195, 255)
    cyanDim = [System.Drawing.Color]::FromArgb(255, 40, 90, 130)
    glowCyan= [System.Drawing.Color]::FromArgb(190, 77, 195, 255)
}

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

# --- controller FRONT (neon ring + green core + LEDs) ---
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $C.baseD
Draw-Border $b 0 0 15 15 $C.border
Draw-Border $b 1 1 14 14 $C.edgeHi
foreach ($corner in @(@(2,2),@(13,2),@(2,13),@(13,13))) { Fill-Rect $b $corner[0] $corner[1] ($corner[0]+1) ($corner[1]+1) $C.cyanDim }
Draw-Border $b 4 4 11 11 $C.cyan
Draw-Border $b 5 5 10 10 $C.cyanDim
Fill-Rect $b 6 6 9 9 $C.inset
Draw-Border $b 6 6 9 9 $C.cyan
Fill-Rect $b 7 7 8 8 $C.ledGreen
Set-Px $b 6 2 $C.ledGreen; Set-Px $b 8 2 $C.cyan; Set-Px $b 10 2 $C.ledGreen
Fill-Rect $b 2 6 3 9 $C.cyanDim
Fill-Rect $b 12 6 13 9 $C.cyanDim
Set-Px $b 3 7 $C.cyan; Set-Px $b 12 8 $C.cyan
Save-Png $b (Join-Path $blk "storage_array_controller_front.png")

# --- controller SIDE (simplified panel + cyan rail) ---
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $C.base
Draw-Border $b 0 0 15 15 $C.border
Draw-Border $b 1 1 14 14 $C.edgeHi
Fill-Rect $b 1 2 1 13 $C.cyanDim          # left rail
Fill-Rect $b 14 2 14 13 $C.cyanDim        # right rail
Set-Px $b 2 2 $C.cyan; Set-Px $b 13 2 $C.cyan
# horizontal vent stripes
for ($i = 0; $i -lt 5; $i++) { Fill-Rect $b 4 (3 + $i * 2) 11 (3 + $i * 2) $C.insetBd }
# rivets
Set-Px $b 2 6 $C.rivet; Set-Px $b 13 6 $C.rivet; Set-Px $b 2 9 $C.rivet; Set-Px $b 13 9 $C.rivet
Set-Px $b 4 2 $C.ledGreen; Set-Px $b 11 14 $C.ledGreen
Save-Png $b (Join-Path $blk "storage_array_controller_side.png")

# --- drive FRONT empty (slots + LEDs) ---
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $C.base
Draw-Border $b 0 0 15 15 $C.border
Draw-Border $b 1 1 14 14 $C.edgeHi
Fill-Rect $b 6 1 9 1 $C.ledGreen
Fill-Rect $b 2 4 13 6 $C.inset; Draw-Border $b 2 4 13 6 $C.insetBd
Fill-Rect $b 2 9 13 11 $C.inset; Draw-Border $b 2 9 13 11 $C.insetBd
Fill-Rect $b 3 5 5 5 $C.edgeHi
Fill-Rect $b 3 10 5 10 $C.edgeHi
Set-Px $b 12 5 $C.ledGreen; Set-Px $b 12 10 $C.ledGreen
Set-Px $b 2 2 $C.rivet; Set-Px $b 13 2 $C.rivet; Set-Px $b 2 13 $C.rivet; Set-Px $b 13 13 $C.rivet
Save-Png $b (Join-Path $blk "storage_array_drives_front.png")

# --- drive FRONT filled (cells glowing in slots + highlight) ---
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $C.base
Draw-Border $b 0 0 15 15 $C.cyanDim              # highlight border (occupied)
Draw-Border $b 1 1 14 14 $C.cyan
Fill-Rect $b 6 1 9 1 $C.ledGreen
Fill-Rect $b 2 4 13 6 $C.inset; Draw-Border $b 2 4 13 6 $C.cyanDim
Fill-Rect $b 2 9 13 11 $C.inset; Draw-Border $b 2 9 13 11 $C.cyanDim
# glowing cell cores in both slots
Fill-Rect $b 4 5 8 5 $C.cyan
Fill-Rect $b 4 10 8 10 $C.cyan
Fill-Rect $b 5 5 7 5 $C.glowCyan
Fill-Rect $b 5 10 7 10 $C.glowCyan
Fill-Rect $b 3 5 3 5 $C.ledGreen; Fill-Rect $b 3 10 3 10 $C.ledGreen
Set-Px $b 12 5 $C.ledGreen; Set-Px $b 12 10 $C.ledGreen
Save-Png $b (Join-Path $blk "storage_array_drives_front_filled.png")

# --- drive SIDE (simplified + rail + vents) ---
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $C.baseD
Draw-Border $b 0 0 15 15 $C.border
Draw-Border $b 1 1 14 14 $C.edgeHi
Fill-Rect $b 1 2 1 13 $C.cyanDim
Set-Px $b 2 2 $C.cyan; Set-Px $b 2 13 $C.cyan
for ($i = 0; $i -lt 6; $i++) { Fill-Rect $b 4 (2 + $i * 2) 11 (2 + $i * 2) $C.insetBd }
Set-Px $b 4 3 $C.ledGreen; Set-Px $b 4 5 $C.ledGreen
Set-Px $b 11 3 $C.cyan; Set-Px $b 11 5 $C.cyan
Set-Px $b 12 8 $C.rivet
Save-Png $b (Join-Path $blk "storage_array_drives_side.png")

Write-Host "=== round-4 face textures done ==="
Get-ChildItem $blk -Filter "storage_array_*front*.png" -ErrorAction SilentlyContinue | Select-Object Name, Length
Get-ChildItem $blk -Filter "storage_array_*side*.png" -ErrorAction SilentlyContinue | Select-Object Name, Length