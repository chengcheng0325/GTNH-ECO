# ECO-GTNH 代码审查与构建验证报告（reviewer）

- 审查人：reviewer（t5）
- 审查日期：2026-08-26 起（多轮复验持续至 2026-08-28）
- 审查范围：`src/main/java/ecoaegtnh/**`、`src/main/resources/**`（lang/mcmod.info/贴图/mixin 配置）、`build.gradle`/`settings.gradle`/`gradle.properties`/`dependencies.gradle`
- 对照基准：`docs/DESIGN.md`（researcher 设计）、参考仓库 `ref/NovaEngineering-ECOAEExtension-main`（1.12.2 原版）、本地依赖源码 `.research/gt5-src`（GT5U 5.09.54.111）与 `.research/ae2u-full/...998-GTNH`（AE2U rv3-beta-998；rv3-beta-1000 存储 API 与 998 同代一致）
- 构建验证：`.\gradlew.bat build`（编译工具链 JDK 8；**Gradle 守护进程用 JDK 21** 时干净 build 全绿，见 §1.1）
- **依赖版本**：`dependencies.gradle` = GT5-Unofficial **5.09.54.20:dev** + Applied-Energistics-2-Unofficial **rv3-beta-1000-GTNH:dev** + StructureLib 1.4.42（服务器 GTNH 2.9.0-beta-2 对齐）；TE4 `ThaumicEnergistics:1.7.60-GTNH:dev {transitive=false}` + Thaumcraft 本地 jar compileOnly。
- **逐轮复验记录（头部摘要，2026-08-26/27，reviewer）**：
- **第一轮（2026-08-26 14:0x）**：P1-1 ✅（JDK21 守护进程解决 spotless）、P1-2 ✅、P1-3 ✅、P1-4 ✅、P1-5 ✅ 全部修复并复验（jar 221,753 B @14:07:37，major 52，`RegistryMTE` sipush 32030/32031/32032，`TileEcoStorageDrive.isCellSupported` 在产物中）；顺带 P2-5（等级限制）、P2-9（创造栏）。剩余 P2×8（6/7/8/10/11/12/13/14）。
- **第二轮（2026-08-26 18:4x，任务 t12）**：t7（配方 null 崩溃）、t8（`getStackType` 构造期 NPE 客户端崩溃）、t9（多方块无法成型：坐标系错位 + 能量舱不入结构 + 无预览）修复并复验（§1.2）；t11 源质盘对照 `docs/ESSENTIA_CELL_RESEARCH.md`（TE4 1.7.60）逐项通过、气体盘彻底移除。jar 80,517 B，SHA256=`5DDD2C67CC6A30B91932781C0FD787D33E94BF9BF8C81FACC170AC2C76EB07D8`（36 类、major 52、无 gas 类）。**P2 剩余 4 项（6/7/11/13）**。
- **第三轮（2026-08-26 21:5x，任务 t19）**：t14（源质门控 modid 大小写——旧门控永不匹配，真实缺陷）、t15（投影/结构方向镜像修正）、t16（盘位潜行交互）、t17（GUI 重设计）、t18（控制器自定义贴图）全部复验（§1.3）。jar 85,552 B，SHA256=`B0FCA147E3E04CE960E55A7CDE2A9C314A3EBB66D05DC213D3FD5B8B45217B09`（37 类）。**P2-7 由 t16 顺带解决；P2-6 仍部分开放；P2-11/13 未动。**
- **第三轮补充（2026-08-26 22:0x，t19 扩展）**：t21（存储盘物品名本地化——缺 `estorage_cell_` 前缀，显示原始键）✅ 与 GUI 纹理 v2 目检（像素区域分析）✅（§1.3）。**发现 jar 内 GUI 纹理为旧版**（v2 未进 21:43:02 的 jar），已重建：jar 85,220 B，SHA256=`4153D4AEE411EAEC86450C0C396F167C1DE8CE7419F662711D6531F89C18113A`。提醒：资源改动后必须重跑 build 再报产物。
- **第四轮（2026-08-27 12:1x，任务 t28）**：t25（潜行交互 `doesSneakBypassUse`——vanilla 字节码实证 + 盘位朝向/两态贴图 + shape 'D' anyMeta）、t26（控制器分面贴图）、t27（TecTech 风格 GUI 重写）、t24（研究文档质量）全部复验（§1.4）。**P2-6 由 t22 闭合**。jar 89,375 B，SHA256=`4A301090933DC657C97AB2D558D70E26BDC55EF154DD5FDCFF284C038C0F581E`。**P2 剩余 2 项（11、13）**。
- **第四轮收尾（2026-08-27 13:0x，任务 t31）**：t29（ModularUI2 TecTech 主题 GUI 重写——旧 Forge GUI 全删）、t30（结构方向最终定稿：列往右扩、总线背侧右角，DESIGN.md 同步）全部复验（§1.4）。jar 86,403 B，SHA256=`06122DDA97ECB28A85B3D398EA8D214A5F11E9031637C6E4D04BE0DB9585F069`（35 类）。
- **第五轮（2026-08-27 18:1x，任务 t34）**：t32（TTMultiblockBase 重构：纯 AE/免维护/长度矩阵/自动朝向/真 TecTech GUI）、t33（盘位分色/盘 tooltip/WAILA）全部复验（§1.5）。jar 90,366 B，SHA256=`2680548793FCE2D3D0AEDCEEE9C2580A02F44DF4072876C52548F83C40C1D4FE`（36 类 = +1 WAILA provider）。
- **第六轮（2026-08-27 19:1x，任务 t41）**：t35（放置即 StackOverflowError 递归崩溃——facing 覆写全删）、t37（维护图标不显示——`supportsMaintenanceIssueHoverable→shouldCheckMaintenance`）、t38（结构说明/tooltip 全中文化，13 新键双语）、t40（WAILA 服务端不再预翻译 + registerItem 前缀处理）全部复验（§1.6）。jar 91,468 B，SHA256=`3D68852BA59AC65E042D8AEE7C25876B89EF47E15E9DADA6FEBA19285578D7C7`。
- **快速复验（2026-08-27 19:5x，任务 t45）**：t43（GUI 主题切 INTERGALACTIC_STANDARD）、t44（维护真根因：`getDefaultHasMaintenanceChecks()→false` 使构造期 fixAllIssues 即执行 + onPostTick 清理陈旧 NO_REPAIR）全部复验（§1.6）。jar 91,631 B，SHA256=`8846BA109B0EB6160695AC8C45F1D05969A91819FD1CE0D9D4ACDF3F89F5B1EA`。
- **第七轮（2026-08-27 20:4x，任务 t48）**：t46（readStack 构造期 NPE——有内容盘插回即崩溃，readStack/getTypeWeight 改走构造安全 getStackType）、t47（星空主题真实可见——MUI2 主题系统本无星空，GUI 显式应用 gtnhintergalactic space_with_stars 平铺）全部复验（§1.7）。jar 91,966 B，SHA256=`38291C132FD01EF78368588B76E3057C18187DFCB100048D7A877630BA9E921E`。
- **t49 字节对齐（2026-08-27 21:2x，任务 t51）**：t49（存储盘字节计算完全对齐 GTNH-AE——totalBytes=MB×1024²、perType=totalBytes/128、MAX_TYPES=63、字节公式全交 AE2U 基类）复验（§1.8）。jar 91,049 B，SHA256=`A84FEE87ABFC9EA05754C956502E62B20B666770345517D80B3BFBD63422B9AE`。
- **t50 GUI（2026-08-27 21:3x，任务 t52）**：t50（量子计算机同款底部 IO 区——Flow.row SPACE_BETWEEN 三 LED + isMEBusConnected sync）复验（§1.9）。jar 92,244 B，SHA256=`9441B2BE576B1AD505A0E9832555EE0DB5D6911DE4A572F53922A862B4563947`。
- **第九轮（2026-08-27 22:1x，任务 t56）**：t54（量子计算机同款 MUI1 GUI——删 MUI2 覆写与 GUI 类、addUIWidgets/drawTexts）、t55（盘位防共用 onAssembled→boolean + 拆控制器/关机断网 isOperational 8 tick 去抖 + 五入口门控）全部复验（§1.10）。jar 93,214 B，SHA256=`8A13CEC76DBB60821CA85ACFEBECF3FED4BEFC2B247AC301E4DF2336E0493817`。
- **第十轮（2026-08-27 23:1x，任务 t64）**：t58（GUI 统计同步——6 sync 字段 setter 写回 + IO 行移到底部参数条）、t59（归属释放——onRemoval→disassembleAll + isCurrentOwnerAlive）、t61（ME 驱动/箱子——openChestGui→GUI_ME + postInit 自检日志；"无法放入"实为 t46 前 readStack NPE）、t62（放置规则——成型前禁放 not_formed/成型后 tier_not_supported/取出不受限）、t63（idleDrain=totalBytes/4,194,304→16M 4.0）全部复验（§1.11）。jar 95,863 B，SHA256=`54554C897FA6FA37E2E7DD6FD248F5CC4D473E205CCE149D2EF9CB16B1435A12`。
- **第十一轮（2026-08-28 00:2x，任务 t70，见 §1.13）**：t65（IO LED 悬停 tooltip）、t66（ECO 盘禁入 ME 驱动器/箱子——mixin 方案）、t67（电容统一 2,000,000 AE + 跨阵列共享 owner 列表）、t68（容量回旧版 byteMultiplier：totalBytes=MB×1000×1024、perType=multiplier×1024、类型 315、idleDrain 保持 4.0）、t69（耗电 B+C：tierBase + 0.5×installedCellCount + ΣidleDrain）复验。**发现 t66 mixin 配置缺陷（见 §1.13，P2-15）**。当时产物 jar 101,468 B，SHA256=`5EEF5AB7A7A71F38BB9384B98911295BE2B0829BD9A751CC8A7DF652BA7E49E3`。
- **第十一轮补充（2026-08-28 00:4x，任务 t72，P2-15 修复复验）**：t71 已把两个 mixin 全类名手动填入 `mixins.ecoaegtnh.json` 的 mixins 数组（根因：gtnhgradle generateAssets 生成的数组恒为空、mixin 0.8 无包扫描）。复验：JDK21 干净 build BUILD SUCCESSFUL；**jar 内 json mixins 数组含 `ecoaegtnh.mixin.MixinSlotRestrictedInput` + `ecoaegtnh.mixin.MixinTileDrive`（非空）**、两 class 在包内、refmap searge 段映射正确（Slot `func_75214_a`；TileDrive `remap=false` 字面）；无回归抽查（t46/t44/t59/t67/t68/t69）全过。**最终产物 jar 101,509 B，SHA256=`CC0242A94445A84618B5F871A7F6432890788F44D35A862BFA75AF8105117BA4`（与预期一致）**。**P2-15 闭合 ✅**。
- **第十二轮（2026-08-28 13:0x，任务 t80，见 §1.14）**：t76（新盘分级 27 种：`CellSize` 9 常量 K_256..M_16384，等级重映射 k→L4/16M..256M→L6/1024M..16384M→L9，`getTierRequired()`；`MixinGridStorageCache` 网络工具容量上报修复——mixin json 增至 3 类）、t77（IO 4 格 LED：状态/物品/流体/源质 + 专属 tooltip `statusTooltip/itemTooltip/fluidTooltip/essentiaTooltip`，旧 `cellStatsTooltip` 删除）、t79（`showMachineStatusInGUI()→false` 隐藏软锤提示 + 删 `energyBarLine` 能量百分比条；t78 贴图 27 张 16×16 vision 已验证）复验。**最终产物 jar 118,979 B，SHA256=`E37A3483EAB85B709149D68A7114C42DCF48228334B764F1738C4FBD7A46A0DB`（与预期一致）**。
- **t81/t82 快速复验（2026-08-28 13:1x，任务 t83，见 §1.15）**：t81（`EcoStorageCellInventoryHandler.getStorageChannel()` 覆写：`handlerType==FLUID_STACK_TYPE ? FLUIDS : ITEMS`——修复流体盘被统计进物品栏）、t82（源质 LED 改用自绘紫色 `ECO_PARAMETER_PURPLE = UITexture.fullImage("ecoaegtnh","gui/picture/parameter_purple")`，贴图在 jar）复验。**最终产物 jar 119,822 B，SHA256=`6787677BAEB38270D84BC56E05BF0609F125EDC7C044D9F157BD2BFDF5D4F523`（与预期一致）**。装机队长已执行（服务器已重启）。

- **t106 基线核验（2026-08-29，交接后 reviewer 独立复核，见 §1.16）**：外壳"紫黑块+原始 key"根因 = `ItemEcoStorageHousing.tierLabel()` 曾返回 `"l4"/"l6"/"l9"`（自带 l），调用处 `"_l"+tierLabel()` 拼成 `_ll4`（双 l），与 lang 键/贴图文件名（单 l `_l4`）不匹配。修复后 tierLabel 返回纯数字。**最终产物 jar 169,804 B，SHA256=`06C831A532BA24476170E995E4E12D8713331AD33D3853F3EEFE97B50D362AFE`（与 HANDOVER.md 记录一致，双端已装、服务器 12:38 重启）**。待用户杀 javaw 重启客户端复测。

> **⚠ 文档事故说明（2026-08-27，t69 阶段）**：本文件在 t69 文档更新时被一次损坏的 PowerShell 写操作意外覆盖，原 §1.1–§1.17 详细审查章节与 §0/§2/§3/§4 正文丢失。本文件已由 reviewer 依据会话内保留的头部轮次记录、`docs/t3-implementation-notes.md`、`build-round*.log` 等证据**重建**（§0 结论、§1.x 逐轮摘要、§2 P2/P3 清单、§3 构建证据、§4 实测清单），并追加第十一轮（t65-t69）复验为 §1.13。

---

## 0. 结论

**P1 全部已修复（十二轮复验通过 + t81/t82/t106 修复均复验）；P2 剩余 2 项实质开放（11、13）建议发布前处理，并完成游戏内/服务器功能实测。**

- **P1×5 全部 ✅**（2026-08-26 修复 + 复验）：P1-1 构建阻断（JDK21 守护进程解决）、P1-5 服务端启动崩溃（MTE ID 改 32030-32032）、P1-2 能量反向供网、P1-3 拆盘丢数据、P1-4 容量算法 1/8——见 §1.1；
- **第二轮修复 ✅（t7/t8/t9/t11）**：配方 null 崩溃、客户端构造期 NPE、多方块成型（坐标系 + 预览）、源质盘（TE4 对照、气体盘移除）——§1.2；
- **第三轮修复 ✅（t13/t14/t15/t16/t17/t18/t21）**：创造栏崩溃、源质门控大小写、结构方向、盘位交互、GUI、控制器贴图、物品名本地化——§1.3；
- **第四轮修复 ✅（t22/t24/t25/t26/t27/t29/t30）**：GUI 入口、研究文档、潜行交互真正生效、分面贴图、TecTech GUI、MUI2 重写、结构方向定稿——§1.4；
- **第五/六轮修复 ✅（t32/t33/t35/t37/t38/t40/t43/t44）**：TTMultiblockBase 重构、分色/WAILA、递归崩溃、维护显示、中文化、WAILA 键、主题切换、维护真根因——§1.5/§1.6；
- **第七/专项修复 ✅（t46/t47/t49/t50/t53/t54/t55/t58/t59/t61/t62/t63）**：readStack 构造安全、星空主题、字节对齐、底部 IO、MUI1 量子计算机 GUI、防共用/断网、GUI 同步、归属释放、ME 驱动、放置规则、idleDrain——§1.7–§1.11；
- **第十一轮（t65-t69）✅（P2-15 已收尾）**：t65 ✅、t66 ✅（t71 修复 mixin 配置空列表后，禁入功能生效——见 §1.13 补充）、t67 ✅、t68 ✅、t69 ✅；
- **第十二轮（t76/t77/t79）✅**：新盘分级 27 种（`CellSize` 9 档 + 等级重映射 + `getTierRequired`）、`MixinGridStorageCache` 网络容量上报（mixin json 3 类）、IO 4 格 LED（状态/物品/流体/源质 + 专属 tooltip）、`showMachineStatusInGUI→false` 隐藏软锤提示、删能量百分比条、27 张贴图 + lang 27×2——§1.14；
- **P2 剩余**：**P2-11**（拆电容丢 AE 能量）、**P2-13**（代码 GPL-3.0 派生合规，需 LICENSE 决策）；P2-14 已知限制（63 类型钳制——t68 已通过 `getTotalItemTypes` 覆写解除）；**P2-15 已修复闭合 ✅**（t71 手动填充 mixins 数组，t72 复验通过）。

建议按 §2 顺序处理剩余 P2 并做游戏内测试后再发布。

---

## 1. 详细审查记录（逐轮摘要）

### 1.1 第一轮（t5，2026-08-26）——P1 修复
- **构建**：JDK8 守护进程下 spotless（google-java-format 1.7）无法解析 5 文件 8 处现代语法 → `gradlew build` 失败；**修复**：Gradle 守护进程改 JDK21（GJF 自动升版），干净 build 全绿，产物仍 JVM8 字节码（major 52）——P1-1 ✅。
- **P1-2** ME 总线补 `MENetworkPowerStorage(PROVIDE_POWER/REQUEST_POWER)` 事件（对照参考 EStorageMEChannel L97-125 + AE2U EnergyGridCache addNode 机制）→ 阵列能量可双向流动。
- **P1-3** `BlockEcoStorageDrive.breakBlock` 掉落槽内盘（EntityItem 含 NBT）。
- **P1-4** 盘容量公式 = `typeWeight × byteMultiplier`（与参考一致）。
- **P1-5** MTE ID 33000-33002 越界（`MAXIMUM_METATILE_IDS=32766`）→ 改 32030/32031/32032（javap sipush 实证）。
- 产物：jar 221,753 B @14:07:37。

### 1.2 第二轮（t12，2026-08-26）——t7/t8/t9/t11
- **t7** 配方材料 null 崩溃（CertusQuartzCharged init 未注册）→ `tryAdd` 统一判空跳过。
- **t8** `getStackType()` 构造期 NPE（父类构造器虚调用）→ null 兜底回退 `super.getStackType()`（javap 实证）。
- **t9** 多方块无法成型（shape 朝向相对坐标 vs 世界坐标错位）+ 能量舱不入结构 + 无预览 → shape 转置 + `CASING_OR_ENERGY_HATCH` + 同坐标系扫描 + `IConstructable/ISurvivalConstructable`；**ShapeVerify（真实 StructureLib 1.4.42）独立重跑 ALL CHECKS PASSED**。
- **t11** 源质盘（对照 ESSENTIA_CELL_RESEARCH.md TE4 1.7.60：FQCN/NBT 键/字节语义/门控/依赖），气体盘彻底移除（grep 零残留）。
- 产物：jar 80,517 B，SHA256=`5DDD2C67…`。

### 1.3 第三轮（t19 + 扩展，2026-08-26）——t13-t18/t21
- **t13** 创造栏 `EcoAEGTNHCore.Blocks.*` 空引用崩溃 → RegistryBlocks 统一赋值（putstatic×5 实证）。
- **t14** 源质门控 `Loader.isModLoaded("ThaumicEnergistics")` 大小写永不匹配（TE4 实际 modid 小写）→ `Mods.ThaumicEnergistics.isModLoaded()`。
- **t15** 结构方向镜像修正（锚点 (1,1,n+1)）+ 能量舱 meta 兜底 + `getExtendedFacing` 防 DOWN；ShapeVerify 重跑通过。
- **t16** 盘位潜行放/取 + 换盘通知网格（forceCellArrayUpdate，P2-7 闭合）。
- **t17** GUI EOH 风格重设计（176×128 与纹理一致）；**t18** 控制器自定义贴图（registerIcons 钩子）。
- **t21** 盘物品名本地化（`estorage_cell_` 前缀补全，9 键逐一匹配；注册名变化无引用影响）。
- 产物：jar 85,220 B（含 GUI v2），SHA256=`4153D4AE…`。

### 1.4 第四轮（t28/t31，2026-08-27）——t22-t30
- **t22** onRightclick→openGui（P2-6 闭合）；**t24** TecTech GUI 研究文档质量 ✅（一手源码/像素实测）。
- **t25** `doesSneakBypassUse→true`（vanilla `ItemInWorldManager` javap 实证链路）+ 盘位 meta 2-5 朝向 + 空/满两态 getIcon + shape 'D' anyMeta（ShapeVerify 重跑通过）。
- **t26** 控制器 front/side 分面贴图；**t27** TecTech 配色（§4.1 hex 逐项对齐）+ 分段能量条。
- **t29** ModularUI2 TecTech 主题 GUI 重写（旧 Forge GUI 类/纹理/registerGuiHandler/GUI_STORAGE_STATS 全删，jar 零残留）；**t30** 结构方向再修正（列往右扩、总线背侧右角，DESIGN.md 同步 + ShapeVerify 通过）。
- 产物：jar 86,403 B，SHA256=`06122DDA…`。

### 1.5 第五轮（t34，2026-08-27）——t32/t33
- **t32** TTMultiblockBase 重构：纯 AE（能量舱/EU/voltageTier 零残留）、免维护、长度 1-12 经 GTStructureChannels、DriveElement 自动朝向、TTMultiblockBaseGui；**ShapeVerify/LengthVerify/StructureAllVerify 三程序独立全过**。
- **t33** 盘位分色（金/蓝/紫三贴图）、盘 tooltip Used/Types（客户端安全 + catch 兜底）、WAILA provider（IMC 注册 + Waila 守卫）。
- 产物：jar 90,366 B，SHA256=`26805487…`。

### 1.6 第六轮 + 快速（t41/t45，2026-08-27）——t35-t40/t43/t44
- **t35** setExtendedFacing 递归崩溃（放置即 StackOverflowError）→ facing 覆写全删（javap 确认）。
- **t37** 维护图标不显示（`supportsMaintenanceIssueHoverable→shouldCheckMaintenance`）；**t38** 结构说明/tooltip 全中文化（13 新键双语）；**t40** WAILA 服务端只发 lang 键（javap 无 getDisplayName）+ registerItem 条件去前缀。
- **t43** GUI 主题切 INTERGALACTIC_STANDARD（javap getstatic 实证）；**t44** 维护真根因（`getDefaultHasMaintenanceChecks()→false` + 陈旧 NO_REPAIR 清理，javap 实证）。
- 产物：jar 91,468 B → 91,631 B，SHA256=`3D68852B…` → `8846BA10…`。

### 1.7 第七轮（t48，2026-08-27）——t46/t47
- **t46** readStack 构造期 NPE（有内容盘插回崩溃，与 t8 同族）→ readStack/getTypeWeight 改走构造安全 `getStackType()`（javap invokevirtual 实证）。
- **t47** 星空主题真实可见（MUI2 主题系统本无星空 → GUI 显式应用 gtnhintergalactic space_with_stars 平铺，javap 字符串引用 + 资源在 dev/server jar）。
- 产物：jar 91,966 B，SHA256=`38291C13…`。

### 1.8 t49 字节对齐（t51，2026-08-27）
- totalBytes=MB×1024²（16M=16,777,216）、perType=totalBytes/128（16M→131,072）、MAX_TYPES=63、EcoStorageCellInventory 仅 4 个必要覆写（javap）、byteMultiplier 全删、isCellSupported 改 getCapacityMB。
- 产物：jar 91,049 B，SHA256=`A84FEE87…`。

### 1.9 t50 GUI（t52，2026-08-27）
- 量子计算机同款底部 IO 区（Flow.row SPACE_BETWEEN 三 LED + isMEBusConnected sync + io.mebus 双语键）；星空/五行/能量条保持。
- 产物：jar 92,244 B，SHA256=`9441B2BE…`。

### 1.10 第九轮（t56，2026-08-27）——t53-t55
- **t53** 移除星空背景（用户要求）；**t54** 量子计算机同款 MUI1 GUI（删 MUI2 覆写与类、addUIWidgets/drawTexts、javap 确认）；**t55** 盘位防共用（onAssembled→boolean 归属认领）+ 拆控制器/关机断网（isOperational 8 tick 去抖 + 五入口门控，javap 确认）。
- 产物：jar 93,214 B，SHA256=`8A13CEC7…`。

### 1.11 第十轮（t64，2026-08-27）——t58-t63
- **t58** GUI 统计同步（6 sync 字段 + FakeSyncWidget setter 写回 + IO 行移到底部 PICTURE_PARAMETER_BLANK 条，javap 确认）；**t59** 归属释放（onRemoval→disassembleAll + isCurrentOwnerAlive）；**t61** ME 驱动/箱子（openChestGui→GUI_ME + postInit 自检日志；"无法放入"实为 t46 前 NPE）；**t62** 放置规则（not_formed/tier_not_supported 双语 + 成型静态门控兜底）；**t63** idleDrain=totalBytes/4,194,304（javap ldc2_w 4194304.0d）。
- 产物：jar 95,863 B，SHA256=`54554C89…`。

### 1.12 第十一轮（t70，2026-08-28）——t65-t69（重点复验，详见 §1.13）
- t65 ✅ / t66 ✅（P2-15 已修复，见 §1.13 补充）/ t67 ✅ / t68 ✅ / t69 ✅。
- 当前 build/libs 产物（t71 P2-15 修复后）：jar 101,509 B，SHA256=`CC0242A94445A84618B5F871A7F6432890788F44D35A862BFA75AF8105117BA4`（JDK21 干净 build BUILD SUCCESSFUL，`build-round14.log`）。

### 1.13 第十一轮详细复验（t65-t69，2026-08-28 00:2x，reviewer）

**构建（t70 复验基准）**：JDK21 干净 `gradlew build` → BUILD SUCCESSFUL（`build-round13.log`）；jar **101,468 B @00:20:16**，SHA256=`5EEF5AB7A7A71F38BB9384B98911295BE2B0829BD9A751CC8A7DF652BA7E49E3`（t69 产物）；`major version: 52`；MTE ID sipush 32030/31/32；t46 `readStack`、t44 维护覆写、t59 归属方法（`isCurrentOwnerAlive`/`isOwnerAlive`/boolean `onAssembled`/双 `onDisassembled`）全部在字节码（无回归）。
**t72 收尾复验（P2-15）**：t71 修复后 JDK21 干净 build → BUILD SUCCESSFUL（`build-round14.log`）；jar **101,509 B @00:30:51**，SHA256=`CC0242A94445A84618B5F871A7F6432890788F44D35A862BFA75AF8105117BA4`（与预期一致）；jar 内 `mixins.ecoaegtnh.json` mixins 数组含两个全类名（非空）、两 mixin class 在包内、refmap searge 段映射正确（Slot `func_75214_a`；TileDrive `remap=false` 字面）；无回归抽查（t46 readStack/t44 维护/t59 归属/t67 CAPACITY+onAssembled/t68 byteMultiplier+1000l/t69 tierBaseForPower+0.5d）全过。

| 项 | 源码/字节码核对 | 独立核验 |
|---|---|---|
| t65 IO LED 悬停 tooltip | `MTEEcoStorageArray.addUIWidgets` 底部参数条替换 t58 的 ioStatusLine 常驻文本为 **3 个 6×4 LED 单元**（参数条槽位 0/9/19，x=12/84/164，y=97）：ME 总线（绿=已连接/红=成型未连/灰=缺失）、盘位（绿=有盘/灰=空）、能量（绿=有能量/灰=空）；各 LED `dynamicTooltip(() -> meBusTooltip()/drivesTooltip()/energyTooltip())` + `FakeSyncWidget...setOnClientUpdate(val -> led.notifyTooltipChange())`（量子计算机参数 LED 的悬停模式，无 parametrization 参与）；lang 复用 io.mebus/storage_stats 键 | javap -p 确认 tooltip 方法在；源码 LED 位置/同步器核对 ✅ |
| t66 ECO 盘禁入 ME 驱动器/箱子 | 用户确认"ECO 盘不可放入 ME 驱动器/箱子"；约束排查：ME 驱动/箱子走 `SlotRestrictedInput.STORAGE_CELLS` + `TileDrive.isItemValidForSlot` → `CellRegistry.isCellHandled`，AE2U 无 per-item 禁入 flag 且 `isStorageCell()` 不能返 false（CellInventory 基类要求）→ **mixin 方案**：`MixinSlotRestrictedInput`（@Inject isItemValid SRG func_75214_a HEAD+cancellable，栈为 ItemEcoStorageCell 即拒）+ `MixinTileDrive`（@Inject func_94041_b HEAD+cancellable，remap=false）；ECO 盘位路径直连 EcoStorageCellHandler 不受影响；gradle.properties `usesMixins=true`/`mixinsPackage=mixin`；refmap 已生成（MixinSlotRestrictedInput.isItemValid→func_75214_a） | t70 复验发现：jar 内 json `"mixins": []` 为空（与源码模板逐字节一致）→ 功能不生效（P2-15）。**t71 已修复：手动把两个全类名填入 `mixins` 数组（根因：gtnhgradle generateAssets 生成的数组恒为空、mixin 0.8 无包扫描）；t72 复验：jar 内 json `mixins` 非空、两 class 在包内、refmap searge 映射正确、TileDrive remap=false 字面——P2-15 闭合 ✅** |
| t67 电容 2M AE + 跨阵列共享 | `TileEcoStorageCapacitance.CAPACITY = 2_000_000D`（三档统一 2M AE，`setCapacityByMeta` 恒设 CAPACITY）；**owner 列表**：`owners` List<MTEEcoStorageArray>，`onAssembled` 先 `owners.removeIf(o -> o != controller && !isOwnerAlive(o))`（t59 语义）再 add、`onDisassembled(controller)` 移除、`isAssembled()=!owners.isEmpty()`；`TileEcoStoragePart` 新增默认 `onDisassembled(MTEEcoStorageArray)`（委托 no-arg）+ `isOwnerAlive(MTEEcoStorageArray)`；多阵列能量聚合共享同一电容组（getEnergyStored/Max、inject/extract 遍所有 owner） | javap：CAPACITY 常量 + setCapacityByMeta 在；`isOwnerAlive(MTEEcoStorageArray)`/双 onDisassembled 在 ✅ |
| t68 容量回旧版 | `getTotalBytes() = MB×1000×1024`（16M=16,384,000，javap ldc2_w 1000l/1024l）；`getBytesPerType()/BytePerType() = byteMultiplier×1024`（恢复 `byteMultiplier` 字段 = millionBytes/4；16M→4096，javap sipush 1024）；`EcoStorageCellInventory` 恢复 t49 删除的 4 个字节覆写（getUsedBytes/getRemainingItemCount/getUnusedItemCount/getTypeWeight，公式 = typeWeight×byteMultiplier，对照 1.12.2 参考 EStorageCellInventory）；`ItemEcoStorageCellItem.MAX_TYPES 63→315` + `EcoStorageCellInventory.getTotalItemTypes()` 覆写（解除 AE2U 63 钳制，fluid 25/essentia 60/80/100）；**idleDrain 保持 4.0**（`millionBytes / 4.0`，独立于 1000 进制 totalBytes） | javap：MAX_TYPES、getTotalBytes ldc2 1000/1024、getBytesPerType sipush 1024 ✅ |
| t69 耗电 B+C | `recalculateEnergyUsage()` 由 `64 + Σcell.getIdleDrain()` 改为 **`tierBaseForPower() + 0.5×installedCellCount + ΣidleDrain(已装 ECO 盘)`**（tierBase：L4=2.0/L6=4.0/L9=8.0；installedCellCount 只统计实际装有 ECO 盘的盘位——空槽/非 ECO 不计）；**双触发**：scanStructureVolume 末尾 + `TileEcoStorageDrive.onCellChanged()`（forceCellArrayUpdate 旁）→ 拆/放盘耗电即更新；例：L6 6 盘位装 4×16M → 4.0+2.0+16.0 = 22 AE/t | javap -c：recalculateEnergyUsage 含 `invokespecial tierBaseForPower` + `ldc2_w 0.5d` + `setIdlePowerUsage`；onCellChanged→recalculateEnergyUsage ✅ |

**第十一轮结论（t70 复验 + t72 收尾）**：t65/t67/t68/t69 ✅；t66 初验发现 mixin 配置空列表缺陷（P2-15）→ **t71 修复、t72 复验通过 ✅**（jar 内 json mixins 非空、refmap 正确）。无其它回归。

### 1.14 第十二轮复验（t80：t76/t77/t79，2026-08-28 13:0x，reviewer）

**构建**：JDK21 干净 `gradlew build` → BUILD SUCCESSFUL（`build-round15.log`）；`build/libs/ecoaegtnh.jar` **118,979 B @12:34:53**，SHA256=`E37A3483EAB85B709149D68A7114C42DCF48228334B764F1738C4FBD7A46A0DB`（与预期 t79 产物一致）；`major version: 52`；MTE ID sipush 32030/31/32。

| 项 | 源码/字节码核对 | 独立核验 |
|---|---|---|
| t76 CellSize 分级 | 新枚举 `CellSize` 9 常量（K_256/K_1024/K_4096 → L4 k 级 totalBytes=value×1024；M_16/M_64/M_256 → L6（t76 重映射）；M_1024/M_4096/M_16384 → L9，totalBytes=value×1000×1024——**容量层级严格递增**：262,144 < 1,048,576 < 4,194,304 < 16,384,000 < … < 16,384M）；`byteMultiplier` k:1/4/16、M:4/16/64/256/1024/4096（perType=mult×1024）；`tier` 字段 0/0/0,1/1/1,2/2/2；`idleDrain()`（k 级 value/4000 下限 0.5、M 级 value/4）；`tierLabel()`/`capacityMB()`；`ItemEcoStorageCell.getTierRequired() → size.tier` | **javap：CellSize 9 常量 + tier/totalBytes 字段 + tierLabel 在**；getTierRequired 在 ✅ |
| t76 MixinGridStorageCache | 新 mixin：`@Mixin(GridStorageCache.class)`，`@Shadow(remap=false) activeCellProviders` + `updateCellsStatusFromRegistry`，`@Inject resetCellInfo TAIL remap=false`——遍历 activeCellProviders 中 `TileEcoStorageMEBus`（isOperational），把其 drive-bay 各 cell 经 `updateCellsStatusFromRegistry(iccr, cell)` 上报（AE2U 网络工具"cells"视图原先不认自定义 ICellContainer → ECO 盘显示 0B；修复后正常显示容量）；**mixins json 增至 3 类**（MixinSlotRestrictedInput/MixinTileDrive/MixinGridStorageCache，短名经 config.package 解析） | jar 内 json mixins 3 类 ✅；MixinGridStorageCache.class 在包内 ✅ |
| t77 IO 4 格 LED | 底部参数条 4 个 LED（状态/物品/流体/源质）：statusLed（GREEN/RED 参数纹理）、itemLed（**ORANGE**）、fluidLed（**BLUE**）、essentiaLed（**RED**），各 `dynamicTooltip(statusTooltip/itemTooltip/fluidTooltip/essentiaTooltip)`；**旧 `cellStatsTooltip` 已删**；盘位按物品/流体/源质三类统计（syncItem/Fluid/EssentiaCellCount + sumStat/Stat 家族统计） | javap：4 个 tooltip 方法在、无 cellStatsTooltip；源码 LED 色引用（GREEN/RED/ORANGE/BLUE）✅ |
| t79 GUI 优化 | `showMachineStatusInGUI() → false`（隐藏软锤/空闲提示行，t79）；**`energyBarLine` 能量百分比条已删**（drawTexts 无残留）；五行统计（Structure/Drives/Columns/Energy）+ 底部 4 LED 保持 | javap：showMachineStatusInGUI 在、无 energyBarLine ✅ |
| t78 贴图/lang | 27 张 `estorage_cell_*.png`（16×16）在 jar（t78 vision 已验证类型色金/蓝/紫 + 档位条 + 刻度段）；lang 盘名键 27×2（en/zh） | jar 27 张、尺寸抽查 16×16、lang 27×2 ✅ |
| 无回归 | t46 `readStack`、t44 维护覆写、t59 归属（isCurrentOwnerAlive/isOwnerAlive）、t67 电容（CAPACITY）、t69 耗电公式（tierBaseForPower）全部在字节码 | javap ✅ |

**第十二轮结论**：t76/t77/t79 全部 ✅，无新缺陷；**最终产物 jar 118,979 B，SHA256=`E37A3483EAB85B709149D68A7114C42DCF48228334B764F1738C4FBD7A46A0DB`**。

### 1.15 t81/t82 快速复验（t83：2026-08-28 13:1x，reviewer）

**构建**：JDK21 干净 `gradlew build` → BUILD SUCCESSFUL（`build-round16.log`，2 tasks 执行）；`build/libs/ecoaegtnh.jar` **119,822 B @13:05:03**，SHA256=`6787677BAEB38270D84BC56E05BF0609F125EDC7C044D9F157BD2BFDF5D4F523`（与预期 t82 产物一致）；`major version: 52`；MTE ID sipush 32030/31/32。

| 项 | 源码/字节码核对 | 独立核验 |
|---|---|---|
| t81 存储统计修复 | `EcoStorageCellInventoryHandler.getStorageChannel()` 覆写：`handlerType == FLUID_STACK_TYPE ? StorageChannel.FLUIDS : StorageChannel.ITEMS`（根因：`CellInventoryHandler.getCellType()` 由此派生 ITEM/FLUID，`ICellCacheRegistry.getStorageChannel()` 默认 ITEMS → 流体盘被统计进物品栏） | **javap -c：getStorageChannel 字节码含 `getstatic FLUID_STACK_TYPE` + `getstatic StorageChannel.FLUIDS/ITEMS`** ✅ |
| t82 紫色 LED | `MTEEcoStorageArray` 静态 `ECO_PARAMETER_PURPLE = UITexture.fullImage("ecoaegtnh", "gui/picture/parameter_purple")`（6×4 RGB(176,108,255)）；源质 LED 改用 `ECO_PARAMETER_PURPLE`（原 TecTech RED 系无紫色）；贴图 `assets/ecoaegtnh/textures/gui/picture/parameter_purple.png` | **javap -p：ECO_PARAMETER_PURPLE 静态字段在**；jar 内 parameter_purple.png 存在 ✅ |
| 无回归 | t46 `readStack`、t44 维护覆写（getDefaultHasMaintenanceChecks/supportsMaintenanceIssueHoverable）、t79 `showMachineStatusInGUI`、t59 归属（isCurrentOwnerAlive/isOwnerAlive）、t67 电容（CAPACITY）、t76 `CellSize`（K_256/M_16384）全部在字节码；mixins json 在 jar | javap ✅ |

**结论**：t81/t82 全部 ✅，无新缺陷；**最终产物 jar 119,822 B，SHA256=`6787677BAEB38270D84BC56E05BF0609F125EDC7C044D9F157BD2BFDF5D4F523`**（装机队长已执行、服务器已重启）。

### 1.16 t106 基线核验（2026-08-29，交接后 reviewer 独立复核）

**背景**：NEI 搜"存储外壳"9 槽全紫黑棋盘格 + tooltip 显示原始 key（`item.ecoaegtnh.storage_housing_fluid_l4.name`），此前多轮误判为客户端缓存；真根因 = `ItemEcoStorageHousing.tierLabel()` 曾返回 `"l4"/"l6"/"l9"`（自带 l 前缀），调用处 `"_l" + tierLabel()` 拼接成 `_ll4`（双 l）——注册名 `storage_housing_item_ll4` 与 lang 键/贴图文件名（单 l `storage_housing_item_l4`）不匹配 → lang 找不到显示原始 key、贴图找不到显示紫黑块。组件/盘用 size 后缀无此 bug，故仅外壳异常（客户端 fml 日志 `Fixed item id mismatch ecoaegtnh:ecoaegtnh.storage_housing_item_ll4/ll6/ll9` 实证）。

**修复**：`tierLabel()` 改返回纯数字 `"4"/"6"/"9"`，`"_l"+"4"="_l4"` 与 lang/贴图匹配。

**构建**：JDK21 干净 `gradlew build` → BUILD SUCCESSFUL（t106 构建 12:37:59）；`build/libs/ecoaegtnh.jar` **169,804 B**，SHA256=`06C831A532BA24476170E995E4E12D8713331AD33D3853F3EEFE97B50D362AFE`（与 HANDOVER.md 记录一致）；`major version: 52`。

**字节码核验（javap -p -c，本次 reviewer 独立执行）**：
- `tierLabel()`：`iconst_2 if_icmpne → ldc "9"`、`iconst_1 if_icmpne → ldc "6"`、兜底 `ldc "4"`——**纯数字，无 l 前缀** ✅；
- 名称拼接调用点：`"ecoaegtnh.storage_housing_" + type + "_l" + tierLabel()`（ldc `ecoaegtnh.storage_housing_`、ldc `_l`、invokevirtual tierLabel）→ 单 l 拼接正确 ✅；
- 贴图名拼接点：`"ecoaegtnh:storage_housing_" + type + "_l" + tierLabel()` 同样单 l ✅。

**结论**：t106 修复在产物字节码中生效，jar SHA 与交接记录一致，装机基线 ✅。**待用户任务管理器杀 javaw 后重启客户端复测**：NEI 搜"存储外壳"应显示 9 个外壳正常贴图 + 中文名（"ECO L4 存储外壳（流体）"等）；若仍异常，查 `fml-client-latest.log` 的 `Fixed item id mismatch ecoaegtnh:...` 注册名行。新增配方日志期望 `ECO recipes registered: 27 assembler + 46 assembly-line = 73 total ..., skipped=0`（t105b 后口径）。

---

## 2. P2/P3 待办清单

### P2（发布前处理）
- **P2-11 [数据丢失·次要]** 拆电容方块丢失存储的 AE 能量：设计（§1.3）要求能量随物品保存（ItemBlock + onBlockPlacedBy 读写 NBT）；实现只有 tile NBT。需自定义 ItemBlock 保存/恢复 energyStored。
- **P2-13 [合规]** 代码 GPL-3.0 派生合规：参考仓库实为 GPL-3.0（贴图已原创化消除纹理侧传染）；代码仍为逐类移植，工程无 LICENSE 文件。需 captain/用户决策（加 LICENSE 或声明）。
- **P2-14 [已知限制]** AE2U 63 类型钳制：t68 已通过 `getTotalItemTypes()` 覆写解除（物品盘 315 生效），流体 25/源质 60/80/100 保持。
- **P2-15 [第十一轮·t66 mixin 配置] —— ✅ 已修复闭合（2026-08-28，t71/t72）** `mixins.ecoaegtnh.json` 的 `mixins: []` 为空（gtnhgradle generateAssets 生成的数组恒为空、mixin 0.8 无包扫描）→ 两个 mixin 未登记、禁入功能不生效。**修复**：手动填入 `"ecoaegtnh.mixin.MixinSlotRestrictedInput"` 与 `"ecoaegtnh.mixin.MixinTileDrive"`；t72 复验：jar 内 json 非空、两 class 在包内、refmap searge 映射正确、无回归——闭合。

### P3（顺手项）
- P3-25 fluid 注释（getAmountPerByte 实为 2048 非 1）；P3-26 等级限制边界（槽内已有盘 + 控制器降级不重校验）；P3-15 PQ 堆序（活比较器）；P3-22 getStackInSlotOnClosing 通知。
- 已随重构自动闭合：P3-16/17/27（旧 Forge GUI 删除）、P3-20（预览实现）、P3-23（ENVIRONMENT.md 更新）、P3-24（ID 修复）。

---

## 3. 附：构建与产物证据

- 构建命令：`$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'; .\gradlew.bat build`（守护进程 JDK21；编译工具链 JDK8，产物 major 52）。
- 日志：`build-round*.log`（round1-13）、`build-t6*.log`（t61-t69）、`build-review*.log`、`build-verify-server.log`（旧版本证据）。
- 验证程序：`docs/verify/ShapeVerify.java`（多版重跑 ALL CHECKS PASSED）、`LengthVerify.java`（12 档）、`StructureAllVerify.java`（12×12 交叉矩阵）——reviewer 用真实 StructureLib 1.4.42 + recompiled_minecraft 1.7.10 + guava 17 独立编译运行。
- 产物 SHA256 链（按轮）：`5DDD2C67…` → `4153D4AE…` → `06122DDA…` → `26805487…` → `8846BA10…` → `38291C13…` → `A84FEE87…` → `9441B2BE…` → `8A13CEC7…` → `54554C89…` → `5EEF5AB7…`（t69）→ `CC0242A9…`（t71 P2-15 修复）→ `E37A3483EAB85B709149D68A7114C42DCF48228334B764F1738C4FBD7A46A0DB`（t79）→ `6787677B…`（t82，119,822 B）→ `13AD5B56…`（t104）→ `0428E1B7…`（t105）→ `2094B3FF…`（t105b）→ **`06C831A532BA24476170E995E4E12D8713331AD33D3853F3EEFE97B50D362AFE`（t106，当前，169,804 B）**。
- javap 关键核验（各轮）：MTE ID sipush 32030/31/32、t8 getStackType 兜底、t13 Blocks putstatic×5、t25 doesSneakBypassUse、t37/t44 维护覆写、t46 readStack invokevirtual getStackType、t59 isCurrentOwnerAlive/isOwnerAlive、t63 ldc2_w 4194304.0d、t67 CAPACITY、t68 ldc2 1000/1024 + MAX_TYPES 315、t69 tierBaseForPower + 0.5d。

---

## 4. 游戏内实测清单（发布前必须）

1. **服务器**：装 jar 启动不崩、MTE ID 32030-32032 注册成功；自检日志 `EcoStorageCellHandler registered; ME drive slot filter self-check ->` 应全 true。
2. **放置规则（t62）**：成型前潜行放盘被拒（"机器未成型"）；L4 阵列放 64M/256M 被拒（"需要 L6/L9 控制器"）；16M 可放；取出不受限；任意列长 1-12 成型（手持 N 个控制器投影 N 列）。
3. **盘位交互（t25/t55/t59）**：潜行+手持盘放/取正常、正面 filled 分色贴图切换；两个重叠阵列盘位只归先成型者；挖控制器 → 重摆可重新成型（归属释放）；拆控制器/关机 → AE 终端内容数秒消失，放回/开机恢复，无抖动。
4. **ME 驱动/箱子（t61/t66）**：盘可放入 ME 驱动（t46 后无 NPE）；**t66（P2-15 修复后）** ECO 盘应被 ME 驱动/箱子拒绝（mixin 已登记生效，需游戏内实测确认）。
5. **GUI（t54/t58/t65）**：量子计算机同款界面（screen_blue + 文字屏五行统计 + 能量条 + 底部参数条三 LED 悬停 tooltip）；统计值 1-2 tick 内同步；IO 行只在底部条。
6. **容量/耗电（t68/t69）**：16M 盘 1 类型 1000 物品 → tooltip "Used: 4.1K / 16.4M bytes + Types: 1 / 315"；AE 能量页显示阵列 idle 耗电 = tierBase + 0.5×已装盘数 + ΣidleDrain（如 L6 4×16M = 22 AE/t）；拆/放盘耗电即时更新。
7. **源质盘（t11/t14）**：TE4 加载时三档可见；放盘位 → Essentia Terminal 可读写；64M/256M 在 L6/L9 上插入被拒。
8. **按 DESIGN §M6**：成型/拆解/断电/重启存档/跨区块卸载/盘 NBT 保留/电容能量保留。

> 附：本审查的构建与源码/字节码级复验已完成（十一轮）；游戏内运行测试仍待执行（reviewer 会话无游戏环境）。`docs/REVIEW.md` 由 reviewer 维护，后续修复由 reviewer 复验。
