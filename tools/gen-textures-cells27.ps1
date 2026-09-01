# =============================================================
# ECO-GTNH 27 种存储盘贴图生成器（t76 新分级）
# 9 大小 x 3 类型：k级(256k/1024k/4096k) / 16M 档(16m/64m/256m) / 大M(1024m/4096m/16384m)
# 设计：暗色芯片外壳 + 类型色电路（物品金/流体蓝/源质紫）+ 档位底色（k级蓝/16M档紫/大M红）
#       + 容量刻度条（右侧 3 段，按档位数量亮起）
# 运行：pwsh -File gen-textures-cells27.ps1
# =============================================================
Add-Type -AssemblyName System.Drawing

$root = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures"
$itm = Join-Path $root "items"
New-Item -ItemType Directory -Force -Path $itm | Out-Null

# ---------- 调色板 ----------
$P = @{
    baseDark  = [System.Drawing.Color]::FromArgb(255, 42, 47, 56)     # 深石墨底
    baseMid   = [System.Drawing.Color]::FromArgb(255, 52, 58, 68)     # 中石墨
    border    = [System.Drawing.Color]::FromArgb(255, 28, 32, 38)     # 外边框
    edgeHi    = [System.Drawing.Color]::FromArgb(255, 82, 90, 102)    # 边缘高光
    rivet     = [System.Drawing.Color]::FromArgb(255, 112, 122, 136)  # 顶部接脚
    darkChip  = [System.Drawing.Color]::FromArgb(255, 16, 18, 22)     # 中央芯片
    gold      = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)   # 物品盘 金
    blue      = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)   # 流体盘 蓝
    purple    = [System.Drawing.Color]::FromArgb(255, 176, 108, 255)  # 源质盘 紫
    tierK     = [System.Drawing.Color]::FromArgb(255, 61, 130, 220)   # k级 档位色 蓝
    tierM     = [System.Drawing.Color]::FromArgb(255, 148, 92, 214)   # 16M档 档位色 紫
    tierBig   = [System.Drawing.Color]::FromArgb(255, 216, 84, 84)    # 大M 档位色 红
}

# ---------- 工具 ----------
function New-Canvas([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap($w, $h)
}
function Set-Px($bmp, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $x -lt $bmp.Width -and $y -ge 0 -and $y -lt $bmp.Height) {
        $bmp.SetPixel($x, $y, $c)
    }
}
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y $c } }
}
function Draw-RectBorder($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) {
    for ($x = $x0; $x -le $x1; $x++) { Set-Px $bmp $x $y0 $c; Set-Px $bmp $x $y1 $c }
    for ($y = $y0; $y -le $y1; $y++) { Set-Px $bmp $x0 $y $c; Set-Px $bmp $x1 $y $c }
}
function Save-Png($bmp, [string]$path) {
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ---------- 盘图标 ----------
# $typeColor: 类型色（金/蓝/紫）；$tierColor: 档位色（k蓝/16M紫/大M红）；$seg: 容量刻度段数 1..3
function New-CellIcon($typeColor, $tierColor, [int]$seg) {
    $b = New-Canvas 16 16
    # 透明底 + 芯片外壳（16x12 主体 y=2..13）
    Fill-Rect $b 1 2 14 13 $P.baseDark
    Draw-RectBorder $b 1 2 14 13 $P.edgeHi
    Draw-RectBorder $b 0 1 15 14 $P.border
    # 顶部接脚
    for ($x = 3; $x -le 12; $x += 2) { Set-Px $b $x 1 $P.rivet }
    # 档位色条（左侧竖条，标识 k/16M/大M 档）
    Fill-Rect $b 2 4 2 11 $tierColor
    # 电路走线（类型色）
    Fill-Rect $b 4 4 12 4 $typeColor
    Fill-Rect $b 4 7 12 7 $typeColor
    Fill-Rect $b 4 10 12 10 $typeColor
    Set-Px $b 4 5 $typeColor; Set-Px $b 4 6 $typeColor
    Set-Px $b 12 5 $typeColor; Set-Px $b 12 6 $typeColor
    Set-Px $b 4 8 $typeColor; Set-Px $b 4 9 $typeColor
    Set-Px $b 12 8 $typeColor; Set-Px $b 12 9 $typeColor
    # 中央芯片
    Fill-Rect $b 6 5 9 9 $P.darkChip
    Draw-RectBorder $b 6 5 9 9 $typeColor
    Fill-Rect $b 7 6 8 8 ([System.Drawing.Color]::FromArgb(255, [Math]::Min(255, $typeColor.R + 40), [Math]::Min(255, $typeColor.G + 40), [Math]::Min(255, $typeColor.B + 40)))
    # 右侧容量刻度（3 段：y=4-5 / 7-8 / 10-11，亮起 seg 段）
    $segYs = @(@(4, 5), @(7, 8), @(10, 11))
    for ($i = 0; $i -lt 3; $i++) {
        if ($i -lt $seg) {
            Fill-Rect $b 14 $segYs[$i][0] 15 $segYs[$i][1] $typeColor
        } else {
            Fill-Rect $b 14 $segYs[$i][0] 15 $segYs[$i][1] ([System.Drawing.Color]::FromArgb(255, 34, 38, 44))
        }
    }
    return $b
}

# ---------- 生成 27 张 ----------
$typeColors = @{ item = $P.gold; fluid = $P.blue; essentia = $P.purple }
# 大小 -> (档位色, 刻度段数)：k 级 1/2/3 段，16M 档 1/2/3 段，大 M 1/2/3 段
$sizes = @{
    "256k"   = @($P.tierK, 1); "1024k" = @($P.tierK, 2); "4096k" = @($P.tierK, 3)
    "16m"    = @($P.tierM, 1); "64m"   = @($P.tierM, 2); "256m"  = @($P.tierM, 3)
    "1024m"  = @($P.tierBig, 1); "4096m" = @($P.tierBig, 2); "16384m" = @($P.tierBig, 3)
}

$count = 0
foreach ($kind in $typeColors.Keys) {
    foreach ($sz in $sizes.Keys) {
        $b = New-CellIcon $typeColors[$kind] $sizes[$sz][0] $sizes[$sz][1]
        $path = Join-Path $itm ("estorage_cell_{0}_{1}.png" -f $kind, $sz)
        Save-Png $b $path
        $count++
    }
}

Write-Host "=== 27 张盘贴图生成完成 ==="
Get-ChildItem $itm -Filter "estorage_cell_*.png" | Sort-Object Name | Select-Object Name, Length
