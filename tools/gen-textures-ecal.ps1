# =============================================================
# ECO-GTNH E-Calculator 原创贴图生成器（阶段 C2，t10）
# 风格：仿 1.12.2 原版 NovaEngineering ECOAE E-Calculator 方块外观
#      （浅灰工业面板 + 青色能量/屏幕 + 金色触点/端子，工业计算风格）
# 全部 16x16 像素均为本脚本程序化原创构图 —— 仅参照原版配色与母题，
# 不拷贝任何原版 PNG（GPL 规避，与 E-Storage 40 张贴图同法）。
# 运行：pwsh -File tools/gen-textures-ecal.ps1
# =============================================================
Add-Type -AssemblyName System.Drawing

$blk = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\blocks"
New-Item -ItemType Directory -Force -Path $blk | Out-Null

# ---------- 调色板（取自原版 E-Calculator 视觉语言） ----------
$P = @{
    # 面板族（浅灰工业）
    frame     = [System.Drawing.Color]::FromArgb(255, 198, 198, 198)   # C6 外框
    frameL    = [System.Drawing.Color]::FromArgb(255, 201, 201, 201)   # C9 框亮
    frameD    = [System.Drawing.Color]::FromArgb(255, 195, 195, 195)   # C3 框暗
    panel     = [System.Drawing.Color]::FromArgb(255, 226, 226, 226)   # E2 面板
    panelL    = [System.Drawing.Color]::FromArgb(255, 241, 241, 241)   # F1 面板亮
    panelD    = [System.Drawing.Color]::FromArgb(255, 205, 205, 205)   # CD 面板暗
    panelD2   = [System.Drawing.Color]::FromArgb(255, 210, 210, 210)   # D2
    # 深色内凹 / 边框
    bezel     = [System.Drawing.Color]::FromArgb(255,  51,  51,  51)   # 33 屏幕外框
    bezelDeep = [System.Drawing.Color]::FromArgb(255,  29,  29,  29)   # 1D 深框
    inset     = [System.Drawing.Color]::FromArgb(255,  65,  65,  65)   # 41 内凹
    insetHi   = [System.Drawing.Color]::FromArgb(255,  72,  72,  72)   # 48 内凹亮
    insetD    = [System.Drawing.Color]::FromArgb(255,  44,  44,  44)   # 2C
    darkBar   = [System.Drawing.Color]::FromArgb(255,  88,  88,  88)   # 58 端子条
    black     = [System.Drawing.Color]::FromArgb(255,   0,   0,   0)   # 屏幕熄灭
    # 青色系（能量/屏幕/发光）
    cyan      = [System.Drawing.Color]::FromArgb(255,  77, 191, 212)   # 4D BFD4
    cyanMid   = [System.Drawing.Color]::FromArgb(255,  69, 172, 191)   # 45 ACBF
    cyanLight = [System.Drawing.Color]::FromArgb(255, 128, 225, 255)   # 80 E1FF
    cyanPale  = [System.Drawing.Color]::FromArgb(255, 204, 253, 255)   # CC FDFF
    # 金色系（触点/端子/图表）
    gold      = [System.Drawing.Color]::FromArgb(255, 222, 200,  68)   # DE C844
    goldD     = [System.Drawing.Color]::FromArgb(255, 196, 173,  35)   # C4 AD23
    goldL     = [System.Drawing.Color]::FromArgb(255, 242, 233, 181)   # F2 E9B5
    goldBr    = [System.Drawing.Color]::FromArgb(255, 255, 229,  76)   # FF E54C
    red       = [System.Drawing.Color]::FromArgb(255, 220,  60,  40)   # 状态红灯
    white     = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    screw     = [System.Drawing.Color]::FromArgb(255, 168, 168, 168)   # A8 螺钉（加深，F3）
    screwD    = [System.Drawing.Color]::FromArgb(255, 138, 138, 138)   # 8A 螺钉影
    # C6 档位色（原版 L6 金：FF9300/FFC600 系，t22）
    c6Acc     = [System.Drawing.Color]::FromArgb(255, 255, 147,   0)   # FF 93 00
    c6AccL    = [System.Drawing.Color]::FromArgb(255, 255, 198,   0)   # FF C6 00
    c6AccP    = [System.Drawing.Color]::FromArgb(255, 255, 224, 174)   # FF E0 AE
    # C9 档位色（原版 L9 紫：8815D8/B06FDD 系，t22）
    c9Acc     = [System.Drawing.Color]::FromArgb(255, 136,  21, 216)   # 88 15 D8
    c9AccL    = [System.Drawing.Color]::FromArgb(255, 176, 111, 221)   # B0 6F DD
    c9AccP    = [System.Drawing.Color]::FromArgb(255, 221, 168, 245)   # DD A8 F5
}

# ---------- 工具 ----------
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

# 浅灰工业面板基底：外框 C6 + 上左高光/下右阴影内沿 + 四角螺钉 + 稀疏拉丝
function New-Panel16 {
    $b = New-Canvas 16 16
    Fill-Rect $b 0 0 15 15 $P.panel
    Draw-Border $b 0 0 15 15 $P.frame
    # 内沿斜光：上/左亮，下/右暗
    for ($x = 1; $x -le 14; $x++) { Set-Px $b $x 1 $P.panelL }
    for ($y = 1; $y -le 14; $y++) { Set-Px $b 1 $y $P.panelL }
    for ($x = 1; $x -le 14; $x++) { Set-Px $b $x 14 $P.panelD }
    for ($y = 1; $y -le 14; $y++) { Set-Px $b 14 $y $P.panelD }
    # 四角螺钉（F3：2x2 深灰 + 阴影，16x16 可读）
    foreach ($c in @(@(2,2), @(13,2), @(2,13), @(13,13))) {
        Fill-Rect $b $c[0] $c[1] ($c[0]+1) ($c[1]+1) $P.screw
        Set-Px $b ($c[0]+1) ($c[1]+1) $P.screwD
    }
    # 稀疏对角拉丝（低对比，原创排布）
    foreach ($i in 3..10) { Set-Px $b $i ($i+2) $P.panelD; Set-Px $b $i ($i+4) $P.panelD2 }
    return $b
}

# ---------- 1. 机壳 ecal_casing ----------
$b = New-Panel16
# F5：极小青/金角标（家族色点缀，避开螺钉位）
Set-Px $b 2 12 $P.cyan
Set-Px $b 13 12 $P.gold
Save-Png $b (Join-Path $blk "ecal_casing.png")

# ---------- 2. 并行核心 ecal_parallel_proc（3x3 核心单元网格，F2 重做） ----------
$b = New-Canvas 16 16
Fill-Rect $b 0 0 15 15 $P.panel
Draw-Border $b 0 0 15 15 $P.frame
# 3x3 核心单元：4x4 单元（1px 深灰描边 + 2x2 内芯），位置 x/y = 1/6/11，间距 1px
foreach ($cy in @(1, 6, 11)) {
    foreach ($cx in @(1, 6, 11)) {
        Draw-Border $b $cx $cy ($cx+3) ($cy+3) $P.frameD
        Fill-Rect $b ($cx+1) ($cy+1) ($cx+2) ($cy+2) $P.panelL
    }
}
# 点亮单元（原创分布：主斜线 + 中心 + 副斜线）：青 4 + 金 2
$lit = @{
    '1,1'   = @($P.cyan, $P.cyanLight)
    '6,1'   = @($P.gold, $P.goldL)
    '11,6'  = @($P.cyan, $P.cyanLight)
    '1,11'  = @($P.gold, $P.goldL)
    '6,6'   = @($P.cyanLight, $P.cyanPale)
    '11,11' = @($P.cyan, $P.cyanLight)
}
foreach ($k in $lit.Keys) {
    $xy = $k.Split(','); $cx = [int]$xy[0]; $cy = [int]$xy[1]
    Fill-Rect $b ($cx+1) ($cy+1) ($cx+2) ($cy+2) $lit[$k][0]
    Set-Px $b ($cx+1) ($cy+1) $lit[$k][1]
}
Save-Png $b (Join-Path $blk "ecal_parallel_proc.png")

# ---------- 3. 线程核心 ecal_thread_core（CPU 芯片） ----------
$b = New-Panel16
# 芯片主体
Fill-Rect $b 3 3 12 12 $P.panelD
Draw-Border $b 3 3 12 12 $P.frame
# 芯片内芯
Fill-Rect $b 5 5 10 10 $P.panel
Draw-Border $b 5 5 10 10 $P.frameD
# 白引脚（上下两排，原创间距）
foreach ($x in @(4, 6, 8, 10, 12)) { Set-Px $b $x 4 $P.white; Set-Px $b $x 12 $P.white }
# 芯内白走线（十字 + 斜线）
Fill-Rect $b 5 7 10 7 $P.white
Fill-Rect $b 7 5 7 10 $P.white
Set-Px $b 6 6 $P.white; Set-Px $b 9 9 $P.white
# 左侧金色触点列
foreach ($y in @(5, 7, 9, 11)) { Set-Px $b 3 $y $P.gold; Set-Px $b 4 $y $P.goldL }
# 青色运行灯（右下角）
Set-Px $b 11 10 $P.cyan; Set-Px $b 12 11 $P.cyanLight
Save-Png $b (Join-Path $blk "ecal_thread_core.png")

# ---------- 4. 晶阵驱动器 ecal_cell_drive（驱动器舱） ----------
$b = New-Panel16
# 顶部 LED 排
foreach ($x in @(3, 6, 9, 12)) { Set-Px $b $x 2 $P.cyan }
# 主舱体
Fill-Rect $b 2 4 13 12 $P.inset
Draw-Border $b 2 4 13 12 $P.bezelDeep
# 舱内三槽分隔
Fill-Rect $b 2 6 13 6 $P.insetD
Fill-Rect $b 2 9 13 9 $P.insetD
# 左列金色触点
foreach ($y in @(5, 8, 11)) { Set-Px $b 3 $y $P.gold; Set-Px $b 4 $y $P.goldL }
# 晶阵插入后的青色发光窗（三档亮度：低/中/高）
Set-Px $b 6 5 $P.cyanPale;  Set-Px $b 7 5 $P.cyan
Set-Px $b 6 8 $P.cyan;      Set-Px $b 7 8 $P.cyanLight
Set-Px $b 6 11 $P.cyanLight; Set-Px $b 7 11 $P.cyanPale
# 右列状态点
foreach ($y in @(5, 8, 11)) { Set-Px $b 12 $y $P.insetHi }
Set-Px $b 12 8 $P.gold
Save-Png $b (Join-Path $blk "ecal_cell_drive.png")

# ---------- 5. ME 通道 ecal_me_channel（网络端口） ----------
$b = New-Panel16
# 深色端口环（带对角切口，呼应原版端口开口母题）
Fill-Rect $b 3 3 12 12 $P.bezelDeep
Fill-Rect $b 4 4 11 11 $P.inset
Draw-Border $b 4 4 11 11 $P.bezel
# 对角切口（F4：切口外缘亮线 + 露出面板角）
foreach ($c in @(@(4,4), @(11,4), @(4,11), @(11,11))) {
    Set-Px $b $c[0] $c[1] $P.panelL
    Set-Px $b ($c[0]+1) $c[1] $P.panelL
    Set-Px $b $c[0] ($c[1]+1) $P.panelL
}
# 中心青色连接核
Fill-Rect $b 6 6 9 9 $P.cyan
Fill-Rect $b 7 7 8 8 $P.cyanLight
Set-Px $b 7 7 $P.white
# 四角金螺栓
foreach ($c in @(@(3,3), @(12,3), @(3,12), @(12,12))) { Set-Px $b $c[0] $c[1] $P.gold }
Save-Png $b (Join-Path $blk "ecal_me_channel.png")

# ---------- 6. 超导传输总线 ecal_transmitter_bus ----------
$b = New-Panel16
# 上/下端子条（暗）
Fill-Rect $b 3 2 12 3 $P.darkBar
Draw-Border $b 3 2 12 3 $P.insetD
Fill-Rect $b 3 12 12 13 $P.darkBar
Draw-Border $b 3 12 12 13 $P.insetD
# 青色能量带（中间，发光）
Fill-Rect $b 1 6 14 9 $P.cyan
# 能量带外壳线
Fill-Rect $b 2 5 13 5 $P.insetD
Fill-Rect $b 2 10 13 10 $P.insetD
# 波光闪烁（原创抖动排布）
foreach ($x in @(3, 6, 9, 12)) { Set-Px $b $x 7 $P.cyanPale }
foreach ($x in @(4, 7, 10, 13)) { Set-Px $b $x 8 $P.white }
Set-Px $b 1 7 $P.cyanMid; Set-Px $b 14 7 $P.cyanMid
Set-Px $b 1 8 $P.cyanLight; Set-Px $b 14 8 $P.cyanLight
# 两端金端帽
Set-Px $b 2 6 $P.gold; Set-Px $b 2 9 $P.gold
Set-Px $b 13 6 $P.gold; Set-Px $b 13 9 $P.gold
Save-Png $b (Join-Path $blk "ecal_transmitter_bus.png")

# ---------- 7. 控制器正面（成型态，浅色白系） ecal_controller_front ----------
# t16：用户拍板整体白色系——屏幕改为浅灰内凹面板，青色仅作功能点缀，金柱状图保留
$b = New-Panel16
# 屏幕区域：浅灰内凹（D2 底 + C3 框 + 上沿高光），与外壳同族
Fill-Rect $b 2 2 13 13 $P.panelD2
Draw-Border $b 2 2 13 13 $P.frameD
Fill-Rect $b 2 2 13 2 $P.panelL
# 金色柱状图（自左向右递增，原创高度）
Fill-Rect $b 4 9 4 12 $P.goldD
Fill-Rect $b 6 8 6 12 $P.gold
Fill-Rect $b 8 7 8 12 $P.goldBr
Fill-Rect $b 10 6 10 12 $P.goldL
Fill-Rect $b 12 8 12 12 $P.gold
# 青色数据线（功能点缀）
Fill-Rect $b 3 4 12 4 $P.cyan
Set-Px $b 4 4 $P.cyanLight; Set-Px $b 8 4 $P.cyanLight; Set-Px $b 12 4 $P.cyanLight
# 白色读数字符点
foreach ($x in @(4, 6, 8, 10, 12)) { Set-Px $b $x 6 $P.white }
# 状态灯（屏幕框上）：左上青（运行），右上红（告警占位）
Set-Px $b 2 2 $P.cyan; Set-Px $b 13 2 $P.red
Save-Png $b (Join-Path $blk "ecal_controller_front.png")

# ---------- 8. 控制器正面（非成型态，浅色白系） ecal_controller_front_off ----------
$b = New-Panel16
# 屏幕区域：与面板同色（E2）+ C3 细框 + 1px D2 内缘阴影（vision 建议，强化屏幕感）
Fill-Rect $b 2 2 13 13 $P.panel
Draw-Border $b 2 2 13 13 $P.frameD
Draw-Border $b 3 3 12 12 $P.panelD2
# 微弱屏内噪点（极浅，暗示未激活）
Set-Px $b 5 5 $P.panelD; Set-Px $b 10 10 $P.panelD; Set-Px $b 8 8 $P.panelD2
# 状态灯熄灭（灰）
Set-Px $b 2 2 $P.frameD; Set-Px $b 13 2 $P.frameD
Save-Png $b (Join-Path $blk "ecal_controller_front_off.png")

# ---------- 9. 控制器侧面 ecal_controller_side（浅色白系百叶） ----------
$b = New-Panel16
# 三排横向散热百叶（浅灰，t16 由深色改浅色）
foreach ($i in 0..2) {
    $y0 = 3 + $i * 4
    Fill-Rect $b 3 $y0 12 ($y0 + 1) $P.panelD
    Fill-Rect $b 3 $y0 12 $y0 $P.panelL
    Fill-Rect $b 3 ($y0 + 1) 12 ($y0 + 1) $P.frameD
}
# 右侧状态灯列（功能点缀）
Set-Px $b 13 3 $P.cyan; Set-Px $b 13 4 $P.cyanLight
Set-Px $b 13 7 $P.gold; Set-Px $b 13 8 $P.goldL
Set-Px $b 13 11 $P.cyan; Set-Px $b 13 12 $P.cyanPale
Save-Png $b (Join-Path $blk "ecal_controller_side.png")

# ---------- 10. 晶阵驱动器正面分面贴图 ecal_cell_drive_front（t16 新增，白色系） ----------
$b = New-Panel16
# 顶部状态 LED 排（功能点缀）
Set-Px $b 3 2 $P.cyan; Set-Px $b 6 2 $P.gold; Set-Px $b 9 2 $P.cyanLight; Set-Px $b 12 2 $P.gold
# 正面舱口：浅灰内凹面板（比外壳深一档，保持白系）
Fill-Rect $b 2 4 13 12 $P.panelD2
Draw-Border $b 2 4 13 12 $P.frameD
Fill-Rect $b 2 4 13 4 $P.panelL
# 晶阵槽口暗示：三条水平卡槽（露出更浅底色 E2）
foreach ($yy in @(6, 9, 12)) {
    Fill-Rect $b 3 $yy 12 $yy $P.panel
    Set-Px $b 3 $yy $P.gold; Set-Px $b 12 $yy $P.gold
}
# 左缘青色运行点
Set-Px $b 3 7 $P.cyan; Set-Px $b 3 8 $P.cyanLight
Save-Png $b (Join-Path $blk "ecal_cell_drive_front.png")

# ---------- 10b. 晶阵驱动器正面 filled 分面贴图 ecal_cell_drive_front_filled（t19 新增） ----------
# 表现手法参照 E-Storage storage_array_drives_front_filled：空态=空舱口卡槽，
# filled=舱口内可见发亮晶阵（青色辉光观察窗 + 窗内金色晶阵主体），白系家族一致
$b = New-Panel16
# 顶部状态 LED 排（filled：全青 = 运行中）
Set-Px $b 3 2 $P.cyan; Set-Px $b 6 2 $P.cyanLight; Set-Px $b 9 2 $P.cyan; Set-Px $b 12 2 $P.cyanLight
# 正面舱口：浅灰内凹面板（同空态）
Fill-Rect $b 2 4 13 12 $P.panelD2
Draw-Border $b 2 4 13 12 $P.frameD
Fill-Rect $b 2 4 13 4 $P.panelL
# 上/下空卡槽（与空态呼应：两条空槽）
foreach ($yy in @(6, 12)) {
    Fill-Rect $b 3 $yy 12 $yy $P.panel
    Set-Px $b 3 $yy $P.gold; Set-Px $b 12 $yy $P.gold
}
# 中部观察窗：青色辉光窗框 + 浅青玻璃（半透明观感）
Draw-Border $b 3 7 12 11 $P.cyanLight
Fill-Rect $b 4 8 11 10 $P.cyanPale
# 窗内晶阵主体（金：外框 + 高亮芯，呼应 item 图标母题）
Draw-Border $b 5 9 10 10 $P.goldD
Fill-Rect $b 6 9 9 9 $P.gold
Set-Px $b 7 9 $P.goldBr; Set-Px $b 8 9 $P.goldBr
Set-Px $b 6 10 $P.goldL; Set-Px $b 9 10 $P.goldL
Save-Png $b (Join-Path $blk "ecal_cell_drive_front_filled.png")

# ---------- 11. C4 闪存晶阵物品图标 items/ecal_cell_c4（64M，透明边距） ----------
$itm = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\items"
New-Item -ItemType Directory -Force -Path $itm | Out-Null
$b = New-Canvas 16 16
# 透明上/下边距（物品图标惯例，同 E-Storage 盘）；主体：浅灰面板 + 框
Fill-Rect $b 1 1 14 14 $P.panel
Draw-Border $b 1 1 14 14 $P.frame
# 内沿斜光
for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 $P.panelL; Set-Px $b $x 13 $P.panelD }
for ($y = 2; $y -le 13; $y++) { Set-Px $b 2 $y $P.panelL; Set-Px $b 13 $y $P.panelD }
# 顶部金色接脚
foreach ($x in @(3, 5, 7, 9, 11, 13)) { Set-Px $b $x 2 $P.gold; Set-Px $b $x 3 $P.goldL }
# 存储窗口（深色内凹 + 边框）
Fill-Rect $b 3 5 12 11 $P.inset
Draw-Border $b 3 5 12 11 $P.bezelDeep
# 窗内青色存储条（C4 = 64M：三格，间隔露出窗底深色作分隔线，F5 修正）
foreach ($pi in 0..2) {
    $yy = 6 + $pi * 2
    if ($pi -lt 2) { Fill-Rect $b 4 $yy 9 $yy $P.cyan } else { Fill-Rect $b 4 $yy 9 $yy $P.cyanLight }
    Set-Px $b 4 $yy $P.cyanLight
}
# 窗右侧金色触点
Fill-Rect $b 10 6 11 11 $P.gold
Set-Px $b 10 6 $P.goldL
# 底部容量刻度（3 点 = 64M 档）
foreach ($x in @(5, 8, 11)) { Set-Px $b $x 13 $P.goldD }
Save-Png $b (Join-Path $itm "ecal_cell_c4.png")

# =============================================================
# t22：C6/C9 档位贴图（金/紫点缀，白色系家族保持）
# 档位色：C6=金橙（FF9300/FFC600，原版 L6 金）、C9=紫（8815D8/B06FDD，原版 L9 紫）
# 母题与 C4 同构，仅档位色替换功能点缀；超线程核心=线程核心双核布局 + HT 标记
# =============================================================

# 并行核心 C6/C9（与 C4 同构：3x3 核心网格，点亮单元用档位色）
function New-ParallelProcTier($acc, $accL, $accP) {
    $b = New-Canvas 16 16
    Fill-Rect $b 0 0 15 15 $P.panel
    Draw-Border $b 0 0 15 15 $P.frame
    foreach ($cy in @(1, 6, 11)) {
        foreach ($cx in @(1, 6, 11)) {
            Draw-Border $b $cx $cy ($cx+3) ($cy+3) $P.frameD
            Fill-Rect $b ($cx+1) ($cy+1) ($cx+2) ($cy+2) $P.panelL
        }
    }
    $lit = @{
        '1,1'   = @($acc, $accL)
        '6,1'   = @($acc, $accL)
        '11,6'  = @($acc, $accL)
        '1,11'  = @($acc, $accL)
        '6,6'   = @($accL, $accP)
        '11,11' = @($acc, $accL)
    }
    foreach ($k in $lit.Keys) {
        $xy = $k.Split(','); $cx = [int]$xy[0]; $cy = [int]$xy[1]
        Fill-Rect $b ($cx+1) ($cy+1) ($cx+2) ($cy+2) $lit[$k][0]
        Set-Px $b ($cx+1) ($cy+1) $lit[$k][1]
    }
    return $b
}
Save-Png (New-ParallelProcTier $P.c6Acc $P.c6AccL $P.c6AccP) (Join-Path $blk "ecal_parallel_proc_c6.png")
Save-Png (New-ParallelProcTier $P.c9Acc $P.c9AccL $P.c9AccP) (Join-Path $blk "ecal_parallel_proc_c9.png")

# 线程核心 C6/C9（与 C4 同构：CPU 芯片，运行灯/走线点缀用档位色）
function New-ThreadCoreTier($acc, $accL) {
    $b = New-Panel16
    Fill-Rect $b 3 3 12 12 $P.panelD
    Draw-Border $b 3 3 12 12 $P.frame
    Fill-Rect $b 5 5 10 10 $P.panel
    Draw-Border $b 5 5 10 10 $P.frameD
    foreach ($x in @(4, 6, 8, 10, 12)) { Set-Px $b $x 4 $P.white; Set-Px $b $x 12 $P.white }
    Fill-Rect $b 5 7 10 7 $P.white
    Fill-Rect $b 7 5 7 10 $P.white
    Set-Px $b 6 6 $P.white; Set-Px $b 9 9 $P.white
    foreach ($y in @(5, 7, 9, 11)) { Set-Px $b 3 $y $P.gold; Set-Px $b 4 $y $P.goldL }
    Set-Px $b 11 10 $acc; Set-Px $b 12 11 $accL
    return $b
}
Save-Png (New-ThreadCoreTier $P.c6Acc $P.c6AccL) (Join-Path $blk "ecal_thread_core_c6.png")
Save-Png (New-ThreadCoreTier $P.c9Acc $P.c9AccL) (Join-Path $blk "ecal_thread_core_c9.png")

# 超线程核心 C6/C9（线程核心双核布局：左右双芯片 + 中间 HT 标记，原版 ThreadCoreHyper 母题）
function New-ThreadCoreHyperTier($acc, $accL, $accP) {
    $b = New-Panel16
    # 双芯片主体（左右两核，共享外框）
    Fill-Rect $b 3 3 12 12 $P.panelD
    Draw-Border $b 3 3 12 12 $P.frame
    # 左核
    Fill-Rect $b 4 5 6 10 $P.panel
    Draw-Border $b 4 5 6 10 $P.frameD
    Set-Px $b 5 7 $acc; Set-Px $b 5 8 $accL
    # 右核
    Fill-Rect $b 9 5 11 10 $P.panel
    Draw-Border $b 9 5 11 10 $P.frameD
    Set-Px $b 10 7 $acc; Set-Px $b 10 8 $accL
    # 中间 HT 标记（双竖线 = 超线程）
    Fill-Rect $b 7 5 7 10 $P.white
    Set-Px $b 8 5 $accP; Set-Px $b 8 6 $accP
    Set-Px $b 8 9 $accP; Set-Px $b 8 10 $accP
    # 引脚（上下）
    foreach ($x in @(4, 6, 9, 11)) { Set-Px $b $x 4 $P.white; Set-Px $b $x 12 $P.white }
    # 金色触点列（家族惯例）
    foreach ($y in @(5, 7, 9, 11)) { Set-Px $b 3 $y $P.gold; Set-Px $b 12 $y $P.gold }
    Set-Px $b 3 5 $P.goldL; Set-Px $b 12 5 $P.goldL
    return $b
}
Save-Png (New-ThreadCoreHyperTier $P.c6Acc $P.c6AccL $P.c6AccP) (Join-Path $blk "ecal_thread_core_hyper_c6.png")
Save-Png (New-ThreadCoreHyperTier $P.c9Acc $P.c9AccL $P.c9AccP) (Join-Path $blk "ecal_thread_core_hyper_c9.png")
# C4 超线程核心（共享名 ecal_thread_core_hyper，青色点缀 = C4 档位色；t22 补生成，替换 engineer-core 占位复制）
Save-Png (New-ThreadCoreHyperTier $P.cyan $P.cyanLight $P.cyanPale) (Join-Path $blk "ecal_thread_core_hyper.png")

# 控制器 C6/C9 正面（成型：档位色柱状图 + 数据线）与侧面（档位色状态灯）
function New-ControllerFrontTier($acc, $accL) {
    $b = New-Panel16
    Fill-Rect $b 2 2 13 13 $P.panelD2
    Draw-Border $b 2 2 13 13 $P.frameD
    Fill-Rect $b 2 2 13 2 $P.panelL
    Fill-Rect $b 4 9 4 12 $acc
    Fill-Rect $b 6 8 6 12 $acc
    Fill-Rect $b 8 7 8 12 $accL
    Fill-Rect $b 10 6 10 12 $accL
    Fill-Rect $b 12 8 12 12 $acc
    Fill-Rect $b 3 4 12 4 $acc
    Set-Px $b 4 4 $accL; Set-Px $b 8 4 $accL; Set-Px $b 12 4 $accL
    foreach ($x in @(4, 6, 8, 10, 12)) { Set-Px $b $x 6 $P.white }
    Set-Px $b 2 2 $acc; Set-Px $b 13 2 $P.red
    return $b
}
function New-ControllerOffTier {
    $b = New-Panel16
    Fill-Rect $b 2 2 13 13 $P.panel
    Draw-Border $b 2 2 13 13 $P.frameD
    Draw-Border $b 3 3 12 12 $P.panelD2
    Set-Px $b 5 5 $P.panelD; Set-Px $b 10 10 $P.panelD; Set-Px $b 8 8 $P.panelD2
    Set-Px $b 2 2 $P.frameD; Set-Px $b 13 2 $P.frameD
    return $b
}
function New-ControllerSideTier($acc, $accL, $accP) {
    $b = New-Panel16
    foreach ($i in 0..2) {
        $y0 = 3 + $i * 4
        Fill-Rect $b 3 $y0 12 ($y0 + 1) $P.panelD
        Fill-Rect $b 3 $y0 12 $y0 $P.panelL
        Fill-Rect $b 3 ($y0 + 1) 12 ($y0 + 1) $P.frameD
    }
    Set-Px $b 13 3 $acc; Set-Px $b 13 4 $accL
    Set-Px $b 13 7 $P.gold; Set-Px $b 13 8 $P.goldL
    Set-Px $b 13 11 $acc; Set-Px $b 13 12 $accP
    return $b
}
Save-Png (New-ControllerFrontTier $P.c6Acc $P.c6AccL) (Join-Path $blk "ecal_controller_c6_front.png")
Save-Png (New-ControllerOffTier) (Join-Path $blk "ecal_controller_c6_front_off.png")
Save-Png (New-ControllerSideTier $P.c6Acc $P.c6AccL $P.c6AccP) (Join-Path $blk "ecal_controller_c6_side.png")
Save-Png (New-ControllerFrontTier $P.c9Acc $P.c9AccL) (Join-Path $blk "ecal_controller_c9_front.png")
Save-Png (New-ControllerOffTier) (Join-Path $blk "ecal_controller_c9_front_off.png")
Save-Png (New-ControllerSideTier $P.c9Acc $P.c9AccL $P.c9AccP) (Join-Path $blk "ecal_controller_c9_side.png")

# 晶阵物品 C6/C9（与 ecal_cell_c4 同构，存储条/触点用档位主色系）
function New-CellItemTier($acc, $accL, $accP, [string]$name) {
    $b = New-Canvas 16 16
    Fill-Rect $b 1 1 14 14 $P.panel
    Draw-Border $b 1 1 14 14 $P.frame
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 $P.panelL; Set-Px $b $x 13 $P.panelD }
    for ($y = 2; $y -le 13; $y++) { Set-Px $b 2 $y $P.panelL; Set-Px $b 13 $y $P.panelD }
    foreach ($x in @(3, 5, 7, 9, 11, 13)) { Set-Px $b $x 2 $P.gold; Set-Px $b $x 3 $P.goldL }
    Fill-Rect $b 3 5 12 11 $P.inset
    Draw-Border $b 3 5 12 11 $P.bezelDeep
    foreach ($pi in 0..2) {
        $yy = 6 + $pi * 2
        if ($pi -lt 2) { Fill-Rect $b 4 $yy 9 $yy $acc } else { Fill-Rect $b 4 $yy 9 $yy $accL }
        Set-Px $b 4 $yy $accP
    }
    Fill-Rect $b 10 6 11 11 $P.gold
    Set-Px $b 10 6 $P.goldL
    foreach ($x in @(5, 8, 11)) { Set-Px $b $x 13 $acc }
    Save-Png $b (Join-Path $itm $name)
}
New-CellItemTier $P.c6Acc $P.c6AccL $P.c6AccP "ecal_cell_c6.png"
New-CellItemTier $P.c9Acc $P.c9AccL $P.c9AccP "ecal_cell_c9.png"

# =============================================================
# t27：晶阵 9 尺寸物品贴图（256k..16384m，k 青/M 金/大M 紫分级）
# 分级色对齐档位色语言：k 级=青（C4 4DBFD4 系）、M 级=金（C6 FF9300 系）、
# 大M级=紫（C9 8815D8 系）；同分级内 3 尺寸用容量刻度区分：
# 窗内亮格数 + 右缘竖容量条 + 底部刻度点 = 1/2/3（最小/中/最大）。
# 母题与 ecal_cell_c4 同构（晶阵主体+存储窗口+金接脚），白系面板/暗存储窗口惯例保持。
# =============================================================
function New-CellItemSize($acc, $accL, $accP, [int]$pips, [string]$name) {
    $b = New-Canvas 16 16
    Fill-Rect $b 1 1 14 14 $P.panel
    Draw-Border $b 1 1 14 14 $P.frame
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 $P.panelL; Set-Px $b $x 13 $P.panelD }
    for ($y = 2; $y -le 13; $y++) { Set-Px $b 2 $y $P.panelL; Set-Px $b 13 $y $P.panelD }
    foreach ($x in @(3, 5, 7, 9, 11, 13)) { Set-Px $b $x 2 $P.gold; Set-Px $b $x 3 $P.goldL }
    # 存储窗口（暗色内凹，同 c4 惯例）
    Fill-Rect $b 3 5 12 11 $P.inset
    Draw-Border $b 3 5 12 11 $P.bezelDeep
    # 窗内三格存储条：前 $pips 格点亮（末格亮色 + 左缘高光），其余暗（inset 底）
    foreach ($pi in 0..2) {
        $yy = 6 + $pi * 2
        if ($pi -lt $pips) {
            if ($pi -eq $pips - 1 -and $pi -lt 2) { Fill-Rect $b 4 $yy 9 $yy $accL } else { Fill-Rect $b 4 $yy 9 $yy $acc }
            Set-Px $b 4 $yy $accP
        }
    }
    # 窗右侧金色触点（家族常驻）
    Fill-Rect $b 10 6 11 11 $P.gold
    Set-Px $b 10 6 $P.goldL
    # 右缘竖容量条（E-Storage 先例：x=13，条数 = 容量档）
    foreach ($pi in 0..($pips - 1)) {
        $yy = 6 + $pi * 3
        Fill-Rect $b 13 $yy 13 ($yy + 1) $(if ($pi -eq $pips - 1) { $accL } else { $acc })
    }
    # 底部容量刻度点（点数 = 容量档，同 c4 位置 x=5/8/11）
    foreach ($pi in 0..($pips - 1)) {
        Set-Px $b (5 + $pi * 3) 13 $acc
    }
    Save-Png $b (Join-Path $itm $name)
}
# k 级（青 4DBFD4 系）：256k=1 / 1024k=2 / 4096k=3
New-CellItemSize $P.cyan $P.cyanLight $P.cyanPale 1 "ecal_cell_256k.png"
New-CellItemSize $P.cyan $P.cyanLight $P.cyanPale 2 "ecal_cell_1024k.png"
New-CellItemSize $P.cyan $P.cyanLight $P.cyanPale 3 "ecal_cell_4096k.png"
# M 级（金 FF9300 系）：16m=1 / 64m=2 / 256m=3
New-CellItemSize $P.c6Acc $P.c6AccL $P.c6AccP 1 "ecal_cell_16m.png"
New-CellItemSize $P.c6Acc $P.c6AccL $P.c6AccP 2 "ecal_cell_64m.png"
New-CellItemSize $P.c6Acc $P.c6AccL $P.c6AccP 3 "ecal_cell_256m.png"
# 大M 级（紫 8815D8 系）：1024m=1 / 4096m=2 / 16384m=3
New-CellItemSize $P.c9Acc $P.c9AccL $P.c9AccP 1 "ecal_cell_1024m.png"
New-CellItemSize $P.c9Acc $P.c9AccL $P.c9AccP 2 "ecal_cell_4096m.png"
New-CellItemSize $P.c9Acc $P.c9AccL $P.c9AccP 3 "ecal_cell_16384m.png"

# =============================================================
# t36：并行/线程驱动器（空/filled 两态）+ 核心物品（21 张）
# 命名与 T35 代码逐名对齐（BlockEcalParallelDrive/ThreadDrive registerIcon、
# ItemEcalParallelCore/ItemEcalThreadCore setTextureName）
# 白系家族保持，功能色点缀（青 4DBFD4 / 金 FF9300 / 紫 8815D8）
# =============================================================

# =============================================================
# t42：驱动器材质分主题（并行阵列 vs 线程芯片，用户拍板）
# 并行驱动器正面=多核网格主题；线程驱动器正面=芯片主题；正侧同框不同内容
# =============================================================

# ---------- 并行驱动器正面空态（多核网格主题：舱口内 3x3 网格暗纹） ----------
function New-ParallelDriveFrontEmpty {
    $b = New-Panel16
    Set-Px $b 3 2 $P.cyan; Set-Px $b 6 2 $P.gold; Set-Px $b 9 2 $P.cyanLight; Set-Px $b 12 2 $P.gold
    Fill-Rect $b 2 4 13 12 $P.panelD2
    Draw-Border $b 2 4 13 12 $P.frameD
    Fill-Rect $b 2 4 13 4 $P.panelL
    # 舱口内 3x3 核心网格暗纹（未点亮：C3 网格线 + F1 单元，呼应并行核心物品母题）
    foreach ($cy in @(6, 8, 10)) {
        foreach ($cx in @(3, 6, 9)) {
            Draw-Border $b $cx $cy ($cx+1) ($cy+1) $P.frameD
            Fill-Rect $b $cx $cy ($cx+1) ($cy+1) $P.panelL
        }
    }
    # 左上单元青角标（空态识别）
    Set-Px $b 3 6 $P.cyan; Set-Px $b 4 6 $P.cyanLight
    Set-Px $b 3 7 $P.cyan; Set-Px $b 3 8 $P.cyanLight
    return $b
}
Save-Png (New-ParallelDriveFrontEmpty) (Join-Path $blk "ecal_parallel_drive_front.png")

# ---------- 线程驱动器正面空态（芯片主题：舱口内单芯片暗纹 + 引脚） ----------
function New-ThreadDriveFrontEmpty {
    $b = New-Panel16
    Set-Px $b 3 2 $P.cyan; Set-Px $b 6 2 $P.gold; Set-Px $b 9 2 $P.cyanLight; Set-Px $b 12 2 $P.gold
    Fill-Rect $b 2 4 13 12 $P.panelD2
    Draw-Border $b 2 4 13 12 $P.frameD
    Fill-Rect $b 2 4 13 4 $P.panelL
    # 舱口内芯片暗纹（未点亮：C3 芯片框 + F1 芯体 + 引脚点，呼应线程核心物品母题）
    Draw-Border $b 4 6 11 10 $P.frameD
    Fill-Rect $b 5 7 10 9 $P.panelL
    Set-Px $b 6 7 $P.frameD; Set-Px $b 9 7 $P.frameD
    Set-Px $b 6 9 $P.frameD; Set-Px $b 9 9 $P.frameD
    foreach ($x in @(4, 6, 8, 10)) { Set-Px $b $x 5 $P.frameD; Set-Px $b $x 11 $P.frameD }
    # 芯内青点（空态识别）
    Set-Px $b 7 8 $P.cyan; Set-Px $b 8 8 $P.cyanLight
    return $b
}
Save-Png (New-ThreadDriveFrontEmpty) (Join-Path $blk "ecal_thread_drive_front.png")

# ---------- 驱动器 filled 正面：观察窗内可见插入的核心 ----------
# 并行 filled：窗内 3x3 核心网格（青色点亮 2x2 阵列，呼应并行核心母题）
function New-ParallelDriveFrontFilled {
    $b = New-Panel16
    Set-Px $b 3 2 $P.cyan; Set-Px $b 6 2 $P.cyanLight; Set-Px $b 9 2 $P.cyan; Set-Px $b 12 2 $P.cyanLight
    Fill-Rect $b 2 4 13 12 $P.panelD2
    Draw-Border $b 2 4 13 12 $P.frameD
    Fill-Rect $b 2 4 13 4 $P.panelL
    foreach ($yy in @(6, 12)) {
        Fill-Rect $b 3 $yy 12 $yy $P.panel
        Set-Px $b 3 $yy $P.gold; Set-Px $b 12 $yy $P.gold
    }
    # 观察窗（青辉光框 + 浅青玻璃）
    Draw-Border $b 3 7 12 11 $P.cyanLight
    Fill-Rect $b 4 8 11 10 $P.cyanPale
    # 窗内 3x3 核心阵列（1px 单元，点亮 2x2）
    foreach ($cy in @(8, 9, 10)) {
        foreach ($cx in @(4, 7, 10)) {
            Set-Px $b $cx $cy $P.frameD
        }
    }
    foreach ($p in @(@(4,8),@(5,8),@(4,9),@(5,9))) { Set-Px $b $p[0] $p[1] $P.cyan }
    Set-Px $b 4 8 $P.cyanLight; Set-Px $b 5 9 $P.cyanLight
    Set-Px $b 10 8 $P.cyanPale; Set-Px $b 10 10 $P.cyanPale
    return $b
}
Save-Png (New-ParallelDriveFrontFilled) (Join-Path $blk "ecal_parallel_drive_front_filled.png")

# 线程 filled：窗内单核芯片（白走线 + 青芯运行灯），与超线程物品双核母题呼应
function New-ThreadDriveFrontFilled {
    $b = New-Panel16
    Set-Px $b 3 2 $P.cyan; Set-Px $b 6 2 $P.cyanLight; Set-Px $b 9 2 $P.cyan; Set-Px $b 12 2 $P.cyanLight
    Fill-Rect $b 2 4 13 12 $P.panelD2
    Draw-Border $b 2 4 13 12 $P.frameD
    Fill-Rect $b 2 4 13 4 $P.panelL
    foreach ($yy in @(6, 12)) {
        Fill-Rect $b 3 $yy 12 $yy $P.panel
        Set-Px $b 3 $yy $P.gold; Set-Px $b 12 $yy $P.gold
    }
    Draw-Border $b 3 7 12 11 $P.cyanLight
    Fill-Rect $b 4 8 11 10 $P.cyanPale
    # 窗内线程芯片（芯片框 + 白走线 + 青运行灯）
    Draw-Border $b 5 8 10 10 $P.frameD
    Fill-Rect $b 6 9 9 9 $P.white
    Set-Px $b 6 8 $P.white; Set-Px $b 9 8 $P.white
    Set-Px $b 7 9 $P.cyan; Set-Px $b 8 9 $P.cyanLight
    Set-Px $b 5 8 $P.gold; Set-Px $b 10 10 $P.gold
    return $b
}
Save-Png (New-ThreadDriveFrontFilled) (Join-Path $blk "ecal_thread_drive_front_filled.png")

# ---------- 驱动器侧面（浅灰百叶 + 状态灯列 + 主题小点缀，t42 分主题） ----------
# 并行侧：百叶 + 右下 2x2 网格点缀（多核主题）
function New-ParallelDriveSide {
    $b = New-Panel16
    foreach ($i in 0..2) {
        $y0 = 3 + $i * 4
        Fill-Rect $b 3 $y0 12 ($y0 + 1) $P.panelD
        Fill-Rect $b 3 $y0 12 $y0 $P.panelL
        Fill-Rect $b 3 ($y0 + 1) 12 ($y0 + 1) $P.frameD
    }
    # 右下 2x2 网格点缀（C3 线 + F1 单元 + 青角）
    Draw-Border $b 9 12 10 13 $P.frameD
    Fill-Rect $b 9 12 10 13 $P.panelL
    Set-Px $b 9 12 $P.cyan; Set-Px $b 10 13 $P.cyanLight
    Set-Px $b 13 3 $P.cyan; Set-Px $b 13 4 $P.cyanLight
    Set-Px $b 13 7 $P.gold; Set-Px $b 13 8 $P.goldL
    Set-Px $b 13 11 $P.cyan; Set-Px $b 13 12 $P.cyanPale
    return $b
}
Save-Png (New-ParallelDriveSide) (Join-Path $blk "ecal_parallel_drive.png")
# 线程侧：百叶 + 右下 2x2 芯片点缀（芯片主题）
function New-ThreadDriveSide {
    $b = New-Panel16
    foreach ($i in 0..2) {
        $y0 = 3 + $i * 4
        Fill-Rect $b 3 $y0 12 ($y0 + 1) $P.panelD
        Fill-Rect $b 3 $y0 12 $y0 $P.panelL
        Fill-Rect $b 3 ($y0 + 1) 12 ($y0 + 1) $P.frameD
    }
    # 右下 2x2 芯片点缀（C3 框 + F1 芯 + 引脚点 + 青芯）
    Draw-Border $b 9 12 10 13 $P.frameD
    Set-Px $b 9 12 $P.cyan; Set-Px $b 10 13 $P.cyanLight
    Set-Px $b 8 11 $P.frameD; Set-Px $b 11 14 $P.frameD
    Set-Px $b 13 3 $P.cyan; Set-Px $b 13 4 $P.cyanLight
    Set-Px $b 13 7 $P.gold; Set-Px $b 13 8 $P.goldL
    Set-Px $b 13 11 $P.cyan; Set-Px $b 13 12 $P.cyanPale
    return $b
}
Save-Png (New-ThreadDriveSide) (Join-Path $blk "ecal_thread_drive.png")

# ---------- 并行核心物品 9 张（浅灰面板 + 暗窗口 + 3x3 核心阵列，9 级用阵列亮格数 + 右缘竖条区分） ----------
function New-ParallelCoreItem([int]$lit, [string]$name) {
    $b = New-Canvas 16 16
    Fill-Rect $b 1 1 14 14 $P.panel
    Draw-Border $b 1 1 14 14 $P.frame
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 $P.panelL; Set-Px $b $x 13 $P.panelD }
    for ($y = 2; $y -le 13; $y++) { Set-Px $b 2 $y $P.panelL; Set-Px $b 13 $y $P.panelD }
    foreach ($x in @(3, 5, 7, 9, 11, 13)) { Set-Px $b $x 2 $P.gold; Set-Px $b $x 3 $P.goldL }
    Fill-Rect $b 3 5 12 11 $P.inset
    Draw-Border $b 3 5 12 11 $P.bezelDeep
    # 窗内 3x3 核心阵列（1px 单元 + 1px 间隔）
    $cells = @(@(4,6),@(6,6),@(8,6), @(4,8),@(6,8),@(8,8), @(4,10),@(6,10),@(8,10))
    for ($i = 0; $i -lt $cells.Count; $i++) {
        $cx = $cells[$i][0]; $cy = $cells[$i][1]
        if ($i -lt $lit) { Set-Px $b $cx $cy $P.cyan; Set-Px $b ($cx+1) $cy $P.cyanLight } else { Set-Px $b $cx $cy $P.insetHi }
    }
    # 右缘竖容量条（条数 = 亮格数 / 3 向上取整，1..3）
    $bars = [Math]::Ceiling($lit / 3.0)
    foreach ($pi in 0..($bars - 1)) {
        $yy = 6 + $pi * 3
        Fill-Rect $b 13 $yy 13 ($yy + 1) $(if ($pi -eq $bars - 1) { $P.cyanLight } else { $P.cyan })
    }
    # 底部容量刻度点（点数 = 亮格数 / 3 向上取整）
    foreach ($pi in 0..($bars - 1)) {
        Set-Px $b (5 + $pi * 3) 13 $P.cyan
    }
    Save-Png $b (Join-Path $itm $name)
}
$parVals = @(1, 4, 16, 64, 256, 1024, 4096, 16384, 65536)
for ($i = 0; $i -lt $parVals.Count; $i++) {
    New-ParallelCoreItem ($i + 1) ("ecal_parallel_core_{0}.png" -f $parVals[$i])
}

# ---------- 线程核心物品 6 张：普通（单核芯片 + 线程刻度）/ 超线程（双核 + HT 标记） ----------
function New-ThreadCoreItem([int]$threads, [string]$name) {
    $b = New-Canvas 16 16
    Fill-Rect $b 1 1 14 14 $P.panel
    Draw-Border $b 1 1 14 14 $P.frame
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 $P.panelL; Set-Px $b $x 13 $P.panelD }
    for ($y = 2; $y -le 13; $y++) { Set-Px $b 2 $y $P.panelL; Set-Px $b 13 $y $P.panelD }
    foreach ($x in @(3, 5, 7, 9, 11, 13)) { Set-Px $b $x 2 $P.gold; Set-Px $b $x 3 $P.goldL }
    Fill-Rect $b 3 5 12 11 $P.inset
    Draw-Border $b 3 5 12 11 $P.bezelDeep
    # 窗内单核芯片（芯片框 + 白走线 + 青芯）
    Draw-Border $b 5 7 9 9 $P.frameD
    Fill-Rect $b 6 8 8 8 $P.white
    Set-Px $b 6 7 $P.cyan; Set-Px $b 7 8 $P.cyanLight; Set-Px $b 9 9 $P.cyan
    # 线程刻度（右缘竖条 = 线程数档 1..3）
    $bars = @{ 1 = 1; 4 = 2; 16 = 3 }[$threads]
    foreach ($pi in 0..($bars - 1)) {
        $yy = 6 + $pi * 3
        Fill-Rect $b 13 $yy 13 ($yy + 1) $(if ($pi -eq $bars - 1) { $P.cyanLight } else { $P.cyan })
    }
    foreach ($pi in 0..($bars - 1)) { Set-Px $b (5 + $pi * 3) 13 $P.cyan }
    Save-Png $b (Join-Path $itm $name)
}
New-ThreadCoreItem 1 "ecal_thread_core_1.png"
New-ThreadCoreItem 4 "ecal_thread_core_4.png"
New-ThreadCoreItem 16 "ecal_thread_core_16.png"

# 超线程：双核布局（左右双芯片 + 中央 HT 双竖线），金/紫双色芯（原版 ThreadCoreHyper 母题）
function New-ThreadCoreHyperItem([int]$hyper, [string]$name) {
    $b = New-Canvas 16 16
    Fill-Rect $b 1 1 14 14 $P.panel
    Draw-Border $b 1 1 14 14 $P.frame
    for ($x = 2; $x -le 13; $x++) { Set-Px $b $x 2 $P.panelL; Set-Px $b $x 13 $P.panelD }
    for ($y = 2; $y -le 13; $y++) { Set-Px $b 2 $y $P.panelL; Set-Px $b 13 $y $P.panelD }
    foreach ($x in @(3, 5, 7, 9, 11, 13)) { Set-Px $b $x 2 $P.gold; Set-Px $b $x 3 $P.goldL }
    Fill-Rect $b 3 5 12 11 $P.inset
    Draw-Border $b 3 5 12 11 $P.bezelDeep
    # 左核（金芯）+ 右核（紫芯），中央 HT 双竖线（白）
    Draw-Border $b 4 7 6 10 $P.frameD
    Draw-Border $b 9 7 11 10 $P.frameD
    Fill-Rect $b 5 8 5 9 $P.c6Acc; Set-Px $b 5 8 $P.c6AccL
    Fill-Rect $b 10 8 10 9 $P.c9Acc; Set-Px $b 10 8 $P.c9AccL
    Fill-Rect $b 7 7 7 10 $P.white
    Set-Px $b 8 7 $P.c6AccP; Set-Px $b 8 8 $P.c6AccP
    Set-Px $b 8 9 $P.c9AccP; Set-Px $b 8 10 $P.c9AccP
    # 线程刻度（右缘竖条 = 超线程档 1..3）
    $bars = @{ 2 = 1; 4 = 2; 8 = 3 }[$hyper]
    foreach ($pi in 0..($bars - 1)) {
        $yy = 6 + $pi * 3
        Fill-Rect $b 13 $yy 13 ($yy + 1) $(if ($pi -eq $bars - 1) { $P.c9AccL } else { $P.c9Acc })
    }
    foreach ($pi in 0..($bars - 1)) { Set-Px $b (5 + $pi * 3) 13 $P.c9Acc }
    Save-Png $b (Join-Path $itm $name)
}
New-ThreadCoreHyperItem 2 "ecal_thread_core_hyper_2.png"
New-ThreadCoreHyperItem 4 "ecal_thread_core_hyper_4.png"
New-ThreadCoreHyperItem 8 "ecal_thread_core_hyper_8.png"

# =============================================================
# t57：GUI 按钮图标（16×16 overlay，透明底 + 白系面板块 + 功能色母题）
# 神锻炉按钮风格：本任务只做 16×16 overlay 图标，底纹由 T56/GUI 背景处理
# =============================================================
$gui = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\gui"
New-Item -ItemType Directory -Force -Path $gui | Out-Null

# 按钮 overlay 基底：透明底 + 中央白系面板圆角块（C6 框 + E2 面 + 左上高光）
function New-ButtonBase {
    $b = New-Canvas 16 16
    Fill-Rect $b 2 2 13 13 $P.panel
    Draw-Border $b 2 2 13 13 $P.frame
    for ($x = 3; $x -le 12; $x++) { Set-Px $b $x 3 $P.panelL }
    for ($y = 3; $y -le 12; $y++) { Set-Px $b 3 $y $P.panelL }
    for ($x = 3; $x -le 12; $x++) { Set-Px $b $x 12 $P.panelD }
    for ($y = 3; $y -le 12; $y++) { Set-Px $b 12 $y $P.panelD }
    return $b
}

# 里程碑按钮：旗帜母题（旗杆 + 三角旗，青色系点缀）
$b = New-ButtonBase
# 旗杆（灰白）
Fill-Rect $b 7 4 7 11 $P.frameD
Set-Px $b 7 4 $P.panelL
# 三角旗（青色系：亮青旗 + 青描边 + 飘尾）
Fill-Rect $b 8 4 12 6 $P.cyanLight
Set-Px $b 8 4 $P.cyan; Set-Px $b 8 5 $P.cyan; Set-Px $b 8 6 $P.cyan
Set-Px $b 9 5 $P.white
# 旗尾飘动
Set-Px $b 11 6 $P.cyanPale; Set-Px $b 10 7 $P.cyanPale
# 基座（金）
Fill-Rect $b 5 11 9 11 $P.gold
Set-Px $b 5 11 $P.goldL; Set-Px $b 9 11 $P.goldD
Save-Png $b (Join-Path $gui "ecal_milestone_button.png")

# 升级按钮：上升箭头母题（阶梯 + 箭头，金色系点缀）
$b = New-ButtonBase
# 阶梯（灰白台阶，自左向右上升）
Fill-Rect $b 4 10 5 10 $P.frameD
Fill-Rect $b 6 9 7 9 $P.frameD
Fill-Rect $b 8 8 9 8 $P.frameD
Fill-Rect $b 10 7 11 7 $P.frameD
# 台阶高光
Set-Px $b 4 10 $P.panelL; Set-Px $b 6 9 $P.panelL; Set-Px $b 8 8 $P.panelL; Set-Px $b 10 7 $P.panelL
# 上升箭头（金色系：金杆 + 亮金箭头）
Fill-Rect $b 7 4 9 7 $P.gold
Fill-Rect $b 6 5 10 5 $P.gold
Set-Px $b 7 4 $P.goldBr; Set-Px $b 8 4 $P.goldBr; Set-Px $b 9 4 $P.goldBr
Set-Px $b 6 5 $P.goldBr; Set-Px $b 10 5 $P.goldBr
# 箭头尖端高光
Set-Px $b 8 4 $P.white
# 基底点（青点缀）
Set-Px $b 5 11 $P.cyan; Set-Px $b 11 11 $P.cyanLight
Save-Png $b (Join-Path $gui "ecal_upgrade_button.png")

# =============================================================
# t66：升级树背景图（科技树/星图风格，198x192）
# 深色科技底（近黑深蓝，与 MUI1 screen_blue 协调）+ 星图/节点连线/电路纹理
# 装饰元素以 16px 网格节奏排布（GUI 贴图惯例）
# =============================================================
$W = 198; $H = 192
$bg = New-Canvas $W $H
# 深蓝近黑垂直渐变（顶 #0B1024 -> 底 #04060D）
for ($y = 0; $y -lt $H; $y++) {
    $t = $y / ($H - 1)
    $r = [int](11 + (4 - 11) * $t); $g = [int](16 + (6 - 16) * $t); $b = [int](36 + (13 - 36) * $t)
    $col = [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
    for ($x = 0; $x -lt $W; $x++) { Set-Px $bg $x $y $col }
}
# 16px 网格锚点（每 16px 格交叉点淡点，低对比科技感）
$gridC = [System.Drawing.Color]::FromArgb(255, 30, 40, 68)
for ($gx = 8; $gx -lt $W; $gx += 16) {
    for ($gy = 8; $gy -lt $H; $gy += 16) { Set-Px $bg $gx $gy $gridC }
}
# 电路纹理：几条低对比走线（横/竖 1px）
$traceC = [System.Drawing.Color]::FromArgb(255, 42, 52, 82)
Fill-Rect $bg 24 40 64 40 $traceC
Fill-Rect $bg 64 40 64 72 $traceC
Fill-Rect $bg 64 72 120 72 $traceC
Fill-Rect $bg 120 72 120 104 $traceC
Fill-Rect $bg 32 136 96 136 $traceC
Fill-Rect $bg 96 136 96 160 $traceC
# 电路焊盘点（金暗化）
$padC = [System.Drawing.Color]::FromArgb(255, 84, 66, 26)
foreach ($p in @(@(24,40),@(64,72),@(120,72),@(120,104),@(32,136),@(96,160))) {
    Fill-Rect $bg ($p[0]-1) ($p[1]-1) ($p[0]+1) ($p[1]+1) $padC
}
# 科技树节点连线（青/金 1px 斜线，星图轨道感）
$cyanLine = [System.Drawing.Color]::FromArgb(255, 44, 110, 128)
$goldLine = [System.Drawing.Color]::FromArgb(255, 110, 88, 34)
# 青线：左上节点 -> 右上节点（斜向）
foreach ($i in 0..44) {
    Set-Px $bg (30 + $i) (96 - $i) $cyanLine
}
# 金线：右上节点 -> 左下节点
foreach ($i in 0..50) {
    Set-Px $bg (76 + $i) (150 - [int]($i * 0.7)) $goldLine
}
# 科技树节点（暗芯 + 青/金 1px 环 + 亮心）
function New-TechNode($bmp, [int]$cx, [int]$cy, $ring, $core) {
    foreach ($dy in -2..2) { foreach ($dx in -2..2) {
        if ([Math]::Max([Math]::Abs($dx), [Math]::Abs($dy)) -eq 2) { Set-Px $bmp ($cx+$dx) ($cy+$dy) $ring }
    } }
    Set-Px $bmp $cx $cy $core
    Set-Px $bmp ($cx-1) $cy $core; Set-Px $bmp ($cx+1) $cy $core
}
# 节点布局（升级树三支暗示：青支左、金支右、交汇点）
New-TechNode $bg 30 96 ([System.Drawing.Color]::FromArgb(255, 77, 191, 212)) ([System.Drawing.Color]::FromArgb(255, 128, 225, 255))
New-TechNode $bg 76 150 ([System.Drawing.Color]::FromArgb(255, 222, 200, 68)) ([System.Drawing.Color]::FromArgb(255, 255, 229, 76))
New-TechNode $bg 126 108 ([System.Drawing.Color]::FromArgb(255, 77, 191, 212)) ([System.Drawing.Color]::FromArgb(255, 204, 253, 255))
New-TechNode $bg 158 52 ([System.Drawing.Color]::FromArgb(255, 222, 200, 68)) ([System.Drawing.Color]::FromArgb(255, 242, 233, 181))
# 星点（伪随机确定性散布：白/青/亮白，少量十字光芒）
$rng = New-Object System.Random(20260830)
$starC = @(
    [System.Drawing.Color]::FromArgb(255, 200, 216, 255),
    [System.Drawing.Color]::FromArgb(255, 128, 225, 255),
    [System.Drawing.Color]::FromArgb(255, 255, 255, 255),
    [System.Drawing.Color]::FromArgb(255, 176, 200, 255)
)
for ($i = 0; $i -lt 60; $i++) {
    $sx = $rng.Next(4, $W - 4); $sy = $rng.Next(4, $H - 4)
    $sc = $starC[$rng.Next(0, $starC.Count)]
    Set-Px $bg $sx $sy $sc
    if ($rng.Next(0, 100) -lt 18) {
        Set-Px $bg ($sx-1) $sy $sc; Set-Px $bg ($sx+1) $sy $sc
        Set-Px $bg $sx ($sy-1) $sc; Set-Px $bg $sx ($sy+1) $sc
    }
}
# 边框（内框 + 四角 L 形青/金暗化装饰）
$borderC = [System.Drawing.Color]::FromArgb(255, 24, 34, 58)
Draw-Border $bg 0 0 ($W-1) ($H-1) $borderC
Draw-Border $bg 1 1 ($W-2) ($H-2) ([System.Drawing.Color]::FromArgb(255, 40, 52, 84))
$cornerC = [System.Drawing.Color]::FromArgb(255, 36, 76, 92)
foreach ($c in @(@(2,2), @(($W-3),2), @(2,($H-3)), @(($W-3),($H-3)))) {
    Set-Px $bg $c[0] $c[1] $cornerC; Set-Px $bg ($c[0]+1) $c[1] $cornerC; Set-Px $bg $c[0] ($c[1]+1) $cornerC
    Set-Px $bg ($c[0]-1) $c[1] $cornerC; Set-Px $bg $c[0] ($c[1]-1) $cornerC
}
Save-Png $bg (Join-Path $gui "ecal_upgrade_bg.png")

Write-Host "=== E-Calculator 原创贴图生成完成 ==="
Get-ChildItem $blk -Filter "ecal_*.png" | Sort-Object Name | Select-Object Name, Length
Get-ChildItem $itm -Filter "ecal_*.png" | Sort-Object Name | Select-Object Name, Length
Get-ChildItem $gui -Filter "ecal_*button.png" | Sort-Object Name | Select-Object Name, Length
Get-ChildItem $gui -Filter "ecal_upgrade_bg.png" | Sort-Object Name | Select-Object Name, Length
