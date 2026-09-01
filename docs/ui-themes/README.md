# GTGuiThemes 主题画廊（docs/ui-themes/）

GTNH 1.7.10 GT5U（5.09.54）ModularUI2 的全部 GUI 主题枚举与图鉴，供用户"一个一个看"后选择 ECO 控制器 GUI 的皮肤。

- 来源：`gregtech.api.modularui2.GTGuiThemes`（权威源码，`.research\gt5-src\gregtech\api\modularui2\GTGuiThemes.java`）+ 服务器 mods `gregtech-5.09.54.20.jar` 内贴图（逐像素实测配色）
- 主题机制：机器 MTE 覆写 `getGuiTheme()` 返回某个 `GTGuiTheme` 即换皮肤（如 TecTech 机器默认返回 `GTGuiThemes.TECTECH_STANDARD`）；子主题 `parent(...)` 继承父主题并覆盖部分资源
- 切换方式（用户选定后由工程师执行）：在 `MTEEcoStorageArray` 中覆写 `getGuiTheme()` 返回选中的主题常量即可（无需改贴图；各主题背景纹理均已提取在本文件夹）

---

## 总览表

| 编号 | 主题名（字段） | 主题 ID | 一句话风格 | 适用机器（现用示例） | 文件夹 |
|------|----------------|---------|-----------|----------------------|--------|
| 01 | STANDARD | `gregtech:standard` | 经典 GT 亮灰金属面板 | 几乎所有 GT 默认机器 | [01-STANDARD](01-STANDARD/) |
| 02 | COVER | `gregtech:cover` | STANDARD 暗字变体（覆盖板） | GT 覆盖板配置界面 | [02-COVER](02-COVER/) |
| 03 | BRONZE | `gregtech:bronze` | 暖黄铜/青铜蒸汽时代 | 青铜蒸汽机器 | [03-BRONZE](03-BRONZE/) |
| 04 | STEEL | `gregtech:steel` | 冷蓝灰钢蒸汽时代 | 钢蒸汽机器 | [04-STEEL](04-STEEL/) |
| 05 | PRIMITIVE | `gregtech:primitive` | 棕褐原始/高炉 | 原始机器 | [05-PRIMITIVE](05-PRIMITIVE/) |
| 06 | COKE_OVEN | `gregtech:coke_oven` | 砖窑棕褐（焦炉） | GT 焦炉 | [06-COKE_OVEN](06-COKE_OVEN/) |
| 07 | **TECTECH_STANDARD** | `tectech:standard` | **深海军蓝黑底+灰细边+蓝霓虹**（TecTech 品牌） | 量子计算机/有源变压器/BEC/EOH/DataBank/特斯拉塔 | [07-TECTECH_STANDARD](07-TECTECH_STANDARD/) |
| 08 | GORGE | `gorge` | TecTech 深蓝+神铸专属按钮 | Godforge 神铸系列 | [08-GORGE](08-GORGE/) |
| 09 | EXOFOUNDRY | `exofoundry` | 深靛蓝紫+暗金文字 | 异形铸造厂 | [09-EXOFOUNDRY](09-EXOFOUNDRY/) |
| 10 | NANOCHIP | `nanochip` | 近黑石板+粉白文字 | 纳米芯片机器 | [10-NANOCHIP](10-NANOCHIP/) |
| 11 | INTERGALACTIC_STANDARD | `inntergalactic:standard` | TecTech 深蓝+星空+蓝黄 logo | GTNH-Intergalactic 机器 | [11-INTERGALACTIC_STANDARD](11-INTERGALACTIC_STANDARD/) |
| 12 | BARTWORKS | `bartworks` | 标准灰+绿色 BW logo | BartWorks 机器 | [12-BARTWORKS](12-BARTWORKS/) |

---

## 每个主题怎么"看"

1. 进入对应编号子文件夹，打开 `说明.md`（配色/风格/适用机器），并直接查看里面的 `.png` 背景纹理（浏览器/看图工具）。
2. 各文件夹内容：
   - `01/02/12`：`singleblock_default.png` —— STANDARD 系列灰面板
   - `03`：`bronze.png` + `popup_bronze.png` —— 青铜
   - `04`：`steel.png` + `popup_steel.png` —— 钢
   - `05`：`primitive.png` + `popup_primitive.png` —— 原始
   - `06`：`coke_oven.png` —— 焦炉
   - `07`：`screen_blue.png` + `screen_blue_no_inventory.png` + `tectech_logo.png` —— TecTech
   - `08`：`screen_blue.png` 系（按钮差异需进游戏看）
   - `09`：`foundry_default.png` + `popup_foundry.png` —— 铸造
   - `10`：`nanochip_default.png` + `popup_nanochip.png` —— 纳米芯片
   - `11`：`screen_blue.png` + `space_with_stars.png` + `space_elevator_logo.png` —— 星际
   - `12`：`bw_logo_47x21.png` + `singleblock_default.png` —— BartWorks

---

## 给 ECO 的推荐

- **07 TECTECH_STANDARD**（推荐首选）：与当前 ECO 存储面板（t17 EOH 风格）同源，只需微调配色（背景 `#000020`、霓虹 `#428AFF`/`#03DEFF`、灰标签、`#52FF42`/`#FF4242` 状态色）即完全对齐 TecTech 语言；t24 已给出 176×128 完整落地方案。
- 备选：01 STANDARD（最朴素）、11 INTERGALACTIC（星空）、10 NANOCHIP（石板粉白）。
- 05/06/03/04（蒸汽/原始系）与 E-Storage 的现代科技定位不符，一般不考虑。

---

## 技术备注（工程师查证用）

- 主题定义唯一来源：`gregtech.api.modularui2.GTGuiThemes`（静态字段 STANDARD/COVER/TIERED_VARIANTS/TECTECH_STANDARD/GORGE/EXOFOUNDRY/COKE_OVEN/INTERGALACTIC_STANDARD/NANOCHIP/BARTWORKS）；`TieredVariant.special_variants = {BRONZE, STEEL, PRIMITIVE}`。
- 纹理 ID 常量：`gregtech.api.modularui2.GTTextureIds`（如 `gregtech:bg_standard`、`tectech:picture_tt_logo`）；实际文件位于 jar 的 `assets/gregtech|tectech|gtnhintergalactic|bartworks/textures/gui/**`。
- 子主题继承关系：COVER→STANDARD；BRONZE/STEEL/PRIMITIVE→STANDARD；COKE_OVEN→PRIMITIVE；TECTECH_STANDARD→STANDARD；GORGE/INTERGALACTIC_STANDARD→TECTECH_STANDARD；EXOFOUNDRY/NANOCHIP/BARTWORKS→STANDARD。
- 贴图提取自 `M:\AA科技\GTNH\服务端\mods\gregtech-5.09.54.20.jar`（TecTech/Intergalactic/BartWorks 已并入 GTNH 合并 gregtech）；颜色为 Java 逐像素实测（探针 `D:\DeepSeek\GTNH-ECO\.research\tectech-gui-ref\PngProbe.java`）。
