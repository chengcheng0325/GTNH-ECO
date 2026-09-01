# E-Calculator 原创贴图视觉验收报告（t10）

- 验收人：vision（贴图/截图视觉验收）
- 验收对象：`src/main/resources/assets/ecoaegtnh/textures/blocks/` 下 9 张 16×16 原创贴图
- 参考物：`tools/ecal-textures-preview.png`（8× 放大拼接预览）
- 方法：因本会话模型无直接图像输入且 describe_image 插件 baseURL 未配置，改为**像素级程序化验收**——
  用 System.Drawing 逐像素解码预览图与全部贴图 PNG，重建 16×16 网格、色彩直方图、面板槽位几何、标签文字结构，
  并与 1.12.2 原版仓库（`.research/NovaEngineering-ECOAEExtension-main`）纹理做相似度矩阵对比。
- 分析脚本与转储：`tools/vision/`（slots_analysis.ps1 / actual_textures.ps1 / similarity_check.ps1 / dump/）

---

## 一、结论

**verdict = needs_revision（轻量）**

9 张贴图本体质量良好、风格统一、原创、成型/非成型区分清晰；
但存在 2 项必须修复项（预览图陈旧不一致、parallel_proc 母题与设计描述不符），修复成本低。

---

## 二、四个验收维度

### ① 风格一致性 —— PASS（强）

9 张全部共享同一设计语言：

- 统一浅灰面板边框（L 级 ~198-205 灰）包裹 16×16 画布；
- 统一强调色板：青 rgb(77,191,212) / 亮青 rgb(128,225,255) / 淡青 rgb(204,253,255)；
  金 rgb(222,200,68) / 淡金 rgb(242,233,181) / 亮金 rgb(255,229,76)；
- 控制器正面/非成型正面共享深色 K 边框设计；控制器侧面与其余方块共享 L 边框 + 横向条带语言；
- 唯一的无彩色贴图是 casing（纯灰）与 front_off（黑屏），与其功能定位一致。

### ② 原创性 —— PASS（量化证据）

与原版 1.12.2 关键贴图逐像素相似度矩阵（同像素% = 每通道差≤20 的像素占比）：

| 新贴图 | data_bus | storage_array_mebus | carbon_fiber_chassis | l6/l9_controller | module_parallel_unit | ec_line |
|---|---|---|---|---|---|---|
| ecal_casing | 57.4 | 21.4 | 0 | 0 | 0 | 2.4 |
| ecal_parallel_proc | 46.5 | 15.1 | 0 | 0 | 0 | 7.1 |
| ecal_thread_core | 50.4 | 13.5 | 0 | 0 | 0 | 2.4 |
| ecal_cell_drive | 43.8 | 7.9 | 13.3 | 0 | 0 | 2.4 |
| ecal_me_channel | 50.4 | 12.7 | 12.5 | 0 | 0 | 2.4 |
| ecal_transmitter_bus | 36.3 | 8.3 | 0 | 0 | 0 | 7.1 |
| ecal_controller_front | 24.2 | 4.0 | 20.3 | 0 | 0 | 0 |
| ecal_controller_front_off | 23.4 | 0.8 | 50.4 | 0 | 0 | 0 |
| ecal_controller_side | 49.6 | 17.5 | 11.7 | 0 | 0 | 2.4 |

解读：

- 对原版**母题纹理**（l6/l9_controller、module_parallel_unit、off、ec_line、carbon_fiber_chassis）相似度基本为 0% —— 无像素级拷贝；
- 对 data_bus 的 23-57% 相似度来自**共享的浅灰边框设计语言**（L 框 + 白底），非内容拷贝（data_bus 内容是金色端子条，casing 是白底+角点）；
- front_off 与 carbon_fiber_chassis 的 50.4% 是"同为深色/黑"造成的颜色重合，非结构拷贝；
- 结论：风格呼应原版工业计算观感（浅灰面板+青/金），构图全部原创。

### ③ 可辨识性 —— 部分通过（1 项弱）

| 贴图 | 母题 | 判定 |
|---|---|---|
| ecal_casing | 白底+四角 1px 螺钉点 | ✓（但太淡，见 F3） |
| ecal_parallel_proc | 稀疏青/金散点（X 形 10 点） | ✗ 弱 —— 与"4×4 核心阵列网格"描述不符，且与 casing 同属"白底+小点"，邻放时难区分 |
| ecal_thread_core | 内框+金色走线列+青色运行灯 | ✓ |
| ecal_cell_drive | 深色舱体+金色触点+三档青窗（行 5/8/11） | ✓ |
| ecal_me_channel | 深色端口环+金色角点+青色连接核 | ✓（描述中的"对角切口"未体现，见 F4） |
| ecal_transmitter_bus | 上下深色端子条+青色能量带 | ✓ |
| ecal_controller_front | 黑屏+青色数据带+金色柱状图 | ✓ |
| ecal_controller_front_off | 纯黑屏 | ✓ |
| ecal_controller_side | 三横散热百叶+青/金状态灯列 | ✓ |

### ④ 成型/非成型控制器正面区分 —— PASS（清晰）

- 成型 front：K 深框 + 深底 + 行 4-5 青色数据带 + 行 7-12 金色柱状图（9 段递增）；
- 非成型 front_off：K 深框 + 全黑内屏；
- 一眼可分，无需 label。

---

## 三、修复项（needs_revision 依据）

- **F1 [高] 预览图与交付文件不一致**：`tools/ecal-textures-preview.png` 明显渲染自旧版贴图。
  证据：预览 SLOT1（r1c1）为纯白面板，无 casing 的四角螺钉点；SLOT6 为大面积青色横带，
  与当前 ecal_parallel_proc（散点）不符；SLOT7 与当前 ecal_controller_front 构图不同；
  SLOT10（r3c2）又出现一个"金色柱状图+青色块"的控制器正面构图（当前文件只有一张 front）。
  另：预览 3 行 × 4 列共 12 槽位，仅 9 张有内容（r3c3/c4 空）。
  → 修复：用当前 9 张 PNG 重新生成拼接预览图，保持 8× 放大 + 文件名标注，并确保槽位一一对应。
- **F2 [中] ecal_parallel_proc 母题与设计描述不符**：描述为"4×4 核心阵列网格，青/金点亮单元"，
  实际为 10 个 1px 散点（青 6 + 金 4，呈 X 形对角分布），16×16 下无明显阵列结构，
  游戏内与 casing 同屏时几乎不可区分。
  → 修复：改为可见的 4×4（或 3×3）核心单元网格：单元格用浅灰/深灰描边或 2px 内芯，
  其中若干单元以青色/金色填充表示"点亮"，间距 2px 保证 16×16 内可读。

## 四、可选优化（不阻塞）

- **F3 [低]** casing 四角螺钉为 1px 浅灰点（L 级 ~202 vs 白底 ~226），对比度过低，游戏内基本不可见；
  建议加深为 2px 深灰/近黑点或加一圈深色描边。
- **F4 [低]** me_channel 描述含"对角切口"，当前端口环为完整圆角矩形；可加 1-2px 对角亮线呼应描述（非必须）。
- **F5 [低]** casing 与 front_off 是全图仅有的两张零彩色贴图；casing 可考虑加一个极小的青/金角标
  强化"同一家族"观感（可选，若保留素面也成立）。

## 五、附：当前 9 张实际贴图 16×16 网格（权威数据）

```
ecal_casing:          ecal_parallel_proc:   ecal_thread_core:     ecal_cell_drive:
LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL
LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL
LWLWWWWWWWWWWLWL      LWCWWWWWWWWWWLWL      LWLWWWWWWWWWWLWL      LWLCWWCWWCWWCLWL
LWWLWWWWWWWWWWLL      LWWCWWWWWWWWWWLL      LWWLLLLLLLLLLWLL      LWWLWWWWWWWWWWLL
LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL      LWWLWWWWWWWWWWWL      LWKKKKKKKKKKKKWL
LWWWWWWWWWWWWWWL      LWWWWCWWWWWYWWWL      LWWYYLLWLLLWLWWL      LWKYYDCCDDDDMKWL
LWWWWWWWWWWWWWWL      LWWWWWCWWWWWWWWL      LWWLWLWWWWLWLWWL      LWDDDDDDDDDDDDWL
LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL      LWWYYWWWWWWWLWWL      LWKDDDDDDDDDDKWL
LWWWWWWWWWWWWWWL      LWWWWWWWYWWWWWWL      LWWLWLWWWWLWLWWL      LWKYYDCCDDDDYKWL
LWWWWWWWWWWWWWWL      LWWWWWWWWYWWWWWL      LWWYYLWWWWLWLWWL      LWDDDDDDDDDDDDWL
LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL      LWWLWLLWLLLCLWWL      LWKDDDDDDDDDDKWL
LWWWWWWWWWWWWWWL      LWWWWYWWWWWCWWWL      LWWYYWWWWWWWCWWL      LWKYYDCCDDDDMKWL
LWWWWWWWWWWWWWWL      LWWWWWWWWWWWCWWL      LWWLWLWLWLWLWWWL      LWKKKKKKKKKKKKWL
LWLWWWWWWWWWWLWL      LWLWWWWWWWWWWLWL      LWLWWWWWWWWWWLWL      LWLWWWWWWWWWWLWL
LWWLWWWWWWWWWWLL      LWWLWWWWWWWWWWLL      LWWLWWWWWWWWWWLL      LWWLWWWWWWWWWWLL
LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL

ecal_me_channel:      ecal_transmitter_bus: ecal_controller_front: ecal_controller_side:
LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL
LWWWWWWWWWWWWWWL      LWWWWWWWWWWWWWWL      LKKKKKKKKKKKKKKL      LWWWWWWWWWWWWWWL
LWLWWWWWWWWWWLWL      LWLDDDDDDDDDDLWL      LKCDDDDDDDDDDYKL      LWLWWWWWWWWWWLWL
LWWYKKKKKKKKYWLL      LWWDDDDDDDDDDWLL      LKDLLLLLLLLLLDKL      LWWMMMMMMMMMMCLL
LWWKWDDDDDDWKWWL      LWWWWWWWWWWWWWWL      LKDCCCCCCCCCCDKL      LWWKKKKKKKKKKCWL
LWWKDDDDDDDDKWWL      LWDDDDDDDDDDDDWL      LKDLLLLLLLLLLDKL      LWWWWWWWWWWWWWWL
LWWKDDCCCCDDKWWL      LCYCCCCCCCCCCYCL      LKDLWLWLWLWLWDKL      LWWWWWWWWWWWWWWL
LWWKDDCWCCDDKWWL      LCCCCCCCCCCCCCCL      LKDLLLLLLLYLLDKL      LWWMMMMMMMMMMYWL
LWWKDDCCCCDDKWWL      LCCCWCCWCCWCCWCL      LKDLLLLLYLYLLDKL      LWWKKKKKKKKKKYWL
LWWKDDCCCCDDKWWL      LCYCCCCCCCCCCYCL      LKDLLLYLYLYLYDKL      LWWWWWWWWWWWWWWL
LWWKDDDDDDDDKWWL      LWDDDDDDDDDDDDWL      LKDLYLYLYLYLYDKL      LWWWWWWWWWWWWWWL
LWWKWDDDDDDWKWWL      LWWWWWWWWWWWWWWL      LKDLYLYLYLYLYDKL      LWWMMMMMMMMMMCWL
LWWYKKKKKKKKYWWL      LWWDDDDDDDDDDWWL      LKDLLLLLLLLLLDKL      LWWKKKKKKKKKKCWL
LWLWWWWWWWWWWLWL      LWLDDDDDDDDDDLWL      LKDDDDDDDDDDDDKL      LWLWWWWWWWWWWLWL
LWWLWWWWWWWWWWLL      LWWLWWWWWWWWWWLL      LKKKKKKKKKKKKKKL      LWWLWWWWWWWWWWLL
LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL      LLLLLLLLLLLLLLLL

ecal_controller_front_off:
LLLLLLLLLLLLLLLL
LKKKKKKKKKKKKKKL
LKDDDDDDDDDDDDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDKKKKKKKKKKDKL
LKDDDDDDDDDDDDKL
LKKKKKKKKKKKKKKL
LLLLLLLLLLLLLLLL

（图例：K=近黑, D=深灰, M=中灰, G=灰, L=浅灰, W=白, C=青, Y=金）
```

## 六、验收方法说明（供复核）

1. 预览图 650×490，3 行面板带（y=5..132 / 165..292 / 325..452），列 x=5/165/325/485（每槽 128×128），
   行间 y=137..147 / 297..307 / 457..467 为青/白/金色文件名标注；r3 仅 2 个面板有内容（side + item），r3c3/c4 为空背景 rgb(60,66,76)。
2. 对每个槽位做 8×8 块多数表决生成 16×16 网格，并与实际 PNG 逐像素对比。
3. 相似度矩阵：逐像素 |ΔR|+|ΔG|+|ΔB| ≤ 60 记为同像素（alpha<128 跳过）。
4. 脚本与中间产物保留在 tools/vision/ 供重跑。

---

# 第二轮补充验收（2026-08-29，10 张定稿版）

## 结论更新

**verdict = needs_revision（轻量，仅剩 1 项必改）**

第 10 张物品图标 `items/ecal_cell_c4.png` **验收通过**；上一轮 F1/F4 两条为**误报，撤回并致歉**
（原因：首轮用 8×8 块多数表决法提取预览槽位网格，而预览渲染器对 1px 特性不按整块填充，
导致网格与真实内容不符。改用 texel 原点单像素采样后，预览与文件完全一致）。

## ① 物品图标 ecal_cell_c4.png —— PASS

- 16×16、内容不透明、**行 0/15 透明边距**（与 E-Storage 物品惯例一致：estorage_cell_item_* 同款 1px 上下留白）；
- 母题齐全：顶部金接脚（行 2-3 W/Y 交替条纹）、深色 K 舱体、青色存储条 + 金色触点（行 6-11）、
  底部 3 枚金色触点（行 13）；
- 风格一致：L 面板框 + 青/金色板，与方块家族同一设计语言；
- 原创性量化（同像素%）：对 E-Storage 物品系 **≤7.7%**、对原版仓库母题 **0%**、对原版 data_bus 26.5%（仅共享浅灰边框）、
  对自家方块 17.9-33.7%（家族边框语言）——全部 ≤ 声称的 42.6% ✓；
- 预览第 10 槽（x=165, y=325）逐像素内容与文件一致（差异仅透明边距被渲染为深色面板底），
  行 3 下方标注条（y=457..467）存在。

## ② 预览图同步性 —— F1 撤回（已核实同步）

texel 原点单像素采样：casing/parallel_proc/thread_core/cell_drive/me_channel/transmitter_bus/front/front_off
8 槽 **100% 精确一致**；side 槽（x=5, y=325）96.1%（差异 = 面板斜边覆盖外圈 L 环）；item 槽（x=165, y=325）内容 100%
（差异仅透明边距）。**预览图与当前 10 张文件完全同步，无需重生成。**

## ③ me_channel 对角切口 —— F4 撤回

当前文件第 7 行 `LWWKDDCWCCDDKWWL` 青色核内含 W 缺口（CWCC），即"对角切口"细节——本就存在，
首轮多数表决网格未显示属方法误差。

## 剩余修复项

- **F2 [中/必改] ecal_parallel_proc 阵列母题**（基于实际文件，仍成立）：实际为 10 个 1px 散点
  （青 6 金 4，X 形分布），无 4×4 网格结构；16×16 下与 casing 同为"白底+小点"，游戏内难区分。
  建议：改为 4×4（或 3×3）核心单元网格——单元 2px 内芯或浅灰描边，青/金填充表示点亮，间距 1-2px。
- **F3 [低/可选] casing 四角螺钉**为 1px 浅灰点（~202 vs 白底 ~226），对比度低；建议 2px 深灰或加深描边。
- **F5 [低/可选] item 描述与纹理不符**：描述"青色存储条三格"，纹理为单条连续 6 行青色块（行 6-11）+ 金色触点列；
  若意图三格容量分段（同 E-Storage 分段条惯例），需加深色分隔线；否则请同步修正描述措辞。

---

# 第三轮终审（v2 修复版，2026-08-29）

## 结论：**PASS** ✓（10/10 全项通过）

## ① 修复项逐条核验（基于实际 PNG 像素级读取）

| 项 | 判定 | 证据 |
|---|---|---|
| F2 parallel_proc 3×3 阵列 | ✅ | 9 单元网格实测：单元 4×4px（1px C3C3C3 描边 + 2×2 内芯），位置 x/y=1/6/11，间距 1px（E2E2E2）；6 单元点亮 = 青 4（r1c1/r2c2/r2c3/r3c3：4DBFD4 + 80E1FF/CCFDFF 亮角）+ 金 2（r1c2/r3c1：DEC844 + F2E9B5），主斜线+中心+副斜线分布 —— 与声明逐像素一致 |
| F3 casing 螺钉 | ✅ | 四角 2×2 深灰螺钉（A8A8A8 + 8A8A8A 阴影），行 2-3/13-14 × 列 2-3/13-14，16×16 清晰可见；面板新增 CDCDCD/D2D2D2 对角拉丝纹 |
| F4 me_channel 四角切口 | ✅ | 四内角 F1F1F1 切口（行 4-5/11-12 × 列 4-5/11-12）+ 青色核内对角亮线（行 7：4DBFD4 FFFFFF 80E1FF 4DBFD4） |
| F5 casing 青/金角标 | ✅ | 行 12 列 2 = 4DBFD4（青），行 12 列 13 = DEC844（金），避开螺钉位 |
| F5' item"三格"分段（上轮建议） | ✅ | 当前 item 文件已分段：行 6/8/10 青色段（4DBFD4），行 7/9 深色分隔（1D1D1D），行 11 深色基座 —— "三格"落实 |

## ② 10 张全量

- 全部 16×16、非空（uniq 色数 6-16，与 artist QA 一致）；
- 家族调色板统一：C6C6C6 外框 / F1F1F1 面板面 / E2E2E2 面板底 / 4DBFD4 青 / DEC844 金 / A8A8A8+8A8A8A 螺钉 / C3C3C3 网格描边；
- 成型/非成型：front（青数据带+金柱状图）vs front_off（纯黑屏）区分清晰。

## ③ 预览图（重要时间线说明）

- **权威预览 = tools/ecal-textures-preview.png（14:38:34 重新生成，锁释放后覆盖成功）**：
  槽 1-9 与当前文件**逐像素 100% 一致**；槽 10（item）**196/196 不透明像素全匹配**（60 个透明边距像素显示底板色，属物品图标正常现象）——与 artist 声称完全吻合；
  三行文件名标注条齐全（y=137-147 / 297-307 / 457-467）。
- **tools/ecal-textures-preview-v2.png（14:30:25）已过时**：其第 10 格为未分段旧版 item（行 6-11 全 CCCC），
  而最终文件（14:38:07）已加三格分段。建议删除 v2 或重命名，避免团队误用旧图。

## ④ 原创性复检（修改后 3 文件）

- 对原版母题纹理（l6/l9_controller、module_parallel_unit、ec_line、carbon_fiber_chassis）**0%**；
- 对 data_bus 48-54.3%（阈值 Δ≤60 宽容计法下的浅灰边框语言共享，非内容拷贝）；
- artist 429 张参考图全量 QA 最高 41.4% < 50% 阈值 —— 通过。

## 终审依据文件

- 实际贴图：`src/main/resources/assets/ecoaegtnh/textures/blocks/ecal_*.png`（9）+ `items/ecal_cell_c4.png`
- 权威预览：`tools/ecal-textures-preview.png`（14:38:34）
- 复核脚本：`tools/vision/review_v2.ps1 / review_v2_slots.ps1 / review_final_slots.ps1`

---

# t16 补充验收（控制器白色系 + cell_drive_front 新增，2026-08-29）

## 结论：**PASS**（11/11）

## ① 控制器三张与外壳的白色系一致性 —— PASS

| 贴图 | light%* | dark% | 实测结构 |
|---|---|---|---|
| ecal_casing | 98.4% | 0% | 面板+四角 A8 螺钉+青/金角标（基准） |
| ecal_controller_front | 96.9% | 0% | C3 框 + D2 内凹屏 + 青数据线(行4) + 金柱状图(行7-12) + 行2 青/金状态灯 |
| ecal_controller_front_off | 98.8% | 0% | C3 细框 + E2 平屏（与面板同色）+ 极浅噪点(uniq8) + 灰状态灯 |
| ecal_controller_side | 98.8% | 0% | 浅灰百叶(行4/8/12 L 条) + 右缘青/金状态灯(行3/7/11) |

*light = avg≥150 像素占比（我方口径；artist 报 front 84% 系阈值口径不同，dark=0% 双方一致）。
四张同族：C6C6C6 框 / F1F1F1 面 / E2E2E2 底 + 4DBFD4/DEC844 点缀，与 casing 邻放无违和。

## ② front/front_off 成型/非成型区分（改浅色后） —— PASS

- front：C3 框 D2 屏 + 青色数据线 + 金色柱状图（5 段递增）+ 彩色状态灯；
- front_off：C3 框 E2 平屏 + 无内容 + 灰色状态灯；
- 区分由内容承载（线+柱+灯 vs 空白），一眼可辨。
- 可选优化（不阻塞）：off 屏 E2 与面板填色相同，若想强化"屏幕区域"感知可加 1px D2 内缘阴影。
- **已落地确认（15:17:55）**：front_off 现为 C3 细框 + E2 平屏 + **D2D2D2 1px 内缘阴影环**（行 3/12 整行 + 行 4-11 左右列）+ 极浅噪点（CDCDCD 散点）+ 灰灯；light 98.8% / dark 0% 维持；预览图 15:18:15 重建，front_off 槽位精确 RGB diff=0/256。

## ③ ecal_cell_drive_front vs ecal_cell_drive 可辨识区分 —— PASS（强）

- front（新，98.4% light）：顶部 C/Y/C/Y 交替 LED 排(行2) → C3 框舱口 → 三行卡槽（行6/9/12 两端金触点 Y 于列3/12）→ 左缘青色运行点(行7-8 列3)；
- cell_drive（侧，61.3% light / 37.1% dark）：深色 K 舱体 + 三档青窗（行5/8/11 Y+C）；
- 深舱+窗 vs 浅面+LED/卡槽：明暗与母题双重区分，同屏不混淆；行 2 LED 排母题两版呼应（side 全青、front 青/金交替），家族感一致。

## 预览图（15:14:35 重建）与原创性

- **11 槽全部逐像素 100% 一致**（方块 256/256；item 196/196 不透明 + 60 透明边距）——槽位布局已定位：
  r1: casing / parallel_proc / thread_core / cell_drive；r2: **cell_drive_front** / me_channel / transmitter_bus / **front**；
  r3: **front_off** / **side** / item / (空 #3C424C)。三条标注带齐全（y=137-147 / 297-307 / 457-467）。
- 原创性：新/改 4 张对原版母题纹理 0%；data_bus 40.6-55.5%（共享浅灰边框语言，side 55.5% 为同语言上限，内容全异）；符合既有判定口径。

## 备注

- artist 声明的 light 百分比（front 84%）与阈值口径相关，我方测量 96.9%+，方向一致（全系 light≥93.8%、dark=0%）。
- t16 核验脚本：`tools/vision/review_t16.ps1 / locate_slots_t16.ps1`。

---

# t19 补充验收（ecal_cell_drive_front_filled 新增，2026-08-29）

## 结论：**PASS**（12/12）

## 核验

**新增 ecal_cell_drive_front_filled.png（16×16，uniq 15）**：

- 实测结构：C6 外框 + F1 面板 + C3/D2 舱口（与空态同框）→ 行 2 LED 排**全青**（4DBFD4/80E1FF 交替 4 灯，空态为青/金混合）→ 行 6/12 两条空卡槽（E2 底 + 两端 DEC844 金触点，与空态呼应）→ **行 7-11 中部观察窗**：80E1FF 青色辉光窗框（行 7/11）+ CCFDFF 浅青玻璃（行 8-10 侧）+ 金晶阵主体（行 9-10：C4AD23 外框 / DEC844 / FFE54C 亮触点 / F2E9B5 高光）；
- light 96.1% / cyan 16.4% / dark 0%（artist 报 light 75.8% 系扣除青/金功能色后的口径：96.1-16.4-~4 ≈ 75.7 ✓ 一致）。

**① filled 与空态（front）母题呼应 + 一眼区分 —— PASS**：

- 呼应：同框同舱口同 LED 排位同金触点卡槽行（6/12），行 2 顶部 A8 高光/底部斜边一致；
- 区分：中部空态为浅灰卡槽（D2/E2 + 行 9 触点），filled 为青辉光窗 + 金晶阵，一眼可见"舱内有发亮插入物"；LED 全青亦作运行态提示。

**② 白系家族一致性 —— PASS**：filled 96.1% light / 0% dark；辉光窗为功能性内凹点缀（同 cell_drive 侧面的深舱逻辑），未破坏白色系观感；金晶阵色（C4AD23/DEC844/FFE54C/F2E9B5）属家族金系。

**③ 原创性 —— PASS（量化）**：对参照物 E-Storage storage_array_drives_front_filled / front **0%**（仅概念参照"filled=可见发光插入物"，无像素拷贝）；对原版仓库母题纹理 0%；对 data_bus 45.7%（共享浅框语言）；对自家空态 front 79.7% 属**预期孪生对**（同骨架差异件，同 front/front_off 逻辑）。artist maxSameAsRef 27% 为另一口径（严格色匹配/排除孪生对），方向一致。

**预览图（12 槽满格）**：槽位全部定位且逐像素 100% 一致（方块 256/256，item 196/196+60 透明边距）：
r1: casing / parallel_proc / thread_core / cell_drive；r2: cell_drive_front / **cell_drive_front_filled** / me_channel / transmitter_bus；r3: front / front_off / side / item。

- t19 核验脚本：`tools/vision/review_t19.ps1`。

---

# t22 补充验收（C6/C9 档位贴图 15 张，2026-08-29）

## 结论：**PASS**（15/15 新增，预览 27 槽全同步）

## ① 档位色一致性 —— PASS（无串色）

实测档位色（hex 级）：
- **C6 组**（parallel_proc_c6 / thread_core_c6 / hyper_c6 / controller_c6_front+off+side / cell_c6）：金橙系 FF9300/FFC600/FFE0AE 在位（2.0-14.5%），**紫色像素 = 0**；
- **C9 组**：紫系 8815D8/B06FDD/DDA8F5 在位（0.8-14.5%），**严格暖色签名（r>200,g 120-240,b<140）像素 = 0**——纯度分析中 C9 文件出现的"C6gold 0.4-2.7%"经 hex 复核全部为 **F2E9B5 家族金触点/高光**（与 FFE0AE 的 Δ40 邻域误归类），非档位串色；
- 设计模式一致：左状态灯/数据线/柱状图/存储条/容量刻度随档位换色；**右状态灯（DC3C28 红橙，C4 起家族恒定）与金触点列（DEC844/F2E9B5）家族常驻**；
- front_off 三档**字节级相同**（同 223B、同 SHA256 39EE70E9177A6E6F）——off 态无档位色属设计使然，预览 r4 行两个 off 槽位内容即此。

## ② 母题可辨识 —— PASS

- 并行网格：C4/C6/C9 同 3×3 骨架（C3 描边+E2 间距），点亮单元 C4 青金混合 → C6 全金 → C9 全紫；
- 线程芯片：金触点列常驻 + 运行灯随档位（C4 青 / C6 金 / C9 紫）；
- 超线程双核：双 C3 核块 + 中央白色 HT 分隔列 + 四角金 + 核内灯随档位（hyper C4 青 3.1% / C6 金 / C9 紫）；
- 控制器：屏内线/柱/左灯随档位（C6 金线金柱 14.5% / C9 紫线紫柱 14.5%）；
- 晶阵物品：三格条随档位（C6 金条 10.9% / C9 紫条 8.2%），金接脚/金触点常驻，底部容量刻度 C6 金/C9 紫。
各档同骨架、异色点缀，档位间与 C4 间均可一眼区分。

## ③ 白系家族 —— PASS

方块组 light 84-98.8% / dark 0%；物品 34.4% dark（存储窗口惯例，同 C4 的 39% 与 E-Storage 惯例）；uniq 7-14 全部符合声明。

## ④ 既有贴图未扰动 + 预览同步 —— PASS

- C4 十张哈希与 t16/t19 终态一致（front/front_off/side 为 t16 白色系版本 64D3…/39EE…/035B…）；
- 预览 850×760（7× 缩放，槽 112px，6 列 × 4 行 + 底部物品行）**27 槽全部定位、逐像素 100% 一致**（方块 256/256、item 196/196+透明边距）；布局：r1 parallel 族+thread；r2 thread/hyper 族+cell_drive；r3 filled/me_channel/transmitter_bus/C4 控制器；r4 C6/C9 控制器；r5 三张 cell 物品。

## ⑤ 原创性 —— PASS

新 15 张对原版母题（l6/l9_controller、module_parallel_unit、ec_line、carbon_fiber）≤14.3%（多数 0%）；data_bus 39.5-55.5% 为既有共享浅框语言（历轮同口径）；artist maxSameAsRef ≤28.5% 为严格口径，方向一致。

## 备注

- t22 核验脚本：`tools/vision/review_t22_purity.ps1 / spot_c9.ps1 / locate_slots_t22.ps1`。

---

# t27 补充验收（晶阵 9 尺寸物品贴图，2026-08-29）

## 结论：**PASS**（9/9）

## ① 分级色一致性 —— PASS（无串色）

- k 组（256k/1024k/4096k）：青色系 4DBFD4/80E1FF/CCFDFF（实测 3.5→7→10.5%，随亮格数递增），**purple=0**；
- M 组（16m/64m/256m）：金橙系 FF9300/FFC600/FFE0AE（6.2→9.8→13.3%），**cyan=0、purple=0**；
- 大 M 组（1024m/4096m/16384m）：紫系 8815D8/B06FDD/DDA8F5（3.5→7→10.5%），**cyan=0**；gold 2.7% 经 hex 复核为 DEC844/F2E9B5 家族金接脚+金触点，非串色（artist 报 6.6% 系计入口径含更多家族金，方向一致）。

## ② 同分级内 1/2/3 容量刻度 —— PASS（三件套逐像素验证）

| 尺寸 | 窗内三格条亮格（行 6/8/10） | 右缘竖条 x=13（2px 段，行 6-7/9-10/12-13） | 底部刻度（行 13） |
|---|---|---|---|
| 256k / 1024k / 4096k | 1 / 2 / 3 | 1 / 2 / 3 | 1 / 2 / 3 |
| 16m / 64m / 256m | 1 / 2 / 3 | 1 / 2 / 3 | 1 / 2 / 3 |
| 1024m / 4096m / 16384m | 1 / 2 / 3 | 1 / 2 / 3 | 1 / 2 / 3 |

最小=1、中=2、最大=3，全 9 张与规格一致，同分级内一眼可辨。

## ③ 母题同构与惯例 —— PASS

- 与 ecal_cell_c4 同构：C6 浅框 + F1/E2 面板 + 顶部金接脚（DEC844/F2E9B5 行 2-3）+ 暗色 1D1D1D 存储窗口（行 5-11 inset）+ 窗右金触点（DEC844 列 10-11）+ 行 0/15 透明边距（**alpha=0 实测**）+ 底部容量刻度；
- 白系 + 功能暗窗惯例保持；全部 16×16、uniq=12 与声明一致；
- 旧 ecal_cell_c4/c6/c9.png 未扰动（c4 sha16=7524AA6F3F1ED1E0 与 t10 终态一致）。

## ④ 预览与原创性 —— PASS

- tools/ecal-cells-preview.png（430×460，7×，3×3）：**9 槽全部定位且不透明像素 196/196 全匹配**（60 透明边距渲染为底板色）；三行文件名标注带齐全（y=125-135 / 265-275 / 420-450）；
- 原创性：对 E-Storage 物品 ≤11.7%、对原版仓库 ≤26%（data_bus 共享浅框语言，母题 0%）；对自家 c4/c6/c9 86-94% 属**预期家族同构**（同骨架换档位色+容量刻度），非拷贝问题。

## 备注

- t27 核验脚本：`tools/vision/review_t27.ps1 / review_t27b.ps1`。

---

# t36 补充验收（并行/线程驱动器方块 + 核心物品 21 张，2026-08-29）

## 结论：**PASS**（21/21）

## ① 驱动器空/filled 两态 + filled 可见内部核心 —— PASS

- 空态（parallel/thread_drive_front）：LED 排（C/Y/C/Y）+ 浅灰舱口 + 三行卡槽（两端 DEC844 金触点）+ 左缘青运行点；
- filled（parallel_drive_front_filled）：LED 全青 + 观察窗（行 7-11）内**可见 3×3 并行核心阵列**（亮格 2px 高光、行列分隔清晰）；
- filled（thread_drive_front_filled）：观察窗内**可见线程芯片**（C 框 + 白走线 + 青芯 + 金色插脚 + 底部 Y 触点）；
- 两态差异：LED 全青 + 观察窗内容，一眼区分；内部核心可见性达标。

## ② 并行核心 9 级亮格递增 —— PASS（逐像素）

| 级 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|
| 窗内亮格（2px 高光） | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| 右缘竖条段数 | 1 | 1 | 1 | 2 | 2 | 2 | 3 | 3 | 3 |
| 底部刻度点 | 1 | 1 | 1 | 2 | 2 | 2 | 3 | 3 | 3 |

实测亮格像素 2→18（每格 2px），单调递增无跳变；三件套分组规律（1-3/4-6/7-9 → 条 1/2/3）与声明一致。

## ③ 线程普通 vs 超线程母题 —— PASS

- 普通（thread_core_1/4/16）：单核芯片（白走线 L/W + 青芯 C）+ 线程刻度条（右缘 1/2/3 + 底部 1/2/3），窗内青 6→9→12px 随级增；
- 超线程（hyper_2/4/8）：双核布局（左金芯 c6Acc + 右紫芯 c9Acc + 中央白色 HT 双竖线 + 金/紫淡色点），紫 3.6→5.1→6.6% 递增、**cyan=0**、金 5.1% 恒定为家族触点；
- 单核/双核 + 青/金紫配色双维度区分明确。

## ④ 白系家族 —— PASS

驱动器 light 98.4-98.8% / dark 0%（filled 的 cyan 14.8-17.6% 为功能性辉光窗）；物品 light 40-53% + 暗窗 dark 38-50%（存储窗口惯例，同 t27）；全部 16×16、uniq 11-17。

## ⑤ 命名对齐与预览 —— PASS

- T35 代码注册名核对：`ecal_parallel_drive_front` / `_front_filled` / `ecal_parallel_drive`（side 面=基础名）及 thread 同名族——**全部文件在位**；
- 预览 tools/ecal-cores-drives-preview.png（850×610，6×4，21/24 槽）：**全部槽位定位且内容 100% 匹配**（驱动器 256/256；物品 196/196+60 透明边距）；r1=6 驱动器，r2-4=15 物品；
- 设计备注：parallel/thread 驱动器的**空态 front 与 side 两两字节级相同**（同 SHA256）——空态与侧面为家族通用造型，仅 filled 区分驱动器类型（驱动器位于结构不同位置，可接受）。

## ⑥ 原创性 —— PASS

对 E-Storage drives_front_filled 0-9.7%（概念参照）；对原版母题（l6_controller / module_parallel_unit）0-2.2%；data_bus 23-44.9% 为历轮共享浅框语言；对自家 cell_drive_front_filled 93.4%（filled 驱动器家族同构）与 cell_c4 75-83.7%（物品骨架）属预期孪生对。

## 备注

- t36 核验脚本：`tools/vision/review_t36a/b/c/e.ps1`。

---

# t42 补充验收（驱动器分主题修复 6 张，2026-08-29）

## 结论：**PASS**（6/6）

## ① 两驱动器互不混淆 —— PASS（t36 同构问题已解决）

- **6 张哈希两两互异**（parallel/thread 的 front、filled、side 全部彼此不同）；
- 空态分主题：
  - parallel_drive_front：LED C/Y/C/Y + 舱口内 **3×3 核心网格暗纹**（D2 网格线 + F1 单元 + 左上 2×2 青角标 4DBFD4/80E1FF，行 6-8）——并行阵列主题；
  - thread_drive_front：LED 同排 + 舱口内**单芯片暗纹**（引脚点行 5/10 + 框行 6/10 + 白走线 + 芯内青点 CC 行 8）——线程芯片主题；
- 侧面分主题：parallel 右下 2×2 网格点缀（C3 线+F1+青角）；thread 右下芯片点缀（C3 框+青芯+引脚点）。

## ② 正侧区分 —— PASS

正面 = LED 排 + 主题舱口（网格/芯片）；侧面 = 浅灰百叶 + 右下主题角标。布局与功能主题均不同。

## ③ filled 观察窗与空态主题呼应 —— PASS

parallel filled 窗内 3×3 阵列 2×2 点亮（呼应空态网格纹）；thread filled 窗内芯片（呼应空态芯片纹）；filled 内容沿用 t36 未动。

## ④ 白系家族 —— PASS

light 98.4-98.8% / dark 0% / uniq 11-13 与声明一致。

## ⑤ 预览与原创性 —— PASS

- tools/ecal-drives-themed-preview.png（520×330，**8× 缩放**，3 列 x=5/175/345 × 2 行 y=5/165）：**6 槽全部定位、逐像素 100% 一致**（行 1 = parallel 三态，行 2 = thread 三态）；
- 原创性：对 E-Storage drives_front 0%；对原版母题（l6_controller 等）0%；data_bus 45-55% 为历轮共享浅框语言；对自家 controller_side 69-99%（百叶家族语言，区分点为右下主题角标与灯色）、cell_drive_front 72-85%（驱动器家族同构）、parallel_proc/thread_core 55-74%（空态主题呼应母题）——均属预期家族语言/主题呼应，非拷贝。

## 备注

- 细节说明：parallel 空态舱口内网格线实测为 D2（artist 描述为 C3，C3 为舱口框色）——视觉意图一致，无碍；
- t42 核验脚本：`tools/vision/review_t42.ps1 / review_t42b.ps1`。

---

# t57 补充验收（GUI 按钮图标 2 张，2026-08-29）

## 结论：**PASS**（2/2）

## ① 两图标母题可辨识 —— PASS

- ecal_milestone_button（里程碑/旗帜，青点缀）：灰白旗杆（C3/F1 列）+ 青三角旗（80E1FF 旗面 + 4DBFD4 描边 + FFFFFF 白点行 5 + CCFDFF 淡青飘尾行 6）+ 金色基座（行 11：F2E9B5/DEC844/C4AD23）；
- ecal_upgrade_button（升级/上升，金点缀）：灰白阶梯（F1/C3 四阶自左向右上升，行 7-10）+ 金色上升箭头（行 4-6：FFE54C 尖 + FFFFFF 高光 + DEC844 杆）+ 底部青/亮青两点（行 11：4DBFD4/80E1FF）；
- 旗帜 vs 阶梯箭头：轮廓与配色双重区分，一眼可辨。

## ② 风格与 GUI 白系一致、纯图标无文字 —— PASS

两图同为 C6 框 + F1/E2 面 + CDCDCD 阴影的浅色面板块，与白系家族一致；全图无文字字形（所有不透明像素均为面板/母题元素）。

## ③ 透明底 overlay 结构 —— PASS

**144/256 不透明**（12×12 面板块）：行 0-1/14-15 与列 0-1/14-15 全透明（alpha<128 实测）——overlay 惯例（透明角叠在按钮底上）正确；uniq 11-13 与声明一致；升级图标金 13px（5.1% of 256）与声明吻合，里程碑青 15px（≈5.9%，含淡青 CCFDFF，口径差异）方向一致。

## ④ 原创性 —— PASS

对原版仓库（data_bus 7.6-12.5%、l6_controller / module_parallel_unit 0%）；对自家 casing 37.5-39.6%（共享 C6/F1/E2 面板语言）；maxSameAsRef ≤43.8% 声称成立。

## 备注

- 文件位置 textures/gui/ 与契约一致（契约 Verify 观测 2）；
- t57 核验脚本：`tools/vision/review_t57.ps1`。

---

# t66 补充验收（升级树背景图，2026-08-30）

## 结论：**PASS**（文件 198×192 全部要素核验通过；预览图 2x 侧截断 1 项低severity备注）

## ① 科技树/星图风格可辨识 —— PASS（逐要素定位）

- **4 节点**：青环 4DBFD4 ×2（(28,94) 与 (124,106)，5×5 环）+ 金环 DEC844 ×2（(156,50) 与 (74,148)，5×5 环）；亮心 52px（80E1FF/FFE54C/CCFDFF/F2E9B5）；
- **青斜线** #2C6E80：43px 完美对角线（(74,52)→(35,91) 下行）；**金斜线** #6E5822：~30px 对角段（(126,115) 下行）——星图轨道感；
- **星点**：~60 颗散布（白/青/亮白，含 18% 十字光芒结构）；
- **电路纹理**：走线 #2A3452 994px（横/竖+对角拐角）+ 金焊点 #54421A 4 处 3×3 簇。

## ② 深色底与 MUI1 深蓝协调 —— PASS

垂直渐变实测：内部 y=3 #0B1024 → y=188 #04060D（与声明逐值一致），深蓝近黑系与 screen_blue #000020 同族；dark 96.2%（阈值口径差异，方向一致）+ cyan 0.7%/gold 0.3% 点缀。

## ③ 16px 网格节奏 —— PASS

网格锚点 (30,40,68) 淡点：**实测 130/144 命中——与声明完全一致**；少数被星点/节点叠加属正常。

## ④ 无文字字形、纯背景 —— PASS

结构全部为渐变/锚点/走线/节点/星点/边框装饰，无任何字形簇。

## ⑤ 边框与原创性 —— PASS

双边框实测：外 1px #18223A + 内 1px #283454/#244C5C，四角 L 形青暗化（#244C5C 行/列 1-2）；原创性：vs screen_blue 及原版参考重采样 0%（纯程序化星场，无拷贝可能）。

## ⑥ 预览图备注（低，不阻塞）

tools/ecal-upgrade-bg-preview.png（614×192）：1x 侧与源图逐像素一致（5 点采样全匹配，原点 (5,0)）；**2x 侧垂直截断**——2× 本应 384 高，画布仅 192 高，2x 侧只显示源图前 ~96 行（y=190 ↔ 源图 y=95），**底部半幅（含金环节点 (74,148) 与金斜线）在 2x 预览中不可见**。建议：预览重生成时画布高用 384（或改用 1.5× 适配），否则 2x 对比图信息不完整。文件本体无此问题。

## 备注

- t66 核验脚本：`tools/vision/review_t66.ps1`（渐变/网格/节点/斜线/星点/边框/预览对齐）。
