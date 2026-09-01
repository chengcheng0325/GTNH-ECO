# ECO AE Extension（1.12.2 参考仓库）— E-Calculator 可扩展计算子系统源码原理调研报告

> 调研对象：`.research/NovaEngineering-ECOAEExtension-main/`（Nova Engineering - ECO AE Extension v1.2.0，1.12.2，GPL-3.0）
> 调研范围：仅 E-Calculator（可扩展计算子系统，mod 内资源名前缀 `ecalculator_*` / `extendable_calculator_subsystem_*`）。EFabricator / EStorage 仅在涉及共享机制（mixin、注册、事件）时提及。
> 证据标注约定：`src/main/java/github/kasuminova/ecoaeextension/...` 缩写为 `S:...`，行号以实际读取为准；仓库根路径统一省略，指 `.research/NovaEngineering-ECOAEExtension-main/`。AE2U(1.7.10) 源码路径缩写为 `A:...`（= `.research/ae2u-full/Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH/src/main/java/...`）。
> 结论均基于逐文件阅读源码核实，未臆测；标注"推断"处表示从代码行为归纳而非源码注释明说。
>
> ⚠️ **等级命名注记（队长 2026-08-29 核实）**：本报告中出现的 L4/L6/L9 均为**源码注册名/枚举**（`Levels` 枚举与 registryname `*_l{4,6,9}`）的准确引用；原版 **lang 玩家显示名为 CE4/CE6/CE9**（C=计算 Calculator，如 `item.ecoaeextension.ecalculator_cell_64m.name=ECO - CE4 闪存晶阵`；E-Storage 对应 SE4/SE6/SE9）。本项目（GTNH 版）E-Calculator 等级命名统一用 **C4/C6/C9**（显示名+注册名），与既有 E-Storage 的 L4/L6/L9 区分。

---

## 0. 结论速览（TL;DR）

1. **ECalculator 与 AE2 原版合成 CPU 是"增强 + 宿主替换"关系，不是替换 AE2 合成系统本身**：
   - 复用 AE2 原版 `CraftingCPUCluster` 类的**全部任务语义**（任务队列、字节存储、样板派发 `executeCrafting`、`updateCraftingLogic` 等），通过 mixin 注入附加字段/接口实现 `ECPUCluster`（S:`common/ecalculator/ECPUCluster.java`）。
   - 子系统创建**额外的 CraftingCPUCluster 实例（虚拟 CPU / vCPU）**，通过 `MENetworkCraftingCpuChange` 事件 + `CraftingGridCache.updateCPUClusters()` 混入（MixinCraftingGridCache）注册进 AE2 原版任务调度器的集群集合，使 AE2 原版合成终端（crafting terminal）的任务派发流程**原封不动**地把任务交给 vCPU。
   - vCPU 的"宿主"从原版合成单元方块（TileCraftingTile 集群）替换为 ECalculator 多方块：`isActive()/getGrid()/getWorld()/markDirty()/getCore()` 等全部重定向到子系统的 ME 通道（MixinCraftingCPUCluster 各注入点）。
   - 任务提交后，mixin 拦截 `CraftingCPUCluster.submitJob()`（S:`MixinCraftingCPUCluster.java:84-90`），把**已装载任务的整个 vCPU 对象**转交给"线程核心"（`ECalculatorThreadCore.addCPU`），任务继续在 AE2 原版逻辑下运行，直到完成/取消 → 集群销毁 → 从线程核心移除并通知网格。
   - 并行度通过写 AE2 字段 `accelerator` 实现（S:`ECalculatorController.recalculateParallelism():125-134` → `novaeng_ec$setAccelerators`；AE2 rv3 中 `remainingOperations = accelerator + 1 - usedOps`，见 A:`CraftingCPUCluster.java:772`）。
2. **系统本质**：把"多台 AE2 合成 CPU"做成了一个可扩展的 GT 风格多方块 + 字节存储池 + 线程/超线程调度器；玩家在 AE2 合成终端看到的每台 vCPU 对应一个真正运行中的 CraftingCPUCluster。
3. **核心数值**（L4/L6/L9）：并行核心 256/2048/16384；线程核心 1/2/4 线程；超线程核心 (+2/+4/+8)；闪存晶阵 64M/1024M/16384M 字节（且 B 级只能 L6+、C 级只能 L9 驱动）。建造：控制器头部固定 3×3×2（18 格，含 ME 通道与进出流舱），向西 1~12 个扩展段（"workers" 动态样板），每段 1 列 2×3（6 格）= 2 并行核心 + 2 晶阵驱动器 + 1 传输总线 + 1 线程核心（或超线程核心）；整机最小 24 格、最大 90 格。
4. **移植可行性（AE2U 1.7.10 对照，补充情报）**：AE2U rv3-beta-998 的 `CraftingCPUCluster` / `CraftingGridCache` / `CraftingCPUStatus` / `GuiCraftingStatus` / `AENetworkProxy` / `TileCraftingTile` / `GridFlags` / `ICraftingJob` / `ICraftingMedium` 等目标类与方法签名绝大多数存在且同名，mixin 集成**基本可行**；但 `executeCrafting` 的 @Overwrite（MixinCraftingCPUClusterTwo）依赖 1.12.2 AE2EL 特有的 `visitedMediums` 字段，rv3 中是 `workableTasks` + `parallelismProvider`，**必须按 rv3 语义重写**；`MECraftingInventory.getItemList()` 在 rv3 不存在，需适配。详见 §10。

---

## 1. 系统组成全景（组件清单）

### 1.1 方块（common/block/ecotech/ecalculator/，均继承 BlockECalculatorPart/BlockECalculator）

| 方块 | 类 / 静态实例 | 注册名 | 说明 |
|---|---|---|---|
| 控制器（主机） | `BlockECalculatorController` L4/L6/L9（S:`BlockECalculatorController.java:40-47`） | `ecoaeextension:extendable_calculator_subsystem_l{4,6,9}`（:59） | 继承 MMCE `BlockController`（:34）；硬度 20/抗爆 2000/镐 2 级（:54-56）；右键开 GUI 需结构成型（:79-87）；`FORMED` 亮光 12/4（:74-76）；TE = `ECalculatorController(machineRegistryName)`（:95-103） |
| 外壳 | `BlockECalculatorCasing.INSTANCE`（S:`BlockECalculatorCasing.java:8-14`） | `ecalculator_casing` | 纯装饰方块 |
| 晶阵驱动器 | `BlockECalculatorCellDrive.INSTANCE`（S:`BlockECalculatorCellDrive.java:30`） | `ecalculator_cell_drive` | 1 格槽位只收 ECalculatorCell（TE 过滤器 S:`ECalculatorCellDrive.java:201-215`）；actualState 显示 LINK/STATUS/STORAGE_LEVEL（:52-77）；拆方块掉出晶阵（:80-94） |
| 超导晶阵传输总线 | `BlockECalculatorTransmitterBus.INSTANCE`（S:`BlockECalculatorTransmitterBus.java:27`） | `ecalculator_transmitter_bus` | 连接上下相邻 CellDrive；actualState 显示 LINK 状态/等级（:63-79）；neighborChanged 转发（:53-59） |
| 并行核心 | `BlockECalculatorParallelProc` L4/L6/L9（S:`BlockECalculatorParallelProc.java:27-29`） | `ecalculator_parallel_proc_l{4,6,9}` | parallelism 256/2048/16384（:27-29）；组装后 STATUS=ON（:62-74） |
| 线程核心 | `BlockECalculatorThreadCore` L4/L6/L9（S:`BlockECalculatorThreadCore.java:37-39`） | `ecalculator_thread_core_l{4,6,9}` | threads 1/2/4（:37-39）；拆方块时把 CPU 数据压缩序列化进掉落物 NBT（:94-152），放置时还原（:154-177）；actualState RUN/ON（:181-193） |
| 超线程核心 | `BlockECalculatorThreadCoreHyper` L4/L6/L9（S:`BlockECalculatorThreadCoreHyper.java:8-10`） | `ecalculator_thread_core_hyper_l{4,6,9}` | threads/hyperThreads = (0,2)/(0,4)/(1,8) |
| ME 通讯接口 | `BlockECalculatorMEChannel.INSTANCE`（S:`BlockECalculatorMEChannel.java:16`） | `ecalculator_me_channel` | 子系统与 AE2 网络的协议接入点；TE = `ECalculatorMEChannel` |
| 尾部 | `BlockECalculatorTail` L4/L6/L9（S:`BlockECalculatorTail.java:27-29`） | `ecalculator_tail_l{4,6,9}` | 扩展段末端封口，FORMED 亮光（:61-67） |

### 1.2 物品（common/item/ecalculator/）

| 物品 | 类 | 注册名 | 说明 |
|---|---|---|---|
| 闪存晶阵（内存条） | `ECalculatorCell` L4/L6/L9（S:`ECalculatorCell.java:22-24`） | `ecalculator_cell_{64,1024,16384}m` | `totalBytes = millionBytes*1000*1024`（:31-33）：64M→65,536,000 B、1024M→1,048,576,000 B、16384M→16,777,216,000 B；不可堆叠；L6 级晶阵 tooltip 说明仅 L6+ 可驱动、L9 仅 L9（:52-57） |
| 各部件 ItemBlock | `ItemECalculatorController`（继承 MMCE `ItemBlockController`，S:`ItemECalculatorController.java:15`）、`ItemECalculatorParallelProc`、`ItemECalculatorThreadCore`（构造时回填 `block.setItem(this)`，S:`ItemECalculatorThreadCore.java:18-20`）、`ItemECalculatorMEChannel`（继承 `ItemBlockME`，放置时 `proxy.setOwner(player)`，S:`ItemBlockME.java:24-43`）、`ItemECalculatorCellDrive` | 同方块 | tooltip 说明职责与修正值 |

### 1.3 TileEntity（common/tile/ecotech/ 与 .../ecalculator/）

| TE | 类 | 职责 |
|---|---|---|
| 控制器 | `ECalculatorController extends EPartController<ECalculatorPart>`（S:`ECalculatorController.java:27`） | 结构成型、部件汇总、并行/字节核算、vCPU 创建与分配、GUI 数据包（详见 §2/§4） |
| 部件基座 | `EPartController`（S:`EPartController.java:19`）→ `TileCustomController`（S:`TileCustomController.java:14`）→ MMCE `TileMultiblockMachineController` | 结构检查/组装/拆解 tick 流 |
| 部件接口 | `EPart`（S:`EPart.java:5`）、`AbstractEPart<C>`（S:`AbstractEPart.java:13`，继承 MMCE `TileEntitySynchronized`）、`ECalculatorPart`（S:`ECalculatorPart.java:9`，缓存 controllerLevel） | 部件生命周期回调 onAssembled/onDisassembled |
| 线程核心 | `ECalculatorThreadCore extends ECalculatorPart`（S:`ECalculatorThreadCore.java:16`） | 持有 `ObjectArrayList<CraftingCPUCluster> cpus`（:20）、线程/超线程上限、CPU 增删/持久化 |
| 并行核心 | `ECalculatorParallelProc`（S:`ECalculatorParallelProc.java:6`） | 只读方块 parallelis m |
| 晶阵驱动器 | `ECalculatorCellDrive implements IAEAppEngInventory`（S:`ECalculatorCellDrive.java:25`） | 1 槽 `AppEngInternalInventory`（:27）、容量供给、传输总线连接状态 |
| 传输总线 | `ECalculatorTransmitterBus`（S:`ECalculatorTransmitterBus.java:14`） | 上/下 CellDrive 连接管理 |
| ME 通道 | `ECalculatorMEChannel implements IActionHost, IGridProxyable`（S:`ECalculatorMEChannel.java:30`） | AENetworkProxy、网格事件、向网格暴露 CPU 列表 |
| 尾部 | `ECalculatorTail`（S:`ECalculatorTail.java:5`） | 占位/状态刷新 |

### 1.4 网络包（common/network/）

| 包 | 方向 | 用途 |
|---|---|---|
| `PktECalculatorGUIData`（S:`PktECalculatorGUIData.java:16`） | S→C | 控制器 GUI 数据（ECalculatorData record），客户端 `GuiECalculatorController.onDataUpdate`（:46-56） |
| （EStorage 共用）`PktCellDriveStatusUpdate`（S:`PktCellDriveStatusUpdate.java:17`） | S→C | 驱动器读写状态灯（仅 EStorage 使用） |

### 1.5 GUI（client/gui/）

| 组件 | 类 | 说明 |
|---|---|---|
| 主 GUI | `GuiECalculatorController extends GuiContainerDynamic<ContainerECalculatorController>`（S:`GuiECalculatorController.java:21`，MMCE 动态组件 GUI） | 255×221；三个组件：StorageBar(7,7)、CPUStatusPanel(7,58)、MonitorPanel(7,137)（:38-40） |
| 存储条 | `StorageBar`（S:`StorageBar.java:21`） | 各 vCPU 占用字节占比横向条（:96-107） |
| CPU 状态面板 | `CPUStatusPanel` + 内部类 `CPUStatus`（S:`CPUStatusPanel.java:28,89`） | 每线程核心一格：等级贴图/线程数/超线程数/进度条（:129-196） |
| 监控面板 | `MonitorPanel`：`DataPanel`（内存/线程/CPU µs/能耗/总并行）+ `TaskPanel`（任务列表，JEI 虚拟槽 `SlotItemVirtualJEI`）（S:`MonitorPanel.java:25,53,197`） | 数据来自 `ECalculatorData` |
| 容器 | `ContainerECalculatorController extends ContainerBase<ECalculatorController>`（S:`ContainerECalculatorController.java:11`） | 无玩家槽（:20-23）；服务端事件轮询发包（见 §5） |

### 1.6 方块状态属性（common/block/ecotech/ecalculator/prop/）

- `Levels`：L4/L6/L9/L11（L11 预留未使用）（S:`Levels.java:3-11`）
- `DriveStorageLevel`：EMPTY/A/B/C（S:`DriveStorageLevel.java:8-13`）
- `DriveLink`：NONE/UP/DOWN；`DriveStatus`：OFF/ON
- `ThreadCoreStatus`：OFF/ON/RUN；`ParallelProcStatus`：OFF/ON
- `TransmitterBusLink`：NONE/UP/DOWN/ALL；`TransmitterBusLinkLevel`：NONE/L4/L6/L9

---

## 2. 核心数据模型：ECPUCluster / ECPUStatus

### 2.1 `ECPUCluster`（S:`common/ecalculator/ECPUCluster.java:11-39`）

一个**纯接口**，`static ECPUCluster from(CraftingCPUCluster)` 直接把 AE2 的 CraftingCPUCluster 强转成该接口（:13-15）——实现由 `MixinCraftingCPUCluster implements ECPUCluster` 提供（S:`MixinCraftingCPUCluster.java:36`）。接口方法（全部带 `novaeng_ec$` 前缀防止冲突）：

| 方法 | 语义 |
|---|---|
| `setAvailableStorage(long)` | 覆盖 AE2 私有字段 `availableStorage`（mixin @Shadow，S:`MixinCraftingCPUCluster.java:54,230-232`）——vCPU 容量 = 任务字节数 |
| `setAccelerators(int)` | 覆盖 `accelerator`（:236-238）——并行数 |
| `getController()/setThreadCore(ECalculatorThreadCore)` | 归属线程核心；set 时同时把 `machineSrc` 换成通道的 `MachineSource`（:242-259） |
| `setVirtualCPUOwner(ECalculatorController)` | 未分配前的 vCPU 主人；非空时 `machineSrc = new MachineSource(channel)`（:261-273） |
| `getControllerLevel()` | 控制器等级（经 thread core 或 virtualCPUOwner 反查，:275-291） |
| `getUsedExtraStorage()/setUsedExtraStorage(long)` | 超线程额外字节（+10%）（:293-303） |
| `markDestroyed()` | 置 `isDestroyed/isComplete = true`（:305-310） |
| `getTimeRecorder()/getParallelismRecorder()` | MMCE `TimeRecorder` 统计（每次 updateCraftingLogic 耗时 / 每 tick 并行数）（:312-322） |

### 2.2 `ECPUStatus`（S:`common/ecalculator/ECPUStatus.java:5-9`）

仅一个方法 `getLevel()`，由 `MixinCraftingCPUStatus` 注入到 AE2 的 `CraftingCPUStatus`（合成状态 GUI 的行数据），让 AE2 原版"合成状态"界面能识别并特殊渲染 ECalculator 的 vCPU 行（见 §3.5）。

### 2.3 数值与等级约束

- 并行核心：L4=256 / L6=2048 / L9=16384（S:`BlockECalculatorParallelProc.java:27-29`）
- 线程核心：L4=1 / L6=2 / L9=4（S:`BlockECalculatorThreadCore.java:37-39`）
- 超线程核心：L4=(0,2) / L6=(0,4) / L9=(1,8)（S:`BlockECalculatorThreadCoreHyper.java:8-10`）
- 晶阵：A=64M / B=1024M / C=16384M 字节（S:`ECalculatorCell.java:22-33`）；B 晶阵在 L4 主机不可用、C 晶阵仅 L9（S:`ECalculatorCellDrive.java:54-70, 72-97`）

---

## 3. 与 AE2 合成系统的集成（mixin 逐个分析）

> 总述：ECalculator **不替换、不改写 AE2 的任务调度语义**，而是：(a) 给 CraftingCPUCluster 附加 ECalculator 状态（ECPUCluster 接口 + 混入字段）；(b) 把**自建的 CraftingCPUCluster 实例**注册进 AE2 的 CraftingGridCache 集群集合；(c) 拦截少数方法把"宿主"重定向到子系统通道。以下逐条列出所有 mixin/accessor 的目标类、注入方法与行为变更（含 EF/EStorage 相关的，注明归属）。

### 3.1 `MixinCraftingCPUCluster`（AE2EL `CraftingCPUCluster`）— ECalculator 核心混入

目标：`appeng.me.cluster.implementations.CraftingCPUCluster`（S:`MixinCraftingCPUCluster.java:35`，remap=false，implements ECPUCluster :36）

| 注入点 | 方法/位置 | 行为变更 |
|---|---|---|
| `injectSubmitJob` | `submitJob(IGrid, ICraftingJob, IActionSource, ICraftingRequester)` @ INVOKE `ICraftingJob.getOutput()`（:84-90） | 若 `virtualCPUOwner != null`：取 `job.getByteTotal()` 调 `owner.onVirtualCPUSubmitJob(usedBytes)` → vCPU（连同已装载任务）被分配进线程核心，并立刻补建新 vCPU（见 §4.3） |
| `injectCancel` | `cancel()` @ RETURN（:92-101） | 若归属线程核心且库存已空 → `destroy()`（立即销毁空集群） |
| `injectUpdateCraftingLogicStoreItems` | `updateCraftingLogic(...)` @ HEAD cancellable（:103-121） | 归属线程核心时：link 已取消 → 清 link 并 cancel；任务完成(isComplete)且库存空 → destroy 并取消本次逻辑（提前回收） |
| `injectUpdateCraftingLogicTail` | `updateCraftingLogic` @ TAIL（:123-127） | 记录 `usedOps[0]` 到 parallelismRecorder（每 tick 并行数统计） |
| `redirectUpdateCraftingLogicIsActive` | WrapOperation `TileCraftingTile.isActive()`（:129-146） | 归属线程核心/vCPU 时改为判断**通道代理 isActive**（不再依赖原版合成方块） |
| `injectDestroy` | `destroy()` @ HEAD cancellable（:148-158） | 归属线程核心：防重复销毁，先 `core.onCPUDestroyed(this)`（从线程核心移除 + 通知控制器） |
| `injectIsActive` | `isActive()` @ HEAD（:160-173） | 归属核心/vCPU → 通道代理 isActive |
| `injectGetGrid` | `getGrid()` @ HEAD（:175-197） | 归属核心/vCPU → 通道代理的 IGridNode.getGrid() |
| `injectGetCore` | `getCore()` @ HEAD（:199-204） | 返回 null（无原版合成方块） |
| `injectGetWorld` | `getWorld()` @ HEAD（:206-214） | 返回控制器所在世界 |
| `injectMarkDirty` | `markDirty()` @ HEAD cancellable（:216-226） | 归属核心/vCPU → 改为 `markNoUpdateSync()`（避免原版方块 tile 依赖） |
| 接口实现 | 各 `novaeng_ec$*` 方法（:228-323） | 见 §2.1 |

### 3.2 `MixinCraftingCPUClusterTwo`（AE2EL `CraftingCPUCluster`，priority=0）— 并行合成引擎（EFabricator 为主，ECalculator 亦受影响）

目标同为 `CraftingCPUCluster`（S:`MixinCraftingCPUClusterTwo.java:64`）。核心是 **@Overwrite `executeCrafting(IEnergyGrid, CraftingGridCache)`**（:122-364，作者注明"完全覆写样板发配方法"）：把 AE2EL 原版一次只推一个样板的逻辑改成**按频率批量推样板**，支持三类合成介质（`r$specialMediumTreatment`，:375-421）：

- `MediumType.NULL`：普通介质（含 ECalculator vCPU 的普通任务），行为≈原版逐次推进（:179,244,250,292,325）；
- `MediumType.MEPatternProvider`：MMCE 的 MEPatternProvider（含 `WorkModeSetting.ENHANCED_BLOCKING_MODE`），按库存可用量裁剪 `r$craftingFrequency`，`r$IgnoreParallel` 时按 1 计（:376-397）；
- `MediumType.EF`：EFabricator 介质（`EFabricatorMEChannel`），按 workers 剩余空间与库存裁剪频率，一次推 n 份（:398-418）。

另含：`injectItems` 的 ghost-inject 包装（:443-463，避免虚产出再次进入链接）、`AccessorTaskProgress`（内嵌 `@Mixin(targets = "...CraftingCPUCluster$TaskProgress")` 接口，暴露 `value` 字段，:465-472）、能耗预扣逻辑（:179-191）、`FluidConvertingInventoryCrafting` 流体转换（:194）。

> 对 ECalculator 的意义：vCPU 上的任务也走这个覆写后的 executeCrafting；并行度除了 accelerator 字段外，批量推送还受 `remainingOperations` 控制（:292-294）。移植时若 MVP 不含 EFabricator，可先按 rv3 原版 executeCrafting + 仅 accelerator 方案，或整体重写（见 §10）。

### 3.3 `MixinCraftingCPUStatus`（AE2 `CraftingCPUStatus`）— 状态行带等级

目标 `appeng.container.implementations.CraftingCPUStatus`，实现 `ECPUStatus`（S:`MixinCraftingCPUStatus.java:15-16`）：
- `<init>(ICraftingCPU, int)` @ RETURN：若 cpu 是 ECPUCluster 记录 `ecLevel`（:21-26）；
- `<init>(NBTTagCompound)` @ RETURN：从 NBT 读 `ecLevel`（:28-33）；
- `writeToNBT` @ RETURN：写 `ecLevel`（:35-41）。
作用：AE2 合成状态界面按等级渲染 ECalculator vCPU 行（配合 MixinGuiCraftingStatus）。

### 3.4 `MixinCraftingGridCache`（AE2EL `CraftingGridCache`）— 网格缓存注入

目标 `appeng.me.cache.CraftingGridCache`（S:`MixinCraftingGridCache.java:25-26`）：
- `injectUpdateCPUClusters`：`updateCPUClusters()` @ RETURN（:39-53）——遍历 `grid.getMachines(ECalculatorMEChannel.class)`，把每个通道 `getCPUs()`（=控制器 getClusterList：线程核心 CPU + 当前 vCPU）加入 `craftingCPUClusters` 集合，并把 `getLastCraftingLink()` 非空的 cluster 的 link 也 `addLink`（**这是 vCPU 进入 AE2 调度器的唯一入口**）；
- `wrapOnUpdateTick`：WrapOperation `updateCraftingLogic` 调用（:55-66）——归属线程核心的 cluster 用 `TimeRecorder` 记录耗时（µs）。

### 3.5 `MixinGuiCraftingStatus`（AE2 `GuiCraftingStatus`，client）— 合成状态 GUI 特殊行

目标 `appeng.client.gui.implementations.GuiCraftingStatus`（S:`MixinGuiCraftingStatus.java:30-31`）：
- `redirectDrawFG`：WrapOperation `List.get(i)`（:88-97）——若行数据是 ECPUStatus 且等级非空，走自定义 `novaeng_ec$renderECPUStatus`（:99-175）渲染 L4(青)/L6(金)/L9(紫) 底色行 + 当前合成物品/数量/存储，否则返回原行。纯客户端渲染增强。

### 3.6 `MixinGuiPatternTerm`（AE2 `GuiPatternTerm`，client）— EFabricator 上传样板按钮

目标 `appeng.client.gui.implementations.GuiPatternTerm`（S:`MixinGuiPatternTerm.java:24-25`）：
- `initGui` @ TAIL order=9999（:43-72）：追加"上传样板"GuiTabButton（EFabricator 控制器图标），并把其他 mod 的 tab 按钮 Y 坐标顺延；
- `actionPerformed` @ HEAD（:74-80）：点击 → 发送 `PktPatternTermUploadPattern`（C→S，服务端注册见 S:`ECOAEExtension.java:61`）并取消原处理；
- `drawFG` @ HEAD（:82-85）：仅合成模式显示按钮。
> 归属 EFabricator 功能，ECalculator 不依赖。

### 3.7 `MixinPacketInventoryAction`（AE2 `PacketInventoryAction`，server）— 防刷包

目标 `appeng.core.sync.packets.PacketInventoryAction`（S:`MixinPacketInventoryAction.java:20-21`）：
- `serverPacketData` @ HEAD cancellable（:26-36）：玩家开着 AEBaseContainer 且动作是 `MOVE_REGION` 时，经 `AEPktInvActionSpamHandler`（S:`AEPktInvActionSpamHandler.java:15-23`，限速 8 包/秒×3）判定超限 → 断线。
> 归属 EFabricator/通用防滥用，与 ECalculator 无直接关系。

### 3.8 `MixinTileChestFilter` / `MixinTileDriveFilter`（AE2 内部过滤器）— EStorage 专属

- 目标 `appeng.tile.storage.TileChest$CellInventoryFilter.allowInsert`（S:`MixinTileChestFilter.java:12-23`）与 `TileDrive$CellValidInventoryFilter.allowInsert`（S:`MixinTileDriveFilter.java:12-23`）：`EStorageCell` 一律拒绝插入（原版箱子/驱动器放不下大硬盘）。**与 ECalculator 无关**（ECalculator 晶阵本来就不进原版驱动器）。

### 3.9 Accessor（S:`mixin/ae2/`）

- `AccessorAbstractCellInventory`：EStorage 单元库存内部访问；
- `AccessorCellRegistry`：`getHandlers()` 暴露 cell handler 列表，EStorageCellHandler 插到队首（S:`CommonProxy.java:69-72`）；
- `AccessorContainerPatternEncoder`：EFabricator 样板上传用。

### 3.10 mmce 侧 mixin（S:`mixin/mmce/`）

- `MixinMachineRegistry`：`MachineRegistry.getWaitForLoadMachines` @ Redirect `List.add`（S:`MixinMachineRegistry.java:21-49`）——把 `modularmachinery:extendable_*` 系列 DynamicMachine 与自定义控制器方块绑定（`BlockController.MACHINE_CONTROLLERS.put`），其余照常 add。**这是"方块↔机器"映射的关键**。
- `MixinMEPatternProvider`：给 MMCE 的 MEPatternProvider 加 `ignoreParallel` 标志（配合 §3.2，EF 功能）。
- `MixinTaskExecutor`：给 MMCE 并发 `TaskExecutor` 加 `addTEMarkTask(TileEntity)` 延迟 markDirty 队列（S:`MixinTaskExecutor.java:41-54`）。
- `MixinIngredientItemStackRenderer`：MMCE JEI 渲染常量微调（缩放 0.5→0.7 等，S:`MixinIngredientItemStackRenderer.java:11-26`）。

---

## 4. 计算线程模型（ThreadCore / Hyper / ParallelProc / TransmitterBus / CellDrive 职责与协作）

### 4.1 部件装配与生命周期（EPartController / EPartMap）

- 每 tick：`doControllerTick()` → `doStructureCheck()`（未成型→`disassemble()`；成型且未 assembled→`assemble()`；`onSyncTick()` 决定是否异步 tick）（S:`EPartController.java:24-37`）。
- `canCheckStructure()` 节流：成型后每 40 tick 或区域变化（MMWorldEventListener）复查（:85-100）。
- `assemble()` 前检查 `checkControllerShared()`：正上方/下方 2 格有同类控制器则拒绝（:39-51, 102-113）。
- 结构校验成功 → `updateComponents()`：遍历 `foundPattern.getTileBlocksArray()` 把每个 AbstractEPart 加入 `EPartMap`（按**类 + 全部超类**双索引，S:`EPartMap.java:19-24`），并 `onAddPart`（S:`EPartController.java:58-81`）。
- 控制器持有：`channel`（唯一 ME 通道）、`virtualCPU`（当前待命 vCPU）、`parallelism`、`totalBytes`（S:`ECalculatorController.java:65-74`）。

### 4.2 各部件职责与协作

| 部件 | 职责 | 协作 |
|---|---|---|
| `ECalculatorParallelProc` | 提供 parallelism（读方块属性，S:`ECalculatorParallelProc.java:14-16`） | 控制器 `recalculateParallelism()` 求和（S:`ECalculatorController.java:125-127`），再写进**每个已分配 vCPU 的 accelerator**（:130-133） |
| `ECalculatorCellDrive` | 1 槽晶阵；`getSuppliedBytes()` 按控制器等级过滤后返回晶阵字节（S:`ECalculatorCellDrive.java:45-70`） | 库存变化 → 控制器 `recalculateTotalBytes()` + `createVirtualCPU()`（:35-43）；`connectTransmitter/disconnectTransmitter` 维护与总线的连接状态（:72-104） |
| `ECalculatorTransmitterBus` | 组装时连接上/下 CellDrive（S:`ECalculatorTransmitterBus.java:20-29,31-73`），方向一致才连（:75-126）；`neighborChanged` 重连/断开（:128-136） | 注意：**连接状态只影响显示**（LINK 属性/灯光），`getSuppliedBytes` 不检查连接（推断：视觉/状态设计，容量核算不依赖总线连接） |
| `ECalculatorThreadCore` | 线程容器：`cpus` 列表 + `maxThreads/maxHyperThreads`（S:`ECalculatorThreadCore.java:16-32`）；`addCPU(cluster, hyperThread)` 先普通后超线程插槽（:38-54）；`getUsedStorage()` = Σ cluster.availableStorage（:103-108） | 拆方块/任务完成时 `onBlockDestroyed/onCPUDestroyed` 通知控制器（:83-101） |
| `ECalculatorController` | 汇总（§4.3） | 与 channel/thread cores/cell drives 全联动 |

### 4.3 控制器核算与 vCPU 分配算法（S:`ECalculatorController.java`）

- `recalculateParallelism()`（:125-134）：parallelism = Σ并行核心；并 `setAccelerators` 到每个线程核心的 cluster。
- `recalculateTotalBytes()`（:136-139）：totalBytes = Σ CellDrive 供给字节。
- `getAvailableBytes()`（:145-148）：totalBytes − Σ 线程核心 usedStorage（已分配给任务的内存）。
- `createVirtualCPU()`（:194-233）：
  1. `availableBytes < totalBytes*0.1` → 销毁现有 vCPU 并停止（**10% 红线**，:195-202）；
  2. 已有 vCPU → 仅刷新 availableStorage/accelerators（:204-209）；
  3. 无 vCPU 且任一线程核心可加 → `new CraftingCPUCluster(pos,pos)`（:224），设 owner=this、availableStorage=availableBytes、accelerators=parallelism（:225-228），`channel.postCPUClusterChangeEvent()`（:230-232）让网格刷新。
- `onVirtualCPUSubmitJob(usedBytes)`（:167-192）——**任务接入点**（由 §3.1 的 submitJob mixin 触发）：
  1. 第一轮：找能**普通线程**插槽的线程核心 `addCPU(vCPU,false)` → availableStorage=usedBytes（:168-178）；
  2. 第二轮：**超线程**插槽 `addCPU(vCPU,true)` → `usedExtraBytes = usedBytes*0.1`（超线程 +10% 内存），availableStorage=usedBytes+extra（:179-189）；
  3. 清 virtualCPU 引用并立刻 `createVirtualCPU()` 补位；全部失败 → 日志告警（:191）。
- `getClusterList()`（:235-248）：线程核心全部 CPU + 当前 vCPU（刷新 owner）——供通道暴露给网格（`getCPUs`，S:`ECalculatorMEChannel.java:80-90`）。
- `onClusterChanged()`（:250-254）：CPU 增删后 `channel.postCPUClusterChangeEvent()`。
- 完成回收：任务完成且库存空 → `destroy()`（§3.1）→ `onCPUDestroyed` → 控制器刷新 → 网格收到 MENetworkCraftingCpuChange 重新统计；vCPU 空闲位在下一轮 `updateComponents`（每 40 tick 结构复查）或晶阵变化时补建。

### 4.4 数据流（文字版）

```
玩家在 AE2 合成终端提交任务
  → CraftingGridCache.submitJob(job, ...)  （AE2 原版）
  → 遍历 craftingCPUClusters（含 MixinCraftingGridCache 注入的 vCPU）
  → 选中 vCPU（CraftingCPUCluster 实例，容量=availableBytes）
  → vCPU.submitJob(...) → mixin injectSubmitJob 触发
  → ECalculatorController.onVirtualCPUSubmitJob(job.getByteTotal())
  → 线程核心.addCPU(vCPU, false|true)（普通优先；超线程+10%字节）
  → 控制器补建新 vCPU（若可用字节≥10%总量）
  → 每 tick：CraftingGridCache.onUpdateTick → 各 cluster.updateCraftingLogic
      （mixin 记录耗时；TileCraftingTile.isActive 被重定向到 ME 通道代理）
  → executeCrafting 按 remainingOperations(=accelerator+1-usedOps) 批量推样板
  → 任务完成/取消 → cluster.destroy()（mixin 拦截）
  → onCPUDestroyed → 线程核心移除 + 控制器.onClusterChanged → 网格刷新
```

### 4.5 ITaskExecutor / MixinTaskExecutor

- `ITaskExecutor`（S:`common/util/ITaskExecutor.java`）是混入接口，`MixinTaskExecutor`（§3.10）让 MMCE 并发 TaskExecutor 支持"延迟 markDirty"；`EPartController.doControllerTick` 用 `ModularMachinery.EXECUTE_MANAGER.addTask(this::onAsyncTick, ...)` 把可选异步 tick 交给 MMCE 执行管理器（S:`EPartController.java:34-36`），ME 通道组装时 `addSyncTask(proxy::onReady)`（S:`ECalculatorMEChannel.java:161`）。
- 本子系统（ECalculator）实际 **onSyncTick 返回 false，不走异步路径**（S:`ECalculatorController.java:82-92`）——所有核算都在主 tick 同步完成；异步设施是 EFabricator 在用。

---

## 5. GUI 交互与数据同步

### 5.1 打开路径

- 控制器方块右键（服务端）：`BlockECalculatorController.onBlockActivated` → `player.openGui(MOD_ID, GuiType.ECALCULATOR_CONTROLLER.ordinal(), ...)`（S:`BlockECalculatorController.java:79-87`）。
- `CommonProxy.getServerGuiElement`：先校验 TE 类型，再 `ModIntegrationAE2.securityCheck(player, channel.getProxy())`（AE2 权限）后建 `ContainerECalculatorController`（S:`CommonProxy.java:122-128`）；客户端 GUI 由 `ClientProxy.getClientGuiElement` 建 `GuiECalculatorController`（S:`ClientProxy.java:66-86`）。GUI handler 在 preInit 注册（S:`CommonProxy.java:58`）。

### 5.2 数据推送（服务端→客户端）

- 控制器每 5 tick `updateGUIDataPacket()` 标记脏（S:`ECalculatorController.java:86-92,278-288`）。
- `ECalculatorEventHandler.onPlayerTick`（服务端，每 10 tick，S:`ECalculatorEventHandler.java:37-56`）：玩家开着该容器时 `NET_CHANNEL.sendTo(controller.getGuiDataPacket(), player)`。
- `PktECalculatorGUIData` 载荷 = `ECalculatorData` record（S:`ECalculatorData.java:22-23`）：`totalStorage / usedExtraStorage / accelerators / List<ThreadCoreData(type,threads,hyperThreads,maxThreads,maxHyperThreads)> / List<ECPUData(crafting输出, usedMemory, usedExtraMemory, parallelismPreSecond, cpuUsagePerSecond)> / cpuUsagePerSecond`；序列化手写 ByteBuf（:75-126）。
- `ECalculatorData.from(controller)`（:25-40）从 `channel.getProxy().getCrafting().getCpus()`（AE2 网格侧 ICraftingGrid）筛出归属本控制器的 ECPUCluster，读 `parallelismRecorder/timeRecorder` 均值（:42-73）。
- 客户端 `GuiECalculatorController.onDataUpdate` → `ECGUIDataUpdateEvent` → 三个组件各自重绘（S:`GuiECalculatorController.java:52-55`；`CPUStatusPanel.java:63-87`、`MonitorPanel.java:174-193,229-252`、`StorageBar.java:95-107`）。

### 5.3 其他交互

- 晶阵插入/取出：**潜行右击晶阵驱动器**（服务端事件 `PlayerInteractEvent.RightClickBlock`，S:`ECalculatorEventHandler.java:73-126`），带 AE2 BUILD 权限校验（:58-71）。
- 方块状态视觉：各部件 actualState 属性随组装/连接/运行状态变化（§1.6），线程核心 RUN 判定 `getThreads()>0`（S:`BlockECalculatorThreadCore.java:181-193`）。
- TOP 集成：`ECalculatorInfoProvider`（S:`ECalculatorInfoProvider.java:23-238`）——控制器存储/线程/并行/耗时进度条与文本；线程核心在线状态+任务列表+µs/t 与 /t 并行（:57-127）。

---

## 6. 外部依赖清单（含 1.7.10 替代性判断）

来源：`gradle/scripts/dependencies.gradle` + libs/ 目录 + 源码 import 核实。

| 依赖 | 版本（参考仓库） | 谁在用（源码证据） | 1.7.10 替代 |
|---|---|---|---|
| **Modular Machinery Community Edition (MMCE)** | curse.maven:modularmachinery-community-edition:6945422 | **核心**：多方块框架（`MachineRegistry`/`DynamicMachine`/`TileMultiblockMachineController`/`TileEntitySynchronized`/`ContainerBase`/`GuiContainerDynamic`+widget 系统/`EXECUTE_MANAGER`/`MachineComponentManager`/`MMWorldEventListener`/`Sides`/`TimeRecorder`/`MEPatternProvider`）；EPartController 基类直接继承其控制器（S:`TileCustomController.java:14`） | **GTNH 无 MM** → 用 GT5U `TTMultiblockBase` + StructureLib（E-Storage 移植已验证，见 docs/DESIGN.md） |
| **AE2 Extended Life (AE2EL, rv6)** | curse.maven:ae2-extended-life:6302098 | 合成 CPU mixin 目标（§3） | **AE2U rv3-beta-1000-GTNH**（1.7.10 fork，§10 已核对 API） |
| **AE2 Fluid Crafting Rework** | curse.maven:ae2-fluid-crafting-rework:5504001 | `CoreModHooks`/`FluidConvertingInventoryCrafting`/`FluidCraftingPatternDetails`（S:`MixinCraftingCPUClusterTwo.java:30-31`） | 仅 EF 批量合成需要；GTNH AE2U 已内置 AE2FC 流体合成（HANDOVER 记 ae2fc-1.5.95-gtnh） |
| **NAE2** | curse.maven:nae2:5380800 | `VirtualPatternDetails`/`PatternTransformWrapper`/`ICancellingCraftingMedium`（S:`MixinCraftingCPUClusterTwo.java:23-25`）；mixin 配置 `mixins.novaeng_ecoaeextension_nae2.json`（MixinJEICellCategory） | 仅 EF 需要；1.7.10 无 NAE2 → 裁剪 |
| **JEI/HEI** | curse.maven:had-enough-items:6930666 | GUI 虚拟物品槽 `SlotItemVirtualJEI`/`SlotItemVirtualJEISmall`（S:`MonitorPanel.java:284`） | 1.7.10 用 NEI/JEI(1.7.10) 对应能力，或 MVP 先做纯文本任务列表 |
| **The One Probe** | curse.maven:the-one-probe:2667280 | `ECalculatorInfoProvider` 等（S:`IntegrationTOP.java`） | 1.7.10 有 TOP；或复用现有 waila 集成 |
| **CraftTweaker** | CraftTweaker2-MC1120-Main:1.12-4.+ | **源码零 API 引用**；仅 `common/crafttweaker/util/NovaEngUtils.java` 包名带 crafttweaker（实为数字格式化工具，被 GUI/TOP/物品 tooltip 用） | 不需要（依赖声明可能是 MMCE 传递链遗留） |
| **MMCE-ComponentModelHider** | libs/MMCE-ComponentModelHider-1.1-dev.jar | `BlockModelHider.hideOrShowBlocks` 可选分支（S:`BlockModelHider.java:22-24`） | 1.7.10 无 → 隐藏内部方块用 TESR/渲染层方案（E-Storage 已有 ISBRH/TESR 经验） |
| **Multiblocked (MBD)** | 可选 | `BlockModelHider` 可选分支（S:`BlockModelHider.java:20-21`） | 无 → 同上 |
| geckolib-forge-1.12.2-3.0.31 | libs/ | **源码无 import**（gradle 声明 + libs 存放；可能为渲染/模型链备件） | 不需要 |
| resourceloader-1.5.3-main.jar | libs/ | **源码无 import** | 不需要 |
| lumenized-1.0.3-dev.jar | libs/ | **源码无 import** | 不需要 |
| CrazyAE-1.12.2-v0.6.0.2.jar | libs/ | **已注释**（dependencies.gradle:91） | 不需要 |
| random-complement / mekanism-energistics / mekanism-ce / configanytime / FTB 系列 / CCL | curse.maven | `RCIConfigurableObject`/`RCSettings`/`IntelligentBlocking`（S:`MixinCraftingCPUClusterTwo.java:26-28`，EF 阻塞模式）；其余运行时链 | 仅 EF 相关 → 裁剪 |

**结论**：ECalculator 本体（不含 EFabricator）真正强依赖只有 **MMCE（结构/容器/GUI 框架）与 AE2EL（合成 CPU mixin）**；两者在 1.7.10 分别由 **GT5U TTMultiblockBase+StructureLib** 与 **AE2U rv3** 替代。geckolib/resourceloader/lumenized/CrazyAE 为死依赖（声明未用），移植无需考虑。

---

## 7. 配方 / 材料 / 研究体系

- **参考仓库没有任何 ECalculator 配方/材料/研究代码**：无 recipe 包；`ECalculatorCell` 仅是物品（creative tab `CreativeTabNovaEng`，S:`ECalculatorCell.java:35`）；`RegistryItems` 只做物品注册与模型（S:`RegistryItems.java`）。1.12.2 原版的获取方式在服务端脚本/其他 mod 中（本仓库不含）——**这正是 t2 网络查证要补的信息**。
- `MachineCoolants`（S:`common/util/MachineCoolants.java:14`）：仅被 `EFabricatorController`（:179,509）与 EF GUI（`ControlPanel.java:68`）使用 → **不属于 ECalculator 子系统**。
- `MediumType`（S:`common/util/MediumType.java:3`）：NULL/MEPatternProvider/EF 三态，仅用于 §3.2 的 executeCrafting 覆写 → 服务对象是 EFabricator/样板供给器；ECalculator 的普通任务恒为 `MediumType.NULL`（走≈原版单次推进）→ **不属于 ECalculator 子系统，但随 executeCrafting 覆写共存于 CraftingCPUCluster 混入中**。
- 材料体系（ItemEcoStorageComponent/Housing 等）与 73 条配方均为**本项目 E-Storage MVP 已实现部分**，与参考仓库的 ECalculator 无对应物；ECalculator 移植时需为本项目自建获取途径（装配线/组装机配方，见 t4 方案）。

---

## 8. 建造结构规则

来源：`assets/ecoaeextension/default_machinery/Nova-extendable_calculator_subsystem_l{4,6,9}.json`（首次启动由 `ModDataHolder` 复制到 `config/modularmachinery/machinery/ECOAEExtension/`，S:`ModDataHolder.java:23-101`）。

### 8.1 布局（以 L4 为例，三档仅部件等级名不同，已验证 L6/L9 结构 JSON 完全同构）

- **控制器**位于 (0,0,0)（面朝外），固定主体 3×3×2（x∈{-1,0,1}, y∈{-1,0,1}, z∈{0,1}）：
  - ME 通道：`ecalculator_me_channel@0` 于 (1,0,1)（JSON parts:129-135）；
  - 流体输入/输出舱：MM 标准 `blockfluidinputhatch`/`blockmefluidinputbus` 于 (1,1,1)、`blockfluidoutputhatch`/`blockmefluidoutputbus` 于 (1,-1,1)（:136-153）——MM 机器定义强制要求的舱位（ECalculator 本身不消耗流体，为 MM 框架惯例）；
  - 其余 15 格 `ecalculator_casing`。
- **动态扩展段**（dynamic-pattern "workers"，faces=west，minSize=1，maxSize=12，offset-start=(-2,0,0)，offset=(-1,0,0)，JSON:6-24）——每段（相对段原点）：
  - (0,-1,0) `ecalculator_cell_drive@2`（下方晶阵驱动器）
  - (0,1,0) `ecalculator_cell_drive@2`（上方晶阵驱动器）
  - (0,0,0) `ecalculator_transmitter_bus@2`（中部传输总线）
  - (0,0,1) `ecalculator_thread_core_l4@0` **或** `ecalculator_thread_core_hyper_l4@0`（二选一元素）
  - (0,±1,1) `ecalculator_parallel_proc_l4@0`
  - parts-end（最后一段）：(0,0,0) `ecalculator_tail_l4@2`，其余 casing。
- 即：**整机 = 3×3×2 头（18 格，x∈{-1,0,1}×y∈{-1,0,1}×z∈{0,1}，含控制器、ME 通道、2 个 MM 流舱）+ 1~12 个扩展段**（"workers" 动态样板，每段 1 列 x=0, y∈{-1,0,1}, z∈{0,1} 共 6 格，沿 -x 逐列延伸；pattern 原点 offset-start=(-2,0,0)，每增 1 段偏移 (-1,0,0)）；整机最小 24 格（18+6）、最大 90 格（18+12×6）。控制器 tooltip 明示"最大长度 12"（lang: `extendable_calculate_subsystem.info.1`）。
- 方向：`@N` 后缀是 MM 朝向（meta 0=NORTH），控制器旋转时 MM 自动整体旋转匹配。

### 8.2 客户端隐藏

- `HIDE_POS_LIST`（控制器周边 18 格，S:`ECalculatorController.java:29-55`）+ `TAIL_HIDE_POS_LIST`（尾部 5 格，:57-63）在客户端按旋转换算后交给 MBD/ComponentModelHider 隐藏（:291-344, 360-373；S:`BlockModelHider.java:19-57`）——内部是空腔视觉（玻璃/镂空模型见 `models/block/ec_modular_synthetic_memory/*glass*`）。

### 8.3 约束

- 控制器等级 L4/L6/L9 各自独立机器定义（registryname `extendable_calculator_subsystem_l{4,6,9}`），通过 `MixinMachineRegistry` 绑定方块（§3.10）。
- 上下相邻同类型控制器互斥（`checkControllerShared`，§4.1）。
- 结构变化 → 40 tick 内复查 → `updateComponents` → 重新核算并补建 vCPU。

---

## 9. 注册与启动流程

1. `ECOAEExtension`（@Mod，S:`ECOAEExtension.java:19-84`）：preInit 注册 8 个网络包（:50-63）+ `CommonProxy.loadModData`（默认机器 JSON 复制，:65）；init 注册事件总线。
2. `CommonProxy`（S:`CommonProxy.java`）：构造时注册 `RegistryBlocks`/`RegistryItems`（:41-44）；preInit 注册 GUI handler 与三大 EventHandler（:57-63）；init 时 TOP 提供者 + AE2 `EStorageCellHandler` 插队（:65-73）；postInit 初始化 MachineCoolants（:75-77，EF）。
3. `RegistryBlocks.registerBlocks()`：经 `GenericRegistryPrimer`（先暂存、RegistryEvent 时按类型灌入 Forge Registry，S:`GenericRegistryPrimer.java:20-39`）注册方块 + ItemBlock（S:`RegistryBlocks.java:46-105`），`registerTileEntities()` 注册 7 个 ECalculator TE（:122-129）。
4. `RegistryItems` 注册物品与模型（含 `ModelBakery` 变体）。
5. 机器 JSON：`ModDataHolder` 首启复制（§8）；`MixinMachineRegistry` 在 MM 加载机器时绑定控制器方块。

---

## 10. GTNH 1.7.10 移植可行性要点（AE2U rv3 源码对照 — 供 t4 方案引用）

> 对照对象：`.research/ae2u-full/Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH/src/main/java/`（AE2U 1.7.10 分支；实际工程依赖 rv3-beta-1000-GTNH，t4 需按工程内 local-maven 版本复核）。

### 10.1 目标类与方法存在性（已逐项 grep 核实）

| 1.12.2 mixin 需求 | AE2U rv3 对应 | 结论 |
|---|---|---|
| `CraftingCPUCluster.updateCraftingLogic(IGrid,IEnergyGrid,CraftingGridCache)` | 存在（A:`CraftingCPUCluster.java:745`） | ✅ 可行 |
| `submitJob` 拦截（目标 `ICraftingJob.getOutput()`） | `submitJob(IGrid, ICraftingJob, BaseActionSource, ICraftingRequester)` 存在（:1092）；`ICraftingJob.getByteTotal()` 存在 | ✅ 可行（签名 IActionSource→BaseActionSource 需适配；getOutput 返回 `StackType` 泛型） |
| `cancel()` / `destroy()` / `markDirty()` / `getGrid()` / `isActive()` / `getCore()` / `getWorld()` | 全部存在（:705/312/541/618/1349/614/1725） | ✅ 可行 |
| 字段 `availableStorage / accelerator / remainingOperations / machineSrc / inventory / isComplete / isDestroyed / usedOps[3] / tasks(TreeMap) / myLastLink` | 全部存在（:168-174,144,147,155-158,162） | ✅ 可行（`usedOps` 为 int[3]） |
| `CraftingGridCache.updateCPUClusters()` + `craftingCPUClusters` Set + `addLink` + `onUpdateTick` | 存在（A:`CraftingGridCache.java:369,119,388,163） | ✅ 可行（1.12.2 是 @Inject RETURN，rv3 同样适用） |
| `CraftingCPUStatus`（合成状态行） | 存在（A:`container/implementations/CraftingCPUStatus.java`） | ✅ 可行（NBT 序列化需按 rv3 字段适配） |
| `GuiCraftingStatus`（client） | 存在（A:`client/gui/implementations/GuiCraftingStatus.java`） | ✅ 可行（渲染代码需 1.7.10 化） |
| `AENetworkProxy(IGridProxyable, String, ItemStack, boolean)` | 构造签名一致（A:`AENetworkProxy.java`） | ✅ 可行 |
| `TileCraftingTile.isActive()` | 存在 | ✅ 可行 |
| `GridFlags.REQUIRE_CHANNEL/DENSE_CAPACITY`、`ICraftingMedium.pushPattern/isBusy`、`CraftingLink.isCanceled`、`IGrid.postEvent`、`MENetworkCraftingCpuChange` | 全部存在 | ✅ 可行 |
| `ICraftingPatternDetails.getCondensedOutputs/Inputs/Inputs/Outputs/isCraftable/canSubstitute/isValidItemForSlot` | 存在（A:`api/networking/crafting/ICraftingPatternDetails.java:42-99`） | ✅ 可行（`getSubstituteInputs` grep 未命中，需进一步核实；仅影响可替换输入分支） |

### 10.2 必须适配的差异

1. **`executeCrafting` @Overwrite（MixinCraftingCPUClusterTwo）**：1.12.2 版依赖 `visitedMediums` 字段，rv3 无此字段，而是 `workableTasks`（A:148）+ `parallelismProvider`（A:228）+ `knownBusyMediums`（A:149）；rv3 的 `executeCrafting` 在 A:796。**结论：需按 rv3 语义重写（或 MVP 先不做批量并行推送，仅靠 `accelerator` 字段驱动并行——rv3 在 A:772 有 `remainingOperations = accelerator + 1 - usedOps`，此路径与 1.12.2 行为一致）**。
2. `MECraftingInventory.getItemList()` 在 rv3 不存在（有 `getAvailableItems`/`getExtractFailedList`）→ mixin 中"库存已空"判断需换 API（A:`crafting/MECraftingInventory.java`）。
3. 泛型体系：1.12.2 为 `IAEItemStack`/`IActionSource`；rv3 为 `IAEStack<?>`（物品/流体统一）/`BaseActionSource` → 所有 mixin 签名适配。
4. `usedOps` 数组：1.12.2 `usedOps[0]` 语义需在 rv3 中核对（rv3 用 usedOps[0..2] 分任务类型计数，A:772）。
5. 客户端渲染（MixinGuiCraftingStatus）与 GUI 框架：1.12.2 用 MMCE widget + AEBaseGui；1.7.10 用 MUI1（E-Storage 已验证）→ 整段重写。
6. `CraftingCPUStatus` NBT 字段（`ecLevel`）序列化方式按 rv3 的 writeToNBT/readFromNBT 位置适配。

### 10.3 结构/GUI/网络侧

- 结构：MM JSON + `TileMultiblockMachineController` → StructureLib 结构定义 + `TTMultiblockBase`（E-Storage 已有全套范式：docs/DESIGN.md §2.4-2.6）；"workers"动态扩展段可用 StructureLib 多结构/动态检查实现（E-Storage 的 drive 列扩展已验证 1~12 段）。
- GUI：`GuiContainerDynamic` widget 体系 → MUI1（`useMui2=false` + FakeSyncWidget 回写，E-Storage 已有范式）；`SlotItemVirtualJEI` → NEI/JEI 1.7.10 或先文本化。
- 网络：`PktECalculatorGUIData` 手写 ByteBuf → 1.7.10 的 `FMLEventChannel`/自建 `SimpleNetworkWrapper` 等价物（本项目已有 network 包先例）。
- 线程核心拆除保存：`CompressedStreamTools` 压缩 NBT 进掉落物 → 1.7.10 同类 API 存在，可直接照搬思路（含 `WRITE_CPU_NBT` ThreadLocal 的同步抑制技巧）。
- 事件：`ECalculatorEventHandler.onPlayerTick` 推送 GUI 数据 → 1.7.10 `PlayerTickEvent` 同构。

---

## 11. 关键源码文件索引（供 t4 引用）

| 文件（S: = src/main/java/github/kasuminova/ecoaeextension/...） | 角色 |
|---|---|
| S:`common/ecalculator/ECPUCluster.java` | vCPU 增强接口（mixin 目标接口） |
| S:`common/ecalculator/ECPUStatus.java` | GUI 状态行等级接口 |
| S:`common/tile/ecotech/EPartController.java`、`EPart.java`、`AbstractEPart.java`、`EPartMap.java`、`TileCustomController.java` | 多方块部件框架 |
| S:`common/tile/ecotech/ecalculator/ECalculatorController.java` | 控制器核算 + vCPU 调度 |
| S:`common/tile/ecotech/ecalculator/ECalculatorThreadCore.java`、`ECalculatorMEChannel.java`、`ECalculatorCellDrive.java`、`ECalculatorTransmitterBus.java`、`ECalculatorParallelProc.java`、`ECalculatorTail.java`、`ECalculatorPart.java` | 各部件 TE |
| S:`common/block/ecotech/ecalculator/BlockECalculator*.java` + `prop/` | 方块与状态属性 |
| S:`common/item/ecalculator/ECalculatorCell.java` + `ItemECalculator*.java`、`common/item/ItemBlockME.java` | 物品 |
| S:`mixin/ae2/MixinCraftingCPUCluster.java` | ECalculator 核心混入（宿主重定向 + submitJob 拦截） |
| S:`mixin/ae2/MixinCraftingCPUClusterTwo.java` | executeCrafting 覆写（EF 批量并行；EC 受影响） |
| S:`mixin/ae2/MixinCraftingCPUStatus.java`、`MixinCraftingGridCache.java`、`MixinGuiCraftingStatus.java` | 网格注册 + 状态/渲染 |
| S:`mixin/ae2/MixinGuiPatternTerm.java`、`MixinPacketInventoryAction.java`、`MixinTileChestFilter.java`、`MixinTileDriveFilter.java`、`Accessor*.java` | EF/EStorage 侧 |
| S:`mixin/mmce/MixinMachineRegistry.java`、`MixinMEPatternProvider.java`、`MixinTaskExecutor.java`、`MixinIngredientItemStackRenderer.java` | MMCE 侧 |
| S:`common/network/PktECalculatorGUIData.java`、`common/container/data/ECalculatorData.java`、`common/container/ContainerECalculatorController.java`、`common/handler/ECalculatorEventHandler.java` | GUI 数据通道 |
| S:`client/gui/GuiECalculatorController.java` + `client/gui/widget/ecalculator/*` | 客户端 UI |
| S:`common/integration/theoneprobe/ECalculatorInfoProvider.java` | TOP |
| S:`common/registry/RegistryBlocks.java`、`GenericRegistryPrimer.java`、`common/data/ModDataHolder.java`、`ECOAEExtension.java`、`CommonProxy.java` | 注册/启动 |
| 资源：`assets/ecoaeextension/default_machinery/Nova-extendable_calculator_subsystem_l*.json` | 结构定义 |
| 资源：`mixins.novaeng_ecoaeextension{,_ae2,_nae2}.json` | mixin 配置（ae2 配置按 AE2 加载门控） |

---

## 12. 风险与开放项（供 t4）

1. **rv3 `getSubstituteInputs` 未命中**：若 1.7.10 ICraftingPatternDetails 无替代输入 API，可替换输入分支的批量推送需降级（先按精确匹配）。
2. **AE2U rv3 `usedOps` 语义**：三个槽位分别计什么（推测按任务类别），影响并行统计注入点，需在 rv3-beta-1000 实包内复核。
3. **10% 红线与超线程 +10%**：纯业务规则，直接照搬；但参考实现用 `(long)(usedBytes * 0.1F)`（S:`ECalculatorController.java:182`）——float 尾数仅 24 位，字节量超过约 1.6e7（16M）即有舍入误差，GTNH 侧动辄 GB 级，移植时建议改为 long 整数运算（`usedBytes / 10`）。
4. **ME 通道 DENSE_CAPACITY**：参考实现要求密集通道；GTNH AE2U 支持 dense，但摆放/耗能需实测。
5. **多控制器互斥与 12 段上限**：TTMultiblockBase 结构检查对动态长度的支持需按 E-Storage 先例验证。
6. **线程核心 CPU 持久化**：参考实现把整份 CPU NBT 塞进掉落物（含任务进度），GTNH 侧 NBT 体积与跨版本兼容需实测（尤其 rv3 CPU NBT 字段与 rv6 不同）。
7. **性能**：每 tick 每个 vCPU 一次 updateCraftingLogic + TimeRecorder 统计；GTNH 单线程主循环下应控制 vCPU 总数（线程上限 L9=4+8=12/段×12 段=144 个并发任务上限，需在方案中给预算）。
8. **许可**：参考仓库 GPL-3.0，本项目已定 GPL-3.0（HANDOVER），移植/改写 mixin 需保留版权声明。

---

*调研完成时间：阶段 1（t1）。配套文档：`docs/ECALCULATOR_WEB_NOTES.md`（t2 网络资料）、`docs/ECALCULATOR_PORT_PLAN.md`（t4 移植方案）。*
