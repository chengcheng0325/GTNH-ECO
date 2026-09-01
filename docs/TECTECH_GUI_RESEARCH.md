# TecTech GUI 设计语言研究报告

- 版本：v1.0（researcher 产出，任务 t24）
- 目标：为 ECO E-Storage 控制器 GUI（当前 176×128 存储统计面板）提供 **TecTech 风格**的设计规范
- 研究源：`D:\DeepSeek\GTNH-ECO\.research\gt5-src\tectech\` + `gt5-src\gregtech\common\gui\modularui\`（权威一手源码）+ 服务器 mods 的 `gregtech-5.09.54.20.jar`（TecTech 已并入 GTNH 合并 gregtech，资产在 `assets/tectech/textures/gui/`）
- 所有颜色均**实测自 jar 内贴图的像素**（Java 探针逐像素采样，非目测）；贴图已提取到 `D:\DeepSeek\GTNH-ECO\.research\tectech-gui-ref\`（25 个代表文件 + 探针工具 `PngProbe.java`，工程师可自行复跑验证）
- 附注：用户给的 gtnh.huijiwiki 链接（`gtnh.huijiwiki.com/wiki/多方块机器#TecTech风格的图形用户界面`）有 Cloudflare 防护（403），**未抓取**；web_search 亦未找到该页转述，本报告全部结论来自本地一手源码/贴图。

---

## 1. 结论摘要（给 engineer-content 的直接答案）

TecTech 机器 GUI = **深海军蓝黑底（#000020）+ 灰色 2px 边框（#808080）+ 纯黑文本区（#000000/#868686）+ 霓虹蓝/青色数据（#428AFF/#03DEFF）+ 琥珀色强调（#FFD142/#FF8D00）+ 灰阶金属按钮/槽位**；布局 = 顶部机器状态 terminal（标签灰 GRAY / 值青 AQUA / 数字金 GOLD）+ 右侧 18px 竖向按钮列 + 右上角 logo/状态角标 + 底部玩家物品栏。参数面板用 4px 高、6px 单元格的**五色动画分段条**（蓝/青/绿/橙/红）。

当前 ECO GUI（t17 的 GuiEcoStorageController）已采用“EOH 风格”深底+青色霓虹，与 TecTech 语言**高度同源**，只需微调对齐（见 §5 对照表）：背景 `#0C1118` → `#000020`、霓虹 `#4DC3FF` → TecTech 系 `#03DEFF`/`#428AFF`、标签色改灰 GRAY、OK/BAD 改 `#52FF42`/`#FF4242`、能量条改 TecTech 蓝参数条样式。

---

## 2. TecTech GUI 视觉设计语言（逐项）

### 2.1 主色调（实测 hex，来自 jar 贴图像素）

| 用途 | 颜色 | 实测来源 |
|------|------|----------|
| **面板背景填充** | `#000020`（RGB 0,0,32，近黑海军蓝） | screen_blue.png 主体 5848/6480 px |
| **面板边框** | `#808080`（灰，2px，可拉伸 9-patch） | screen_blue.png 边缘 632 px |
| **terminal 文本区填充** | `#000000`（纯黑） | terminal.png 3640 px |
| **terminal 文本区边框** | `#868686`（浅灰） | terminal.png 336 px |
| 按钮标准面 | `#888888` 中灰、高光 `#F8F8F8`、暗边 `#303030` | button_standard_16x16 |
| 按钮亮面（light） | `#C6C6C6` | button_standard_light_16x16 |
| 槽位（mesh/rack） | `#606060`/`#8B8B8B`/`#C5C5C5` 灰阶金属 | overlay_slot_mesh/rack |
| **霓虹蓝（参数条）** | `#428AFF`（亮）/ `#0046BA`（暗边） | picture_parameter_blue |
| **霓虹青（参数条）** | `#03DEFF`（亮）/ `#008194`（暗边） | picture_parameter_cyan |
| **霓虹绿（参数条）** | `#52FF42`（亮）/ `#0B9B00`（暗边） | picture_parameter_green |
| **霓虹橙（参数条）** | `#FFD142`（亮）/ `#BA8C00`（暗边） | picture_parameter_orange |
| **霓虹红（参数条）** | `#FF4242`（亮）/ `#BA0000`（暗边） | picture_parameter_red |
| 禁用/中性参数条 | `#C6C6C6` | picture_parameter_gray |
| 电源开图标 | `#5598FF`（亮蓝）/ `#174BA4`（暗蓝） | power_switch_on |
| 电源关图标 | `#001232`/`#000016`（暗海军蓝） | power_switch_off |
| 热量图标 | `#FFE000`（亮黄）/ `#FF8D00`（橙） | heat_on |
| 统计图标 | `#265AB4`/`#3873D7`/`#103988`（蓝）+ `#FFBF00`（黄强调） | statistics |
| TecTech logo | `#0072FF`（蓝）+ 白高光 | tectech_logo |
| 辉光背景（EOH 档位色） | 蓝 `#3838D0`/`#001028`、绿 `#18A040`/`#002000`、橙 `#E08010`/`#181810`、红 `#980000`/`#280000`、白 `#202020`/`#101010` | background/*_glow（300×300 径向辉光） |

文字色（源码里的 `EnumChatFormatting`）：标签/说明 **GRAY**、值/名称 **AQUA**、数字 **GOLD**（见 §2.3）；标题/运行行 **WHITE**（`Color.WHITE.main`）。

### 2.2 背景与面板（screen_blue 体系）

- **screen_blue.png（90×72，2px 边框，`AdaptableUITexture` 可 9-patch 拉伸）**：填充 `#000020` 深海军蓝黑 + 灰 `#808080` 边框。这是 TecTech 所有机器面板/文本区的标准底。
- **screen_blue_no_inventory.png（190×171）**：同色系整面板（含灰色边框、右下角透明缺口）。
- **terminal.png（142×28，`adaptable(4)`）**：纯黑填充 + 浅灰边框 —— 机器“终端文本区”的底。
- **辉光系列（300×300）**：`blue/green/orange/purple/red/rainbow/white_glow` —— 深色底 + 中心径向彩色辉光 + 白/灰边缘羽化，用作 EOH 等级背景/氛围光（配色即 §2.1 辉光行）。
- **space.png / star.png**：EOH 空间背景（星点）素材。

> 结论：TecTech 的“背景纹理风格”不是花哨斜线/电路，而是**大块纯深海军蓝 + 细灰边框**的极简科技感；视觉丰富度全靠**控件层**（参数条、logo、辉光、槽位、图标按钮）叠加。ECO 面板照此即可：纯色底 + 2px 灰框 + 少量霓虹装饰线/角标。

### 2.3 标题栏与数据行（terminal 文本区排版）

`MTEMultiBlockBaseGui.createTerminalTextWidget` 的 ListWidget 按行堆叠（`fullWidth()`、左对齐、行间 `marginBottom(2)`）：
- 机器模式行、启动检查行、运行行：**WHITE**。
- 停机时长/停机原因/结构错误/配方结果：动态 IKey 文本，`setEnabledIf` 条件显示。
- BEC 系附加行（`MTEBECMultiblockBaseGui.createCondensateWidget`）格式：
  - `GRAY + 标题`（如 “Available Condensate:”）
  - `GRAY + "  " + AQUA + 名称 + GRAY + " x " + GOLD + 数量` —— **标签灰、名称青、数字金**的三段式数据行。
- `MTEBECAssemblerGui` 的 nanite 行同款：`GRAY 标题 + AQUA 等级 + GOLD 数量`。
- `MTEBECStorageGui` 用 `SettingsPanelBuilder.addReadout(标签, 同步值, 格式化)` 在参数面板加只读统计行。

### 2.4 按钮 / 槽位 / 进度条 / 参数条

- **按钮**：`standard_16x16`/`standard_light_16x16` 灰阶斜切（左上高光 `#F8F8F8`、右下暗 `#303030`）；overlay 按钮（power_switch/heat/power_pass/safe_void/statistics/structure_check/batch_mode/input_separation/sound/trash_can 等）均为 **16×16 透明底 + 单色系图标**（开=亮色图标，关=暗色图标，disabled=灰）。
- **槽位**：`overlay_slot/mesh`（金属网眼）与 `overlay_slot/rack`（金属横条架）两种 18×18 灰阶槽底；控制器槽额外叠 `heat_sink_small`（18×6 银白散热片小图标）。
- **进度条**：`progressbar/research_station_1/2/3`、`godforge_plasma`（图片式条，非纯色矩形）。
- **参数条（TecTech 招牌）**：`picture/parameter_blue|cyan|green|orange|red`（158×4 长条，`UITexture.partly` 切成 20 个 6px 单元格 = 20 帧动画）——**4px 高、分段发光的动画状态条**，蓝/青/绿/橙/红按值/档位变色，`parameter_gray` 为禁用态。参数面板（SettingsPanel）即用这些条 + TextField 编辑器展示/调参。
- **logo**：`picture/tectech_logo`（18×18，蓝 `#0072FF` 白边）放 terminal 右下角（`makeLogoWidget`，`PICTURE_LOGO` 主题槽）。

### 2.5 布局骨架（TTMultiblockBaseGui / MTEMultiBlockBaseGui 实测）

```
面板 198×203（getBasePanelWidth=198, Height=181+22 间隙）
├─ createMainColumn（padding=边框半径）
│  ├─ terminal 行 190×94（不绑玩家物品栏时 174 高）
│  │  └─ terminal 父组件（BACKGROUND_TERMINAL 主题=黑底灰边 或 TecTech screen_blue）
│  │     ├─ terminal 文本 ListWidget（左对齐多行状态文本）
│  │     ├─ 右上角列（右下对齐）：停机原因 hover、维护问题 hover、TecTech logo 18×18
│  │     └─ 左上角列（左下对齐）：（可选角标）
│  ├─ 消音按钮（右上 13px 外缩）
│  ├─ 面板间隙 22px
│  └─ 玩家物品栏行（supportsInventoryRow）
└─ 右侧按钮列 Flow.column width=18 rightRel(1,-2,1) 底对齐：
   power_pass 开关 → 编辑参数按钮（开参数面板）→ power_switch 电源 → （控制器槽+散热片图标）
```

- 主题挂钩：`TTMultiblockBase.getGuiTheme()` 返回 **`GTGuiThemes.TECTECH_STANDARD`**（parent=STANDARD，仅两处覆写：`PICTURE_LOGO → tectech_logo`、`BACKGROUND_TERMINAL → gregtech:gui/background/screen_blue`）——**TecTech 风格 = 标准 GT 布局 + 换 terminal 底为 screen_blue + 换 logo**。
- Godforge 系再派生 `GORGE`（自定义按钮纹理）；EOH/DataBank/BEC/特斯拉塔/主动变压器全部共用 `TTMultiblockBaseGui` + `TECTECH_STANDARD`。

---

## 3. 代表 GUI 布局（ASCII 草图）

### 3.1 BEC 组装机 / BEC 存储（MTEBECAssemblerGui / MTEBECStorageGui）

```
┌──────────────────────────────────────────────┐ ← 面板 198 宽，screen_blue 底+灰边
│  机器名/状态（terminal 黑底区，190×94）          │
│  ┌────────────────────────────────────────┐   │
│  │ 模式: Assembler            [logo 18px] │   │ ← 右上角 TecTech logo
│  │ 启动检查: OK                            │   │
│  │ 运行中                                  │   │
│  │ Available Condensate:                  │   │
│  │   Hydrogen(?) x 123.4k   ← GRAY/AQUA/GOLD │
│  │ Providing Nanites:                      │   │
│  │   Tier 4 x 5,000          ← 同上三段式    │   │
│  └────────────────────────────────────────┘   │
│                                              │
│  [玩家物品栏行: 27+9 槽]                      │
└──────────────────────────────────────────────┘
                    右侧 18px 按钮列: [power_pass][参数][电源]
```

### 3.2 通用 TecTech 多块（TTMultiblockBaseGui 本体，DataBank/EOH/特斯拉塔同款）

```
┌───────────────────────────────┬───┐
│ terminal 黑底/蓝底文本区         │ ⏻ │ ← 右侧按钮列(18px,底对齐): power_pass、
│ 模式/启动/运行/停机原因/结构错误  │ ⚙ │   编辑参数(开 SettingsPanel)、power_switch、
│ 配方结果等多行状态文本           │ 🔌 │   控制器槽(+散热片小图标)
│                          [logo]│   │
├───────────────────────────────┴───┤
│  (22px 间隙)                       │
│ 玩家物品栏 3×9 + 快捷栏 9           │
└──────────────────────────────────┘
```

### 3.3 EOH / DataBank（机器 GUI = 同基类；此处另附 EOH 的 NEI 配方前端样式）

`EyeOfHarmonyFrontend`（NEI 页）：配方背景 **170×115**，左上 logo(8,8)，物品输入 1 格在 (79,8)，输出网格 9 列 ×12 行自 (7,44) 起，流体输出 9 列 ×2 行；输出物品角标白字（0xffffff, 0.5 缩放, 右下对齐）显示数量；特殊信息（氢气/氦气输入、时空层级、EU 收支、基础成功率）用灰字列表。

---

## 4. 176×128 ECO 存储统计面板的 TecTech 风格设计建议

### 4.1 配色（对照现有实现，直接替换）

| 元素 | TecTech 规范值 | 当前 ECO（t17） | 建议 |
|------|----------------|-----------------|------|
| 面板背景填充 | `#000020` | `#0C1118`（C_TRACK） | 改 `#000020`（或保留 `#0C1118`，二者同属近黑海军蓝，差异极小） |
| 面板边框/描边 | `#808080`（2px） | 无 | 加 2px 灰边（可在贴图里画，或 drawRect 描边） |
| 标题文字 | WHITE `#FFFFFF` | `#FFFFFFFF` | 保持白 |
| 标签文字 | GRAY `#AAAAAA`（chat GRAY） | `#A8C4D4` 蓝灰 | 改纯灰 `#AAAAAA`（更 TecTech） |
| 数值/强调文字 | AQUA `#55FFFF` / 蓝 `#428AFF` | `#4DC3FF` 青 | 用 TecTech 参数蓝 `#428AFF` 或青 `#03DEFF` |
| 金色数字 | GOLD `#FFAA00`（仅数字） | — | 数值可用 GOLD 段点缀（如 Columns/Drives 数字） |
| OK/有效 | `#52FF42`（参数绿） | `#4DE3A5` | 改 `#52FF42` |
| BAD/无效 | `#FF4242`（参数红） | `#FF5C5C` | 改 `#FF4242` |
| 能量条填充 | TecTech 蓝参数条 `#428AFF` 亮芯 `#8FE3FF` | `#4DC3FF`+`#8FE3FF` 亮芯 | 改 `#428AFF` 填充 + 白/亮青芯；可做成 4px 高分段条（每 8px 一段）仿 parameter_blue |
| 能量条轨道 | `#000000` 或 `#001028` + 灰边 | `#0C1118`+`#2A3B4A` 边 | 轨道 `#000000`、边 `#808080` |

### 4.2 布局 ASCII（176×128，可直绘）

```
┌──────────────────────────────────────────┐ ← 176×128，2px 灰边(#808080)，底 #000020
│ ECO E-Storage Array          [L4]  │ ← 标题 WHITE + 档位标签 蓝(#428AFF) 右对齐
│ ─────────────────────────────────  │ ← 霓虹下划线（蓝/青渐变 1-2px，仿 TecTech 标题线）
│ Structure:      Valid              │ ← GRAY 标签左 + OK绿#52FF42 值右
│ Drives:         12                 │ ← 值 AQUA#03DEFF（数字可 GOLD）
│ Columns:        3                  │ ← 同上
│ Energy:         2.4M / 10M         │ ← 同上
│ EU Input:       LV                 │ ← 同上
│                                   │
│ ┌──────────────────────────────┐   │
│ │▇▇▇▇▇▇▇▇▇▇▇░░░░░░░░░░░░░░  24%│ ← 能量条：轨道#000000+灰边，填充#428AFF分段
│ └──────────────────────────────┘   │
│  Energy:                      24%  │ ← GRAY 标签 + 百分比 AQUA（右对齐）
└──────────────────────────────────────────┘
装饰（可选，增强 TecTech 感）：
· 右上角 18×18 TecTech logo 或自定义 ECO logo（蓝 #0072FF 系）
· 左上/右下角 45° 斜线点缀（1px，#428AFF 半透明 0x22/0x66 双层）
· 左侧 1-2px 竖霓虹线（#428AFF 半透明，2~104px）
· 电路角标（3×3 节点 + 连线，仿现有 drawCircuitNode，改 #428AFF）
```

### 4.3 实现途径（engineer-content）

1. **贴图法（推荐）**：把 `assets/ecoaegtnh/textures/gui/estorage_controller.png` 重绘为 176×128：底 `#000020`、2px 灰边 `#808080`、标题区/数据区分隔线、能量条轨道与底座、可选斜线/角标装饰；保留透明区域给动态文字。参考贴图已提取在 `.research\tectech-gui-ref\background_screen_blue_no_inventory.png`（190×171）与 `background_screen_blue.png`（90×72 角块）。
2. **代码法（当前实现已具备）**：`GuiEcoStorageController` 里替换常量值（§4.1 表）+ 追加 drawRect 描边/斜线/logo（现有 drawCircuitNode/drawDiagonal 直接复用，改色即可）；能量条 fill 颜色与亮芯沿用现有绘制逻辑。
3. 可选：把面板尺寸从 176×128 扩到 TecTech 标准（198×203）并加右侧按钮列/玩家物品栏——**仅当产品要求完整 TecTech 化时做**；176×128 统计面板保持紧凑即可，套用配色与装饰即达成风格统一。

### 4.4 与 wiki 描述的对应

gtnh.huijiwiki「多方块机器#TecTech风格的图形用户界面」原文被 Cloudflare 拦截（403），web_search 未找到转述。据本地实证，wiki 所描述的“TecTech 风格”即：深蓝黑底 + 灰色边框 + 青色/金色数据文本 + 右侧竖向按钮列的这套语言（§2/§3）。本报告的配色/布局均以源码与贴图像素为准，比 wiki 文字更精确。

---

## 5. 关键 FQCN 清单（供 engineer-content 查证）

```
// TecTech 侧（.research\gt5-src\tectech\）
tectech.thing.gui.TecTechUITextures                     // 纹理注册表：BACKGROUND_SCREEN_BLUE / OVERLAY_SLOT_MESH / PICTURE_PARAMETER_* 等
tectech.thing.gui.bec.MTEBECAssemblerGui                // 参考：terminal 文本行 + nanite/condensate 三段式数据
tectech.thing.gui.bec.MTEBECStorageGui                  // 参考：addReadout 统计行
tectech.thing.gui.bec.MTEBECDiodeGui
tectech.thing.gui.bec.MTEBECIONodeGui                   // 参考：参数驱动的状态文本 + TextField 调参
tectech.thing.gui.bec.MTEBECMultiblockBaseGui           // BEC 基类（createCondensateWidget：GRAY/AQUA/GOLD 格式）
tectech.recipe.EyeOfHarmonyFrontend                     // EOH NEI 前端（170×115，9 列网格，白字角标）
tectech.thing.metaTileEntity.multi.base.TTMultiblockBase // getGuiTheme() → GTGuiThemes.TECTECH_STANDARD
tectech.thing.metaTileEntity.multi.base.parameter.Parameter / IntegerParameter / NumericParameter / BooleanParameter / EnumParameter / FluidParameter / StringParameter / CompositeParameter
tectech.thing.metaTileEntity.multi.base.parameter.SettingsPanelParameterCompat

// GT5U 侧（.research\gt5-src\gregtech\）
gregtech.common.gui.modularui.multiblock.base.TTMultiblockBaseGui   // TecTech 多块 GUI 基类（右按钮列/参数面板/控制器槽）
gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui // 布局骨架：198 面板、terminal 行、右侧列、物品栏行
gregtech.api.modularui2.GTGuiThemes                                // TECTECH_STANDARD / GORGE
gregtech.api.modularui2.GTGuiTextures                              // TT_BACKGROUND_TEXT_FIELD / TT_OVERLAY_SLOT_MESH / TT_CONTROLLER_SLOT_HEAT_SINK ...
gregtech.api.modularui2.GTWidgetThemes                             // BACKGROUND_TERMINAL / PICTURE_LOGO / TEXT_TITLE ...
gregtech.api.modularui2.GTTextureIds                               // BACKGROUND_TERMINAL_TECTECH / PICTURE_TECTECH_LOGO ...
gregtech.common.gui.modularui.widget.settings.SettingsPanelBuilder // addReadout / addIntEditor / addEnumCycleButton / addToggleButton / build(...)
gregtech.common.gui.modularui.widget.LineChartWidget               // 图表控件（统计页可选）
com.cleanroommc.modularui.screen.ModularPanel / widgets.*          // ModularUI2 控件库（面板/Flow/ListWidget/TextWidget/ButtonWidget/TextFieldWidget）

// 本项目当前实现（对比用）
ecoaegtnh.gui.GuiEcoStorageController          // 176×128 面板；常量 C_NEON=0xFF4DC3FF / C_TRACK=0xFF0C1118 ...
ecoaegtnh.gui.ContainerEcoStorageController    // Forge progress-bar 同步 6 统计值
ecoaegtnh.gui.EcoAEGuiHandler
```

---

## 6. 纹理资源清单（jar 内路径 → 已提取到 .research\tectech-gui-ref\）

| jar 内路径（gregtech-5.09.54.20.jar） | 提取名 | 尺寸/说明 |
|--------------------------------------|--------|-----------|
| assets/tectech/textures/gui/background/screen_blue.png | background_screen_blue.png | 90×72，底 #000020 + 灰边 |
| assets/tectech/textures/gui/background/screen_blue_no_inventory.png | background_screen_blue_no_inventory.png | 190×171 整面板 |
| assets/tectech/textures/gui/background/terminal.png（gregtech 命名空间） | background_terminal.png | 142×28 黑底灰边 |
| assets/tectech/textures/gui/background/blue|green|orange|red|white_glow.png | background_*_glow.png | 300×300 径向辉光 |
| assets/tectech/textures/gui/button/standard_16x16.png / standard_light_16x16.png | button_*.png | 灰阶斜切按钮 |
| assets/tectech/textures/gui/overlay_button/power_switch_on|off.png、heat_on.png、statistics.png | overlay_button_*.png | 16×16 图标 |
| assets/tectech/textures/gui/overlay_slot/mesh.png、rack.png | overlay_slot_*.png | 18×18 槽底 |
| assets/tectech/textures/gui/picture/parameter_blue|cyan|green|orange|red.png、parameter_gray.png | picture_parameter_*.png | 158×4 动画参数条 |
| assets/tectech/textures/gui/picture/heat_sink_small.png | picture_heat_sink_small.png | 18×6 散热片 |
| assets/tectech/textures/gui/picture/tectech_logo.png | picture_tectech_logo.png | 18×18 蓝 logo |
| assets/tectech/textures/gui/progressbar/research_station_1.png | progressbar_research_station_1.png | 进度条参考 |

其余（未提取，jar 内可查）：overlay_button 全系列（uncertainty/0-15、safe_void、power_pass、batch_mode、input_separation、sound、trash_can、asteroid…）、picture/rack_large、heat_sink、uncertainty monitor、godforge 系、background/space.png、star.png、assLineRender.png 等。

---

## 7. 关键结论

1. **TecTech 风格的本质**：标准 GT ModularUI 布局 + `TECTECH_STANDARD` 主题（terminal 底换 `screen_blue`、logo 换 TecTech）+ 右侧竖向按钮列 + 黑底灰边 terminal 文本区 + GRAY/AQUA/GOLD 三段式数据行 + 五色 4px 动画参数条。
2. **ECO 176×128 面板的落地方案**：保持紧凑尺寸与现有 EOH 骨架，替换配色为 §4.1 表（背景 #000020、标签灰、数值青/金、OK #52FF42、BAD #FF4242、能量条 #428AFF 分段亮芯），加 2px 灰描边与可选 logo/斜线/角标装饰即可完全对齐 TecTech 语言；重绘贴图（§4.3 途径 1）效果最佳。
3. **不要做的事**：不要引入花哨斜线/电路满铺背景（TecTech 底就是纯色+灰边）；不要改 176×128 尺寸（除非产品要求完整 198×203 + 玩家物品栏布局）。
