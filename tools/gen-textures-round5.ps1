# =============================================================
# ECO-GTNH round-5 filled drive textures by cell type
# item=gold, fluid=blue, essentia=purple (highlight variants)
# =============================================================
Add-Type -AssemblyName System.Drawing
$blk = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\blocks"
New-Item -ItemType Directory -Force -Path $blk | Out-Null

$C = @{
    base    = [System.Drawing.Color]::FromArgb(255, 52, 58, 68)
    border  = [System.Drawing.Color]::FromArgb(255, 28, 32, 38)
    edgeHi  = [System.Drawing.Color]::FromArgb(255, 82, 90, 102)
    inset   = [System.Drawing.Color]::FromArgb(255, 20, 23, 28)
    insetBd = [System.Drawing.Color]::FromArgb(255, 64, 71, 82)
    ledGreen= [System.Drawing.Color]::FromArgb(255, 61, 220, 132)
    gold    = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)
    goldGlow= [System.Drawing.Color]::FromArgb(190, 255, 184, 77)
    blue    = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)
    blueGlow= [System.Drawing.Color]::FromArgb(190, 77, 166, 255)
    purple  = [System.Drawing.Color]::FromArgb(255, 176, 108, 255)
    purpleGlow = [System.Drawing.Color]::FromArgb(190, 176, 108, 255)
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

function New-FilledDrive($accent, $accentGlow) {
    $b = New-Canvas 16 16
    Fill-Rect $b 0 0 15 15 $C.base
    Draw-Border $b 0 0 15 15 $accent
    Draw-Border $b 1 1 14 14 $accentGlow
    Fill-Rect $b 6 1 9 1 $C.ledGreen
    Fill-Rect $b 2 4 13 6 $C.inset; Draw-Border $b 2 4 13 6 $accent
    Fill-Rect $b 2 9 13 11 $C.inset; Draw-Border $b 2 9 13 11 $accent
    Fill-Rect $b 4 5 8 5 $accent
    Fill-Rect $b 4 10 8 10 $accent
    Fill-Rect $b 5 5 7 5 $accentGlow
    Fill-Rect $b 5 10 7 10 $accentGlow
    Fill-Rect $b 3 5 3 5 $C.ledGreen; Fill-Rect $b 3 10 3 10 $C.ledGreen
    Set-Px $b 12 5 $C.ledGreen; Set-Px $b 12 10 $C.ledGreen
    return $b
}

Save-Png (New-FilledDrive $C.gold $C.goldGlow) (Join-Path $blk "storage_array_drives_front_filled_item.png")
Save-Png (New-FilledDrive $C.blue $C.blueGlow) (Join-Path $blk "storage_array_drives_front_filled_fluid.png")
Save-Png (New-FilledDrive $C.purple $C.purpleGlow) (Join-Path $blk "storage_array_drives_front_filled_essentia.png")

Write-Host "=== filled-by-type textures done ==="
Get-ChildItem $blk -Filter "storage_array_drives_front_filled*.png" | Select-Object Name, Length