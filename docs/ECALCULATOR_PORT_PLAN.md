# ECO E-Calculator（可扩展计算子系统）→ GTNH 1.7.10 移植方案

- 版本：v1.5（engineer-core 产出，阶段 1 任务 t4；v1.1 增补用户拍板设计约束 §0.1；v1.2 分级代号统一 C4/C6/C9；v1.3 补充命名来龙去脉；v1.4 显示名统一格式；**v1.5 按 t5 评审 findings（repair round-2）修订：R14 mixin 竞争风险、阶段 B 拆解行为与验证、checkControllerShared 需新实现、头部 16 格外壳、ae2fc 独立安装表述、M3/M4 分组说明**）
- 目标环境：GTNH 1.7.10 / Forge 10.13.4.1614（stable_12）/ GT5U 5.09.54.20 / AE2U rv3-beta-1000-GTNH / StructureLib 1.4.42 / UniMixins
- 依据文档：`docs/ECALCULATOR_RESEARCH.md`（t1 源码原理调研，下文简称 **R1**）、`docs/ECALCULATOR_WEB_NOTES.md`（t2 网络查证，**R2**）、`docs/DESIGN.md` 与 `docs/HANDOVER.md`（E-Storage 移植既有决策，**D**）、`docs/t3-implementation-notes.md`
- 参考仓库：`.research/NovaEngineering-ECOAEExtension-main/`（Nova Engineering - ECO AE Extension v1.2.0，1.12.2，GPL-3.0）
- 验证来源（本方案 §6 全部"可行"结论均出自下列三源之一，非臆测）：
  - **A998**：`.research/ae2u-full/Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH/src/main/java/`（AE2U rv3 全量源码）
  - **J1000**：`C:\Users\30792\.gradle\caches\modules-2\files-2.1\com.github.GTNewHorizons\Applied-Energistics-2-Unofficial\rv3-beta-1000-GTNH\...\Applied-Energistics-2-Unofficial-rv3-beta-1000-GTNH-dev.jar`（**工程编译期实际依赖**，javap -p 核验）
  - **JREL**：`M:\AA科技\GTNH\服务端\mods\appliedenergistics2-rv3-beta-1000-GTNH.jar`（**运行时实际 jar**，javap -p 核验成员名）
  - 说明：`.research/ae2u-1041/` 仅含 6 个**存储 API** 文件（IStorageGrid/ICellHandler/ICellInventory/IMEInventory/StorageChannel/MEInventoryHandler，与 998 逐字节一致，见 D §0），不含合成类；合成 CPU 相关核实以 A998 + J1000 + JREL 三源为准。

---

## 0. 结论速览（TL;DR）

1. **移植可行，核心机制 1:1 保留**：ECalculator = "AE2 合成 CPU 增强 + 宿主替换"（R1 §0.1-2）。vCPU = 直接 `new CraftingCPUCluster(pos,pos)` 的自建实例，经 mixin 注册进 AE2 网格 `CraftingGridCache.craftingCPUClusters`，任务提交/执行/完成全程走 AE2 原版逻辑。**rv3-beta-1000 的 `CraftingCPUCluster` 全部关键成员（字段+方法）在 dev jar 与运行时 release jar 中均保留 MCP 原名（无 SRG 混淆）**，与现有 `MixinGridStorageCache`（remap=false + MCP 名）先例一致 → mixin 主路径风险低。
2. **必须适配的 rv3 差异（共 6 处，全部有替代方案）**：submitJob 拦截点（1.12.2 的 `@At(INVOKE getOutput())` 在 rv3 有两条调用且首条在 merge 分支内 → 改 `@Inject(RETURN)` + 守卫）；`inventory.getItemList()` 不存在 → `isEmpty()`；`IActionSource` → `BaseActionSource`；`executeCrafting @Overwrite` 依赖 1.12.2 `visitedMediums` → **MVP 不做**（rv3 原生 executeCrafting + `accelerator` 字段已驱动并行，A998:772）；`MixinGuiCraftingStatus` 的 `List.get(i)` redirect 因 rv3 改走 `GuiCraftingCPUTable` 不可照搬 → 阶段 C 可选简化；`ICraftingJob` 新增 `supportsCPUCluster` 默认返回 false → 已核实 `CraftingJobV2` 覆写为 `cluster instanceof CraftingCPUCluster`，vCPU 天然通过（**无门禁风险**）。
3. **GTNH 单线程主循环不构成障碍**：参考实现本身就在主线程逐 tick 驱动全部 vCPU（"线程核心"是逻辑容器，并行=每 tick 可推送的样板数 `remainingOperations = accelerator+1-usedOps`）。移植后行为一致，仅需性能预算（MVP C4 上限 12 并发任务）。
4. **MVP 范围**：C4 单档（控制器 + 外壳 + 并行核心 + 线程核心 + 晶阵驱动器 + 传输总线(静态) + ME 通道 + C4 闪存晶阵）+ 结构成型（**西向** 1~12 段）+ vCPU 计算闭环（2 个 mixin）+ MUI1 文本 GUI + C4 配方。超线程核心 / C6、C9 / C6、C9 晶阵 / 状态 GUI 特殊行 / 任务持久化 / TOP 全部后置或不做（§2）。**用户拍板的三点设计约束（GUI= E-Storage MUI1 同款、结构= E-Storage 机制+西向列、外观= 仿原版原创贴图）见 §0.1，本方案其余章节已对齐。分级代号：E-Calculator 一律 C4/C6/C9（§4.2）。**
5. **工作量估计**：阶段 A ~5-7 人日、B ~8-12 人日、C ~3-5 人日、D ~4-6 人日，合计 **~20-30 人日（3-4 周）**，复用 E-Storage 全部基础设施（结构/GUI/配方/装机管线）。

---

## 0.1 设计约束（用户拍板，2026-08-29，**必须遵守**）

> 来源：项目负责人（用户）拍板，经队长下达。以下三点为**硬约束**，覆盖本方案 §4 架构映射、§5 结构与 §8 GUI 与 §11 任务拆分的对应条目；与原 1.12.2 参考实现的差异以本节约束为准。

1. **UI 界面：仿造本项目 E-Storage 的 MUI1 量子计算机同款 GUI**（`useMui2=false`，深蓝 `screen_blue` 主题、198×192、Scrollable 文字屏 + 底部参数条 + `FakeSyncWidget` 数据回写），实现参考 `src/main/java/ecoaegtnh/metatileentity/MTEEcoStorageArray.java` 与 `docs/DESIGN.md`（§2.6/t54-t65 的既有 GUI）。**不照搬 1.12.2 原版 ECalculator 的自定义面板 GUI**（`MonitorPanel`/`CPUStatusPanel`/`StorageBar` 等，R1 §1.5）；原版 GUI 的**信息内容**（字节池/线程核心状态/任务进度/µs 统计）保留，**呈现形式**一律 E-Storage 化（文本行 + LED 悬停）。
2. **多方块结构：仿造 E-Storage 的结构模式**——`TTMultiblockBase` + StructureLib（非 MMCE）；**头部 + 西向扩展列（1~12 列）**布局、**C4/C6/C9 分级**、成型/免维护/结构预览（`construct`/`survivalConstruct` + `GTStructureChannels` 长度通道）机制全部复用现有实现方式（参考 `MTEEcoStorageArray.java`、`docs/DESIGN.md` §2.4-2.5）。原版"3×3×2 头 + 西向 1~12 段每段 6 格"的具体格数作内容参考（§5.1 保留），**结构代码实现方式必须与 E-Storage 一致**（shape 程序化生成、`checkMachine` 12-shape 下降循环、`scanStructureVolume` 收集、`getAlignmentLimits` 等）。
3. **方块外观：贴图/模型仿造 1.12.2 原版 ECO 的 E-Calculator 方块**（控制器/机壳/线程核心/并行核心/晶阵驱动器/传输总线等）。实现阶段由 **model-artist** 参考 `.research/NovaEngineering-ECOAEExtension-main/src/main/resources` 原版贴图产出**原创贴图**（规避 GPL 贴图直接复用，与 E-Storage 40 张原创贴图同法，HANDOVER §2）；我方仅以原版作**视觉风格参照**，不拷贝贴图文件。

> **与 R1（t1 调研）无冲突确认**：①约束 1 正是 R1 §10.3 已建议的"`GuiContainerDynamic` widget → MUI1 整段重写"的落实，且 R1 §5 所述 GUI 数据（ECalculatorData）在 §8.1 已映射为 FakeSyncWidget 数据流，功能信息不丢失；②约束 2 与 R1 §10.3"结构：MM JSON → StructureLib + TTMultiblockBase（E-Storage 已验证 1~12 段）"一致；③约束 3 是 E-Storage 贴图先例（原创化）的延续，不涉及 R1 任何技术结论。详见 §12 增补条目。

---

## 1. 目标与成功标准（MVP）

> 硬约束（来自 D / HANDOVER）：依赖 GT5-Unofficial 而非 Modular Machinery；控制器用 `TTMultiblockBase` + StructureLib（可复用 E-Storage 的结构预览/成型/维护机制）；GUI 用 MUI1（`useMui2=false`，量子计算机同款 198×192 screen_blue）；AE2U rv3 集成 + UniMixins（json 简单类名、remap=false 字面名）；1.7.10 渲染坑（TESR 图集 IIcon + disableLightmapUnit）仅贴图/模型阶段涉及。

**MVP 成功标准（可验证）**：
1. C4 控制器在游戏内成型（1~12 段结构检查通过，拆解/重建正常，免维护、纯 AE 供电）；
2. ME 通讯接口接入 AE2 网格（网络工具可见节点，idle 耗电生效）；
3. 晶阵驱动器放入 A 级闪存晶阵后，控制器核算字节池；在 AE2 合成终端提交任务 → vCPU 被分配进线程核心 → 任务按 `accelerator` 并行度推进 → 产物回库 → 任务完成 → 线程槽释放 → vCPU 补位（日志可观测全流程）；
4. 取消任务/拆方块路径无崩溃、无泄漏（网格 CPU 列表正确刷新）；
5. MUI1 GUI 显示字节/并行/线程/每任务状态，5 tick 刷新；
6. C4 全套配方入库（服务器日志 `skipped=0`，与 E-Storage 同一计数管线）。

---

## 2. MVP 范围裁剪（必做 / 可裁剪 / 不做）

### 2.1 必做（MVP 闭环）
| 项 | 理由 |
|---|---|
| C4 控制器（MTE ID 32033）+ 三档参数化类结构（tier 字段，C6/C9 仅数值） | 结构/GUI/配方管线与 E-Storage 完全同构，参数化成本≈0 |
| 部件方块：外壳、晶阵驱动器(TE)、线程核心(TE)、并行核心(静态)、ME 通道(TE)、传输总线(**静态**，MVP 无 TE) | 传输总线连接状态仅视觉（R1 §4.2 已证 `getSuppliedBytes` 不检查连接）→ 静态方块可省 TE 工作量；阶段 C 再补状态灯 |
| 结构成型：头 3×3×2 + **西向** 1~12 动态段（StructureLib 12 shape，`GTStructureChannels` 长度通道） | 参考机器核心体验（R1 §8）；**用户拍板（§0.1 约束 2）**：机制与 E-Storage 完全一致，仅扩展方向取西向 |
| C4 闪存晶阵（64M 字节，物品） + 驱动槽过滤/等级门控 | 字节池的最小闭环；等级门控逻辑与 E-Storage 盘位双保险模式一致 |
| vCPU 机制：`ECPUCluster` 接口 + `MixinCraftingCPUCluster`（宿主重定向 + submitJob 拦截 + destroy/cancel 回收）+ `MixinCraftingGridCache`（vCPU 注册 + 计时） | 系统灵魂（§6/§7） |
| 控制器核算：parallelism 求和、totalBytes 求和、10% 红线、vCPU 创建/补位、线程核心分配（普通槽优先） | R1 §4.3 全量照搬，float→long 整数化（R1 §12.3） |
| 任务执行：rv3 **原生** `executeCrafting` + `accelerator` 驱动 `remainingOperations` | 不做 @Overwrite 批量引擎（§6.4），MVP 已验证路径即可提供"可验证的计算能力" |
| MUI1 GUI：字节/并行/线程/每任务文本行（Scrollable + TextWidget + FakeSyncWidget，5 tick） | 最小可观测性；**用户拍板（§0.1 约束 1）**：E-Storage 量子计算机同款呈现，数据层直接复用 E-Storage GUI 模式 |
| C4 配方全套（EV 组装机为主 + 少量装配线，≥4 固体输入） | 获取途径闭环（§9） |
| 语言（zh_CN/en_US）、创造页入库、注册（init 阶段） | 装机基本要求 |

### 2.2 可裁剪（阶段后置，含理由）
| 项 | 后置原因 |
|---|---|
| 超线程核心（hyperThread +10% 内存） | 普通线程槽已能验证全部机制；+10% 语义简单但需第三套方块/贴图/结构元素 → 阶段 D |
| C6/C9 档位（并行 2048/16384、线程 2/4、C6/C9 晶阵） | 数值参数化已完成，但配方×3、平衡测试×3、结构元素×3；先 C4 验证机制，阶段 D 扩档 |
| `MixinCraftingCPUStatus` + `MixinGuiCraftingStatus`（AE2 合成状态 GUI 特殊行渲染） | 纯视觉增强（R1 §3.3/3.5）；且 rv3 渲染走 `GuiCraftingCPUTable` 需重写 → 阶段 C 后期可选 |
| 线程核心拆方块 CPU 持久化（NBT 压缩进掉落物） | 复杂且 rv3 CPU NBT 字段与 1.12.2 不同（R1 §12.6）；MVP 拆方块=取消任务+聊天提示 → 阶段 D 可选 |
| TOP / WAILA 集成 | E-Storage 已有 waila 先例，纯增量 → 阶段 D |
| 传输总线 LINK 状态灯/贴图细化 | 纯视觉 → 阶段 C |
| AE2 安全权限检查（`ISecurityGrid`） | E-Storage 先例未做（D §3.4）→ 阶段 D 可选 |
| 任务/线程状态记录器的高级统计展示（µs/t 曲线等） | 保留数据采集（廉价），展示简化 → 阶段 C |

### 2.3 不做（含理由）
- **EFabricator / 合成子系统 / 存储子系统**（参考仓库其他模块，超出本任务范围；合成子系统还需 NAE2/AE2FC，1.7.10 无对应物，R1 §6）。
- **`MixinCraftingCPUClusterTwo`（executeCrafting @Overwrite 批量引擎）**：服务对象主要是 EFabricator（R1 §3.2）；@Overwrite 依赖 1.12.2 `visitedMediums`，rv3 语义完全不同（`workableTasks`/`parallelismProvider`），风险高收益低（MVP 无 EF）。
- **MMCE 一切依赖**（MachineRegistry/ContainerBase/GuiContainerDynamic/TaskExecutor/MEPatternProvider，R1 §6）→ 无 MM 环境。
- **geckolib / resourceloader / lumenized / CrazyAE**（死依赖，源码零引用，R1 §6）。
- **NAE2 / AE2 Fluid Crafting Rework 集成**（1.7.10 无 NAE2；AE2FC 为**服务端独立安装**的 mod ae2fc-1.5.95-gtnh（t5 评审修正"内建"表述），仅 EF 需要，MVP 不使用）。
- **气体存储**（1.7.10 无 Mekeng，E-Storage 已用源质替代——ECalculator 无气体需求）。
- **MMCE-ComponentModelHider / Multiblocked 隐藏内部方块**（无对应物；内部视觉用静态贴图/渲染方案，R1 §6）。

---

## 3. 技术路线与依赖映射（参考仓库 → GTNH 1.7.10）

| 参考仓库依赖（1.12.2） | 用途（源码证据） | GTNH 1.7.10 替代 | 结论 |
|---|---|---|---|
| Modular Machinery CE（MMCE） | 多方块框架：`TileMultiblockMachineController`/`ContainerBase`/`GuiContainerDynamic`/`TimeRecorder`/`EXECUTE_MANAGER`/`MachineRegistry` | **GT5U `TTMultiblockBase` + StructureLib**（E-Storage 已验证全套范式：结构定义/检查/成型/投影/免维护/拆解） | ✅ 替换 |
| ModularUI（MMCE 动态 GUI） | `GuiContainerDynamic` widget 体系 + `SlotItemVirtualJEI` | **MUI1**（`useMui2=false` → `GTUIInfos.openGTTileEntityUI` → `addUIWidgets`/`drawTexts`，E-Storage 量子计算机同款） | ✅ 替换（虚拟物品槽 → 文本行） |
| AE2 Extended Life（AE2EL rv6） | 合成 CPU mixin 目标（`CraftingCPUCluster`/`CraftingGridCache`/`CraftingCPUStatus`/`GuiCraftingStatus`/`AENetworkProxy`） | **AE2U rv3-beta-1000-GTNH**（工程已锁定；§6 逐条核实） | ✅ 替换 |
| AE2 Fluid Crafting Rework | `FluidConvertingInventoryCrafting` 等（仅 EF 批量合成用，R1 §6） | **服务端独立安装 ae2fc-1.5.95-gtnh（独立 mod，非 AE2U 内建；已核验服务端 mods 目录存在独立 jar）**；MVP 不使用 | ➖ 裁剪（作为 R14 风险条背景依据） |
| NAE2 | `VirtualPatternDetails` 等（仅 EF，R1 §6） | 无 | ➖ 裁剪 |
| JEI/HEI | `SlotItemVirtualJEI`（监控面板任务列表） | NEI（GTNH 内置）；MVP 任务列表纯文本 | ✅ 替换 |
| The One Probe | `ECalculatorInfoProvider` | 阶段 D（1.7.10 TOP 或复用现有 waila 集成） | ✅ 替换（后置） |
| CraftTweaker | 源码零 API 引用（R1 §6） | 不需要 | ➖ 裁剪 |
| MMCE-ComponentModelHider / Multiblocked | 内部方块隐藏（可选分支） | 不需要（静态贴图方案） | ➖ 裁剪 |
| geckolib / resourceloader / lumenized / CrazyAE | 死依赖（R1 §6） | 不需要 | ➖ 裁剪 |
| MixinBooter 10.5 | mixin 运行时 | **UniMixins**（`minVersion 0.8.5-GTNH`，json 简单类名 + refmap，E-Storage 先例） | ✅ 替换 |
| 网络包（`PktECalculatorGUIData` + 10 tick onPlayerTick 推送） | GUI 数据 S→C | **MUI1 `FakeSyncWidget`**（GUI 打开期间自动同步，无自定义包；E-Storage 先例） | ✅ 替换 |
| `ModDataHolder` + 机器 JSON + `MixinMachineRegistry` | 结构定义与方块绑定 | **StructureLib 代码定义 shape**（无 JSON、无 mmce mixin） | ✅ 替换 |
| `CompressedStreamTools` CPU NBT 持久化 | 线程核心拆方块保存任务 | 阶段 D 可选（1.7.10 `NBTTagCompound` 同类 API） | ⏸ 后置 |
| `TimeRecorder`（MMCE util） | 每 tick 耗时/并行统计 | **自研 `EcoTimeRecorder`**（~40 行滚动平均，见 §7.5） | ✅ 自研 |

**依赖版本锁定**：AE2U 锁定 **rv3-beta-1000-GTNH**（服务端/客户端已装同名 jar，SHA 可核）。未来升级 AE2U 必须重跑 §6 的签名核对脚本（javap 成员清单），否则 mixin 可能静默/显式失败。

---

## 4. 架构映射（参考仓库包/类 → 1.7.10 设计）

### 4.1 类映射总表

| 参考仓库（1.12.2） | GTNH 1.7.10 设计 | 说明 |
|---|---|---|
| `BlockECalculatorController` + `ECalculatorController` + `ItemECalculatorController` | **`MTEEcalArray`**（MTE 32033/32034/32035，tier 参数 0/1/2） | TTMultiblockBase 子类；核算 + vCPU 调度 + MUI1 GUI 全在此（同 `MTEEcoStorageArray` 模式） |
| `BlockECalculatorCasing` | `BlockEcalCasing`（自定义 Block，无 TE） | 结构填充；硬度 20/抗爆 2000/镐 2 级（参考值） |
| `BlockECalculatorCellDrive` + `ECalculatorCellDrive` | `BlockEcalCellDrive` + `TileEcalCellDrive`（自定义 Block+TE） | 1 槽 IInventory，过滤 `ItemEcalCell` + 等级门控；变更 → 控制器重算 + 网格刷新 |
| `BlockECalculatorTransmitterBus` + `ECalculatorTransmitterBus` | `BlockEcalTransmitterBus`（**静态方块，MVP 无 TE**） | 连接状态仅视觉（R1 §4.2）；阶段 C 补 TE/状态灯 |
| `BlockECalculatorParallelProc` | `BlockEcalParallelProc`（静态方块，meta=等级） | 控制器按 meta 求和 parallelism |
| `BlockECalculatorThreadCore` + `ECalculatorThreadCore` | `BlockEcalThreadCore` + `TileEcalThreadCore` | 持有 `List<CraftingCPUCluster>`、线程上限、`addCPU`/`onCPUDestroyed`；MVP 拆方块=取消任务 |
| `BlockECalculatorThreadCoreHyper` | 阶段 D：`BlockEcalThreadCoreHyper` + `TileEcalThreadCoreHyper` | 超线程 (+2/+4/+8) 与 +10% 内存 |
| `BlockECalculatorMEChannel` + `ECalculatorMEChannel` + `ItemBlockME` | `BlockEcalMEChannel` + `TileEcalMEChannel` | `AENetworkProxy` + `IGridProxyable` + `IActionHost`；`getCPUs()`/`postCPUClusterChangeEvent()`（§7.3） |
| `BlockECalculatorTail` | **省略**（外壳封口） | E-Storage 同款决策（D §1.1） |
| `prop/Levels` 等状态属性 | 枚举 + 方块 meta；动态状态用 `getIcon(IBlockAccess,...)` 或静态贴图 | 1.7.10 无 BlockState 属性系统；E-Storage 最终走静态贴图（D §2.3） |
| `ECalculatorCell`（闪存晶阵） | `ItemEcalCell`（64M/1024M/16384M；MVP 仅 64M） | 非 AE2 `IStorageCell`，纯字节内存物品；`totalBytes = MB×1000×1024`（R1 §1.2） |
| `EPartController`/`EPart`/`AbstractEPart`/`EPartMap` | MTE 内 `scanStructureVolume` 收集 + `TileEcalPart` 基类（controller 引用 + `onAssembled`/`onDisassembled`） | E-Storage 同款（D §1.8/§2.4） |
| `ECalculatorMEChannel.getCPUs` 暴露集群 | 同逻辑（§7.3） | |
| `ECPUCluster`（接口） | `ecoaegtnh.ecalculator.ECPUCluster`（方法前缀改 `ecoaegtnh$`） | 混入接口，`from(CraftingCPUCluster)` 强转（R1 §2.1） |
| `ECPUStatus`（接口） | 阶段 C 可选：`ecoaegtnh.ecalculator.ECPUStatus` | |
| `MixinCraftingCPUCluster` | `ecoaegtnh.mixin.MixinCraftingCPUCluster`（rv3 适配版，§6.2） | 核心混入 |
| `MixinCraftingGridCache` | `ecoaegtnh.mixin.MixinCraftingGridCache`（rv3 适配版，§6.3） | vCPU 注册入口 |
| `MixinCraftingCPUClusterTwo`（executeCrafting @Overwrite） | **不做** | §2.3 |
| `MixinCraftingCPUStatus` / `MixinGuiCraftingStatus` | 阶段 C 可选（§6.5/6.6） | |
| `mixin/mmce/*`（4 个） | **全部不需要** | 无 MM |
| `mixin/ae2/MixinGuiPatternTerm` 等 EF/EStorage 侧 | 不需要（EF 不做；EStorage mixin 已有） | |
| `network/PktECalculatorGUIData` / `ContainerECalculatorController` / `GuiECalculatorController` + widgets | **不需要独立 GUI 类**：MUI1 `addUIWidgets`/`drawTexts` 内嵌 MTE（**E-Storage 量子计算机同款，用户拍板 §0.1 约束 1**；`MonitorPanel`/`CPUStatusPanel`/`StorageBar` 等原版自定义面板不照搬，仅取其信息内容）；数据类 `EcalData`（服务端 supplier 聚合） | |
| `ECalculatorEventHandler`（放/取晶阵、GUI 推送） | 放/取晶阵走 `BlockEcalCellDrive.onBlockActivated`（潜行右键，E-Storage 模式）；GUI 推送由 FakeSyncWidget 承担 | |
| `ECalculatorInfoProvider`（TOP） | 阶段 D：waila/TOP provider | |
| `RegistryBlocks`/`RegistryItems`/`GenericRegistryPrimer` | 并入现有 `EcoAERegistry`/`RegistryBlocks`/`RegistryItems`（新 `RegistryEcal` 或同文件扩展） | 注册名见 §4.2 |
| `ModDataHolder` + `default_machinery/*.json` | 不需要（StructureLib 代码定义） | |
| `MachineCoolants` / `MediumType` / `ITaskExecutor` / `MixinTaskExecutor` | 不需要（EF 专属，R1 §7） | |
| `TimeRecorder`（MMCE） | 自研 `EcoTimeRecorder`（§7.5） | |

### 4.2 注册名 / MTE ID / 语言键（草案）

| 对象 | 注册名 / ID | 语言键（中文示例） |
|---|---|---|
| 控制器 C4/C6/C9（MTE） | `ecoaegtnh.ecalculator.array.c4/c6/c9`；MTE ID **32033/32034/32035**（32030-32032 已被 E-Storage 占用；32050 起为 GT_Framer，段内空闲，`METATILEENTITIES` 上限 32766）；代码常量 `MTE_ID_C4/C6/C9` | `ECO C4 可扩展计算主机`（建议，最终以用户确认为准） |
| 外壳 | `ecoaegtnh:ecalculator_casing` | `ECO 计算子系统外壳` |
| 晶阵驱动器 | `ecoaegtnh:ecalculator_cell_drive` | `ECO 晶阵驱动器` |
| 超导晶阵传输总线 | `ecoaegtnh:ecalculator_transmitter_bus` | `ECO 超导晶阵传输总线` |
| 并行核心 C4/C6/C9 | `ecoaegtnh:ecalculator_parallel_proc_c4/c6/c9`（meta 或独立注册） | `ECO 并行核心 (C4)` |
| 线程核心 C4/C6/C9 | `ecoaegtnh:ecalculator_thread_core_c4/c6/c9` | `ECO 线程核心 (C4)` |
| ME 矩阵通讯接口 | `ecoaegtnh:ecalculator_me_channel` | `ECO ME 矩阵通讯接口` |
| 闪存晶阵 C4/C6/C9 | `ecoaegtnh:ecalculator_cell_c4/c6/c9`（**用户拍板：注册名与显示名统一 C 系**；尺寸 64M/1024M/16384M 信息进 lang/tooltip，如 `ECO C4 闪存晶阵（64M）`；原版注册名为尺寸式 `ecalculator_cell_64m`、原版显示名型号 CE4，本方案按拍板统一 C4/C6/C9，如后续用户要尺寸式改回成本低） | `ECO 闪存晶阵 (C4/C6/C9)` |
| TE 注册名 | `ecoaegtnh.ecal_cell_drive` / `ecoaegtnh.ecal_thread_core` / `ecoaegtnh.ecal_me_channel` | — |

命名沿用参考仓库中文体系（"ECO - 可扩展计算子系统主机"，实况称"计算网络中心"，R2 §2/§4.5），与 E-Storage 的 `ecoaegtnh.estorage_*` 命名风格一致。

> **⚠️ 分级代号（队长更正 + 命名来龙去脉，2026-08-29，硬性）**：E-Calculator 所有内容一律使用 **C4/C6/C9**（"C" = Calculator，与 1.12.2 原版/mcmod 物品页一致，如 C9 主机 mcmod 859367），**严禁**混用 E-Storage 的 L4/L6/L9 体系：
> - **来龙去脉（已核实原版源码）**：原版**源码注册名**（`Levels` 枚举/registryname）确为 `_l4/_l6/_l9`——**R1 调研中的 l4/l6/l9 即对此源码注册名的准确引用，不是方案命名建议**；原版 **lang 玩家显示名**是 CE4/CE6/CE9 等型号（如 `item.ecoaeextension.ecalculator_cell_64m.name = ECO - CE4 闪存晶阵`；E-Storage 对应 SE4/SE6/SE9）——**均为原版历史命名，仅作参照，本项目显示名统一 C4/C6/C9**。
> - **用户拍板**：本项目 E-Calculator **注册名与显示名统一用 C4/C6/C9**；物品/方块注册名档位后缀一律 `_c4/_c6/_c9`（与 E-Storage 的 `_l4/_l6/_l9` 区分，避免玩家混淆）。
> - 控制器档位、线程核心/超线程核心、并行核心、晶阵全部按 **C4/C6/C9** 命名；显示名统一格式 `ECO xxx (C4)/(C6)/(C9)`（如 `ECO 并行核心 (C4)`、`ECO 线程核心 (C4)`、`ECO 闪存晶阵 (C4)`）；**不使用**原版部件型号 CT4/CM4A/CM4B/CE4 等作显示名（仅历史参照，R2 §1）；
> - 注册名（`ecalculator.array.c4`、`ecalculator_parallel_proc_c4`、`ecalculator_thread_core_c4`、`ecalculator_cell_c4` 等）、MTE 常量（`MTE_ID_C4/C6/C9`）、配方分档、tooltip/语言键全部用 C4/C6/C9；
> - 物品中文名带"**计算**"字样以区分（如 `ECO C4 可扩展计算主机` vs E-Storage 的 `ECO E-Storage 阵列 (L4)`）；
> - 代码内 tier 枚举/字段统一用 C4/C6/C9（或内部序号 0/1/2），**不沿用**参考仓库源码 `Levels` 枚举的 L4/L6/L9 字面量；R1 调研中晶阵的 A/B/C 级描述仅为内部等级追溯，玩家可见命名一律 C4/C6/C9（原版 lang 型号 CE4 等仅历史参照）。

### 4.3 包结构草案

```
src/main/java/ecoaegtnh/
  ecalculator/
    ECPUCluster.java          # vCPU 混入接口（ecoaegtnh$ 前缀）
    EcoTimeRecorder.java      # 滚动平均计时器（替代 MMCE TimeRecorder）
    EcalData.java             # GUI 数据聚合（服务端 supplier，替代 ECalculatorData）
    ECPUStatus.java           # [阶段 C] 状态行等级接口
  block/ecalculator/
    BlockEcalCasing.java  BlockEcalCellDrive.java  BlockEcalTransmitterBus.java
    BlockEcalParallelProc.java  BlockEcalThreadCore.java  BlockEcalMEChannel.java
  item/ecalculator/
    ItemEcalCell.java          # 闪存晶阵（等级枚举 C4/C6/C9，MVP 仅 C4）
  metatileentity/
    MTEEcalArray.java          # 控制器（TTMultiblockBase，tier 参数化）
  tile/ecalculator/
    TileEcalPart.java          # 部件基类（controller 引用 + onAssembled/onDisassembled/markNoUpdateSync）
    TileEcalCellDrive.java  TileEcalThreadCore.java  TileEcalMEChannel.java
  mixin/
    MixinCraftingCPUCluster.java   # 核心混入（宿主重定向 + submitJob 拦截 + 接口实现）
    MixinCraftingGridCache.java    # vCPU 注册 + updateCraftingLogic 计时
    # [阶段 C] MixinCraftingCPUStatus.java / MixinGuiCraftingStatus.java / AccessorTaskProgress.java
  recipe/
    EcalRecipes.java          # 配方总控（tryAddAssembler/tryAddAL 计数模式）
  registry/
    RegistryEcal.java         # 方块/物品/TE/MTE 注册（或并入现有 Registry*）
  waila/                      # [阶段 D]
```

`mixins.ecoaegtnh.json`：新增 `MixinCraftingCPUCluster`、`MixinCraftingGridCache`（**server** 组）。简单类名 + `package: ecoaegtnh.mixin` + `remap=false`（沿用现有约定，HANDOVER §4）。**阶段 C 落地 M3/M4 时的分组（t5 评审补充）**：`MixinCraftingCPUStatus`（M3）为**双侧**（`CraftingCPUStatus` 在服务端容器与客户端行数据两侧均使用 → 默认 mixins 组或双侧声明）、`MixinGuiCraftingStatus`（M4）**仅 client**；MVP 的 M1/M2 server 组配置不受影响。

---

## 5. 多方块结构设计（StructureLib）

### 5.1 布局（参考 R1 §8.1；**用户已拍板（§0.1 约束 2）：西向扩展列**）

- **头部固定 3×3×2（18 格）**：控制器 `~` 位于 (0,0,0)；ME 通道 `M` 1 格（头内固定位置，建议控制器背侧右角，与 E-Storage meBus 同侧惯例）；**其余 16 格外壳 `C`**（18 − 控制器 1 − ME 通道 1；t5 评审修正原"17 格"笔误）。整机最小 24 格、最大 90 格结论不变（18+12×6，与参考一致，R1 §8.1）。
- **动态扩展段 1~12**（每段 6 格，沿**控制器局部西向**延伸——即 1.12.2 原版 MM local west 语义（R1 §8.1），世界方向随控制器朝向整体旋转）：每段含
  - 晶阵驱动器 `D` ×2（上/下）
  - 传输总线 `B` ×1（中）
  - 线程核心 `T` ×1（后中）
  - 并行核心 `P` ×2（后上/后下）
- **尾部**：外壳封口（省略 tail 方块）。
- 整机最小 24 格、最大 90 格（18+12×6），与参考一致（R1 §8.1）。
- ⚠️ 方向实现说明：**结构代码机制与 E-Storage 完全一致**（§5.2 的 shape 程序化生成/锚点/`checkMachine` 循环），仅扩展列偏移方向取"局部西向"（E-Storage 为"正面右手侧"）；该方向在 `buildDefinitions()` 的 shape 字符串列轴定义中体现，不改变任何成型/预览/维护机制。

### 5.2 StructureLib 写法（复用 E-Storage 范式，D §2.4）

- `buildDefinitions()` 程序化生成 `size1`..`size12` 共 12 个 shape（列长 n → 头部 + n 段）；字符约定：`~` 控制器锚点、`C` 外壳、`D` 晶阵驱动器、`B` 传输总线、`T` 线程核心、`P` 并行核心、`M` ME 通道、`-` 空气。
- `checkMachine`：从 `size12` 到 `size1` 下降循环，命中即成型；`scanStructureVolume` 收集部件 TE（drive/threadCore/channel），`onAssembled`/`onDisassembled` 生命周期（同 `MTEEcoStorageArray`）。
- `construct`/`survivalConstruct`：`GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stack, 1, 12)`（手持控制器数量 = 段数，E-Storage 先例）。
- `getAlignmentLimits`：仅水平、不旋转、不翻转（E-Storage t35 教训：不覆写 facing getter/setter）。
- 免维护：`getDefaultHasMaintenanceChecks()→false` + `supportsMaintenanceIssueHoverable()→shouldCheckMaintenance()` + onPostTick 清 NO_REPAIR 残留（E-Storage t44 全套）。
- **无 EU 舱**：结构元素不含能量舱；纯 AE 供电（同 E-Storage t32 决策）。耗电 = ME 通道 `proxy.setIdlePowerUsage(tierBase + Σ线程核心)`（tierBase：C4=2/C6=4/C9=8，成型时重算；阶段 D 可调优）。
- 防叠放：`checkControllerShared`（正上/下 2 格同类控制器拒绝成型）——**需新实现**（参考 `S:EPartController.java:39-51/102-113` 的 `checkControllerShared` 逻辑，R1 §4.1；E-Storage 的 `MTEEcoStorageArray` 未实现此方法，不能直接复用）；阶段 A 交付物 2 已含该项，工作量不受影响。

---

## 6. AE2U mixin 集成点逐条可行性评估（对照 A998 + J1000 + JREL）

> 通用结论先行：**J1000 与 JREL 中 `CraftingCPUCluster`/`CraftingGridCache`/`CraftingCPUStatus`/`MECraftingInventory`/`TileCraftingTile`/`AENetworkProxy`/`CraftingLink` 全部关键成员保留 MCP 原名**（这些类无 vanilla 覆写，不参与 SRG 混淆），因此 mixin 注解可直接写 MCP 名 + `remap=false`——与现有 `MixinGridStorageCache`（`resetCellInfo` + remap=false）完全同构，**不存在 t66 `func_94041_b` 式 SRG 名问题**。下表"结论"列基于三源核验。

### 6.1 总览

| # | mixin / 目标 | 结论 | 关键差异与适配 |
|---|---|---|---|
| M1 | `MixinCraftingCPUCluster` → `CraftingCPUCluster`（implements ECPUCluster） | ✅ **可行**（9 个注入点 + 10 个接口方法，逐条见 §6.2） | `submitJob` 注入点改 RETURN；`inventory.getItemList()`→`isEmpty()`；`IActionSource`→`BaseActionSource` |
| M2 | `MixinCraftingGridCache` → `CraftingGridCache` | ✅ **可行** | `updateCPUClusters()V` @RETURN 注入（A998:369 验证）；`onUpdateTick` WrapOperation target 存在（A998:173） |
| M3 | `MixinCraftingCPUStatus` → `CraftingCPUStatus` | ✅ 可行（阶段 C 可选） | rv3 构造器多一个 `(ByteBuf)` 与 `writeToPacket`；NBT 路径 `(NBTTagCompound)`+`writeToNBT` 仍在 |
| M4 | `MixinGuiCraftingStatus` → `GuiCraftingStatus` | ⚠️ **需重写**（阶段 C 可选） | rv3 行渲染走 `GuiCraftingCPUTable` widget，1.12.2 的 `List.get(i)` redirect 目标不存在；替代：注入 `GuiCraftingCPUTable` 或不做特殊行 |
| M5 | `MixinCraftingCPUClusterTwo`（executeCrafting @Overwrite） | ❌ **不做** | 依赖 1.12.2 `visitedMediums`；rv3 为 `workableTasks`+`parallelismProvider`（A998:148-149,228）；MVP 用原生 executeCrafting + accelerator |
| M6 | `AccessorTaskProgress`（`CraftingCPUCluster$TaskProgress.value`） | ✅ 可行 | `protected long value`（J1000/JREL 均确认） |
| M7 | （新）无 mixin 的天然兼容点 | ✅ | `ICraftingJob.supportsCPUCluster` 默认 false，但 `CraftingJobV2` 覆写为 `instanceof CraftingCPUCluster`（A998:239-241）→ vCPU 通过；`craftingAllowMode` 默认 `ALLOW_ALL`（A998:183） |

### 6.2 M1 `MixinCraftingCPUCluster` 逐注入点（参考 R1 §3.1 表格 → rv3 适配）

| 注入点（1.12.2） | rv3-1000 实况（证据） | 适配 |
|---|---|---|
| `submitJob` @ INVOKE `ICraftingJob.getOutput()` | rv3 的 `submitJob` 内**两次** `getOutput()` 调用：merge 分支（A998:1097）+ 提交分支（A998:1135）；merge 分支需 `requestingMachine==null && standalone && busy` 才会执行 → 首 INVOKE 注入点对普通任务**不触发** | **改 `@Inject(method="submitJob", at=@At("RETURN"), cancellable)`**：`if (novaeng_ec$core==null && novaeng_ec$virtualCPUOwner!=null && cir.getReturnValue()!=null) owner.onVirtualCPUSubmitJob(job.getByteTotal());`。RETURN 时任务已确认装载（`usedStorage=job.getByteTotal()` 已执行，A998:1140），语义等价且无字节码 ordinal 脆弱性。merge 路径被 `core!=null` 守卫排除（已分配 vCPU 不再重复分配） |
| `cancel()` @ RETURN，库存空则 `destroy()` | 方法存在（J1000/JREL）；`inventory.getItemList()` **不存在** | 改 `if (this.inventory.isEmpty()) destroy();`（J1000/JREL 确认 `isEmpty()`） |
| `updateCraftingLogic` @ HEAD cancellable（link 取消清理 + 完成即销毁） | 方法签名一致（J1000/JREL）；方法体 A998:745-794 已核实 | 照搬；`getItemList().isEmpty()` → `isEmpty()` |
| `updateCraftingLogic` @ TAIL（记录 `usedOps[0]` 并行数） | `usedOps` 语义已核实：`usedOps[0]=started-remainingOperations`（A998:772,785-787）= 本 tick 实际启动操作数 | 照搬（**关闭 R1 §12.2 开放项**：usedOps[0] 即本 tick 并行数） |
| WrapOperation `TileCraftingTile.isActive()`（在 updateCraftingLogic 内） | rv3 方法体 A998:746 `if (!this.getCore().isActive()) return;` —— `isActive()` INVOKE 存在 | 照搬。注意 `getCore()` 对 vCPU 返回 null（M1 的 getCore 注入），WrapOperation 拦截 `isActive()` 调用点、vCPU 分支不触碰 instance → **无 NPE** |
| `destroy()` @ HEAD cancellable（防重入 + `core.onCPUDestroyed`） | `destroy()` 存在（J1000/JREL；方法体 A998:312-334：guard isDestroyed → 置位 → 通知注销 → 遍历 tiles（vCPU 为空）发事件） | 照搬：`isDestroyed` 时 cancel；否则 `core.onCPUDestroyed(this)`。vCPU 无 tiles，原方法体本身不发网格事件 → 必须走控制器 `postCPUClusterChangeEvent` 补发（§7.3） |
| `isActive()` @ HEAD → 通道代理 isActive | 存在（J1000/JREL） | 照搬 |
| `getGrid()` @ HEAD → 通道节点网格 | 存在 | 照搬 |
| `getCore()` @ HEAD → null | `protected TileCraftingTile getCore()`（J1000/JREL） | 照搬（protected 可注入） |
| `getWorld()` @ HEAD → 控制器世界 | `protected World getWorld()` | 照搬 |
| `markDirty()` @ HEAD cancellable → 控制器 `markNoUpdateSync()` | 存在 | 照搬（`TileEcalPart`/MTE 提供 `markNoUpdateSync` 实现） |
| 接口实现 10 方法（`novaeng_ec$*`） | 字段全部存在（J1000/JREL）：`availableStorage/accelerator/machineSrc/usedOps/isDestroyed/isComplete/myLastLink` | 照搬，前缀改 `ecoaegtnh$`；`machineSrc = new MachineSource(channel)`（rv3 `MachineSource extends BaseActionSource`） |
| `@Shadow` 字段/方法 | 全部存在且 MCP 名（JREL 核验清单见 §0） | `remap=false` + 字面名 |

**新增风险点（rv3 特有，已核实无害）**：`CraftingCPUCluster.submitJob` 的 `job.supportsCPUCluster(this)`（A998:1110）与 `CraftingAllow` 检查（`ALLOW_ALL` 默认，A998:183）均放行 vCPU；`finalOutput`/`mergeJob`（J1000）仅在 merge 分支触发，被 M1 守卫排除。

### 6.3 M2 `MixinCraftingGridCache`（参考 R1 §3.4 → rv3 适配）

| 注入点 | rv3 实况（证据） | 适配 |
|---|---|---|
| `updateCPUClusters()` @ RETURN | A998:369-386：`craftingCPUClusters.clear()` → 遍历 `grid.getMachinesClasses()` 过滤 `TileCraftingStorageTile` → add + addLink。RETURN 注入追加 vCPU 完全兼容 | 照搬：遍历 `grid.getMachines(TileEcalMEChannel.class)` → `channel.getCPUs()` → add 进 `craftingCPUClusters` + `addLink(lastCraftingLink)`。`craftingCPUClusters` 为 `protected final Set`（J1000/JREL） |
| `onUpdateTick` WrapOperation `CraftingCPUCluster.updateCraftingLogic(...)` | A998:171-173：`for cpu : craftingCPUClusters { cpu.updateCraftingLogic(grid, energyGrid, this) }` —— INVOKE target 存在 | 照搬（TimeRecorder 换自研 `EcoTimeRecorder`） |
| `@Shadow addLink(CraftingLink)` | `public void addLink(CraftingLink)`（J1000/JREL） | 照搬 |

**触发链**：控制器/通道 CPU 增减 → `postCPUClusterChangeEvent()` → `grid.postEvent(new MENetworkCraftingCpuChange(node))`（J1000/JREL 类存在，构造器 `(IGridNode)`）→ `updateList=true` → 下个 tick `updateCPUClusters()` 重扫（A998:402-404）→ vCPU 列表刷新。与 1.12.2 完全同构。

### 6.4 M5 不做 @Overwrite 的理由与替代

rv3 原生 `executeCrafting(IEnergyGrid, CraftingGridCache)`（A998:796 起）在 `updateCraftingLogic` 内按 `remainingOperations`（A998:772 `= accelerator+1-usedOps[0..2]`）循环推送；`accelerator` 即 M1 接口的 `setAccelerators` 写入口。**MVP 并行度 = 每 tick 推送次数由 accelerator 驱动，与 1.12.2 行为一致**（R1 §10.2.1 结论）。不做 @Overwrite 规避了：rv3 语义重写成本、与未来 AE2U 升级的冲突风险、`FluidConvertingInventoryCrafting`/NAE2 依赖（R1 §3.2）。

### 6.5 M3/M4（阶段 C 可选）注意事项

- `CraftingCPUStatus`（J1000/JREL）：新增 `(ByteBuf)` 构造器与 `writeToPacket`（GTNH 网络化状态行）；NBT 路径仍在 → `ecLevel` 字段可注入 `(NBTTagCompound)` 构造器 + `writeToNBT`（R1 §3.3 思路可行）。
- `GuiCraftingStatus`（J1000/JREL）：`extends GuiCraftingCPU implements ICraftingCPUTableHolder, IGuiSub`，行渲染经 `GuiCraftingCPUTable` → 1.12.2 的 `redirectDrawFG(List.get(i))` **目标不存在**。替代方案二选一：(a) 注入 `GuiCraftingCPUTable` 的行构建/绘制方法（需现场 javap 定位，风险中）；(b) 放弃特殊行（仅靠控制器 MUI1 GUI 展示 vCPU 状态，功能无损）。

### 6.6 需要"现场核对"的残留项（阶段 B 首个 mixin 落地时）

1. `CraftingGridCache` 内部 `craftingMethods`/`updatePatterns` 在 1000 与 998 的差异（不影响 M2 注入点）。
2. `updateCraftingLogic` 在 1000 是否新增提前 return 分支（J1000 签名一致；若行为差异导致 vCPU 不 tick，日志立刻可见——mixin `required=true` + 每 tick 日志自检）。
3. `ICraftingPatternDetails.getSubstituteInputs`（R1 §12.1）：rv3 用 ore 替换走 `OreListMultiMap`（J1000 CraftingGridCache 字段），可替换输入分支在原生 executeCrafting 内已处理 → MVP 无需额外 mixin。

---

## 7. 计算模型移植（线程/任务模型 → 1.7.10 单线程主循环）

### 7.1 线程模型本质（R1 §4 + 本节核实）

"线程核心"是**逻辑容器**，不是 OS 线程：`TileEcalThreadCore` 持有 `List<CraftingCPUCluster> cpus` + `maxThreads`/`maxHyperThreads`。所有 vCPU 由 `CraftingGridCache.onUpdateTick` 在**服务器主线程**逐 tick 顺序驱动 `updateCraftingLogic`（A998:171-173）。GTNH 与 1.12.2 同为单线程主循环 → **调度模型 1:1 移植，无并发改造**。"并行度"= 每 tick 可推送的样板数（`remainingOperations`），不是线程数。

### 7.2 vCPU 生命周期（数据流，R1 §4.4 适配 rv3）

```
玩家在 AE2 合成终端提交任务
  → CraftingGridCache.submitJob（A998:618）：遍历 craftingCPUClusters（含 M2 注入的 vCPU）
     选中条件：isActive && !isBusy && availableStorage >= job.getByteTotal()（A998:643）
     （allow 默认 ALLOW_ALL，A998:646-652；排序按 coProcessors/存储/名称）
  → vCPU.submitJob（A998:1092）：merge 分支跳过 → 守卫通过 → job.supportsCPUCluster（CraftingJobV2:239 = instanceof ✅）
     → job.startCrafting → ci.commit → usedStorage=byteTotal（A998:1140）
  → M1 RETURN 注入触发 → MTEEcalArray.onVirtualCPUSubmitJob(byteTotal)
      ① 找普通线程槽 addCPU(vCPU,false) → availableStorage=byteTotal
      ② 无普通槽则超线程槽（阶段 D）addCPU(vCPU,true) → usedExtra=byteTotal/10
      ③ 清 virtualCPUOwner 引用 → createVirtualCPU() 补位（10% 红线判断）
  → 每 tick：gridCache.onUpdateTick → vCPU.updateCraftingLogic
      （M2 计时；A998:746 getCore().isActive() 被 M1 WrapOperation 重定向到通道代理）
      → remainingOperations = accelerator+1-usedOps → executeCrafting 推样板 → 装配机/ME 接口执行
  → 任务完成（isComplete && inventory.isEmpty()，A998:757-763）→ M1 updateCraftingLogic@HEAD 拦截 → destroy()
  → M1 destroy@HEAD → core.onCPUDestroyed → 线程核心移除 + 控制器 onClusterChanged → 通道 postCPUClusterChangeEvent
  → 网格 updateCPUClusters 重扫 → vCPU 列表刷新 → 空闲位由控制器补建
```

### 7.3 ME 通道（TileEcalMEChannel）要点（1.12.2 → rv3 API 对照）

| 项 | 实现 |
|---|---|
| 代理 | `new AENetworkProxy(this, "channel", visualItemStack, true)`（构造签名 J1000/JREL 一致）；`setFlags(REQUIRE_CHANNEL, DENSE_CAPACITY)`；`setIdlePowerUsage(...)` 成型时重算 |
| 生命周期 | `validate/invalidate/onChunkUnload/onReady` 四件套 + NBT 读写（E-Storage D §3.2 照抄，不继承 AENetworkPowerTile） |
| 电缆 | `AECableType.DENSE`（1.7.10 无 DENSE_SMART，E-Storage 先例） |
| CPU 暴露 | `getCPUs()`：`proxy.isActive() && assembled → controller.getClusterList()`（线程核心 CPU + 当前 vCPU） |
| 事件 | `MENetworkPowerStatusChange`/`MENetworkChannelsChanged` → active 翻转时 `postCPUClusterChangeEvent()`（`grid.postEvent(new MENetworkCraftingCpuChange(proxy.getNode()))`，`GridAccessException` 吞掉） |
| `MachineSource` | `new MachineSource(channel)`（rv3 `appeng.api.networking.security.MachineSource`）——M1 写 `machineSrc`、晶阵变更 post 用 |

### 7.4 控制器核算（MTEEcalArray，R1 §4.3 照搬 + 整数化）

- `recalculateParallelism()`：Σ 并行核心 parallelism（C4=256/C6=2048/C9=16384）→ 写进**每个已分配 vCPU** 的 `accelerator`（M1 `setAccelerators`）。
- `recalculateTotalBytes()`：Σ 晶阵驱动器 `getSuppliedBytes()`（按控制器等级过滤：**C4 晶阵→C4+、C6 晶阵→C6+、C9 晶阵→仅 C9**；MVP 仅 C4。R1 内部 A/B/C 级描述仅作追溯）。
- `getAvailableBytes()` = totalBytes − Σ 线程核心 usedStorage。
- `createVirtualCPU()`：`availableBytes < totalBytes/10`（**long 整数运算**，修复 R1 §12.3 float 舍入）→ 停；否则 `new CraftingCPUCluster(pos, pos)`（J1000/JREL 构造器一致）→ `setVirtualCPUOwner(this)` + `setAvailableStorage(availableBytes)` + `setAccelerators(parallelism)` → `postCPUClusterChangeEvent()`。
- `onVirtualCPUSubmitJob(usedBytes)`：普通槽 → 超线程槽（+`usedBytes/10`，阶段 D）→ 补位 vCPU；全部失败日志告警。
- 重算时机：成型/拆解、晶阵增删、线程核心增删、每 5 tick GUI 数据、40 tick 结构复查（E-Storage onPostTick 节奏）。

### 7.5 统计（自研 EcoTimeRecorder）

`EcoTimeRecorder`（~40 行）：`addUsedTime(int)` + 滚动窗口（如 100 tick）均值/峰值，替代 MMCE `TimeRecorder`。用途：M2 记录每 vCPU `updateCraftingLogic` 耗时（µs/t）、M1 TAIL 记录 `usedOps[0]`（并行数/t）。GUI/日志展示；也是性能预算的观测手段。

### 7.6 性能预算（GTNH 单线程约束）

- vCPU 总数上限 = Σ(线程核心线程数+超线程数)。**MVP C4**：1 线程/核心 × 12 段 = **12 并发任务上限**；C9 理论 144（阶段 D 再评估）。
- 每活跃 vCPU 每 tick 1 次 `updateCraftingLogic`（与 AE2 原版每合成 CPU 集群同路径；原版 GTNH 包内已有大量 CPU 集群运行，开销量级一致）。
- GUI 数据聚合每 5 tick；TimeRecorder 仅 int 累加。
- 监控手段：日志每 200 tick 输出 Σ(µs/t)（调试开关），超阈值（如 >2ms/tick 总量）告警。
- **结论：MVP 预算内无性能风险**（12 任务 × 单次逻辑 ≈ 原版 12 台合成 CPU 的等价开销）。

---

## 8. 数据同步 / GUI / 网络方案

### 8.1 控制器 GUI（MUI1，**用户拍板（§0.1 约束 1）：E-Storage 量子计算机同款机制**）

- 打开：`TTMultiblockBase` 默认右击开 GUI（MUI1：`useMui2()=false` → `GTUIInfos.openGTTileEntityUI` → `addUIWidgets`），窗口 198×192，`screen_blue` 背景（`TecTechUITextures` 直绘）。
- **呈现形式 = E-Storage 同款**（参考 `MTEEcoStorageArray.addUIWidgets/drawTexts` 与 D §2.6）：Scrollable 文字屏 + 底部参数条（含 LED 悬停格）+ FakeSyncWidget 数据回写；**不照搬 1.12.2 原版 `MonitorPanel`/`CPUStatusPanel`/`StorageBar` 自定义面板**（R1 §1.5 仅作信息内容参考）。
- 布局（信息内容照 R1 §1.5 语义，E-Storage 化呈现）：
  - 顶部参数条：字节池（total/used/available + 10% 红线灯）、总并行数、线程核心数（悬停 tooltip，仿 E-Storage t65 LED 格）；
  - 中部 Scrollable 文字屏：每线程核心一行（等级/线程数/超线程数）+ 每任务一行（输出物品名 × 数量、进度、µs/t、并行/t）；
  - 底部：结构状态行（`super.drawTexts` 基座行，E-Storage 同款）。
- 数据流：服务端每 5 tick 聚合 `EcalData`（从 `proxy.getGrid().getCache(ICraftingGrid.class).getCpus()` 过滤本控制器 vCPU，J1000 `getCpus()` 存在）→ 写入 MTE 字段 → `FakeSyncWidget.*Syncer`（`LongSyncer`/`IntegerSyncer`/`BooleanSyncer`，supplier 读服务端字段、客户端写回字段）→ `TextWidget.dynamicString` 展示。**无自定义网络包**（替代 1.12.2 `PktECalculatorGUIData` + onPlayerTick 推送，R1 §5.2）。
- 晶阵放/取：`BlockEcalCellDrive.onBlockActivated` 潜行右键（E-Storage 模式）；成型门控 + 等级门控 + 聊天提示（`drive.cell.not_formed` 同款）。

### 8.2 方块状态/贴图（1.7.10 渲染约束）

- MVP 全部部件**静态贴图**（`setBlockTextureName`，E-Storage D §2.3 最终方案）；动态状态（LINK/STATUS/STORAGE_LEVEL）阶段 C 用 `getIcon(IBlockAccess,...)` 或 TE 渲染扩展。
- 控制器 MTE 贴图：`registerIcons` + `IIconContainer` + `TextureFactory.of(...)`（E-Storage t18 模式，服务端回退 `MACHINE_CASING_STABLE_TITANIUM`）。
- 贴图全部**原创**（参考仓库贴图 GPL-3.0 且已规避先例，HANDOVER §2）；风格对齐 AE2 深蓝（`tools/gen-textures*.ps1` 管线可复用）。
- **外观约束（§0.1 约束 3，用户拍板）**：方块/物品贴图与模型**仿造 1.12.2 原版 ECO 的 E-Calculator 方块外观**（控制器/机壳/线程核心/并行核心/晶阵驱动器/传输总线等），由 **model-artist** 参考 `.research/NovaEngineering-ECOAEExtension-main/src/main/resources` 原版贴图产出**原创贴图**（视觉风格参照，不拷贝文件，GPL 规避——与 E-Storage 40 张原创贴图同法）；原版方块模型/镂空视觉（R1 §8.2）同样以"参照风格 + 原创产出"处理。

---

## 9. 配方 / 研究 / 创造页规划

### 9.1 配方体系（参照 E-Storage 73 条体系与分档规则，D §4.2 + HANDOVER §2）

- API：`GTValues.RA.stdBuilder()` + `RecipeMaps.assemblerRecipes` / `assemblyLineRecipes`；`tryAddAssembler`/`tryAddAL` 按返回值计数，空集合 → `skipped++` + 告警（E-Storage t105 教训）。
- **C4 档（MVP，EV 电压）**：组装机配方（≈8 条）：
  - 外壳 ×2 配方（钛板 + 铱板组合，EV 组装机）；
  - 并行核心 (C4)（256 并行）、线程核心 (C4)、晶阵驱动器、传输总线、ME 通讯接口（含 AE2 元件，如 1k 合成存储/逻辑处理器）、C4 闪存晶阵（64M，需 AE2 存储元件 + GT 电路）；
  - 装配线 1-2 条（≥4 固体输入，如控制器本体或高级部件，LuV 装配线）——验证装配线路径。
- **C6/C9（阶段 D）**：装配线配方（ZPM/UHV），材料钛/铱/中子素体系延续；档位互锁（低档产物为高档输入之一，仿 E-Storage 研究前置语义）。
- 计数验证：服务器日志 `ECO ecalc recipes registered: N assembler + M assembly-line = T total, skipped=0`（沿用现有日志管线）。

### 9.2 创造页

- 现有 4 tab（机器/盘/组件/外壳）不动；新增**"计算"tab**（或并入机器 tab，实现时定）：控制器 ×3、外壳、并行核心 ×3、线程核心 ×3、晶阵驱动器、传输总线、ME 通道、晶阵 ×3；按 `displayAllReleventItems` 显式排序（E-Storage t104 经验）。

### 9.3 语言/工具提示

- zh_CN/en_US 全量键（名称 + tooltip：职责、线程/并行/字节数值、等级限制、"未成型不可用"提示）；`ecoaegtnh.structure.error.*` 结构错误键（沿用 E-Storage）。

---

## 10. 风险清单与缓解

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| R1 | **mixin apply 失败**（成员名/签名不匹配，required=true 直接崩） | 高 | §6 已按 J1000+JREL 双 jar 核验全部成员为 MCP 名；阶段 B 最小 mixin 集先行（先 M2 后 M1），首装即验 FML 日志无 `Mixin apply failed` |
| R2 | **AE2U 版本漂移**（1000→更高版改合成类） | 中 | 锁定 rv3-beta-1000-GTNH；升级前重跑 §0 签名核对（javap 清单脚本化保存至 `tools/ae2u-signature-check.ps1`） |
| R3 | rv3 `submitJob` 语义差异（merge 分支/supportsCPUCluster/CraftingAllow） | 中 | 已核实：CraftingJobV2 `instanceof` 放行、allow 默认 ALLOW_ALL；注入点改 RETURN+守卫（§6.2）；任务闭环用日志逐步验证 |
| R4 | `getCore().isActive()` NPE（vCPU 的 getCore 返回 null） | 高 | M1 WrapOperation 在 `isActive()` 调用点拦截（A998:746 已核实存在），vCPU 分支不调用 original → 无解引用 |
| R5 | 任务完成/取消后网格 CPU 列表残留（destroy 不触发网格刷新） | 中 | vCPU 无 tiles，原生 destroy 不发事件 → 控制器 `onClusterChanged` → `postCPUClusterChangeEvent` 补发（§7.3）；日志断言 `getCpus()` 数量 |
| R6 | 性能：vCPU 过多拖慢主线程 | 低 | MVP 12 任务上限；TimeRecorder 观测；阶段 D 再评估 C9 144 上限 |
| R7 | TTMultiblockBase 限制（动态长度/免维护/无 EU） | 低 | E-Storage 全套已验证（12 shapes、t44 免维护、t32 纯 AE） |
| R8 | 结构方向与用户预期不符 | 低 | **已拍板（§0.1 约束 2）：西向扩展列**（原版语义）；阶段 A 实现即按西向，装机后用户复核确认 |
| R9 | 1.7.10 渲染坑（贴图/模型） | 低 | MVP 静态贴图规避；阶段 C 动态状态若需要，遵循 TESR 图集 IIcon + disableLightmapUnit 教训 |
| R10 | 许可合规（参考仓库 GPL-3.0） | 低 | 项目已定 GPL-3.0（HANDOVER §6.5）；改写代码文件头注明出处与许可；贴图原创 |
| R11 | 与现有 E-Storage mixin 冲突 | 低 | 目标类不同（`GridStorageCache` vs `CraftingCPUCluster`/`CraftingGridCache`）；同名方法无交集；UniMixins 独立配置 |
| R12 | 任务持久化缺失导致拆方块丢任务 | 低 | MVP 明示"拆方块=取消任务"（聊天提示）；阶段 D 可选 NBT 持久化（含 rv3 NBT 字段核对） |
| R13 | 晶阵字节池与 job.getByteTotal() 口径不符 | 中 | 均按 AE 字节（`MB×1000×1024`，R1 §1.2）；集成测试：64M 晶阵提交一个 `byteTotal` 已知的任务，验证 10% 红线/容量拒绝行为 |
| R14 | **ae2fc/其他 mod 与 M1/M2 目标类（`CraftingCPUCluster`/`CraftingGridCache`）的 mixin 竞争**——ae2fc-1.5.95-gtnh 为**服务端独立安装**的 mod（§3 依赖表背景），其核心修改亦可能触及 AE2 合成相关类 | 中 | 缓解：①**UniMixins `priority` 设定**（M1/M2 设较高优先级，控制与 ae2fc 等 mod 的 mixin 应用顺序）；②装机后核对 **FML 日志 mixin apply 状态**（无 `Mixin apply failed`/冲突告警）；③**装机验证含 ae2fc 实测**——在 ae2fc 存在环境下跑通合成终端任务全流程（提交→执行→完成→取消） |

---

## 11. 任务拆分（阶段 A-D：交付物 + 验证方式 + 工作量）

> 通用验证管线（每阶段）：`gradlew build -x test`（JDK21，`JAVA_HOME` 按 HANDOVER）→ 双端装 jar + SHA 核对 → 重启服务端（`M:\像素工厂\jdk-25.0.1\bin\java.exe ... lwjgl3ify-forgePatches.jar nogui`）→ 查 `server-log.txt`（无异常、无 `NOT added`、mixin 无 apply 失败）→ 用户游戏内复测 + 截图。

### 阶段 A：骨架与结构成型（~5-7 人日）
- 交付物：
  1. `MTEEcalArray`（C4，tier 参数化）、`BlockEcal*` 7 类方块 + `ItemEcalCell(C4)` + `TileEcal*` 3 个 TE + 注册（RegistryEcal，FML init 注册 MTE 32033/`MTE_ID_C4`）；
  2. StructureLib 12 shape 结构定义（**西向扩展列**，§5.1）+ `checkMachine`/`construct`/`survivalConstruct` + 免维护 + `checkControllerShared`——实现方式逐项对照 `MTEEcoStorageArray.java`（§0.1 约束 2）；
  3. `TileEcalMEChannel` 网格节点（空 CPU 列表）+ idle 耗电；
  4. 基础 MUI1 GUI 占位（结构状态行 + 空数据面板，**E-Storage 同款**，§0.1 约束 1）+ 语言键。
- 验证：成型/拆解/重建（12 段长度通道）；ME 通道入网格（网络工具可见、耗电生效）；右击开 GUI 无崩溃；西向布局装机后用户复核。
- 退出条件：结构检查日志正确、拆解无泄漏、GUI 可开。

### 阶段 B：计算核心（~8-12 人日，最高风险）
- 交付物：
  1. `EcoTimeRecorder` + `ECPUCluster` 接口 + `MixinCraftingCPUCluster`（rv3 适配，§6.2）+ `MixinCraftingGridCache`（§6.3）+ mixins.json 登记（server 组）；
  2. `TileEcalCellDrive`（1 槽 + 等级门控 + 变更重算）、`TileEcalThreadCore`（cpus 列表 + addCPU/onCPUDestroyed + 拆方块取消任务）；
  3. `MTEEcalArray` 核算：parallelism/totalBytes/10% 红线/vCPU 创建/分配/补位 + `postCPUClusterChangeEvent` 全链路；
  4. **控制器拆解/失效行为**（t5 评审补充）：拆解/失效时 `destroy` 待命 vCPU（若存在）→ 逐线程核心对在途集群执行 `cancel`/`destroy`（取消任务并释放线程槽）→ `postCPUClusterChangeEvent()` 通知网格刷新 CPU 列表（结构重建成型后由 `createVirtualCPU()` 正常补位）；
  5. 调试日志：vCPU 创建/分配/销毁/任务完成事件 + 每 200 tick 性能汇总。
- 验证：
  1. 合成终端提交任务 → 日志见 vCPU 分配 → 样板推送装配机 → 产物回库 → 任务完成 → 槽释放 → vCPU 补位；
  2. 取消任务路径、拆线程核心路径（任务取消 + 网格刷新）、10% 红线行为（字节池见底停建 vCPU）；
  3. 并行核心数量变化 → `remainingOperations` 变化（日志验证 accelerator 生效）；
  4. 双端稳定运行 ≥30 分钟无崩溃、`getCpus()` 无残留；
  5. **拆控制器方块验证（t5 评审补充）**：拆控制器后 `getCpus()` 无残留（待命 vCPU 与在途集群全部清理）、无泄漏（线程槽/网格引用释放）、重启服务端后无异常（旧存档加载 + 结构重建成型后 vCPU 正常补建）。
- 退出条件：完整任务闭环日志链可观测、无 mixin 告警。

### 阶段 C：GUI 与贴图（~3-5 人日）
- 交付物：
  1. MUI1 完整面板（字节条/线程核心状态/任务列表动态文本 + LED 悬停，**E-Storage 同款呈现**，§0.1 约束 1）+ 语言全量 + 传输总线静态贴图细化（可选状态灯）；
  2. **方块/物品贴图（§0.1 约束 3）**：由 **model-artist** 参考 `.research/NovaEngineering-ECOAEExtension-main/src/main/resources` 原版 E-Calculator 贴图产出**原创贴图**（控制器/机壳/线程核心/并行核心/晶阵驱动器/传输总线/晶阵，AE2 深蓝风、GPL 规避，与 E-Storage 40 张贴图同法）。
- 验证：GUI 数据随任务实时刷新；贴图无 missing（客户端 FML 日志）；外观与 1.12.2 原版风格对标、与 E-Storage 风格统一（用户确认）。
- 可选（若时间允许）：`MixinCraftingCPUStatus`/`MixinGuiCraftingStatus` 特殊行（§6.5 方案 a/b 二选一；**落地 M3 时确认其与 M4 的分组：M3 双侧、M4 仅 client，见 §4.3 mixins.json 说明**）。

### 阶段 D：配方/扩展/打磨（~4-6 人日）
- 交付物：C4 全套配方（§9.1）+ 创造页"计算"tab + WAILA/TOP（可选）+ 装机打磨（平衡数值、tooltip）。
- 可选扩展（按用户优先级）：C6/C9 档位 + C6/C9 晶阵 + 超线程核心 + CPU 持久化。
- 验证：服务器配方计数日志 `skipped=0`；双端装机 SHA 一致；用户游戏内全流程复测（合成→计算→完成）；HANDOVER 更新。

### 里程碑与依赖
```
A（结构）→ B（计算闭环）→ C（GUI/贴图）→ D（配方/打磨）
A 完成后即可并行启动 D 的配方骨架（依赖方块/物品注册）
B 完成后即可提前装机验证核心价值（可裁剪 C 直接进入 D）
```

---

## 12. 与 R1/R2 结论的一致性声明

- 本方案全部技术结论引用 R1 并与其无矛盾；R1 开放项在本方案的关闭/细化情况：
  - R1 §12.2（`usedOps` 语义）→ **已关闭**：A998:772,785-787 证实 `usedOps[0]` = 本 tick 启动操作数，TAIL 注入点照搬（§6.2）。
  - R1 §10.2.1（executeCrafting 重写）→ **设计决策关闭**：MVP 不重写，用原生 + accelerator（§6.4），与 R1 建议一致。
  - R1 §10.2.2（`getItemList()` 缺失）→ **已关闭**：`isEmpty()` 替代（J1000/JREL 确认）。
  - R1 §12.3（float 舍入）→ **已关闭**：long 整数运算（§7.4）。
  - R1 §12.4（DENSE_CAPACITY）→ 沿用 E-Storage 的 `AECableType.DENSE` 结论（§7.3）。
  - R1 §12.6（CPU NBT 持久化）→ MVP 不做，阶段 D 可选（§2.2）。
  - R2 §4.3（MVP 裁剪建议：C4 主机+线程核心+闪存晶阵+ME 接口闭环）→ **采纳**，本方案 §2 与之吻合。
- 本方案相对 R1 的**新增发现**（t4 核验补充）：①JREL 证实合成类成员全部保留 MCP 名（§0/§6）；②rv3 `submitJob` 存在 merge 分支 + `supportsCPUCluster` 门禁（A998:1094-1112），注入点改 RETURN（§6.2）；③`CraftingJobV2.supportsCPUCluster = instanceof`（A998:239-241）无门禁风险；④`GuiCraftingStatus` 改走 `GuiCraftingCPUTable`，原 redirect 不可照搬（§6.5）。
- **用户设计约束（§0.1）与 R1 无冲突确认**（v1.1 增补）：
  - 约束 1（GUI 仿 E-Storage MUI1，不照搬原版自定义面板）= R1 §10.3"`GuiContainerDynamic` widget → MUI1 整段重写"的落实；R1 §5.2 的 GUI 数据内容（ECalculatorData）在 §8.1 全部映射为 FakeSyncWidget 数据流，**信息不丢失、仅呈现形式 E-Storage 化**；
  - 约束 2（TTMultiblockBase + StructureLib、西向 1~12 列、复用成型/维护/预览机制）= R1 §10.3"结构：MM JSON → StructureLib + TTMultiblockBase（E-Storage 已验证 1~12 段）"的直接落实；R1 §8 的格数/段数内容保留为 §5.1 布局参考；
  - 约束 3（贴图仿原版外观 + 原创产出）= E-Storage 贴图原创化先例（HANDOVER §2）的延续，R1 §6 依赖表无贴图依赖，无冲突。
  - 结论：**三点约束与 R1 全部技术结论一致，无任何矛盾**；约束仅收窄实现形态（GUI/结构/外观），不改变 §6/§7 的核心机制设计。
- **分级代号更正（v1.2/v1.3/v1.4）与 R1/R2 无冲突确认**：R1 源码调研中的 L4/L6/L9 是参考仓库**代码内部** `Levels` 枚举/注册名（`_l4/_l6/_l9`）字面量——**系对源码注册名的准确引用，非方案命名建议**；原版 **lang 玩家显示名**为 CE4/CE6/CE9 等型号（如 `item.ecoaeextension.ecalculator_cell_64m.name = ECO - CE4 闪存晶阵`；E-Storage 对应 SE4/SE6/SE9，均仅历史参照），mcmod 百科亦用 C 系（R2 §3.2：C9 主机物品页 859367）。**用户拍板：本项目注册名与显示名统一 C4/C6/C9、显示名格式统一 `ECO xxx (C4)/(C6)/(C9)`**（§4.2 硬性规则，注册名后缀 `_c4/_c6/_c9` 与 E-Storage 的 `_l4/_l6/_l9` 区分；不使用 CT4/CM4A/CE4 等型号作显示名），与 R1 的源码内部命名不矛盾（代码内部 tier 用序号 0/1/2 或 C 系命名实现，不沿用 L 字面量）；与 E-Storage 的 L4/L6/L9 体系明确区分，避免玩家混淆。

---

## 13. 开放决策点（需用户/评审确认）

> v1.1 更新：原决策 1（结构延伸方向）与决策 3（GUI 深度）已由用户拍板关闭（见 §0.1）；剩余如下。

1. ~~**结构延伸方向**~~ → ✅ **已拍板：西向扩展列**（§0.1 约束 2，2026-08-29）。
2. **MVP 档位**：只做 C4（推荐，机制验证优先；C6/C9 数值参数化已内建）vs 三档同做（配方/测试×3）。
3. ~~**GUI 深度**~~ → ✅ **已拍板：E-Storage 量子计算机同款 MUI1 文本面板**（§0.1 约束 1；1.12.2 原版自定义面板不采用）。
4. **拆方块任务策略**：取消（推荐 MVP）vs 持久化（阶段 D）。
5. **"计算"tab**：新建 vs 并入现有"机器"tab。
6. **能耗模型**：纯 AE（tierBase+线程数，推荐，与参考/E-Storage 一致）vs 引入 GT EU（出范围）。

---

*移植方案完成时间：阶段 1（t4）；v1.1 修订：增补用户拍板设计约束 §0.1；v1.2 修订：分级代号更正为 C4/C6/C9；v1.3 修订：补充命名来龙去脉；v1.4 修订：显示名统一格式 `ECO xxx (C4)/(C6)/(C9)`；**v1.5 修订（2026-08-29，repair round-2，t5 findings）：§10 新增 R14（ae2fc/其他 mod mixin 竞争，中，UniMixins priority + FML 日志 + ae2fc 实测）、§11 阶段 B 控制器拆解/失效行为与验证清单第 5 项、§5.2 checkControllerShared 改为需新实现（参考 S:EPartController.java:39-51/102-113）、§5.1 头部修正为其余 16 格外壳（24~90 格结论不变）、§3/§2.3 ae2fc 改为服务端独立安装（R14 背景依据）、§4.3/§11 阶段 C 明确 M3 双侧、M4 仅 client**。配套文档：`docs/ECALCULATOR_RESEARCH.md`（t1）、`docs/ECALCULATOR_WEB_NOTES.md`（t2）。方案评审：t5。*
