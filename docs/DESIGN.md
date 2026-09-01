# ECO AE Extension (GTNH) — E-Storage 移植设计文档

- 版本：v1.0（researcher 产出）
- 日期：与任务 t1 同步
- 目标 MC/Forge：1.7.10 / Forge 10.13.4.1614（stable_12）
- 参考仓库（1.12.2 原版行为来源）：`ref/NovaEngineering-ECOAEExtension-main`（MIT 许可）
- 目标依赖（已写入 `dependencies.gradle`）：
  - `com.github.GTNewHorizons:GT5-Unofficial:5.09.54.111:dev`
  - `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-998-GTNH:dev`
  - 新增建议：`com.github.GTNewHorizons:StructureLib:1.4.42:dev`（GT5U 传递依赖，显式声明更稳）

> **本设计的所有 GT5U/AE2U API 签名均直接取自 5.09.54.111 与 rv3-beta-998-GTNH 的源码**
> （本地已验证：`D:\DeepSeek\GTNH-ECO\.research\gt5-src`（GT5U sources）、
> `D:\DeepSeek\GTNH-ECO\.research\Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH`（AE2U sources）、
> `D:\DeepSeek\GTNH-ECO\.research\libs`（两个 dev jar））。
> 补充（2026-08 代理可用后）：已逐文件对比 AE2U **rv3-beta-1041-GTNH** 与 998 的存储 API——
> `IMEInventory / ICellHandler / StorageChannel / IStorageGrid / MEInventoryHandler` 逐字节一致，
> `ICellInventory` 仅新增 `default boolean isOverflow()`（纯增量）。**998→1041 对本设计无源码兼容风险**（见 §7）。

> ## 实现与文档同步说明（t23，最终 jar 8EA99D6F…）
>
> 本文件已与当前实现代码同步（`src/main/java/ecoaegtnh/**`，最终构建 jar `8EA99D6F…`）。与原稿相比的实质差异：
> 1. **结构方向（t15→t30 修正）**：§1.7/§2.5 已改为“**驱动列沿控制器正面朝向、朝玩家延伸**”的布局（t15），随后按用户确认修正为“**列往右扩、ME 总线在控制器背侧右角**”（t30）：驱动列沿控制器正面的**右手侧**延伸（垂直正面方向），ME 总线仍在控制器背面那一层、右侧角落（世界坐标不变）。原版体验已实证：原版 JSON 的动态列虽写在 machine local west 面，但控制器放置时正面朝向玩家、MMCE 旋转后列实际朝向玩家侧——GTNH 版按用户最终确认的“列在控制器右侧”复现。
> 2. **存储盘注册名（t21）**：unlocalizedName = `ecoaegtnh.estorage_cell_<type>_<size>m`（注册名同（去 `item.` 前缀））；**气体盘被源质盘替代**（1.7.10 无 Mekeng），源质盘门控用 `gregtech.api.enums.Mods.ThaumicEnergistics.isModLoaded()`（t14；modid 全小写 `thaumicenergistics`）。
> 3. **控制器纹理（t18）**：`registerIcons` + `IIconContainer` + `TextureFactory.of(IIconContainer)` 挂自定义贴图 `ecoaegtnh:storage_array_controller`（不再用 MACHINE_CASING_STABLE_TITANIUM，它只作服务端渲染回退）。
> 4. **GUI/交互（t16/t17/t22）**：控制器右击打开 EOH 风格存储统计面板；驱动盘位潜行右键放/取盘。
> 5. 配方（§4.2 草案）已被 `ecoaegtnh/recipe/Recipes.java` 的最终配方取代，见 §4.2 同步表。

---

## 0. 核心决策摘要

| # | 决策 | 理由 |
|---|------|------|
| D1 | 控制器用 **GT 机器槽位**（`MetaTileEntity` / **t32 起 `TTMultiblockBase`** 子类，ID>2048） | 白嫖 GT 的多方块生命周期、结构重检、拆解爆炸、Waila/NEI 兼容、`getStackForm(1)` 拿物品；TecTech 基类换取**真 TecTech GUI**（用户选定路线 1） |
| D2 | 外壳/驱动/电容/通风口/ME 总线用**自定义 Block + TileEntity**（`GameRegistry` 注册），不用 GT 机器槽位 | 与原版一致；这些部件不是机器，不需要 GT 机器行为；贴图已就位（`assets/ecoaegtnh/textures/blocks/`） |
| D3 | 结构检查用 **StructureLib**（`IStructureDefinition` + shape 字符串），驱动列按 1–12 单元生成 12 个 shape | GTNH 现代多块标准写法，自带结构错误提示、生存构建、透视工具提示 |
| D4 | AE2 接入点唯一：**ME 总线方块**（TE 实现 `ICellContainer + IGridProxyable + IAEPowerStorage`），驱动盘位只提供 cell handler | 与原版 `EStorageMEChannel` 一致；网格只认一个 ICellContainer |
| D5 | 存储盘实现 `IStorageCell`，cell inventory 子类化 AE2U 的 `appeng.me.storage.CellInventory`，handler 子类化 `CellInventoryHandler`，并注册自定义 `ICellHandler` | AE2U 998 的 `CellInventory.getCell()` 工厂不支持自定义字节公式，必须自己实现 |
| D6 | 配方统一走 **`GTValues.RA.stdBuilder()` + `RecipeMaps.assemblerRecipes`**（新配方 API） | 5.09.54 已移除 `GT_Recipe.GT_Recipe_Map`（见 §4.1） |
| D7 | 气体盘被**源质盘（ThaumicEnergistics）**替代（已实现，t10/t14/t21） | 1.7.10 无 Mekeng；TE4 提供源质 `IAEStackType`，依赖门控 `Mods.ThaumicEnergistics.isModLoaded()` |
| D8 | 机器注册时机：FML **init** 阶段实例化 MTE 构造器（GT 的 preload→postload 窗口内） | `CommonMetaTileEntity` 构造器要求 `sPreloadStarted && !sPostloadStarted` |

---

## 1. E-Storage 功能清单（1.12.2 行为归纳，来自参考仓库源码）

### 1.1 方块 / 物品总表

| 名称 | 注册名（1.12.2） | 类型 | 1.12.2 行为（移植基准） |
|------|------------------|------|------------------------|
| 控制器 L4/L6/L9 | `extendable_digital_storage_subsystem_l4/l6/l9` | 多方块控制器（`BlockController`） | 见 §1.2 |
| 外壳（casing） | `estorage_casing` | 普通方块（无 TE），硬度 20 / 抗爆 2000，镐 2 级 | 结构填充块 |
| 驱动盘位（drive） | `estorage_cell_drive` | 有 TE 方块，1 格 cell 槽 | 见 §1.4 |
| 电容 A/B/C | `storage_array_capacitance`（meta 0/1/2；t67 容量统一 2,000,000 AE/个） | 有 TE 方块 | 见 §1.3 |
| ME 总线 | `estorage_me_channel` | 有 TE 方块（硬度 5） | 见 §1.5 |
| 通风口 | `estorage_vent` | 普通方块（有朝向，无 TE） | 结构填充 + 贴图 |
| 尾部（tail） | `estorage_tail_l4/l6/l9` | 普通方块（FORMED 属性，仅渲染） | 1.12.2 有但移植可选（GTNH 版可省略，用外壳代替） |
| 物品盘 16M/64M/256M | `estorage_cell_item_{16,64,256}m` | 物品（IStorageCell） | 见 §1.6 |
| 流体盘 16M/64M/256M | `estorage_cell_fluid_{16,64,256}m` | 物品（IStorageCell） | 见 §1.6 |
| 气体盘 16M/64M/256M | `estorage_cell_gas_{16,64,256}m` | 物品（IStorageCell，Mekeng 可选） | 见 §1.6 |
| 输入/输出总线 | `estorage_*_bus` | 空壳类（参考仓库中未实现，`BlockEStorageBus` 为空类） | **不移植**（MVP 无此功能） |

方块属性：硬度 20、抗爆 2000、SoundType.METAL、镐 2 级、`isOpaqueCube=false`、CUTOUT 渲染；放驱动/电容/通风口时按玩家朝向设置水平朝向。

### 1.2 控制器（`EStorageController extends EPartController<EStoragePart>`）

- 结构成型后右击打开 GUI（`GuiEStorageController`）。
- 每 5 tick（`onSyncTick`）：
  - 对所有 drive 调用 `updateWriteState()`（写状态灯：40 tick 内有写入 → RUN，否则 IDLE；每 200 tick 或状态变化时发 `PktCellDriveStatusUpdate` 给附近玩家）；
  - 对电容检查 `shouldRecalculateCap()` → `recalculateCapacity()`（更新 EMPTY/LOW/MID/HIGH/FULL 状态并 markForUpdate）。
- 空闲耗电（t69 用户选定方案 B+C）`idlePowerUsage = tierBase + 0.5 × installedCellCount + Σ idleDrain(已装盘)`，实时写入 ME 通道的 `proxy.setIdlePowerUsage(...)`（见 §1.5）；tierBase 按控制器等级（L4=2.0/L6=4.0/L9=8.0），installedCellCount 只统计**实际装有 ECO 盘**的盘位数（空槽不计），成型（scanStructureVolume 末尾）与拆盘/放盘（TileEcoStorageDrive.onCellChanged → controller.recalculateEnergyUsage()）都重算。
- 能量聚合（AE 单位，double）：
  - `injectPower(amt, mode)`：按“当前电量最少优先”的堆顺序向电容注入（SIMULATE 走同序）；
  - `extractPower(amt, mode)`：按“当前电量最多优先”的堆顺序抽取；
  - `getEnergyStored()` / `getMaxEnergyStore()` 为所有电容之和。
- 装配：结构成型后把结构内的 `EStoragePart` TE 收集进 `parts` map（`updateComponents`），电容进两个堆、ME 通道记录为 `channel`；拆解时清空并 `proxy.invalidate()`。
- 防叠放：`checkControllerShared()` — 控制器正上方/正下方 2 格处存在同类控制器则拒绝成型。
- GUI 数据：`PktEStorageGUIData` 每 20 tick 推给打开 GUI 的玩家（能量条、每个 drive 的 cell 类型/等级/已用类型数/已用字节）。

### 1.3 电容（`TileEcoStorageCapacitance`，t67：统一 **2,000,000 AE/个**，A/B/C meta 不再改变容量；可跨阵列共享）

- 纯 double 能量池：`injectPower(amt, Actionable.SIMULATE/MODULATE)` 返回“无法注入的量”；`extractPower` 同理。
- **t67 共享**：电容**豁免** t55 防共用——`onAssembled` 永远放行并加入 owner 列表（多阵列可同时认领同一批电容，共享能量池）；`onDisassembled(controller)` 只摘除本控制器的认领（拆一个阵列不影响其它阵列），无 owner 时 assembled=false。驱动盘位/ME 总线的单 owner 防共用保持。
- 状态阈值：fillFactor ≥0.9 FULL、≥0.7 HIGH、≥0.5 MID、≥0.05 LOW、否则 EMPTY；方块状态按此驱动光照（`ordinal()*2`）与贴图。
- 拆方块时**能量随物品保存**：`energyStored` 写入物品 NBT，放置时读回；t67 起容量恒为 2M（读 NBT 时忽略旧 A/B/C 容量值并钳制 energyStored≤2M）。
- 注意：这是 **AE 能量（double）**，不是 GT EU；全部能量由 AE 网格经 ME 总线注入/抽取（t32 用户确认**纯 AE 供电**，已移除原计划的“可选 GT EU 充电路径”——外壳不再接受 GT 能量舱，见 §2.6）。

### 1.4 驱动盘位（`EStorageCellDrive`）

- 单槽 `AppEngCellInventory`（1.12.2；1.7.10 用 `AppEngInternalInventory`），槽过滤器只允许 `IStorageCell`（`CellInvFilter.allowInsert`）。
- 潜行右键：空槽且手持存储盘 → 放入（`EStorageEventHandler.onRightClickBlock`，事件优先级 LOW）；槽内有盘且空手 → 取出；若所在网格通电且有安全权限则拦截无权限玩家（`ISecurityGrid.hasPermission(player, SecurityPermissions.BUILD)`）。
- 拆方块时弹出槽内存储盘。
- 动态渲染状态（`getActualState`）：storage_type(empty/item/fluid/gas)、storage_level(empty/a/b/c)、storage_capacity(empty/type_max/full)、status(idle/run)；光照：空 2 / 有盘 6。
  - `getMaxTypes`：ITEM 315、FLUID 25、GAS 25（无 Mekeng 则为 0）。
  - `getMaxBytes`：按盘等级取 `EStorageCellItem/Fluid/Gas.LEVEL_*.getBytes()`。
  - `getCapacity(cellInvHandler)`：storedTypes==0→EMPTY；freeBytes<=0→FULL；storedTypes>=totalTypes→TYPE_MAX；否则 EMPTY。
- 等级限制（`isCellSupported`）：A 盘 → L4/L6/L9 均可用；B 盘 → L6/L9；C 盘 → 仅 L9。**移植必须保留**（GTNH 版用控制器 tier 字段判断）。t62 按用户偏好双保险：①玩家插入门控——`interactWithCell` 要求阵列已成型（`controller != null && controller.isStructureValid()`，否则聊天提示 `drive.cell.not_formed`），成型后 `isCellSupported` 不通过拒绝并聊天提示 `drive.cell.tier_not_supported`（附所需 L6/L9）；②成型门控兜底——`TileEcoStorageDrive.requiredTier/isSupportedByTier`（静态，不依赖 controller 字段）在 `MTEEcoStorageArray.scanStructureVolume` 成型校验中逐盘位复查，超等级盘使结构检查失败并显示 `ecoaegtnh.structure.error.cell_tier_exceeded`（需要 %s 控制器），堵住"未成型时放行"的绕过（含漏斗/管道等非玩家插入路径）。
- AE2 接入：`updateHandler()` 用自定义 `EStorageCellHandler.getCellInventory(stack, drive, channel)` 建立 `ECellDriveWatcher`（见 §4.4），并把 handler 放进 `inventoryHandlers` map（按 storage channel/type 键）。
- 槽变更（`onChangeInventory`）：重算 handler → `postChanges(gs, removed, added, source)`（对每个 storage type 取“旧盘全部内容取负 + 新盘全部内容”post 到 `IStorageGrid.postAlterationOfStoredItems`）→ `grid.postEvent(new MENetworkCellArrayUpdate())`。
- 拆解（`onDisassembled`）：把槽内盘的可用内容全部取负 post 到网格 + `MENetworkCellArrayUpdate`。

### 1.5 ME 总线（`EStorageMEChannel`）

- 网格节点：`AENetworkProxy(this, "channel", visualItemStack, true)`；`setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)`；初始 `setIdlePowerUsage(1.0)`，成型后由控制器 `recalculateEnergyUsage()` 覆盖。
- **空闲耗电（t69 方案 B+C，替代旧 `64 + Σ`）**：`idlePowerUsage = tierBase + 0.5 × installedCellCount + Σ idleDrain`——tierBase：L4=2.0 / L6=4.0 / L9=8.0（`MTEEcoStorageArray.tierBaseForPower()`）；installedCellCount = driveBays 中 `getCellStack() != null` 且为 ECO 盘的个数（空槽/非 ECO 盘不计，每盘 0.5）；Σ idleDrain 为已装 ECO 盘 `getIdleDrain()` 之和（16M=4.0）。例：L6、6 盘位装 4 个 16M → 4.0+4×0.5+4×4.0=**22 AE/t**；满 6 → 31 AE/t。重算时机：成型（scanStructureVolume 末尾）与拆盘/放盘（`TileEcoStorageDrive.onCellChanged` → `controller.recalculateEnergyUsage()`，forceCellArrayUpdate 旁）——拆盘后耗电立刻下降；未成型 meBus==null 安全跳过。
- `ICellContainer.getCellArray(channel)`：返回**所有 drive** 的 handler 列表（成型时遍历 `partController.getCellDrives()`）。
- `IAEPowerStorage`（AE 供能口）：
  - `injectAEPower(amt, mode)` → `partController.injectPower(...)`；电量从 <0.01 首次注入时 post `MENetworkPowerStorage(PROVIDE_POWER)`；
  - `extractAEPower(amt, mode, multiplier)` → `multiplier.divide(partController.extractPower(multiplier.multiply(amt), mode))`；从满电首次抽取时 post `MENetworkPowerStorage(REQUEST_POWER)`；
  - `getAEMaxPower()/getAECurrentPower()` 委托控制器；`isAEPublicPowerStorage()=true`；`getPowerFlow()=READ_WRITE`。
- 网格事件：`MENetworkPowerStatusChange` / `MENetworkChannelsChanged` → 若 active 状态翻转则 post `MENetworkCellArrayUpdate()`（`wasActive` 去抖）。
- 电缆类型：1.12.2 返回 `AECableType.DENSE_SMART`；**1.7.10 无 DENSE_SMART，用 `AECableType.DENSE`**（32 通道）。
- 生命周期：`readFromNBT/writeToNBT`（proxy）、`onChunkUnload`、`invalidate`、成型时 `proxy.onReady()` + 重算 idle + `MENetworkCellArrayUpdate`，拆解时 `proxy.invalidate()`。
- 视觉表示：1.12.2 为控制器物品；**最终实现（TileEcoStorageMEBus.getVisualItemStack）为 ME 总线物品**（`new ItemStack(EcoAEGTNHCore.Blocks.meBus, 1, 0)`，t13 修复了引用空实例的崩溃）。

### 1.6 存储盘（`EStorageCell` 系列）

- 抽象基类 `EStorageCell<T extends IAEStack<T>> implements IStorageCell<T>`：
  - **t68 起恢复旧版 ECO 容量设计**（用户"最后变回旧版eco的容量"）：`totalBytes = MB × 1000 × 1024`（16M=16,384,000；k 级 = value×1024：256k=262,144）；**t91 byteMultiplier 严格 ×2 递增序列 1/2/4/8/16/32/64/128/256**（256k→1、1024k→2、4096k→4、16M→8、64M→16、256M→32、1024M→64、4096M→128、16384M→256——消除旧 1024k 与 16M 同为 4、4096k 与 64M 同为 16 的重复），`getBytesPerType = byteMultiplier × 1024`（256k→1KB … 16384M→256KB）；`getTotalTypes`：ITEM **315** / FLUID 25 / ESSENTIA 60/80/100（t49 曾对齐 AE2U：totalBytes/128 per type + ITEM 63，用户觉不耐用）。t49 时期公式见 t3-implementation-notes.md t49 节。
  - **盘内字节公式**（`EcoStorageCellInventory`，t68 恢复自覆写，对照 1.12.2 参考 `EStorageCellInventory`）：`used = types×bytesPerType + (storedCount+unused) ÷ (typeWeight×byteMultiplier)`；`remaining = freeBytes×(typeWeight×byteMultiplier) + unused`；`unused = (typeWeight×byteMultiplier) − storedCount%(typeWeight×byteMultiplier)`；`typeWeight = stackType.getAmountPerByte()`（物品=8）。`getTotalItemTypes()` 覆写返回 item 声明值（315/25），绕过 AE2U 基类 63 钳制（基类只在 getTotalItemTypes 处限类型数；loadCellStacks 全量读 NBT，无数据丢失）。
  - `getIdleDrain() = MB/4`（t63 值保留：16M→4.0、64M→16.0、256M→64.0；t68 起与 1000 制 totalBytes 解耦，待 t69 耗电方案定夺）。
  - `getBytes(cellItem) = totalBytes`；`isStorageCell=true`、`storableInStorageCell=false`、`isBlackListed=false`、`isEditable=true`。
  - 升级槽 2（`CellUpgrades`）、配置槽（`CellConfig`）、`FuzzyMode` 存物品 NBT（`Platform.openNbtData`）。
  - 物品 Tooltip：AE 单元格信息（Used/Types，t33）+ 插入/取出提示 + B/C 级“需 L6/L9 控制器”提示。
- 通道（1.12.2）：`AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)` / `IFluidStorageChannel`；气体 `IGasStorageChannel`（Mekeng）。
  - **1.7.10 对应：`appeng.util.item.AEItemStackType.ITEM_STACK_TYPE` / `AEFluidStackType.FLUID_STACK_TYPE`**（详见 §4.3）。
- 自定义 cell inventory（**t49**：`EcoStorageCellInventory extends CellInventory` 只保留 `readStack`（t46 构造安全）/`getStackTypeTag`/`getStackCountTag`/`getStackType`（t8 构造安全）——**字节公式全部交给 AE2U 基类**（`getUsedBytes = types×bytesPerType + (storedCount+unused)/typeWeight`、`getRemainingItemCount = freeBytes×typeWeight + unused`、`getUnusedItemCount = typeWeight − storedCount%typeWeight`、`typeWeight = stackType.getAmountPerByte()`（物品=8）），与 GTNH-AE 逐字节一致；原 1.12.2 参考的“×乘数”自定义算法已删除；NBT 键 `it/ic`。
- 自定义 handler：`EcoStorageCellHandler`，`isCell = item instanceof ItemEcoStorageCell`；`getCellInventory` 建 `EcoStorageCellInventory` 并包 `EcoStorageCellInventoryHandler`。

> **GTNH 注册名（t21，与最终实现一致）**：所有存储盘 unlocalizedName = `ecoaegtnh.estorage_cell_<type>_<size>m`
> （`<type>` ∈ `item/fluid/essentia`，`<size>` ∈ `16/64/256`），注册名 = 去 `item.` 前缀后的同串
> （`GameRegistry.registerItem(item, name, "ecoaegtnh")`），贴图 `ecoaegtnh:estorage_cell_<type>_<size>m`，
> lang 键 `item.ecoaegtnh.estorage_cell_<type>_<size>m.name`。**1.7.10 无气体盘**：气体盘被**源质盘（essentia）**替代
> （见 §6.4 与 `docs/ESSENTIA_CELL_RESEARCH.md`），源质盘仅在 `Mods.ThaumicEnergistics.isModLoaded()` 时注册。

### 1.7 多方块结构（最终布局，t30 修正；原版 JSON 见 `default_machinery/Nova-extendable_digital_storage_subsystem_l*.json`）

> **方向修正（t30，用户确认）**：最终实现的驱动列**沿控制器正面朝向的右手侧（R）延伸**——“列往右扩”。
> ME 总线保持在控制器**背侧右角**（控制器背面那一层、右侧角落；t15 已在世界坐标 (−1,0,+1)（朝东时），t30 旋转后位置不变）。
> t15 的“列朝玩家（正面延伸）”布局已被替换；原版（1.12.2）动态列在世界西侧，用户明确要求 GTNH 版体验为“列在控制器右侧”。

StructureLib shape 约定（实现于 `MTEEcoStorageArray.buildDefinitions()`，为列长 1..12 生成 `size1`..`size12` 共 12 个 shape）：
- 轴：外层 String[] = **C 切片（前后）**、内层 = **B 行（上→下）**、字符 = **A 列（左→右，即列轴）**。
- **控制器锚点 `~` 位于 shape (A=n+2, B=1, C=0)**，结构检查/构建的基准偏移为 **(n+2, 1, 0)**（控制器右侧 n+2 格 = 列远端；A=0 是最远列）。

各 C 切片（每个切片 3 行 × (n+3) 字符，A=0..n+2；B=0 顶 / B=1 中 / B=2 底）：

| A 位置 | 内容 | C=0 片（控制器平面）B=0..2 | C=1 片（背面）B=0..2 | 说明 |
|--------|------|---------------------------|------------------------|------|
| A=0 | **列端封口** | `C / C / C` | `C / C / C` | 整面 2×3 外壳（6 格），封住驱动列远端 |
| A=1..n | **驱动列**（n 列） | `D / D / D` | `E / V / E` | 每列：C=0 平面 = 3 驱动盘位；C=1 平面 = 电容(顶/底)+通风口(中) |
| A=n+1 | **头部右侧切片** | `C / C / C` | `C / M / C` | **ME 总线位于 (A=n+1, B=1, C=1)**（控制器背侧右角），其余 5 格外壳 |
| A=n+2 | **控制器切片** | `C / ~ / C` | `C / C / C` | 控制器位于 A=n+2,B=1,C=0，其余 7 格外壳 |

- 每驱动列（A=1..n）：**3 驱动盘位**（C=0 平面 B=0..2，与控制器同平面）+ **2 电容**（C=1 平面 B=0/2）+ **1 通风口**（C=1 平面 B=1）——与原版逐单元布局一致（原版驱动在 z=0、E/V 在 z=1、ME 总线与 E/V 同侧；GTNH 版“驱动与控制器同平面、E/V 与 ME 总线同平面”等价语义）。
- **外壳 `C` 格 = 纯外壳方块**（t32 纯 AE 供电：不再接受 GT 能量舱，`CASING_OR_ENERGY_HATCH` 元素已删除）。
- 电容 `E` 格固定为电容方块（meta 任意）；驱动 `D`、通风口 `V`、ME 总线 `M` 格固定；`D` 格放置时带朝向（t32 autoplace 修复：创意/全息 autoplace 按控制器正面朝向设 meta 2-5，生存放置走物品路径按玩家朝向）。

### 1.8 部件绑定模型（1.12.2）

`EPartController.updateComponents()`：结构检查通过后，遍历 `foundPattern` 覆盖的所有相对坐标，把 `AbstractEPart` 的 TE 收集起来：`setController(this)` + `parts.addPart(part)` + `onAddPart(part)`；拆解/卸载时 `disassemble()` 反向清理。GTNH 移植版用同样的“成型后扫描结构体收集 TE”逻辑（§3.5）。

---

## 2. GTNH 1.7.10 移植架构

### 2.1 ⚠️ 关键更正：GT5U 5.09.54 的 API 已更名（任务书假设作废）

以下旧类名在 `5.09.54.111` **已不存在**（已在本地 sources 验证，全部 MISS）：

| 旧名（网上教程/任务书） | 5.09.54 实际类名 |
|------------------------|------------------|
| `gregtech.api.metatileentity.GT_MetaTileEntity` | `gregtech.api.metatileentity.MetaTileEntity`（new 式 MTE） |
| `gregtech.api.metatileentity.implementations.GT_MetaTileEntity_MultiBlockBase` | `gregtech.api.metatileentity.implementations.MTEMultiBlockBase` |
| `GT_MetaTileEntity_TieredMachineBlock` | 已移除（单块机器用 `MTEBasicMachineWithRecipe` + builder） |
| `gregtech.common.blocks.GT_Block_Machines` | `gregtech.common.blocks.BlockMachines`（机器方块实例 = `GregTechAPI.sBlockMachines`） |
| `gregtech.api.util.GT_Recipe` | `gregtech.api.util.GTRecipe`（新对象模型） |
| `GT_Recipe.GT_Recipe_Map.sAssemblerRecipes` | `gregtech.api.recipe.RecipeMaps.assemblerRecipes`（`RecipeMap`） |
| `GT_Multiblock_Tooltip_Builder` | `gregtech.api.util.MultiblockTooltipBuilder` |
| `GT_Values` | `gregtech.api.enums.GTValues`（`GTValues.RA` 仍在，类型变 `IGTRecipeAdder`） |

MTE 注册机制（5.09.54）：`MetaTileEntity(int aID, String aBasicName, String aRegionalName, int aInvSlotCount)` 构造器（或 `MTEMultiBlockBase(int aID, String aName, String aNameRegional)`）在 load 阶段被调用即注册进 `GregTechAPI.METATILEENTITIES[id]`；物品形态 `getStackForm(1)` → `new ItemStack(GregTechAPI.sBlockMachines, 1, id)`。ID 必须 >2048（4096–5095 为 GT 框架、5096–6099 为 GT 管道；GT5U 自身及附属用到了 32000+，见 `MetaTileEntityIDs` 枚举）。
> **实际实现（RegistryMTE.java）采用 32030/32031/32032（L4/L6/L9）**：该段在 `MetaTileEntityIDs` 中 32029（TecTech 末尾）之后、32050（GT_Framer）之前，验证空闲；ID 必须 < 32766（服务器 GT5U 5.09.54.20 的 `METATILEENTITIES` 数组大小）。

### 2.2 类映射（参考 1.12.2 → GTNH 1.7.10）

| 参考 1.12.2 | GTNH 1.7.10（本设计） |
|-------------|----------------------|
| `BlockEStorageController` + `EStorageController` + `ItemEStorageController` | `MTEEcoStorageArray`（A/B/C 三实例，一个类 + tier 字段；GT 机器槽位） |
| `BlockEStorageCasing` | `BlockEcoStorageCasing`（自定义 Block，无 TE） |
| `BlockEStorageCellDrive` + `EStorageCellDrive` | `BlockEcoStorageDrive` + `TileEcoStorageDrive`（自定义 Block+TE） |
| `BlockEStorageEnergyCell` + `EStorageEnergyCell` + `ItemBlockEStorageEnergyCell` | `BlockEcoStorageCapacitance`（A/B/C 三实例）+ `TileEcoStorageCapacitance`（自定义 Block+TE） |
| `BlockEStorageMEChannel` + `EStorageMEChannel` + `ItemBlockME` | `BlockEcoStorageMEBus` + `TileEcoStorageMEBus`（自定义 Block+TE） |
| `BlockEStorageVent` | `BlockEcoStorageVent`（自定义 Block，无 TE，有朝向） |
| `BlockEStorageTail` | 省略（用外壳代替） |
| `EStorageCellHandler` / `EStorageCellInventory` / `ECellDriveWatcher` | 同名移植到 `ecoaegtnh.ae2`（按 §4 适配 1.7.10 API） |
| `EPartController`/`EPart`/`AbstractEPart`/`EPartMap` | `TileEcoStoragePart`（基类：controller 引用 + onAssembled/onDisassembled/markForUpdate）+ 控制器内部部件收集逻辑 |
| `ContainerEStorageController`/`GuiEStorageController`/widgets/`PktEStorageGUIData`/`PktCellDriveStatusUpdate` | `gui/ContainerEcoStorageController`（Forge progress-bar 同步 6 个统计值，**无网络包**）+ `gui/GuiEcoStorageController`（EOH 风格，176×128）+ `gui/EcoAEGuiHandler`（t17/t22） |

### 2.3 方块注册方案（推荐组合）

- **控制器 = GT 机器槽位**：`MTEEcoStorageArray`（见 §2.6）。理由：免费获得结构重检/拆解/爆炸、WAILA、NEI 机器页、`getStackForm` 物品形态、机器贴图约定（`storage_arrays_controller_*` 贴图可直接用）。
- **部件 = 自定义 Block + TileEntity**（`GameRegistry.registerBlock` + `GameRegistry.registerTileEntity`，最终注册名见 `RegistryBlocks.java`）：
  - 外壳 `storage_array_casing`：普通 Block（`Material.iron`），无需 TE。
  - 通风口 `storage_array_vent`：普通 Block。
  - 驱动盘位 `storage_array_drive`：`BlockContainer`，TE 单槽盘库存。
  - 电容 `storage_array_capacitance`：`BlockContainer`，TE 存 double 能量；meta 0/1/2 保留（`damageDropped` 保留 meta，t67 起容量统一 2,000,000 AE 与 meta 无关），填充状态只存 TE 内（未做方块级渲染）；t67 电容可被多阵列共享认领（owner 列表）。
  - ME 总线 `storage_array_me_bus`：`BlockContainer`，TE 为 AE2 网格节点（§4.2）。
  - TE 注册名：`ecoaegtnh.drive` / `ecoaegtnh.capacitance` / `ecoaegtnh.me_bus`。
- 渲染适配（1.7.10，最终实现为**静态贴图**，未做动态状态渲染）：各部件用 `setBlockTextureName` 挂固定贴图——外壳 `storage_array_housing`、驱动盘位 `storage_array_drives`、电容 `storage_array_capacitance_a_empty`（meta 0/1/2 编码 A/B/C 等级，贴图同一张）、通风口 `storage_array_vents_a`、ME 总线 `storage_array_mebus`。电容填充状态/驱动盘位写状态等动态信息在最终实现中未做方块级渲染（保留在 GUI/数据层面），如后续需要可用 1.7.10 的 `getIcon(IBlockAccess,...)` 读 TE 方案扩展。

### 2.4 结构检查（StructureLib 写法）

依赖：`com.gtnewhorizon.structurelib`（`StructureLib`，GTNH 核心库；建议在 `dependencies.gradle` 显式加 `api('com.github.GTNewHorizons:StructureLib:1.4.42:dev')`）。

固定主体 shape + 驱动列：为列长 1–12 各生成一个 shape（`size1`..`size12`），程序化生成字符串数组；`checkMachine` 时从大到小尝试，命中即成型。字符约定（StructureLib）：`~` = 控制器锚点；`c/h/d/e/v/m` 等 = 元素；`-` = 必须空气；` `（空格）= 任意。

```java
// 伪代码：控制器 MTE 内（与最终实现一致；t32 起基类为 TecTech TTMultiblockBase，
// 结构检查入口是继承的 checkMachine_TT -> 本 checkMachine）
public static final IStructureDefinition<MTEEcoStorageArray> STRUCTURE_DEFINITION = buildDefinitions(); // 12 个 shape

@Override
public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
    for (int n = 12; n >= 1; n--) {
        // 控制器锚点 '~' 位于 shape (A=n+2,B=1,C=0)，检查基准偏移 (n+2, 1, 0)
        if (STRUCTURE_DEFINITION.check(this, "size" + n, world, getExtendedFacing(),
                x, y, z, n + 2, 1, 0, true)) {
            driveColumnLength = n;
            scanStructureVolume(...); // 收集 drive/电容/ME 总线 TE
            return;
        }
    }
    disassembleAll();
}
```

- **`getExtendedFacing()`**（t15 概念，t32/t35 基类接管）：GT 机器 tile 在客户端/服务端同步放置朝向前的默认值是 DOWN，垂直或 UNKNOWN 朝向会把 A/B/C 轴映射错（列会朝上渲染）。t32 迁移后 `IAlignment` 由 `MTEEnhancedMultiBlockBase` 完整管理（`mExtendedFacing` 字段默认 `ExtendedFacing.DEFAULT`=NORTH，放置时 `setFrontFacing → onFacingChange → toolSetDirection → setExtendedFacing` 同步、NBT `eRotation/eFlip` 持久化、`getCorrectedAlignment` 兜底非法朝向、扳手旋转 + 客户端对齐包同步）；**本类不覆写 getter/setter**（t35 修复：t32 曾覆写 `setExtendedFacing` 并调用 `setFrontFacing`，形成 `setFrontFacing → onFacingChange → toolSetDirection → setExtendedFacing → setFrontFacing` 无限递归，放置机器即 StackOverflowError 双端崩溃；TecTech 惯例如 MTEDataBank/MTEQuantumComputer 均不覆写）。只保留 `getAlignmentLimits` 限制（水平、不旋转、不翻转），基类用它校验/纠正每次朝向变更。
- 部件收集（`scanStructureVolume`）：按命中 shape 的 (A,B,C) 相对坐标用 `facing.getWorldOffset` 转世界坐标，收集 `TileEcoStorageDrive`（入 `driveBays`）、`TileEcoStorageCapacitance`（按 meta 设容量后入两个堆）、`TileEcoStorageMEBus`（唯一，多出报结构错误）。**t32 起不再收集能量舱**（纯 AE 供电）。随后对新增/消失部件分别 `onAssembled`/`onDisassembled` 并 `recalculateEnergyUsage()`。
- 维护舱：E-Storage 不需要维护机制。**t44 根治**：覆写 `getDefaultHasMaintenanceChecks() → false`——字段初始化器在构造器体之前调用它，`MTEMultiBlockBase` 构造器里的 `if (!shouldCheckMaintenance()) fixAllIssues()` 因此能在构造期执行（六个维护位 mWrench..mCrowbar 从放置瞬间全 true，`getRepairStatus()` 恒等于 `getIdealStatus()`，机器永不进入 NO_REPAIR"机器损坏"停机）。**t37 补充（显示层）**：另覆写 `supportsMaintenanceIssueHoverable() → shouldCheckMaintenance()`——基类该钩子硬编码 `getDefaultHasMaintenanceChecks()`，不覆写则 MUI2 终端右上角仍渲染维护图标；**t44 补充（显示残留）**：NO_REPAIR 停机原因会随 tile NBT 持久化（`shutDownReasonID`/`mWasShutdown`），`onPostTick` 在成型时清除 NO_REPAIR 原因与 wasShutdown 标志，防止旧存档继续显示"机器损坏"。（另有全局配置 `MachineStats.machines.disableMaintenanceChecks`。）
- **t32 基类迁移**：`extends TTMultiblockBase`（TecTech 已并入 gregtech jar，无新依赖）。差异要点：`getStructureDefinition()` 在 TT 是 final，改实现 `getStructure_EM()`；`checkProcessing()` 是 final，改实现 `checkProcessing_EM()`（本机返回 NONE，无配方）；`getDescription()` 在 `MTETooltipMultiBlockBase` 是 final，改实现 `createTooltip()`（按 MTE id 缓存一次，只放静态行，动态列长放 `getStructureDescription`/GUI）；`isFacingValid` 已 final；构造器仍调 super，`parametrization`（TT 参数系统）自动初始化但本机不实现 `IParametrized`（GUI 参数按钮灰显）。

### 2.5 摆放指引与 ASCII 结构图（最终布局 t30，控制器面朝 EAST 时）

> 一句话指引：**把控制器正面朝向自己放置**——驱动列沿控制器正面的**右手侧**延伸 1..12 格（玩家视角是左手边），
> ME 总线在控制器背侧右角，头部（控制器 + 总线 + 10 外壳）在控制器右侧 1 格、前后各 1 格。纯 AE 供电，免维护。

世界坐标（控制器位于 (0,0,0)，正面朝 EAST = +x；A=0 → z=+n+2、C=0 → x=0 控制器平面、C=1 → x=-1 背面；B=0 → y=+1）：

```
           +z（南，控制器右侧）
  ┌──────────────────────────────►
  │  z=+n+2        z=+n+1..+2        z=+1        z=0
  │  ┌────┐    ┌────┐  ...  ┌────┐   ┌────┐    ┌────┐
y=+1│  C  C     E  D   ...   E  D    C  C      C  C
y= 0│  C  C     V  D   ...   V  D    M  C      C  ~(ctrl)
y=-1│  C  C     E  D   ...   E  D    C  C      C  C
  │  A=0         A=1          A=n    A=n+1      A=n+2
  │  列端封口     驱动列(1..n)         头部右侧    控制器切片
  └─  （每个 A 切片：x=0 = D/D/D（3 驱动），x=-1 = E/V/E（电容顶/底+通风口中））
图例：C=外壳   D=驱动盘位   E=电容   V=通风口   M=ME 总线   ~=控制器
```

结构检查要点：
- 锚点 `~` = shape (A=n+2, B=1, C=0)，检查基准偏移 `(n+2, 1, 0)`（`checkMachine`/`construct`/`survivalConstruct` 一致）。
- 朝向：`getExtendedFacing()` 由 `MTEEnhancedMultiBlockBase` 管理——默认 `ExtendedFacing.DEFAULT`（NORTH），放置/加载/扳手经 `setFrontFacing → onFacingChange → toolSetDirection` 与 NBT 同步；`getAlignmentLimits` 限制水平、不旋转、不翻转（防全息投影/结构检查轴向错乱，见 §2.4；t35 起本类不再覆写 facing 方法）。
- 成型后 `driveColumnLength`（1..12）与全部部件位置可查询（GUI 显示 Columns/Drives 数）。

### 2.6 控制器 MTE 骨架要点

```java
// t32：基类 = TecTech TTMultiblockBase（真 TecTech GUI）；IAlignment/IConstructable 由
// MTEEnhancedMultiBlockBase 提供，显式只需 ISurvivalConstructable
public class MTEEcoStorageArray extends TTMultiblockBase implements ISurvivalConstructable {
    private final int tier; // 0=L4(A), 1=L6(B), 2=L9(C)

    public MTEEcoStorageArray(int aID, String aName, String aNameRegional, int tier) {
        super(aID, aName, aNameRegional);
        this.tier = tier;
        this.hasMaintenanceChecks = false; // 免维护（用户确认）
    }
    @Override public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) { return new MTEEcoStorageArray(mName, tier); }
    @Override public IStructureDefinition<MTEEcoStorageArray> getStructure_EM() { return STRUCTURE_DEFINITION; } // TT 的 getStructureDefinition 是 final
    @Override public void checkMachine(IGregTechTileEntity base, ItemStack aStack, List<StructureError> errors) { /* §2.4 */ }
    @Override protected CheckRecipeResult checkProcessing_EM() { return CheckRecipeResultRegistry.NONE; } // TT 的 checkProcessing 是 final
    @Override public void onPostTick(IGregTechTileEntity base, long aTick) { /* 每 5 tick 驱动 handler 失效重查；纯 AE，无 EU 路径 */ }
    @Override protected MultiblockTooltipBuilder createTooltip() { /* 静态行；getDescription 在基类是 final */ }
    @Override protected boolean useMui2() { return true; } // 真 TecTech MUI2 GUI
    @Override protected MTEMultiBlockBaseGui<?> getGui() { return new MTEEcoStorageArrayGui(this); } // extends TTMultiblockBaseGui
}
```

- **等级限制**：移植到 `TileEcoStorageDrive.isCellSupported(stack)`——按 `controller.getTier()` 判断：16M(A) → L4/L6/L9；64M(B) → L6/L9；256M(C) → 仅 L9（未成型时放行）。该限制对所有插入路径生效（玩家/漏斗/管道，见 `setInventorySlotContents`/`isItemValidForSlot`）。
- **能量聚合（纯 AE，t32 用户确认）**：inject/extract 堆逻辑放控制器 MTE（double 值，least-full-first / most-full-first），ME 总线 TE 委托给它（§3.2 IAEPowerStorage）。`recalculateEnergyUsage()` = 64 + Σ 盘 idleDrain → `meBus.getProxy().setIdlePowerUsage(...)`。**已删除**：`CASING_OR_ENERGY_HATCH` 元素、`addEnergyInputToMachineList` 扫描、`voltageTier` 螺丝刀档位、`EU_PER_TICK`/`drainEnergyInput` EU 注入、NBT 里的 voltageTier、tooltip 的 EU 行。
- **注册**：`RegistryMTE.register()` 在 **FML init** 里 `new MTEEcoStorageArray(32030, "estorage.array.l4", "ECO E-Storage Array (L4)", TIER_A)` 等 3 个实例（ID 32030/32031/32032，见 §2.1）；`getStackForm(1)` 进创造栏与配方。
- **纹理（t18，最终实现）**：`@SideOnly(CLIENT) registerIcons(IIconRegister)` 注册 `ecoaegtnh:storage_array_controller_front/side`，包成匿名 `IIconContainer`（`getIcon/getOverlayIcon/getTextureFile`），`getTexture(...)` 返回 `new ITexture[] { TextureFactory.of(controllerIconContainer) }`——**不再用 `MACHINE_CASING_STABLE_TITANIUM`**（仅作服务端渲染/图标未注册时的回退）。
- **GUI（t29/t32→t54 最终实现：与量子计算机完全相同，MUI1 机制）**：MTEQuantumComputer 不覆写 useMui2/getGui → TTMultiblockBase 默认 **MUI1**（`CommonMetaTileEntity.openGui` → `GTUIInfos.openGTTileEntityUI` → `addUIWidgets`/`drawTexts`，窗口 198×192）。ECO 已**删除** MUI2 定制（`useMui2()/getGuiTheme()/getGui()` 覆写与 `MTEEcoStorageArrayGui` 类），改为覆写 MUI1 `addUIWidgets`（screen_blue 背景 + Scrollable 文字屏 + 电源直通/安全清空/电源开关三按钮 + 控制器槽+散热片 + 底部参数条背景 + **t65 三个 IO LED 悬停格**）与 `drawTexts`（super 基座状态行 + Structure/Drives/Columns/Energy 五行 + `█░` 能量条；参数 LED/不确定度监视器因非 IParametrized 省略）。IO 状态（t58→t65）：t58 把 ME 总线/盘位/能量三 LED 文本行从文字屏移到底部参数条，t65 按用户要求改**悬停 tooltip**——参数条上三个 6×4 LED 方格（绿/红/灰状态色），悬停显示详情（ME 总线连接状态+结构状态、盘位数/列数、能量当前/上限/百分比），条上无常驻文本（机制照抄 `TTMultiblockBase.addParameterLED` 的 dynamicTooltip + FakeSyncWidget.setOnClientUpdate→notifyTooltipChange）。MUI1 不用 GTGuiTheme（主题观感来自 TecTechUITextures 直绘 screen_blue）。
- **autoplace 朝向（t32 修复）**：`D` 元素改为自定义 `DriveElement`——检查接受任意朝向 meta，放置时按控制器正面朝向写 meta（2N/3S/4W/5E），生存放置走物品路径由 `onBlockPlacedBy` 按玩家朝向（不再恒为 meta 0/朝北）。
- **构建/投影长度（t32 补充修复，用户实测"只有最长 12 列成型"）**：`construct`/`survivalConstruct` 用 **GTNH 结构通道**（`gregtech.common.misc.GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stack, 1, 12)`，默认 = 手持控制器物品 stackSize）决定列长——手持 N 个控制器 → 投影/建造 N 列（同 MTEAssemblyLine/MTEIndustrialCokeOven 惯例）；已成型时用 `driveColumnLength`。**结构检查**（`checkMachine` 的 size12..size1 下降循环）本身与列长无关、逐格验证全过（`docs/verify/StructureAllVerify.java`：每种列长只命中对应 size）。玩家缩短已成型结构时须在新远端重建列端封口。

---

## 3. AE2U（rv3-beta-998-GTNH）集成设计

> 1.7.10 AE2U 在 998 已全面切换到 **`IAEStackType`** 体系，但保留了 `StorageChannel` 枚举（deprecated）作为兼容层。**移植代码以 `IAEStackType` + `AEStackTypeRegistry` 为准**（与 998 内部 `GridStorageCache`/`TileChest` 一致）；`StorageChannel` 只用于 `IStorageGrid.postAlterationOfStoredItems` 的重载兼容。

### 3.1 双轨 API 对照（998 实测签名）

```java
// 通道/类型
public enum StorageChannel { ITEMS(IAEItemStack.class), FLUIDS(IAEFluidStack.class); public final Class<? extends IAEStack> type; ... }
public interface IAEStackType<T extends IAEStack> { String getId(); T loadStackFromNBT(NBTTagCompound); IItemList<T> createList(); int getAmountPerUnit(); ... }
public class AEStackTypeRegistry { public static void register(IAEStackType<?> t); public static Collection<IAEStackType<?>> getAllTypes(); public static IAEStackType<?> getType(String id); }
// 静态实例
appeng.util.item.AEItemStackType.ITEM_STACK_TYPE
appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE

// 核心存储接口（998）
public interface IMEInventory<StackType extends IAEStack> {
    StackType injectItems(StackType input, Actionable type, BaseActionSource src);
    StackType extractItems(StackType request, Actionable mode, BaseActionSource src);
    IItemList<StackType> getAvailableItems(IItemList<StackType> out, int iteration);
    IAEStackType<?> getStackType();                       // @Nonnull，新
    StorageChannel getChannel();                          // @Deprecated，兼容
}
public interface ICellInventory<StackType extends IAEStack<StackType>> extends IMEInventory<StackType> {
    double getIdleDrain(); long getTotalBytes(); long getFreeBytes(); long getUsedBytes();
    long getTotalItemTypes(); long getStoredItemCount(); long getStoredItemTypes();
    long getRemainingItemTypes(); long getRemainingItemCount(); int getUnusedItemCount(); ...
}
public interface ICellInventoryHandler<StackType> extends IMEInventoryHandler<StackType> { ICellInventory<StackType> getCellInv(); }
public interface ICellContainer extends IActionHost, ICellProvider, ISaveProvider { default void blinkCell(int slot) {} }
public interface ICellProvider { default List<IMEInventoryHandler> getCellArray(IAEStackType<?> type); /* + deprecated getCellArray(StorageChannel) */ }
```

### 3.2 网格接入（ME 总线 TE，对应 1.12.2 `EStorageMEChannel`）

```java
public class TileEcoStorageMEBus extends TileEntity implements IGridProxyable, IActionHost, IAEPowerStorage, ICellContainer {
    protected final AENetworkProxy proxy = new AENetworkProxy(this, "channel", getVisualItemStack(), true);
    protected final BaseActionSource source = new MachineSource(this);   // 1.7.10: MachineSource extends BaseActionSource

    public TileEcoStorageMEBus() {
        proxy.setIdlePowerUsage(1.0D);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY);
    }

    // ICellContainer / ICellProvider
    @Override public List<IMEInventoryHandler> getCellArray(IAEStackType<?> type) {
        // 成型时：遍历控制器收集的 drive，返回 drive.getHandler(type)（非空者）
    }

    // IGridProxyable
    @Override public AENetworkProxy getProxy() { return proxy; }
    @Override public DimensionalCoord getLocation() { return new DimensionalCoord(this); }
    @Override public void gridChanged() {}
    // IGridHost
    @Override public IGridNode getGridNode(ForgeDirection dir) { return proxy.getNode(); }
    @Override public AECableType getCableConnectionType(ForgeDirection dir) { return AECableType.DENSE; } // 1.12.2 的 DENSE_SMART 不存在
    @Override public void securityBreak() { worldObj.func_147480_a(xCoord, yCoord, zCoord, true); } // destroyBlock

    // IAEPowerStorage（委托控制器；见 §1.5 事件细节）
    @Override public double injectAEPower(double amt, Actionable mode) { return controller == null ? amt : controller.injectPower(amt, mode); }
    @Override public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) { ... }
    @Override public double getAEMaxPower() { return controller == null ? 0 : controller.getMaxEnergyStore(); }
    @Override public double getAECurrentPower() { return controller == null ? 0 : controller.getEnergyStored(); }
    @Override public boolean isAEPublicPowerStorage() { return true; }
    @Override public AccessRestriction getPowerFlow() { return AccessRestriction.READ_WRITE; }

    // 生命周期：validate/invalidate/onChunkUnload 里 proxy.validate()/invalidate()/onChunkUnload()；
    // 成型装配时 proxy.onReady() + recalculateEnergyUsage()；拆解时 proxy.invalidate()
    // NBT: readFromNBT/writeToNBT 里 proxy.readFromNBT(tag)/writeToNBT(tag)
    // 网格事件: @MENetworkEventSubscribe stateChange(MENetworkPowerStatusChange/MENetworkChannelsChanged) -> 活跃翻转时 post MENetworkCellArrayUpdate
}
```

要点：
- 可参考 AE2U 源码 `appeng.tile.grid.AENetworkPowerTile`（`extends AEBasePoweredTile implements IActionHost, IGridProxyable`）的做法，但**不要**直接继承它（其内部电池走 `MekJoules`，与“委托到控制器电容”冲突）；照抄它的 proxy 生命周期四件套即可（`validate/invalidate/onChunkUnload/onReady`）。
- `ItemBlock`（对应 1.12.2 `ItemBlockME`）：放置后 `proxy.setOwner(player)`，保证安全权限归属。
- 激活判定：`proxy.isActive()`；网格存取 `proxy.getStorage()` / `proxy.getGrid()`（throws `appeng.me.GridAccessException`）。

### 3.3 存储盘（cell）实现（1.7.10 适配）

```java
// 物品（Item 类）
public abstract class ItemEcoStorageCell<T extends IAEStack<T>> extends Item implements IStorageCell {
    // IStorageCell（998）：getBytes / getBytesPerType / getTotalTypes / isBlackListed / storableInStorageCell / isStorageCell
    //                       / isEditable / getUpgradesInventory / getConfigInventory / getFuzzyMode / setFuzzyMode / getStackType
    @Override public IAEStackType<?> getStackType() { return stackType; } // ITEM_STACK_TYPE / FLUID_STACK_TYPE（气体可选）
}
public class ItemEcoStorageCellItem extends ItemEcoStorageCell<IAEItemStack> { /* totalTypes=315, bytesPerType=mult*1024, stackType=ITEM_STACK_TYPE */ }
public class ItemEcoStorageCellFluid extends ItemEcoStorageCell<IAEFluidStack> { /* totalTypes=25,  ..., FLUID_STACK_TYPE */ }
// 气体（可选二期）：gate on Mekeng 存在；1.7.10 气体 stack type 由 MekanismEnergistics 注册，FQCN 需按实际 dev jar 确认
// 升级/配置库存：appeng.items.contents.CellUpgrades(is, 2) / CellConfig(is)（流体用 FluidCellConfig）；FuzzyMode 存 Platform.openNbtData(is)
```

```java
// cell inventory：子类化 AE2U 基类（998 无 AbstractCellInventory，基类即 CellInventory）
public class EcoStorageCellInventory<StackType extends IAEStack<StackType>> extends CellInventory<StackType> {
    public EcoStorageCellInventory(ItemStack o, ISaveProvider container) throws AppEngException { super(o, container); }
    // 覆写字节/类型算法：getUsedBytes / getRemainingItemCount / getUnusedItemCount（照抄 1.12.2 EStorageCellInventory 的乘数公式）
    // inject/extract 复用父类逻辑（父类已按 getBytesPerType/getTotalTypes 抽象，注意验证父类默认算法与参考版一致，必要时覆写）
}

// handler：CellInventoryHandler 为 abstract + protected 构造器，必须子类化
public class EcoStorageCellInventoryHandler<StackType extends IAEStack<StackType>> extends CellInventoryHandler<StackType> {
    public EcoStorageCellInventoryHandler(IMEInventory<StackType> c, IAEStackType<StackType> type) { super(c, type); }
}

// ICellHandler 注册（postInit 阶段，t61 附自检日志；init 太早 AE2U API 尚未就绪）：
//   AEApi.instance().registries().cell().addCellHandler(EcoStorageCellHandler.INSTANCE);
//   // t61: log isCellHandled(item16M/fluid16M/essentia16M) + getHandler(item16M)==INSTANCE 自检
// openChestGui = Platform.openGUI(player, (TileEntity) chest, chest.getUp(), GuiBridge.GUI_ME)（ME 箱子支持，同 BasicCellHandler）
// t66：ECO 盘**仅限 ECO 盘位**——ME 驱动器/箱子槽位拒绝（MixinSlotRestrictedInput.isItemValid[func_75214_a] 覆盖 GUI、
//   MixinTileDrive.func_94041_b[remap=false] 覆盖漏斗/管道；isCellHandled 兜底无条件、IStorageCell 无拒绝标志、
//   isStorageCell 不能为 false（CellInventory 构造器强制），故选槽位层 mixin）；盘位路径不经过 AE2U 槽位，零影响。
public class EcoStorageCellHandler implements ICellHandler {   // 参考 appeng.core.features.registries.entries.BasicCellHandler
    @Override public boolean isCell(ItemStack is) { return is != null && is.getItem() instanceof ItemEcoStorageCell; }
    @Override public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, IAEStackType<?> type) {
        if (!isCell(is) || ((ItemEcoStorageCell<?>) is.getItem()).getStackType() != type) return null;
        try { return new EcoStorageCellInventoryHandler<>(new EcoStorageCellInventory<>(is, host), type); }
        catch (AppEngException e) { return null; }
    }
    // 其余 ICellHandler 方法：getStatusForCell / cellIdleDrain 委托 ICellInventoryHandler.getCellInv()；getTopTexture_* 可返回 null
}
```

注意：`CellInventoryHandler` 构造器会扫描升级槽/配置槽（`getUpgradesInventory/getConfigAEInventory/getFuzzyMode/getOreFilter`），因此 `ItemEcoStorageCell` 必须正确实现 `IStorageCell` 的这些方法（§1.6 已列）。

> **源质盘分支（t10/t14，最终实现）**：`EcoStorageCellHandler` 增加 essentia 分支——`type == AEEssentiaStackType.ESSENTIA_STACK_TYPE` 时返回
> `new EcoStorageCellInventoryEssentiaHandler(new EcoStorageCellInventoryEssentia(is, host))`（两者分别继承 `CellInventory<AEEssentiaStack>` /
> `CellInventoryHandler<AEEssentiaStack>`，`getCellType()=TYPE.ESSENTIA`）；物品 `ItemEcoStorageCellEssentia` 的 `getBytesPerType()=0`
> （源质按 `getAmountPerByte()=2` 计字节，`CellInventory.typeWeight=2`）。完整设计见 `docs/ESSENTIA_CELL_RESEARCH.md`。

### 3.4 驱动盘位（1.7.10 适配）

- 库存：`TileEcoStorageDrive implements IInventory`（1 槽；`getStackInSlot(0)`/`decrStackSize`/`setInventorySlotContents`，NBT 键 `cell`）；槽过滤在 `isItemValidForSlot` + `setInventorySlotContents` 内（`EcoStorageCellHandler.isCell` + 等级门控 `isCellSupported`）。handler 缓存按 stack type 键控：ITEM/FLUID 专用缓存 + “其他类型”（如 TE4 源质）共用缓存，换盘时 `invalidateHandlers()` 失效。
- 回调/变更上报：驱动盘位没有走 AE2U 的 `IAEAppEngInventory` 回调，而是在换盘路径 `onCellChanged()` 里直接 `invalidateHandlers` + `markBlockForUpdate` + `meBus.forceCellArrayUpdate()`（让 `GridStorageCache` 重查 cell array）；写入/抽取跟踪（`onWriting`/`isWriting`，40 tick 窗口）用于盘位写状态灯。
- watcher（写状态 + 变更上报）：`EcoCellDriveWatcher<T extends IAEStack<T>> extends MEInventoryHandler<T>`，构造器 `(IMEInventory<T> i, IAEStackType<T> type)`；`injectItems/extractItems` 里 MODULATE 成功后经控制器桥接 `postAlteration(type, changes)` → `meBus.proxy.getStorage().postAlterationOfStoredItems(...)` + `drive.onWriting()`。
- 交互（t16，最终实现）：**潜行右键**驱动盘位（`BlockEcoStorageDrive.onBlockActivated`）→ `TileEcoStorageDrive.interactWithCell(player)`：空盘 + 手持存储盘（且等级允许）→ 放入 1 个；有盘 + 空手 → 取出。原版的 `ISecurityGrid` 权限检查未移植（本版无 AE 安全终端联动）。

### 3.5 网格侧行为确认（998 源码验证）

- `appeng.me.cache.GridStorageCache implements IStorageGrid`：`cellUpdate(MENetworkCellArrayUpdate)` 事件会遍历**所有** `ICellProvider`（含 `ICellContainer` 机器），对每个注册 type 调 `getCellArray(type)`，并按 `IActionHost.getActionableNode().isActive()` 决定加入/移出；所以 ME 总线只要实现 `ICellContainer` + 正确 post `MENetworkCellArrayUpdate`，网格会自动发现/移除 drive handler——**无需手动 registerCellProvider**。
- 参考实现：`appeng.tile.storage.TileChest`（`getHandler(IAEStackType<?>)` 用 `AEApi.instance().registries().cell().getHandler(is)` + `cellHandler.getCellInventory(is, this, type)` 遍历 `AEStackTypeRegistry.getAllTypes()`，包 `MEInventoryHandler` 设 priority，累加 `cellIdleDrain` 进 `proxy.setIdlePowerUsage`）。

---

## 4. GT 配方体系设计

### 4.1 选择

5.09.54 的**唯一**配方入口是**新 RecipeMap API**（`GT_Recipe.GT_Recipe_Map` 已移除）：

```java
// 入口
gregtech.api.enums.GTValues.RA              // public static IGTRecipeAdder RA
GTValues.RA.stdBuilder()                    // -> gregtech.api.util.GTRecipeBuilder
RecipeMaps.assemblerRecipes                 // gregtech.api.recipe.RecipeMaps.assemblerRecipes (RecipeMap<AssemblerBackend>)

// GTRecipeBuilder 关键方法（998/5.09.54 实测）：
//   .itemInputs(ItemStack...) / .itemInputs(Object...)（矿词）/ .itemInputsUnified(...)
//   .itemOutputs(ItemStack...) / .fluidInputs(FluidStack...) / .fluidOutputs(FluidStack...)
//   .circuit(int n)   —— 内置集成电路
//   .eut(int)（也可用 gregtech.api.enums.TierEU.RECIPE_LV 等常量）
//   .duration(int)（用 GTRecipeBuilder.SECONDS / TICKS 常量，如 5*SECONDS）
//   .addTo(RecipeMap) / .build()
```

注册时机：FML init/postInit 均可（GT 官方在 postload 批量加）。推荐集中放 `ecoaegtnh/recipe/Recipes.java`，init 里调用。

### 4.2 配方（同步说明：以下草案已被 `ecoaegtnh/recipe/Recipes.java` 的最终实现取代）

**最终配方总表（Recipes.java，t7 null 安全 + t21 源质盘 + t97 分档 + t98b 分机 + t100 中间材料）**：

t100 起存储盘体系重构为**中间材料**：27 个 ME 存储组件（容量档，按类型）+ 9 个存储外壳（控制器档，按类型），成品盘 = **外壳 + 组件**。机器分机（t98b）：**L4=EV 组用复杂组装机**（装配线 LuV 才解锁，EV 无装配线）；**L6=ZPM / L9=UHV 组用装配线**。**研究前置改为"低一档同类产物"**（GTNH 惯例：做 ZPM 马达扫 IV 马达）：256M 盘←64M 盘、1024M 盘←256M 盘、16M 组件←4096k 组件、L6 控制器←L4 控制器、L9 控制器←L6 控制器、电容 B←A、电容 C←B。材料（t97）：EV=Titanium、ZPM=Iridium、UHV=Neutronium；流体=组档熔融金属+焊锡合金+润滑剂；scanning=1min@组档；eut=组档。

| 配方 | 机器 | 主要输入 | 流体 | 输出 | 电路 | EU/t | 时长 |
|------|------|---------|------|------|------|------|------|
| R1 外壳 ×2 | 组装机 | Titanium Plate×4 + Titanium Frame + EV 马达/泵/传感器 | 焊锡+润滑剂 | 外壳 ×2 | 1 | EV | 30s |
| R2 驱动盘位 | 组装机 | 外壳×2 + Titanium Plate×2 + EV 马达/传送带/活塞 + Data 电路 | 焊锡+润滑剂 | 驱动盘位 ×1 | 3 | EV | 30s |
| R3 电容 A / B / C | 组装机/装配线/装配线 | A: 外壳×2+Titanium+Redstone+EV 件；B: 外壳×2+Iridium+Naquadah+ZPM 件（研究=A）；C: 外壳×2+Neutronium+NaquadahAlloy+UV 件（研究=B） | 焊锡 / Iridium 熔融 / Neutronium 熔融 + 焊锡 + 润滑剂 | 电容 meta A/B/C | 5/6/7 | EV/ZPM/UHV | 30/40/50s |
| R4 ME 总线 | 组装机 | 外壳×2 + CertusQuartz×4 + Titanium + EV 传感器/发射器 + Data 电路 | 焊锡+润滑剂 | ME 总线 ×1 | 4 | EV | 30s |
| 通风口 ×2 | 组装机 | 外壳×1 + Titanium×2 + EV 马达 | 焊锡+润滑剂 | 通风口 ×2 | 2 | EV | 20s |
| R5a 存储组件 ×27 | 组装机(k)/装配线(M/big) | k: 钛+石英+红石+Data 电路（256k 起链式：上一档组件）；M: 上一档组件+铱+锘（研究=上一档组件）；big: 上一档组件+中子素+硅岩合金 | 档位熔融金属+焊锡+润滑剂 | 组件 9 档×3 类型 | 31..39/41..49/51..59 | EV×3｜ZPM×3｜UHV×3 | 30..150s |
| R5b 存储外壳 ×9 | 组装机(L4)/装配线(L6/L9) | L4: 钛板×2+石英（源质+瓶）便宜壳；L6: 铱板×1+石英（研究=L4 外壳）；L9: 中子素板×1+石英（研究=L6 外壳）——**外壳便宜、成本大头在组件**（t100b 经济学） | 焊锡 / Iridium 熔融 / Neutronium 熔融 + 焊锡 + 润滑剂 | 外壳 L4/L6/L9×3 类型 | 71/74/77 | EV/ZPM/UHV | 20/30/40s |
| R5 存储盘 ×27 | 组装机(k)/装配线(M/big) | **外壳(类型,档) + 组件(类型,容量)**；M/big 研究=低一档同类型盘 | 档位熔融金属+润滑剂 | 盘 9 档×3 类型 | 21..29/61..69/81..89 | EV×3｜ZPM×3｜UHV×3 | 30..150s |
| R6 控制器 L4/L6/L9 | 组装机/装配线/装配线 | 外壳×4 + 驱动盘位 + 电容 A/B/C + ME 总线 + 同档马达×2/泵/传感器/发射器/机械臂（L6 研究=L4 控制器、L9 研究=L6 控制器） | 焊锡 / Iridium 熔融 / Neutronium 熔融 + 焊锡 + 润滑剂 | 控制器 L4/L6/L9 | 11/12/13 | EV/ZPM/UHV | 60/75/90s |

**t97 分档设计逻辑**：外壳/通风口/驱动盘位/ME 总线为 **EV 基础套件**（L4 控制器输入含它们，必须 ≤EV 才能兑现 "L4=EV 解锁"）；每档专属门控落在 电容（A=EV/B=ZPM/C=UHV）+ 盘（k=EV、16M..256M=ZPM、1024M..16384M=UHV）+ 控制器（L4=EV/L6=ZPM/L9=UHV）。同组内小→大用石英/源质瓶数量 + 电路号递增。**档位材料依据 GTNH 官方阶段文档（GTNewHorizons/GTNH-Dev-Doc tech tree）**：EV 主材料=Titanium、ZPM 主材料=Iridium（Naquadah 线为 ZPM 重点）、UHV 主材料=Neutronium（NaquadahAlloy 为 UV 次要材料，作 UHV 电容 C 补充）。

实现要点：所有配方经 `tryAddAssembler(...)`（组装机，含电路号与流体）或 `tryAddAL(...)`（装配线，含研究物品与扫描）注册——任一输入/输出为 null（FML init 时某些矿词未注册，如 CertusQuartzCharged）则跳过并告警，绝不把 null 传给配方 builder；源质系列经 TE4 门控（`Mods.ThaumicEnergistics.isModLoaded()`），essentia 组件/外壳/盘为 null 时配方自动跳过；不同电路号区分同类组装机配方。NEI 展示由对应配方表自动提供（装配线含研究物品/流体输入显示）。

以下为设计阶段的草案示例（保留作历史参考，数值/材料以最终 `Recipes.java` 为准）：

```java
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeBuilder.TICKS;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;

// R1 外壳 ×2（LV 组装机）
GTValues.RA.stdBuilder()
    .itemInputs(Materials.Steel.getPlate(4),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Steel, 1))
    .circuit(1)
    .itemOutputs(new ItemStack(BlockEcoStorageCasing.INSTANCE, 2))
    .eut(TierEU.RECIPE_LV).duration(5 * SECONDS)
    .addTo(RecipeMaps.assemblerRecipes);

// R2 驱动盘位 ×1（LV）
GTValues.RA.stdBuilder()
    .itemInputs(new ItemStack(BlockEcoStorageCasing.INSTANCE, 2),
                Materials.Aluminium.getPlate(2))
    .circuit(2)
    .itemOutputs(new ItemStack(BlockEcoStorageDrive.INSTANCE))
    .eut(TierEU.RECIPE_LV).duration(10 * SECONDS)
    .addTo(RecipeMaps.assemblerRecipes);

// R3 电容 A（L4，10M AE）×1（MV）——材料可用 AE2 能量元件/GT 电池替换
GTValues.RA.stdBuilder()
    .itemInputs(new ItemStack(BlockEcoStorageCasing.INSTANCE, 2),
                Materials.Redstone.getDust(8),
                Materials.Gold.getPlate(2))
    .circuit(3)
    .itemOutputs(new ItemStack(BlockEcoStorageCapacitance.A))
    .eut(TierEU.RECIPE_MV).duration(15 * SECONDS)
    .addTo(RecipeMaps.assemblerRecipes);
// 电容 B/C 同理（用更高阶材料/电路，tier 递进 MV->HV->EV）

// R4 物品盘 16M ×1（MV）——ME Storage Housing 的 1.7.10 物品引用方式待工程师按 AE2U 注册表确认
GTValues.RA.stdBuilder()
    .itemInputs(ME_STORAGE_HOUSING, // 占位：AE2U 物品（见下注）
                Materials.CertusQuartz.getDust(4) /* 占位材料 */)
    .circuit(4)
    .itemOutputs(new ItemStack(ItemEcoStorageCellItem.LEVEL_A))
    .eut(TierEU.RECIPE_MV).duration(20 * SECONDS)
    .addTo(RecipeMaps.assemblerRecipes);
// 注：AE2U 998 的物品注册表入口请用 AE2U 源码确认（appeng.core.features.registries.ItemRegistry /
//     appeng.core.AEItems 等），工程师在编译期直接引用对应 dev jar 即可。

// R5 控制器 A（L4）×1（HV，含 ME 总线与电容组件）
GTValues.RA.stdBuilder()
    .itemInputs(new ItemStack(BlockEcoStorageCasing.INSTANCE, 4),
                new ItemStack(BlockEcoStorageDrive.INSTANCE),
                new ItemStack(BlockEcoStorageCapacitance.A),
                new ItemStack(BlockEcoStorageMEBus.INSTANCE))
    .circuit(6)
    .itemOutputs(MTEEcoStorageArray.A.getStackForm(1))
    .eut(TierEU.RECIPE_HV).duration(30 * SECONDS)
    .addTo(RecipeMaps.assemblerRecipes);
```

补充建议：若希望 NEI 展示更友好，可给部件方块加“机器配方页”聚合（`RecipeMaps.assemblerRecipes` 已自动进 NEI）。存储盘配方里的 AE2 材料项（ME Storage Housing、Certus Quartz 等）以 AE2U dev jar 为准，若 AE2U 未暴露便捷物品入口，可用 `GameRegistry.findItemStack` 或 ItemStack 直引。

---

## 5. 项目结构与开发里程碑

### 5.1 包结构（`modId=ecoaegtnh`，`modName="ECO AE Extension (GTNH)"`）

```
src/main/java/ecoaegtnh/
├── EcoAEGTNHCore.java          // @Mod 主类 + creativeTab + Blocks 引用 + GUI_STORAGE_STATS 常量
├── EcoAERegistry.java          // preInit: 方块/物品/GUI handler；init: MTE + 配方；postInit: 注册 EcoStorageCellHandler
├── CommonProxy.java / ClientProxy.java
├── Tags.java                   // gradle token 生成
├── registry/
│   ├── RegistryBlocks.java     // GameRegistry.registerBlock + registerTileEntity（storage_array_*）
│   ├── RegistryItems.java      // 存储盘 Item 注册（item/fluid + TE4 门控的 essentia）
│   └── RegistryMTE.java        // init 阶段 new MTEEcoStorageArray(32030/32031/32032, ...)
├── metatileentity/
│   └── MTEEcoStorageArray.java // 控制器 MTE（A/B/C 三实例；结构/能量/纹理/GUI）
├── block/estorage/
│   ├── BlockEcoStorageCasing.java
│   ├── BlockEcoStorageDrive.java
│   ├── BlockEcoStorageCapacitance.java   // A/B/C 等级由 meta 编码
│   ├── BlockEcoStorageVent.java
│   └── BlockEcoStorageMEBus.java
├── tile/estorage/
│   ├── TileEcoStoragePart.java           // 抽象基类：controller ref + onAssembled/onDisassembled/markForUpdate
│   ├── TileEcoStorageDrive.java          // IInventory 1 槽 + watcher 缓存 + 潜行交互 + 等级门控
│   ├── TileEcoStorageCapacitance.java    // double 能量池 + 状态灯
│   └── TileEcoStorageMEBus.java          // AENetworkProxy + ICellContainer + IAEPowerStorage
├── item/estorage/
│   ├── ItemEcoStorageCell.java           // 抽象基类（unlocalizedName = ecoaegtnh.estorage_cell_<type>_<size>m）
│   ├── ItemEcoStorageCellItem.java
│   ├── ItemEcoStorageCellFluid.java
│   └── ItemEcoStorageCellEssentia.java   // 源质盘（TE4，替代原气体盘；门控注册）
├── ae2/
│   ├── EcoStorageCellHandler.java        // extends BasicCellHandler；item/fluid/essentia 三分支
│   ├── EcoStorageCellInventory.java
│   ├── EcoStorageCellInventoryHandler.java
│   ├── EcoStorageCellInventoryEssentia.java
│   ├── EcoStorageCellInventoryEssentiaHandler.java
│   └── EcoCellDriveWatcher.java
├── gui/
│   ├── EcoAEGuiHandler.java              // GUI id → Container
│   ├── ContainerEcoStorageController.java // Forge progress-bar 同步 6 个统计值
│   └── GuiEcoStorageController.java      // EOH 风格存储统计面板（176×128）
├── recipe/Recipes.java
└── lang：assets/ecoaegtnh/lang/en_US.lang / zh_CN.lang
```

### 5.2 里程碑

| 里程碑 | 内容 | 验收标准 |
|--------|------|----------|
| M0 骨架 | gradle 模板 + 依赖 pin（已完成）；`ecoaegtnh` 主类/proxy 可编译进游戏（基本完成） | `gradlew build` 通过、进游戏不崩 |
| M1 注册 | 5 个部件 Block + TE 注册、3 个控制器 MTE 注册、9 个存储盘 Item 注册（含 lang/贴图引用） | 创造栏可放置所有方块/物品，贴图正常 |
| M2 结构 | StructureLib 12 个 shape + checkMachine + 部件 TE 收集/拆解 + 防叠放 | 摆 L4/L6/L9 结构可成型/拆解，NEI 结构预览可用 |
| M3 AE2U | ME 总线网格接入（proxy 生命周期）、drive handler/watcher、cell inventory/handler、网格事件上报、能量委派 | 成型后连上 ME 终端可见盘位/能量；放盘、取盘实时更新；网格断电驱动盘失效 |
| M4 GUI | 控制器 GUI（能量条 + 每盘 cell 信息）+ 2 个网络包 | 打开 GUI 数据每 20 tick 刷新 |
| M5 配方 | §4 配方入库（assembler），NEI 可见 | 配方可合成、产物正确 |
| M6 审查 | reviewer 对照本设计逐项检查 + 单人测试清单（成型/拆解/断电/重启存档/跨区块卸载/盘 NBT 保留/电容能量保留） | 见 docs/REVIEW.md |

建议顺序：M1 → M2 → M3（核心）→ M4 → M5 → M6；M0 若有编译问题先修。

---

## 6. 关键 API FQCN 清单（供工程师直接使用）

### 6.1 GT5-Unofficial 5.09.54.111

```
gregtech.api.metatileentity.MetaTileEntity                       // 新 MTE 基类（旧 GT_MetaTileEntity 已移除）
gregtech.api.metatileentity.BaseMetaTileEntity
gregtech.api.metatileentity.implementations.MTEMultiBlockBase    // 多块基类（旧 GT_MetaTileEntity_MultiBlockBase 已移除）
gregtech.api.metatileentity.implementations.MTETooltipMultiBlockBase
gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase
gregtech.api.enums.MetaTileEntityIDs                             // ID 枚举（ID 字段 public final int ID）
gregtech.api.GregTechAPI        // sBlockMachines / METATILEENTITIES[] / registerMachineBlock / constructBaseMetaTileEntity
gregtech.common.blocks.BlockMachines                             // 机器方块（旧 GT_Block_Machines 已移除）
gregtech.api.interfaces.tileentity.IGregTechTileEntity
gregtech.api.interfaces.metatileentity.IMetaTileEntity
gregtech.api.enums.GTValues      // RA(IGTRecipeAdder) / V / VP / VN ...
gregtech.api.interfaces.internal.IGTRecipeAdder                  // GTValues.RA 的类型；stdBuilder()
gregtech.api.util.GTRecipeBuilder                                // SECONDS / TICKS 常量亦在此
gregtech.api.util.GTRecipe
gregtech.api.recipe.RecipeMaps                                   // assemblerRecipes / circuitAssemblerRecipes ...
gregtech.api.recipe.RecipeMap / RecipeMapBuilder / RecipeMapBackend
gregtech.api.enums.TierEU                                        // RECIPE_LV / RECIPE_MV / RECIPE_HV ...
gregtech.api.enums.Materials / OrePrefixes / SubTag
gregtech.api.util.GTOreDictUnificator                            // get(OrePrefixes, Materials, amount)
gregtech.api.util.GTUtility                                      // getIntegratedCircuit(int) 等
gregtech.api.util.MultiblockTooltipBuilder                       // 旧 GT_Multiblock_Tooltip_Builder 已移除
gregtech.api.structure.StructureWrapper / IStructureProvider / IStructureInstance / CasingInfo
gregtech.api.structure.error.StructureError / StructureErrorRegistry / PositionedStructureError
gregtech.api.interfaces.ITexture
gregtech.api.render.TextureFactory                               // 贴图工厂（新）
gregtech.api.enums.Textures / Textures.BlockIcons
```

### 6.2 StructureLib（建议显式依赖 1.4.42）

```
com.gtnewhorizon.structurelib.structure.IStructureDefinition     // builder()/addShape/addElement/build/check
com.gtnewhorizon.structurelib.structure.StructureUtility         // ofBlock/ofBlockAnyMeta/ofChain/isAir/transpose ...
com.gtnewhorizon.structurelib.structure.IStructureElement
com.gtnewhorizon.structurelib.alignment.IAlignment / IAlignmentLimits
com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable
com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing
```

### 6.3 Applied-Energistics-2-Unofficial rv3-beta-998-GTNH

```
appeng.api.storage.StorageChannel                        // enum ITEMS/FLUIDS（deprecated 兼容层）
appeng.api.storage.data.IAEStackType / AEStackTypeRegistry
appeng.api.storage.data.IAEStack / IAEItemStack / IAEFluidStack / IItemList
appeng.util.item.AEItemStackType.ITEM_STACK_TYPE / AEFluidStackType.FLUID_STACK_TYPE
appeng.api.storage.IMEInventory / IMEInventoryHandler / ICellInventory / ICellInventoryHandler
appeng.api.storage.ICellContainer / ICellProvider / ISaveProvider / ICellHandler / ICellRegistry
appeng.core.features.registries.entries.BasicCellHandler
appeng.me.storage.CellInventory / CellInventoryHandler / MEInventoryHandler
appeng.me.storage.ItemCellInventory / ItemCellInventoryHandler / FluidCellInventory / FluidCellInventoryHandler  // 参考实现
appeng.api.networking.IGrid / IGridNode / IGridHost / IGridBlock / IGridCache / GridFlags
appeng.api.networking.events.MENetworkEvent / MENetworkCellArrayUpdate / MENetworkChannelsChanged
appeng.api.networking.events.MENetworkPowerStatusChange / MENetworkPowerStorage(PowerEventType)
appeng.api.networking.security.IActionHost / BaseActionSource / MachineSource / ISecurityGrid
appeng.api.networking.storage.IStorageGrid
appeng.api.networking.energy.IAEPowerStorage / IEnergyGrid / IEnergySource
appeng.api.config.Actionable / AccessRestriction / PowerMultiplier / FuzzyMode / SecurityPermissions
appeng.api.util.AECableType / DimensionalCoord
appeng.me.helpers.AENetworkProxy / IGridProxyable
appeng.me.GridAccessException
appeng.tile.grid.AENetworkPowerTile                      // proxy 生命周期参考（不直接继承，见 §3.2）
appeng.tile.inventory.AppEngInternalInventory / IAEAppEngInventory / InvOperation
appeng.api.implementations.items.IStorageCell / ICellWorkbenchItem
appeng.api.implementations.tiles.IChestOrDrive
appeng.items.contents.CellConfig / CellUpgrades
appeng.util.Platform                                    // openNbtData 等
appeng.me.cache.GridStorageCache                        // 网格侧行为参考（ICellContainer 自动发现）
appeng.tile.storage.TileChest                           // ME Chest 参考实现
```

### 6.4 源质盘（ThaumicEnergistics，替代原气体盘——已实现，t10/t14/t21）

- **背景**：1.7.10 无 MekanismEnergistics（当前 GTNH 整合包 Modlist 亦不含，见 §7）→ 原设计的“气体盘”被**神秘4源质盘**取代。
- **依赖**：`implementation('com.github.GTNewHorizons:ThaumicEnergistics:1.7.60-GTNH:dev') { transitive = false }`；Thaumcraft 用本地 jar
  `compileOnly(rfg.deobf(files('libs/Thaumcraft-1.7.10-4.2.3.5.jar')))`（⚠️ `thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev` maven 坐标实测当前不可解析，见 `docs/ESSENTIA_CELL_RESEARCH.md` §5.2）。
- **关键 FQCN**：`thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE`（静态入口，TE4 preInit 注册）、
  `AEEssentiaStack`、`EssentiaList`；实现类 `ItemEcoStorageCellEssentia`、`EcoStorageCellInventoryEssentia`、`EcoStorageCellInventoryEssentiaHandler`
  （分别继承 `CellInventory<AEEssentiaStack>` / `CellInventoryHandler<AEEssentiaStack>`，`getCellType()=TYPE.ESSENTIA`）。
- **门控**（t14）：`gregtech.api.enums.Mods.ThaumicEnergistics.isModLoaded()`（modid 全小写 `thaumicenergistics`；`Loader.isModLoaded` 大小写敏感会失效）。
- 完整设计/容量取值/兼容性见 **`docs/ESSENTIA_CELL_RESEARCH.md`**。

---

## 7. 依赖版本验证结果（2026-08 实测）

| 依赖 | 任务书给定 | nexus 实测最新 | 结论 |
|------|-----------|----------------|------|
| GT5-Unofficial | ≥5.09.46.23（建议 5.09.54.111） | **5.09.54.111**（GitHub latest tag 同值，2026-08-26） | ✅ 已 pin 最新 |
| Applied-Energistics-2-Unofficial | rv3-beta-998-GTNH:dev | **rv3-beta-1041-GTNH**（2026-08-24；998/999/1000 亦存在） | ✅ 已 pin 998 且已与 1041 做逐文件 API 对比：存储 API 完全兼容（仅 `ICellInventory` 新增 default `isOverflow()`），可随时升级 1041 |
| StructureLib | （未提及） | **1.4.42** | 新增建议依赖 |
| ThaumicEnergistics（源质盘，已实现） | （未提及） | **1.7.60-GTNH**（nexus 200 验证；整合包运行 1.7.56-GTNH，API 无签名差异） | `implementation ... { transitive = false }`；dev pin 1.7.60（含 cell NBT 清理 bugfix） |
| Thaumcraft 4.2.3.5（源质盘材料） | （未提及） | maven 坐标不可解析（旧 nexus 已死 / CF 已下架）；**本地 jar 方案已验证** | `compileOnly(rfg.deobf(files('libs/Thaumcraft-1.7.10-4.2.3.5.jar')))` |

版本旁证：GTNH 官方整合包 `GT-New-Horizons-Modpack`（master README Modlist）当前 pin **AE2U rv3-beta-1000-GTNH**，位于 998 与 1041 之间，同代 API。

本地可用资源（已验证存在）：
- `D:\DeepSeek\GTNH-ECO\.research\libs\GT5-Unofficial-5.09.54.111-dev.jar` / `-sources.jar`
- `D:\DeepSeek\GTNH-ECO\.research\libs\AE2U-rv3-beta-998-GTNH-dev.jar`
- `D:\DeepSeek\GTNH-ECO\.research\gt5-src`（GT5U 源码解包）、`.research\Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH`（AE2U 源码）

---

## 8. 风险与开放项

1. **AE2U 版本漂移**：998 → 1041 已做逐文件 API 对比，存储接口完全兼容（见文档头注），升级无源码风险；master 分支仍在重构 storage API（`getAvailableItems(out, iteration, filter)`、`IterationCounter` 等新成员），**不要**追 master。
2. **GT5U 新旧 API 混淆**：网上大量 5.09.2x 教程用的是 `GT_MetaTileEntity_*`；本工程必须用 §2.1 的新名。
3. **1.7.10 无 BlockStates/Capability**：动态渲染用 `getIcon(IBlockAccess…)`；物品交互用 `IInventory` + 事件；机械手（Mek/Refined?）兼容仅保证 AE2 路径。
4. **MTEMultiBlockBase 默认维护机制**：已通过 `hasMaintenanceChecks=false` 关闭（t32 迁移 TTMultiblockBase 后同样生效，用户确认**免维护**）；若后续想保留维护玩法再开。
5. **电容能量 = AE 能量（double）**；t32 用户确认**纯 AE 供电**——已删除可选 GT EU 充电路径（能量舱 + 螺丝刀档位），能量全部经 ME 总线来自 AE 网格。
6. **ME 总线贴图/朝向**：总线方块需可被智能线缆（smart cable）连接且 4 面可接；`getCableConnectionType` 返回 DENSE 会让网格把它当 32 通道节点，注意与普通线缆混用的渲染。
7. **ID 段**：最终采用 32030–32032（< 32766 = 服务器 GT5U 5.09.54.20 数组上限；32029 之后、32050 GT_Framer 之前，验证空闲）；新增机器前用 `MetaTileEntityIDs` 枚举核对。
8. **源质盘（已实现）**：依赖 TE4 与 Thaumcraft（门控 `Mods.ThaumicEnergistics.isModLoaded()`）；Thaumcraft maven 坐标不可解析的绕过方案（本地 jar）见 `docs/ESSENTIA_CELL_RESEARCH.md` §5.2。
9. **1.12.2 参考中的 mixin（ae2 包）**：GTNH 版优先用 API 完成（§3 已给出无 mixin 路径）；`EStorageCellInventory` 若需访问 `CellInventory` 私有字段（如 `cellItems`/`maxItemTypes`），GTNH 侧字段名不同，先看 998 源码再决定是否 AT/mixin。

---

## 附录 A：参考仓库关键文件索引

| 主题 | 文件（ref 仓库内） |
|------|--------------------|
| 控制器 TE | `common/tile/ecotech/estorage/EStorageController.java` |
| 部件基类 | `common/tile/ecotech/EPartController.java`、`AbstractEPart.java`、`EPart.java`、`TileCustomController.java` |
| 驱动盘位 | `common/tile/ecotech/estorage/EStorageCellDrive.java`、`common/block/ecotech/estorage/BlockEStorageCellDrive.java` |
| 电容 | `common/tile/ecotech/estorage/EStorageEnergyCell.java`、`BlockEStorageEnergyCell.java` |
| ME 通道 | `common/tile/ecotech/estorage/EStorageMEChannel.java` |
| 存储盘 | `common/item/estorage/EStorageCell*.java`、`common/estorage/EStorageCellInventory.java`、`EStorageCellHandler.java`、`ECellDriveWatcher.java` |
| 交互/事件 | `common/handler/EStorageEventHandler.java` |
| 注册 | `common/registry/RegistryBlocks.java`、`RegistryItems.java` |
| 结构 JSON | `assets/ecoaeextension/default_machinery/Nova-extendable_digital_storage_subsystem_l{4,6,9}.json` |
| GUI | `client/gui/GuiEStorageController.java`、`common/container/ContainerEStorageController.java`、`common/network/PktEStorageGUIData.java` |
