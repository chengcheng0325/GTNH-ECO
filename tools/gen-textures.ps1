# =============================================================
# ECO-GTNH 原创贴图生成器（程序化生成，完全原创，无任何参考仓库素材）
# 设计风格：暗色石墨工业面板 + 彩色能量条/接口
# 运行：pwsh -File gen-textures.ps1
# =============================================================
Add-Type -AssemblyName System.Drawing

$root = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures"
$blk = Join-Path $root "blocks"
$itm = Join-Path $root "items"
$gui = Join-Path $root "gui"
New-Item -ItemType Directory -Force -Path $blk, $itm, $gui | Out-Null

# ---------- 调色板 ----------
$P = @{
    base      = [System.Drawing.Color]::FromArgb(255, 52, 58, 68)    # 石墨底
    baseDark  = [System.Drawing.Color]::FromArgb(255, 42, 47, 56)    # 深石墨
    border    = [System.Drawing.Color]::FromArgb(255, 28, 32, 38)    # 边框
    edgeHi    = [System.Drawing.Color]::FromArgb(255, 82, 90, 102)   # 边缘高光
    rivet     = [System.Drawing.Color]::FromArgb(255, 112, 122, 136) # 铆钉
    inset     = [System.Drawing.Color]::FromArgb(255, 20, 23, 28)    # 内凹
    insetBd   = [System.Drawing.Color]::FromArgb(255, 64, 71, 82)    # 内凹描边
    ledGreen  = [System.Drawing.Color]::FromArgb(255, 61, 220, 132)
    accentA   = [System.Drawing.Color]::FromArgb(255, 61, 220, 132)  # A 级 绿
    accentB   = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)  # B 级 蓝
    accentC   = [System.Drawing.Color]::FromArgb(255, 176, 108, 255) # C 级 紫
    gold      = [System.Drawing.Color]::FromArgb(255, 255, 184, 77)  # 物品盘 金
    blue      = [System.Drawing.Color]::FromArgb(255, 77, 166, 255)  # 流体盘 蓝
    purple    = [System.Drawing.Color]::FromArgb(255, 176, 108, 255) # 源质盘 紫
    darkChip  = [System.Drawing.Color]::FromArgb(255, 16, 18, 22)
}

# ---------- 工具 ----------
function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    return $bmp
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
# 16x16 工业面板底板：石墨底 + 边框 + 顶部高光 + 四角铆钉 + 对角拉丝
function New-Panel16 {
    $b = New-Canvas 16 16
    Fill-Rect $b 0 0 15 15 $P.base
    Draw-RectBorder $b 0 0 15 15 $P.border
    Draw-RectBorder $b 1 1 14 14 $P.edgeHi
    Set-Px $b 1 14 $P.border; Set-Px $b 14 1 $P.border
    # 顶部高光带
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 ([System.Drawing.Color]::FromArgb(255, 66, 73, 84)) }
    # 四角铆钉（注意：循环变量不能叫 $p，PowerShell 变量不区分大小写会覆盖调色板 $P）
    foreach ($corner in @(@(2,2),@(13,2),@(2,13),@(13,13))) { Fill-Rect $b $corner[0] $corner[1] ($corner[0]+1) ($corner[1]+1) $P.rivet }
    # 对角拉丝（低对比）
    for ($i = 0; $i -lt 12; $i++) {
        Set-Px $b (3 + $i) (4 + $i) ([System.Drawing.Color]::FromArgb(255, 47, 53, 62))
        Set-Px $b (3 + $i) (6 + $i) ([System.Drawing.Color]::FromArgb(255, 47, 53, 62))
        Set-Px $b (3 + $i) (8 + $i) ([System.Drawing.Color]::FromArgb(255, 47, 53, 62))
        Set-Px $b (3 + $i) (10 + $i) ([System.Drawing.Color]::FromArgb(255, 47, 53, 62))
    }
    return $b
}
# 内凹槽（带描边与内部暗底）
function Add-InsetSlot($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1) {
    Fill-Rect $bmp ($x0+1) ($y0+1) ($x1-1) ($y1-1) $P.inset
    Draw-RectBorder $bmp $x0 $y0 $x1 $y1 $P.insetBd
}

# ---------- 1. 外壳 housing ----------
$b = New-Panel16
Save-Png $b (Join-Path $blk "storage_array_housing.png")

# ---------- 2. 驱动盘位 drives ----------
$b = New-Panel16
# 顶部 LED 条
Fill-Rect $b 6 1 9 1 $P.ledGreen
# 两个水平盘槽
Add-InsetSlot $b 2 4 13 6
Add-InsetSlot $b 2 9 13 11
# 槽内触点
Fill-Rect $b 3 5 5 5 ([System.Drawing.Color]::FromArgb(255, 96, 104, 116))
Fill-Rect $b 3 10 5 10 ([System.Drawing.Color]::FromArgb(255, 96, 104, 116))
# 槽右端 LED
Set-Px $b 12 5 $P.ledGreen; Set-Px $b 12 10 $P.ledGreen
Save-Png $b (Join-Path $blk "storage_array_drives.png")

# ---------- 3. 电容 capacitance（A/B/C x 5 态） ----------
$tiers = @{ a = $P.accentA; b = $P.accentB; c = $P.accentC }
$states = @{ empty = 0; low = 1; mid = 2; high = 3; full = 4 }
foreach ($t in $tiers.Keys) {
    foreach ($s in $states.Keys) {
        $b = New-Panel16
        $lit = $states[$s]
        # 左侧竖能量条
        Add-InsetSlot $b 2 3 5 12
        for ($seg = 0; $seg -lt 3; $seg++) {
            # 3 段：y=4-5, 7-8, 10-11
            $sy = 4 + $seg * 3
            if ($lit -gt $seg) {
                Fill-Rect $b 3 $sy 4 ($sy + 1) $tiers[$t]
            } else {
                Fill-Rect $b 3 $sy 4 ($sy + 1) ([System.Drawing.Color]::FromArgb(255, 34, 38, 44))
            }
        }
        # 右侧：等级徽记（A/B/C 数量不同的刻度条）
        $n = @{ a = 2; b = 3; c = 4 }[$t]
        for ($i = 0; $i -lt $n; $i++) {
            Set-Px $b (8 + $i * 2) 3 $tiers[$t]
            Set-Px $b (8 + $i * 2) 4 $tiers[$t]
        }
        # 满态外发光
        if ($lit -eq 4) {
            Fill-Rect $b 2 2 5 2 ([System.Drawing.Color]::FromArgb(180, $tiers[$t].R, $tiers[$t].G, $tiers[$t].B))
        }
        Save-Png $b (Join-Path $blk ("storage_array_capacitance_{0}_{1}.png" -f $t, $s))
    }
}

# ---------- 4. ME 总线 mebus ----------
$b = New-Panel16
# 中央圆形接口（用方块近似 4x4）
Fill-Rect $b 5 5 10 10 $P.inset
Draw-RectBorder $b 5 5 10 10 $P.insetBd
Fill-Rect $b 6 6 9 9 $P.darkChip
# 接口芯
Fill-Rect $b 7 7 8 8 ([System.Drawing.Color]::FromArgb(255, 255, 157, 61))
# 两侧数据槽
Add-InsetSlot $b 1 3 3 12
Add-InsetSlot $b 12 3 14 12
Set-Px $b 2 6 ([System.Drawing.Color]::FromArgb(255, 96, 104, 116))
Set-Px $b 2 9 ([System.Drawing.Color]::FromArgb(255, 96, 104, 116))
Set-Px $b 13 6 ([System.Drawing.Color]::FromArgb(255, 96, 104, 116))
Set-Px $b 13 9 ([System.Drawing.Color]::FromArgb(255, 96, 104, 116))
Save-Png $b (Join-Path $blk "storage_array_mebus.png")

# ---------- 5. 通风口 vents_a ----------
$b = New-Panel16
# 四条百叶
for ($i = 0; $i -lt 4; $i++) {
    $y = 3 + $i * 3
    Add-InsetSlot $b 2 $y 13 ($y + 1)
    # 百叶斜面：下半亮
    Fill-Rect $b 3 ($y + 1) 12 ($y + 1) ([System.Drawing.Color]::FromArgb(255, 70, 77, 88))
}
Save-Png $b (Join-Path $blk "storage_array_vents_a.png")

# ---------- 6. 物品存储盘 cell（item/fluid/essentia x 16/64/256） ----------
function New-CellIcon($accent, [int]$pips) {
    $b = New-Canvas 16 16
    # 透明底 + 圆角芯片外壳（16x12 主体，y=2..13）
    Fill-Rect $b 1 2 14 13 $P.baseDark
    Draw-RectBorder $b 1 2 14 13 $P.edgeHi
    Draw-RectBorder $b 0 1 15 14 $P.border
    # 顶部接脚
    for ($x = 3; $x -le 12; $x += 2) { Set-Px $b $x 1 $P.rivet }
    # 电路走线（accent 色）
    Fill-Rect $b 3 4 12 4 $accent
    Fill-Rect $b 3 7 12 7 $accent
    Fill-Rect $b 3 10 12 10 $accent
    Set-Px $b 3 5 $accent; Set-Px $b 3 6 $accent
    Set-Px $b 12 5 $accent; Set-Px $b 12 6 $accent
    Set-Px $b 3 8 $accent; Set-Px $b 3 9 $accent
    Set-Px $b 12 8 $accent; Set-Px $b 12 9 $accent
    # 中央芯片
    Fill-Rect $b 6 5 9 9 $P.darkChip
    Draw-RectBorder $b 6 5 9 9 $accent
    Fill-Rect $b 7 6 8 8 ([System.Drawing.Color]::FromArgb(255, [Math]::Min(255, $accent.R + 40), [Math]::Min(255, $accent.G + 40), [Math]::Min(255, $accent.B + 40)))
    # 容量刻度 pips（右侧 1..3）
    for ($i = 0; $i -lt $pips; $i++) {
        $py = 4 + $i * 3
        Fill-Rect $b 14 $py 15 ($py + 1) $accent
    }
    return $b
}
$itemAccents = @{ item = $P.gold; fluid = $P.blue; essentia = $P.purple }
$capPips = @{ 16 = 1; 64 = 2; 256 = 3 }
foreach ($kind in $itemAccents.Keys) {
    foreach ($mb in $capPips.Keys) {
        $b = New-CellIcon $itemAccents[$kind] $capPips[$mb]
        Save-Png $b (Join-Path $itm ("estorage_cell_{0}_{1}m.png" -f $kind, $mb))
    }
}

# ---------- 7. GUI 背景（176x128） ----------
$g = New-Canvas 176 128
Fill-Rect $g 0 0 175 127 $P.base
# 边框
Draw-RectBorder $g 0 0 175 127 $P.border
Draw-RectBorder $g 1 1 174 126 $P.edgeHi
# 顶部标题带
Fill-Rect $g 2 2 173 15 ([System.Drawing.Color]::FromArgb(255, 42, 47, 56))
Fill-Rect $g 2 15 173 16 $P.ledGreen
# 标题带纹理点
for ($x = 4; $x -le 172; $x += 6) { Set-Px $g $x 4 $P.rivet; Set-Px $g $x 13 $P.rivet }
# 主体信息区（左右分隔）
Fill-Rect $g 4 20 173 82 ([System.Drawing.Color]::FromArgb(255, 46, 52, 61))
Draw-RectBorder $g 4 20 173 82 $P.insetBd
# 左侧标签列装饰线
Fill-Rect $g 6 24 96 24 ([System.Drawing.Color]::FromArgb(255, 66, 73, 84))
Fill-Rect $g 6 34 96 34 ([System.Drawing.Color]::FromArgb(255, 66, 73, 84))
Fill-Rect $g 6 44 96 44 ([System.Drawing.Color]::FromArgb(255, 66, 73, 84))
Fill-Rect $g 6 54 96 54 ([System.Drawing.Color]::FromArgb(255, 66, 73, 84))
# 右侧值区能量条装饰
Fill-Rect $g 104 24 168 24 ([System.Drawing.Color]::FromArgb(255, 34, 38, 44))
Fill-Rect $g 104 24 168 24 $P.ledGreen
# 底部状态条
Fill-Rect $g 4 88 173 124 ([System.Drawing.Color]::FromArgb(255, 42, 47, 56))
Draw-RectBorder $g 4 88 173 124 $P.insetBd
Fill-Rect $g 8 92 40 96 $P.darkChip
Draw-RectBorder $g 8 92 40 96 $P.insetBd
Set-Px $g 16 94 $P.ledGreen; Set-Px $g 24 94 $P.ledGreen; Set-Px $g 32 94 $P.ledGreen
# 装饰对角线纹理
for ($i = 0; $i -lt 40; $i++) {
    Set-Px $g (6 + $i) (100 + [int]($i / 2)) ([System.Drawing.Color]::FromArgb(255, 48, 54, 63))
}
Save-Png $g (Join-Path $gui "estorage_controller.png")

Write-Host "=== 原创贴图生成完成 ==="
Get-ChildItem $blk, $itm, $gui -File | Select-Object @{N="Path";E={$_.FullName.Replace($root + "\", "")}}, Length
