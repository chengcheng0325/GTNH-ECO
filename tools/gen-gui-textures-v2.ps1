# =============================================================
# ECO GUI texture v2 (t114r): redraw only the upgrade button.
#   ecal_upgrade_button.png - 16x16 upgrade core (gold hexagon +
#   cyan circuit corners).
# (ecal_upgrade_bg.png and parameter_purple.png stay untouched -
#  user keeps their own versions.)
# Output: src/main/resources/assets/ecoaegtnh/textures/gui/
# ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI,
# so UTF-8 CJK comments corrupt parsing.
# =============================================================
Add-Type -AssemblyName System.Drawing

$gui = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\gui"

function New-Bmp([int]$w, [int]$h) {
    New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}
function Set-Px($b, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $y -ge 0 -and $x -lt $b.Width -and $y -lt $b.Height) { $b.SetPixel($x, $y, $c) }
}
function Fill-Rect($b, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $b $x $y $c } }
}
function Save-Png($b, [string]$path) {
    $b.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose()
}

# ECO palette: deep-blue panel + gold + cyan.
$P = @{
    panel  = [System.Drawing.Color]::FromArgb(255, 14, 22, 42)
    panelL = [System.Drawing.Color]::FromArgb(255, 26, 38, 66)
    gold   = [System.Drawing.Color]::FromArgb(255, 214, 170, 46)
    goldL  = [System.Drawing.Color]::FromArgb(255, 255, 214, 96)
    goldD  = [System.Drawing.Color]::FromArgb(255, 150, 116, 30)
    cyan   = [System.Drawing.Color]::FromArgb(255, 64, 188, 210)
    white  = [System.Drawing.Color]::FromArgb(255, 240, 248, 255)
}

# -------------------------------------------------------------
# Upgrade button 16x16: gold hexagon "upgrade core" + cyan
# circuit corners on a deep-blue rounded panel.
# -------------------------------------------------------------
$b = New-Bmp 16 16
# Panel base (rounded corners via 1px lighter rim).
Fill-Rect $b 1 1 14 14 $P.panel
Fill-Rect $b 2 0 13 0 $P.panelL
Fill-Rect $b 2 15 13 15 $P.panelL
Fill-Rect $b 0 2 0 13 $P.panelL
Fill-Rect $b 15 2 15 13 $P.panelL
Set-Px $b 1 1 $P.panelL; Set-Px $b 14 1 $P.panelL; Set-Px $b 1 14 $P.panelL; Set-Px $b 14 14 $P.panelL
# Cyan circuit corners (top-left / bottom-right).
Set-Px $b 3 2 $P.cyan; Set-Px $b 4 2 $P.cyan; Set-Px $b 3 3 $P.cyan
Set-Px $b 12 13 $P.cyan; Set-Px $b 11 13 $P.cyan; Set-Px $b 12 12 $P.cyan
# Gold hexagon, point up (center 8,8, radius ~4).
Set-Px $b 8 3 $P.goldL
Set-Px $b 7 4 $P.gold; Set-Px $b 8 4 $P.goldL; Set-Px $b 9 4 $P.gold
Set-Px $b 6 5 $P.gold; Set-Px $b 7 5 $P.gold; Set-Px $b 8 5 $P.goldL; Set-Px $b 9 5 $P.gold; Set-Px $b 10 5 $P.gold
# Point down.
Set-Px $b 7 12 $P.goldD; Set-Px $b 8 12 $P.gold; Set-Px $b 9 12 $P.goldD
Set-Px $b 6 11 $P.goldD; Set-Px $b 7 11 $P.gold; Set-Px $b 8 11 $P.gold; Set-Px $b 9 11 $P.gold; Set-Px $b 10 11 $P.goldD
# Belly (diamond body).
Fill-Rect $b 6 6 10 10 $P.gold
Set-Px $b 6 6 $P.gold; Set-Px $b 7 6 $P.goldL; Set-Px $b 8 6 $P.goldL; Set-Px $b 9 6 $P.goldL; Set-Px $b 10 6 $P.gold
Set-Px $b 6 10 $P.goldD; Set-Px $b 7 10 $P.gold; Set-Px $b 8 10 $P.gold; Set-Px $b 9 10 $P.gold; Set-Px $b 10 10 $P.goldD
# White core.
Set-Px $b 8 8 $P.white
# Inner ascending-arrow shade (upgrade semantics).
Fill-Rect $b 8 6 8 10 $P.goldD
Set-Px $b 7 7 $P.goldD; Set-Px $b 9 7 $P.goldD
Save-Png $b (Join-Path $gui "ecal_upgrade_button.png")

# -------------------------------------------------------------
# (ecal_upgrade_bg.png / parameter_purple.png: user keeps theirs.)
# -------------------------------------------------------------

Write-Host "GUI texture v2 generated:"
Get-ChildItem $gui -Filter "ecal_upgrade_*.png" | Select-Object Name, Length | Format-Table -AutoSize | Out-String
Get-ChildItem $gui -Filter "parameter_purple.png" | Select-Object Name, Length | Format-Table -AutoSize | Out-String
