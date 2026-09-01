# t3 实现记录 — E-Storage GT 多方块 + AE2U 集成（engineer-content）

## 交付内容（`src/main/java/ecoaegtnh/`）

按 `docs/DESIGN.md`（researcher 产出，t1）实现，包结构与之对应：

- `metatileentity/MTEEcoStorageArray.java` — 控制器 MTE（A/L4、B/L6、C/L9 三实例，ID 33000/33001/33002），继承 `MTEMultiBlockBase`，`hasMaintenanceChecks=false`
- `block/estorage/` — 部件方块：Casing / Drive / Capacitance(A/B/C 由 meta 区分) / Vent / MEBus
- `tile/estorage/` — 部件 TE：`TileEcoStoragePart`（抽象基类）+ Drive / Capacitance / MEBus
- `item/estorage/` — 存储盘：`ItemEcoStorageCell`（抽象）+ Item / Fluid / Gas（16M/64M/256M）
- `ae2/` — AE2U 集成：`EcoStorageCellHandler`、`EcoStorageCellInventory`、`EcoStorageCellInventoryHandler`、`EcoCellDriveWatcher`
- `registry/` — RegistryBlocks / RegistryItems / RegistryMTE
- `gui/` — Container + Gui + GuiHandler（存储统计面板，标准 Forge Container/Gui）

## 关键 API 实际用法（GT5U 5.09.54.111 / AE2U rv3-beta-998-GTNH，全部从 sources 验证）

### GT5U（类名与网上旧教程不同！）
- 多块基类：`gregtech.api.metatileentity.implementations.MTEMultiBlockBase`（旧 `GT_MetaTileEntity_MultiBlockBase` 已不存在）
- MTE 注册：`new MTEEcoStorageArray(id, name, regional, tier)` 构造器在 load 阶段调用即注册进 `GregTechAPI.METATILEENTITIES[id]`；物品形态用 `getStackForm(1)`；ID 必须 >2048（我们用了 DESIGN.md 建议的 33000+ 段）
- 结构检查：5.09.54 现代签名是 `checkMachine(IGregTechTileEntity, ItemStack, List<StructureError>)`，旧 boolean 版仍在但 deprecated
- 结构定义用 StructureLib：`IStructureDefinition.builder().addShape(name, String[][])...build()`；形状语义：**char=左右(A)、string=上下(B)、string[]=前后(C)**；`~`=控制器锚点；check() 的 basePositionA/B/C = 控制器左侧/上方/前方的块数（不把控制器自己算进去）
- `hasMaintenanceChecks` 是 public 字段，构造器里设 false 可免维护舱
- EU 输入：`addEnergyInputToMachineList(IGregTechTileEntity, casingIndex)`（casingIndex 传 -1 也安全，hatch 内部会钳制）；`drainEnergyInput(long)` 从所有能量舱抽 EU
- GUI：MTE 右击打开标准 Forge GUI 用 `aPlayer.openGui(modInstance, id, world, x, y, z)` + `NetworkRegistry.INSTANCE.registerGuiHandler`

### AE2U（998 已全面切到 IAEStackType 体系，StorageChannel 是 deprecated 兼容层）
- `ICellHandler`：`isCell(ItemStack)` + `getCellInventory(ItemStack, ISaveProvider, IAEStackType<?>)`；注册走 `AEApi.instance().registries().cell().addCellHandler(handler)`
- 单元格基类：998 没有 `AbstractCellInventory`，直接继承 `appeng.me.storage.CellInventory`（抽象方法：readStack / getStackTypeTag / getStackCountTag，NBT 键 `#/@/it/ic`）
- handler 基类：`CellInventoryHandler` 是抽象且构造器 protected，必须子类化（`EcoStorageCellInventoryHandler`）
- watcher：`appeng.me.storage.MEInventoryHandler` 构造器 `(IMEInventory<T>, IAEStackType<T>)`；覆写 inject/extract 在 MODULATE 成功后向网格 `postAlterationOfStoredItems(type, changes, BaseActionSource)` 并标记写入
- 网格接入：MEBus TE 实现 `ICellContainer`（extends IActionHost, ICellProvider, ISaveProvider）+ `IGridProxyable` + `IAEPowerStorage`；`AENetworkProxy(this, nbtName, visualStack, true)`，`setFlags(REQUIRE_CHANNEL, DENSE_CAPACITY)`，`AECableType.DENSE`（1.7.10 无 DENSE_SMART）；`@MENetworkEventSubscribe` 处理 PowerStatusChange/ChannelsChanged 并在 active 翻转时 post `MENetworkCellArrayUpdate`
- 网格事件：`proxy.getStorage()` / `proxy.getGrid()` 抛 `appeng.me.GridAccessException`

## 遇到的坑（编译期踩过的）
1. **1.7.10 映射无泛型注入**：`gradle.properties` 里 `enableGenericInjection=false`，覆盖 `CreativeTabs.displayAllReleventItems` 必须用 raw `List`，否则名称冲突。
2. **ISaveProvider 签名**：998 的 `ISaveProvider.saveChanges(IMEInventory)`（不是 1.12 的 `ICellInventory<?>`），覆写错了编译直接报抽象方法未实现。
3. **IMetaTileEntity.getDescription()** 是抽象方法，MTEMultiBlockBase 不实现；直接子类必须自己实现（用 MultiblockTooltipBuilder.getInformation()）。
4. **CellConfig 不是 IInventory**：998 的 `CellConfig extends IAEStackInventory`，`getConfigInventory` 要求 IInventory——应覆写 `getConfigAEInventory` 返回 `new CellConfig(is)`，让默认 `getConfigInventory`（CellConfigLegacy）包一层。
5. **Jabel 现代语法可用**：`enableModernJavaSyntax=true`，代码里可以用 switch 箭头、instanceof pattern 等，但要先过 spotless（GTNH 风格）。
6. **构建 JDK 分层**：Gradle 守护进程必须用 JDK 21+（系统 PATH 有 jdk-21），编译工具链由 `org.gradle.java.installations.paths` 解析到 JDK 8；JAVA_HOME 指向 JDK 8 跑 gradle 会失败。
7. **BOM 陷阱**：用 PowerShell Set-Content 写 Java 文件会带 UTF-8 BOM，javac 报 `非法字符: '\ufeff'`；统一用 UTF-8 无 BOM。

## 验证
- `gradlew spotlessApply build` → **BUILD SUCCESSFUL**，产物 `build/libs/ecoaegtnh.jar`（212KB）
- 30 个类全部编译通过（compileJava）
- 纹理已复制至 `assets/ecoaegtnh/textures/blocks/`（71 个文件 + ATTRIBUTION.txt，注明来源与 GPL-3.0 许可——注意参考仓库实际是 **GPL-3.0** 而非任务书所说的 MIT）

## 与 DESIGN.md 的差异/待办（给 reviewer/t4）
- 结构检查：实现了 1–12 驱动列的 12 个 shape 与逐列尝试成型；~~未实现 `ISurvivalConstructable`/NEI 结构预览~~ → 已在 t9 补齐（实现 `IConstructable` + `ISurvivalConstructable`，`getStructureDefinition()` 供投影/预览）
- 网络包 PktEStorageGUIData / PktCellDriveStatusUpdate 未实现——GUI 用标准 Container progress-bar 同步，足够 MVP
- 方块动态贴图（驱动盘位 type/level/capacity/status 状态贴图、电容 5 态贴图）未做 `getIcon(IBlockAccess...)` 动态渲染，暂用静态贴图（M1 部分）

## t4 补充（配方/语言/文档，engineer-content）
- 配方：`ecoaegtnh/recipe/Recipes.java`，全部走 `GTValues.RA.stdBuilder() + RecipeMaps.assemblerRecipes`（新 RecipeMap API，GT_Recipe.GT_Recipe_Map 已移除）；含外壳、驱动盘位、电容 A/B/C、ME 总线、通风口、物品盘 16M/64M/256M（ME Storage Housing 用 `AEApi.instance().definitions().items().cellContainer().maybeStack(1)`）、控制器 L4/L6/L9（`RegistryMTE.Lx.getStackForm(1)`）；init 阶段注册（EcoAERegistry.init 里 MTE 注册后调用）。
- 语言：en_US.lang / zh_CN.lang 补齐方块/物品/机器/GUI/存储盘提示（insert/extract/L6/L9 提示键已接入 `ItemEcoStorageCell.addInformation`，注意 1.7.10 映射无泛型注入，List 用 raw）。
- mcmod.info：description 描述内容与依赖（Forge/gregtech/appliedenergistics2/structurelib）。
- README.md：构建/安装/游戏内测试步骤。
- 验证：`gradlew spotlessApply build` → BUILD SUCCESSFUL（ecoaegtnh.jar 216KB）。

## t6 修复（P1-2/3/4 + P2 快速项，engineer-content，按 docs/REVIEW.md）
- **P1-2 能量反向供网**：`TileEcoStorageMEBus.injectAEPower/extractAEPower` 补齐 `MENetworkPowerStorage` 事件——`injectAEPower` 在 MODULATE 且当前电量 <0.01 时 post `PROVIDE_POWER`；`extractAEPower` 在 MODULATE 且当前满电（≥max-0.001）时 post `REQUEST_POWER`（照搬参考 EStorageMEChannel L97-125；AE2U EnergyGridCache 只靠节点入网 + 该事件登记 provider/requester）。
- **P1-3 拆盘位丢盘**：`BlockEcoStorageDrive.breakBlock` 先取 `TileEcoStorageDrive.cellStack`、清槽、以 `EntityItem` 掉落（随机偏移 + delayBeforeCanPickup=10），再 super。
- **P1-4 容量 ×8**：`EcoStorageCellInventory.getUsedBytes/getRemainingItemCount/getUnusedItemCount` 除数改为 `typeWeight × byteMultiplier`（typeWeight = `stackType.getAmountPerByte()`，物品=8），与参考公式 itemsPerByte(8)×byteMultiplier 一致；`getRemainingItemsCountDist` 不覆写（父类用 typeWeight，与参考一致）。
- **P2-a 等级限制**：`TileEcoStorageDrive.isCellSupported(stack)`（16M→L4+、64M→L6+、256M→L9），接入 `isItemValidForSlot` 与 `setInventorySlotContents`（覆盖玩家/漏斗/管道所有插入路径）。
- **P2-b 物品贴图**：`assets/ecoaegtnh/textures/items/` 生成 9 张占位 png（item/fluid/gas × 16/64/256M，纯色+边框）。
- **P2-c 创造栏**：5 个部件方块构造器补 `setCreativeTab(EcoAEGTNHCore.creativeTab)`。
- **P2-d mcmod.info version**：`"version": "0.0.1"`（原 `${modVersion}` 在非 git 工程解析为 unspecified）。
- 验证：`gradlew spotlessApply build`（JDK21 守护）→ BUILD SUCCESSFUL（ecoaegtnh.jar 221KB）。依赖已切服务器版 GT5U 5.09.54.20 + AE2U rv3-beta-1000。

> ⚠️ 未在 t6 范围：P1-5（MTE ID 33000-33002 越界，5.09.54.20 数组仅 32766 槽，服务器 init 崩溃）——ID 需落在 [0,32766) 且避开 GT5U 已占用段，属独立修复项，已提醒队长。

## t7 修复（配方材料 null 崩溃，engineer-content，服务器实测）
- **问题**：`Recipes.java:144` `Materials.CertusQuartzCharged.getDust(4)` 在 FML init 返回 null（AE2 联动材料 ore dict 未注册），`GTRecipeBuilder.itemInputs` 收到 null 抛 IllegalArgumentException → 服务器 init 崩溃。
- **修复**：`Recipes.java` 重构为统一走 `tryAdd(name, inputs, output, circuit, eut, duration)` 辅助方法：先遍历 inputs，任一为 null 则跳过该条配方并打 warn 日志（"input material is not registered yet (null)"），绝不把 null 传给 builder；output 也判空。全部 13 条配方（含 CertusQuartzCharged 的 64M/256M 盘两条、CertusQuartz 的 ME 总线/16M 盘）均走该路径，所有 `Materials.*.get*(...)` 调用只在数组构造里求值。
- **注册时机**：保持 FML init（MTE 注册后立即），不挪 postInit——null 跳过方案已彻底阻断崩溃，且 init 内引用 `RegistryMTE.Lx` 时序最稳。
- 验证：`gradlew spotlessApply build`（JDK21 守护）→ BUILD SUCCESSFUL；jar `build/libs/ecoaegtnh.jar` SHA256 = `5DF37B443A02478DC3EC1650052189140DD02F2F2486BD9713109490EF4B0794`（222168 B）。

## t8 修复（getStackType 构造期 NPE，engineer-content，客户端崩溃）
- **问题**：客户端打开创造栏/NEI 过滤存储盘时崩溃。`CellInventory.<init>`（AE2U 998 L98）调用 `this.getStackType()`，虚分派到我们子类的覆写——而子类的 `stackType` 字段在 `super()` 返回后才赋值，构造期间为 null → `createPrimitiveList()` NPE。
- **修复**：`EcoStorageCellInventory.getStackType()` 改为 null 兜底——`stackType != null` 返回自己的字段，否则返回 `super.getStackType()`（基类实现从 cell item 解析，构造期安全，等价于 AE2U 自己 ItemCellInventory 不覆写的行为）。`stackType` 字段保留，供构造后的 `readStack()`/`getTypeWeight()` 使用。
- 验证：`gradlew spotlessApply build`（JDK21 守护）→ BUILD SUCCESSFUL；jar `build/libs/ecoaegtnh.jar`（33 class，185241 B）SHA256 = `3401D9E967A1C3FE851B84D092D33BC3A6969A5266AC62BE8D1D7F5FD1E47358`。

## t9 修复（多方块无法成型：能量舱替换外壳 + 结构预览，engineer-content，全部改动在 `metatileentity/MTEEcoStorageArray.java`）

### 根因（三个叠加问题）
1. **能量舱与外壳互斥（任务疑点 1）**：shape 里 'C'/'H' 元素只认 `BlockEcoStorageCasing(meta 0)`，玩家把 GT 能量输入舱放进外壳后 shape 检查直接失败；而 checkMachine 里"能量舱可替换外壳"的扫描（`addEnergyInputToMachineList`）在 shape 通过之后才跑，永远没机会执行。
2. **shape 与部件收集坐标系不一致（任务疑点 3，实际比疑点更严重）**：原 shape 把驱动列放在"控制器左侧"（shape-A 轴），按 StructureLib 朝向换算（如面北 → A+ = 西 → 驱动列在世界东侧 x=+1..+n）；但 `collectPartsAndValidate` 和能量舱扫描用**世界坐标** x=-n-1..+1（驱动列在西侧）。两个扫描对不上：面北时 shape 通过的前提是列在东，而收集只扫到 x=+1（漏掉 n-1 列）；按 DESIGN.md 摆（面东、列在西）则 shape 永远不通过 → "结构无法成型"。原 `'~' 在 A=n+1、offsetA=n+1` 的锚点规则本身没错（EBF 佐证），错在把 shape 的朝向相对轴当世界轴用。
3. **没有结构预览（任务疑点 2）**：MTE 未实现 `IConstructable`/`getStructureDefinition()`，StructureLib 投影工具对控制器无效；NEI 预览无定义可读。

### 修复内容
- **shape 转置为 DESIGN.md §1.7/§2.5 的原版布局**：'~' 锚点固定在 (A=1,B=1,C=1)，offsets 恒为 (1,1,1)；C=0 前切片 "CC"/"MC"/"CC"（ME 总线在 A=0,B=1，即面东时世界 (1,0,1)），C=1 控制器切片 "CC"/"C~"/"CC"，C=2..n+1 驱动列切片 "ED"/"VD"/"ED"（z=0 三块 drive、z=1 y=±1 两块电容 + y=0 通风口），C=n+2 整面 2×3 外壳封口。任意面朝下：列在控制器"身后"（+C），总线在前右角（C=0、A=0）。**已用真实 StructureLib 1.4.42 jar 写独立验证程序逐格核对**（`build/verify/ShapeVerify.java`）：12 个 size 的锚点 `isContainedInStructure(1,1,1)` 全过、元素数 = 9n+25、面东时全部 35 格世界坐标与 DESIGN.md 逐一吻合（ALL CHECKS PASSED）。
- **外壳格接受"外壳 OR GT 能量舱"**：新增私有 `IStructureElement CASING_OR_ENERGY_HATCH`（'C' 元素）——`check()` = 外壳(meta 0) 或 `IGregTechTileEntity.getMetaTileEntity() instanceof MTEHatchEnergy`；`spawnHint/placeBlock/survivalPlaceBlock/getBlocksToPlace` 按外壳方块处理（投影提示外壳，生存构建放外壳）。能量舱注册仍由检查后的全量扫描 `addEnergyInputToMachineList` 完成（对非舱位是 no-op）。
- **部件收集改为与 shape 同一朝向换算**：`scanStructureVolume()` 按 shape 单元格 (a,b,c) 遍历（c=0..n+2, b=0..2, a=0..1，跳过控制器格），`abc=(a-1,b-1,c-1)` + `getExtendedFacing().getWorldOffset` 转世界坐标，再收集 drive/电容/ME 总线 + 注册能量舱。与 iterateV2 的实际访问坐标完全一致（stepB 只 reset A、stepC reset A+B 的语义已在验证程序里复现确认）。
- **结构预览/投影接入**：实现 `IConstructable`（`getStructureDefinition()` 返回 `STRUCTURE_DEFINITION`、`construct()` 走 `buildOrHints`、`getStructureDescription()` 返回布局说明）+ `ISurvivalConstructable`（`survivalConstruct()` 走 `STRUCTURE_DEFINITION.survivalBuild`）。投影 piece = 已成型列长，未成型取 max(12)。5.09.54.20 的 `BaseMetaTileEntity implements IConstructableProvider`，故投影工具/NEI 预览直接生效。
- **工具提示更新**：`beginVariableStructureBlock(2,2,3,3,4,15,false)`、`addController("Rear-left corner of the 2x3x2 head, middle layer")`、`addEnergyHatch("0+","Any casing position",1)`、脚注说明列在控制器身后/总线在前右角。
- **未改**：能量聚合/onPostTick/NBT/螺丝刀档位/GUI 均不动；`prevDriveBays`/`prevCaps` 清理逻辑核对无 bug（身份比较、成型/拆解对称）。

### 验证
- 静态核对：GT5U 5.09.54.20 dev jar 确认 `MTEHatchEnergy`、`MultiblockTooltipBuilder.getStructureHint()`、`BaseMetaTileEntity.getConstructable()` 存在；与 EBF（MTEBrickedBlastFurnace，MetaTileEntity + IConstructable 参考）和 MTEResearchCompleter/MTEDistillationTower（ofChain 能量舱元素参考）对照。
- 几何验证：`docs/verify/ShapeVerify.java`（真实 StructureLib 1.4.42 + recompiled_minecraft 1.7.10 + guava 17 跑通）→ **ALL CHECKS PASSED**。
- `gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（34 class，77650 B）SHA256 = `BC1FE27F802602BF0DD84A3CD9338B0ACE36B5913261D7D48E62A100A7BE5003`。

> 队长侧验证建议：装新 jar 后（1）按 DESIGN.md §2.5 摆（控制器面东、列在西、总线在 (1,0,1) 前右角）确认成型；（2）任意面朝 + 任意列长 1-12 各测一次；（3）外壳任一格换成 LV-HV 能量舱再测成型；（4）拿 StructureLib 投影工具（Constructable Trigger）右击控制器看投影 + 潜行创造构建；（5）破一格外壳验证拆解。

## t11 源质盘（Essentia Cell）替换气体盘（engineer-content，按 docs/ESSENTIA_CELL_RESEARCH.md t10 研究）

### 删除气体盘
- `ItemEcoStorageCellGas.java` 删除（1.7.10 无气体 stack type；MEGA 不存在）。
- `RegistryItems` 移除 gasCell16M/64M/256M 字段、构造与注册、`gasCell(int)` 辅助；gas 贴图 3 张删除。
- 气体盘原本无 assembler 配方（配方只有 item/fluid 之外的 item 盘），无配方可删；lang 的 gas 条目替换为 essentia 条目。
- `mcmod.info` description 的 "item/fluid/gas" → "item/fluid/essentia"。

### 新增源质盘（TE4 + Thaumcraft）
- **`ItemEcoStorageCellEssentia`**（extends 现有 `ItemEcoStorageCell` 基类，16M/64M/256M）：`getBytesPerType()/BytePerType() = 0`（TE4 语义）、`getTotalTypes()` = 60/80/100（研究 §6 建议；AE2U CellInventory 会把实际上限钳制到 63 ≥ TC4 ~60 种源质，已在注释说明）、stack type 走 lazy `EssentiaStackTypeHolder`（只在实例化时才加载 `AEEssentiaStackType.ESSENTIA_STACK_TYPE`，类本身可无 TE4 加载）。
- **`EcoStorageCellInventoryEssentia`**（extends `CellInventory<AEEssentiaStack>`）：照抄 TE4 1.7.60 的 `EssentiaCellInventory`（`saveChanges` 清理上界用 `getMaxTypes()` 的 bugfix 版；NBT 键 `Essentia#`/`et`/`ec`）；`getStackType()` 返回静态常量 → 基类构造期 `getStackType().createPrimitiveList()` 无 t8 式 NPE（构造安全）。
- **`EcoStorageCellInventoryEssentiaHandler`**（extends `CellInventoryHandler<AEEssentiaStack>`）：`getCellType() = TYPE.ESSENTIA`（AE2U 998 的 `ICellCacheRegistry.TYPE`）、`getStackType()` 返回 essentia 类型、`setPriorityList` 照 TE4 用 `EssentiaList + PrecisePriorityList` 构建分区。
- **`EcoStorageCellHandler`** 增加 essentia 分支：`is.getItem() instanceof ItemEcoStorageCellEssentia`（该类无 TE4 依赖，安全）时按 `type != ((ItemEcoStorageCell) is.getItem()).getStackType()` 比对（通过物品自身 stackType 字段，不触发 TE4 类加载），构造 essentia inventory+handler。
- **`TileEcoStorageDrive`**：新增 `cachedHandlerOther` 缓存第三类 stack type（essentia 等），`invalidateHandlers` 一并清空；`isCellSupported` 等级限制零改动（essentia 盘 extends 基类，`getByteMultiplier()` 4/16/64 自动映射 A/B/C）。
- **`RegistryItems`**：essentia 三档仅在 `Loader.isModLoaded("ThaumicEnergistics")` 时实例化+注册；`essentiaCell(int)` 在 TE4 缺席时返回 null（配方 tryAdd 自动跳过）。
- **配方**（`Recipes.java` R5b，assembler）：ME Storage Housing + Thaumcraft 源质瓶（`thaumcraft.common.config.ConfigItems.itemEssence`，惰性查找 + try/catch + isModLoaded 门控，null 时 tryAdd 跳过）→ 三档源质盘；circuit 14/15/16（避开 1-13），EU 档 MV/HV/EV（镜像物品盘），时长 20/25/30s。仅 TE4 加载时注册。
- **依赖**（`dependencies.gradle`）：`implementation('com.github.GTNewHorizons:ThaumicEnergistics:1.7.60-GTNH:dev') { transitive = false }`（必须 transitive=false，否则拉不可解析的 thaumcraft dev 坐标）；`compileOnly(files('libs/Thaumcraft-1.7.10-4.2.3.5.jar'))`（整合包运行 jar，类名/字段名未混淆——已 javap 验证 `ConfigItems.itemEssence` 等）。
- **lang**：`item.ecoaegtnh.estorage_cell_essentia_{16,64,256}m.name` = "ECO E-Storage Essentia Cell (16M/64M/256M)" / 中文 "ECO 存储阵列源质盘 (16M/64M/256M)"。贴图 `estorage_cell_essentia_*.png` 已存在（此前预生成）。
- **t8 不回归**：`EcoStorageCellInventory.getStackType()` 的构造期 null 兜底未动（grep 确认）；essentia inventory 因 `getStackType()` 返回静态常量而天然无此问题。

### 验证
- API 全部对照 AE2U 998 源码（`CellInventory` 抽象钩子/`getMaxTypes()` public/`ICellCacheRegistry.TYPE.ESSENTIA`/`PrecisePriorityList(IItemList)`/`MEInventoryHandler.getStackType()` public）与 TE4 1.7.60 sources（EssentiaCellInventory/Handler/Config、AEEssentiaStackType.createPrimitiveList 默认走 createList）。
- `gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（36 class，80517 B）SHA256 = `5DDD2C67CC6A30B91932781C0FD787D33E94BF9BF8C81FACC170AC2C76EB07D8`。jar 内含 3 个 essentia 类 + holder，无任何 gas 类/贴图。
- 队长侧建议实测：装新 jar 后（1）创造栏确认源质盘三档可见、气体盘消失；（2）源质盘放进驱动盘位，用 TE4 的 Essentia Terminal 确认网格可见/可读写；（3）64M/256M 盘在 L6/L9 控制器上插入被拒（等级限制）；（4）拆盘掉落保留 NBT；（5）assembler 配方（housing + 源质瓶）出盘。

## t13 修复（创造栏图标 null 方块崩溃，engineer-content，服务器+客户端日志双重实证）
- **问题**：客户端打开创造模式物品栏即崩溃（`GuiContainerCreative.func_147051_a` → `renderItemAndEffectIntoGUI` → NPE "ItemStack.getItem() is null"）。根因：`EcoAEGTNHCore` 内嵌类 `Blocks` 的 5 个字段（casing/capacitance/drive/vent/meBus）从未被赋值——`RegistryBlocks.register()` 只设置各 Block 类的 `INSTANCE` 字段，而创造标签页 `getTabIconItem()`/`getIconItemStack()`（EcoAEGTNHCore.java:66/71）用 `new ItemStack(Blocks.drive)`、`TileEcoStorageMEBus.getVisualItemStack()`（:53）用 `new ItemStack(EcoAEGTNHCore.Blocks.meBus, 1, 0)`，全部拿到 null 方块。
- **修复**：`RegistryBlocks.register()` 末尾统一补齐赋值（更内聚，且保证 preInit 阶段即生效，早于创造 GUI/ME 总线网格代理的任何使用时机）：`EcoAEGTNHCore.Blocks.casing/drive/capacitance/vent/meBus = BlockEcoStorageXXX.INSTANCE`。无 import 冲突（block 类在 `ecoaegtnh.block.estorage`，`Blocks` 是 `EcoAEGTNHCore` 内嵌类）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（36 class）；jar `build/libs/ecoaegtnh.jar`（80734 B）SHA256 = `C6B90A485473B2C3C7A6B86E5E854A8EC1C4668F6B57A949DF9A016D47004069`。三处使用点（EcoAEGTNHCore:66/:71、TileEcoStorageMEBus:53）现均解析到已赋值字段。

## t14 修复（源质盘注册门控 modid 大小写错误，engineer-content，用户实测创造栏无源质盘）
- **问题**：`RegistryItems.java:56` 与 `Recipes.java:163` 用 `Loader.isModLoaded("ThaumicEnergistics")` 门控源质盘注册，但 TE4 实际 modid 是 **`thaumicenergistics`（全小写）**——已从服务端 `thaumicenergistics-1.7.56-GTNH.jar` 的 mcmod.info 实证。`Loader.isModLoaded` 大小写敏感 → 判断永远 false → 源质盘从未注册/配方从未注册。
- **修复**：两处门控改用 GT 的 `gregtech.api.enums.Mods.ThaumicEnergistics.isModLoaded()`（javap 验证该枚举常量内部 modid 字符串正是小写 `thaumicenergistics`）；`Recipes.thaumcraftEssenceVial()` 的 Thaumcraft 门控也顺手改为 `Mods.Thaumcraft.isModLoaded()`（TC 真实 modid 是 "Thaumcraft"，大小写本就正确，改用枚举更一致）；相应 import 换成 `gregtech.api.enums.Mods`。
- **mcmod.info**：dependencies 里 `"ThaumicEnergistics"` → `"thaumicenergistics"`（一并修正；"Thaumcraft" 本就正确）。
- **lang 核对**：en_US/zh_CN 的 `estorage_cell_essentia_{16,64,256}m` 条目均在（"ECO E-Storage Essentia Cell (16M/64M/256M)" / "ECO 存储阵列源质盘 (16M/64M/256M)"）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（81537 B）SHA256 = `DD4190F942397C62E115D30870C8D0701B6A50117E6073C8DFDFB82A0E501188`。grep 确认源码中已无大小写错误的门控字符串（仅注释提及）。

## t15 修复（GT 仓室放置 + 结构投影方向 90°，engineer-content，用户实测两项）
全部改动在 `src/main/java/ecoaegtnh/metatileentity/MTEEcoStorageArray.java`。

### 1) GT 能量舱无法放置/成型
- **核实**：`CASING_OR_ENERGY_HATCH.check()` 的 TE 路径（`IGregTechTileEntity.getMetaTileEntity() instanceof MTEHatchEnergy`）正确——已从服务端 `gregtech-5.09.54.20.jar` 实证能量舱（LV..UXV）都是直接 `new MTEHatchEnergy(...)` 实例；且 `addEnergyInputToMachineList` 的扫描范围与 shape 逐格一致（同朝向换算）。
- **加固**：check() 增加 **GT 机器方块 meta 兜底**——`block == GregTechAPI.sBlockMachines` 且 `meta ∈ [0, METATILEENTITIES.length)` 且 `GregTechAPI.METATILEENTITIES[meta] instanceof MTEHatchEnergy`（覆盖 TE/MTE 尚未初始化的瞬间，如放置同 tick 的重检；对应队长提示的 "GT 机器方块 meta 范围 / GregTechAPI.isMachineBlock" 调查项）。
- **注意事项**（写进 tooltip/结构描述）：能量舱只能替换 **外壳 'C' 格**（头部 10 格 + 列末端 6 格封口），不能放驱动/电容/通风口/ME 总线位——先按投影把结构摆完整，舱放外壳即可成型。

### 2) 结构投影方向（90° 旋转）
- **根因（对照原版）**：拉取参考仓库 `sddsd2332/NovaEngineering-ECOAEExtension` 的 `Nova-extendable_digital_storage_subsystem_l4.json` 实证——原版结构**世界固定**：驱动列恒在 -x（west），头部在 +x，ME 总线 (1,0,1)；而 GT 控制器放置时正面始终朝玩家 → 原版体验 = **列朝玩家方向延伸（"往前扩"）**。t9 的朝向相对 shape 把列放在控制器"身后"（背离玩家），旧 t3 shape 把列放在 A 轴（对北向机器 = 右侧）——两者都不符合玩家预期。
- **修复**：shape 镜像为"列从控制器**往前**延伸"（沿机器正面朝向 = 朝向玩家）：
  - C=0 列末端整面外壳封口；C=1..n 驱动列（z=0 三 drive、z=1 y=±1 两电容 + y=0 通风口）；C=n+1 控制器切片（"CC"/"C~"/"CC"）；C=n+2 头部后切片（"CC"/"MC"/"CC"，**ME 总线移至头部后右角**，因原 (1,0,1) 前右角被列占用）。
  - 锚点 '~' = (A=1,B=1,C=n+1)；check/scan/construct/survivalConstruct 的 offsetC 统一 = n+1（`offsetCFor(length)`）。
  - **`getExtendedFacing()` 加固**：GT 机器 TE 未同步前 mFacing 默认 **DOWN**（BaseMetaTileEntity.java:114 实证）——`ExtendedFacing.of(DOWN)` 会把 A/B/C 轴映射到错误世界轴（列会渲染成竖直向上）。现对 UP/DOWN/UNKNOWN 一律回退 `ExtendedFacing.DEFAULT`（NORTH_NORMAL_NONE），杜绝客户端未同步窗口内投影/检查方向错乱。
- **几何验证**：`docs/verify/ShapeVerify.java`（真实 StructureLib 1.4.42）→ 12 个 size 锚点 `isContainedInStructure(1,1,n+1)`、元素数 9n+25、面东 n=3 全部 35 格世界坐标（列 x=+1..+n 朝玩家、封口 x=+n+1、ME 总线 (-1,0,+1) 后右）→ **ALL CHECKS PASSED**；`docs/verify/ShapeView.java` 四个朝向俯视图确认列均朝玩家方向延伸。
- **用户视角摆放**：把控制器放地上（正面朝你即可），**驱动列从控制器往你所在方向延伸**（在控制器前方/你和控制器之间），列末端是整面外壳封口；ME 总线在控制器**后方右角**（背对你那一侧）；能量舱可替换任意外壳格。1–12 列任意长度均可成型。

### 验证
- `gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（36 class）；jar `build/libs/ecoaegtnh.jar`（81876 B）SHA256 = `D8AC3D628B5799B362BE19CC0CDE641C5C43993BAC22DA1D0163430CF60C2097`。
- 队长侧建议实测：（1）面朝任意方向放控制器，投影确认列朝你延伸；（2）外壳任放 LV-HV 能量舱后成型；（3）列长 1/3/12 各测；（4）ME 总线在后右角接 ME 线缆；（5）破壳/拆列验证拆解。

## t16 驱动盘位右键放入/取出存储盘（engineer-content，用户实测无法右键放盘）
- **参考行为**（`ref/NovaEngineering-ECOAEExtension-main` 的 `EStorageEventHandler.onRightClickBlock`，priority LOW，仅服务端、仅主手、仅潜行）：空槽+手持存储盘 → 放入一个（手上扣一个，余量回手）；非空槽+空手 → 取出到手上；其余情况不拦截。
- **实现**：
  - `BlockEcoStorageDrive.onBlockActivated`：仅潜行触发；客户端直接返回 true 拦截点击（服务端改槽，交互同步）；服务端调 `TileEcoStorageDrive.interactWithCell(player)`。
  - `TileEcoStorageDrive.interactWithCell`（服务端）：空槽 + 手持 `EcoStorageCellHandler.isCell` 且 `isCellSupported`（t6 等级限制：64M 需 L6/L9、256M 需 L9，未成型时控制器为 null 则放行——与 t6 一致）→ 放入一个（`inHand.copy()` stackSize=1，手上 >1 则 -1，否则清空主手）；非空槽 + 空手 → 整盘取出到主手并清槽；两次都发本地化聊天提示（`ecoaegtnh.drive.cell.inserted/removed`）。
  - **网格同步**：`onCellChanged()` 在既有 markBlockForUpdate（客户端刷新）基础上，新增 `controller.getMEBus().forceCellArrayUpdate()`——`TileEcoStorageMEBus.forceCellArrayUpdate()` 在 proxy active 时无条件 post `MENetworkCellArrayUpdate`（参考原版 drive 的 onChangeInventory 也 post 该事件），让 GridStorageCache 重查 cell array 并自动增删 handler/给终端发内容差异（DESIGN §3.5 机制，无需手动 registerCellProvider）。
  - **lang**：en/zh 新增 `ecoaegtnh.estorage_cell.interact.tip`（潜行右键驱动盘位可放入/取出本存储盘，接入 `ItemEcoStorageCell.addInformation`）+ 两个聊天键（"Storage cell inserted/removed" / "存储盘已放入/取出"）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（36 class）；jar `build/libs/ecoaegtnh.jar`（82854 B）SHA256 = `3D97D3A787AC1F58E59B7C9B2ECFDDDDBAECF3192E68989BAA14B50E7B64C3F3`。
- 队长侧建议实测：（1）潜行右键空盘位+手持存储盘 → 放入并扣一个；（2）潜行右键占用盘位+空手 → 取出到手上；（3）64M 盘在 L4 阵列潜行放入被拒（等级限制，无提示但盘不进入槽）；（4）放入后 ME 终端立即看到盘内容（无需重连/重启）；（5）非潜行右键无任何反应（不拦截其他交互）。

## t17 GUI 重设计为 TecTech 鸿蒙之眼（Eye of Harmony）风格（engineer-content，用户要求）
- **尺寸/纹理**：保持 `xSize=176, ySize=128`，`BG` 仍指向 `textures/gui/estorage_controller.png`（不换文件名，无需改引用）；队长按 176x128 重生成 EOH 深色底（#0a0e14 系 + #4dc3ff 系霓虹）纹理即可，文本区（标题 y4-18 全宽、左列 x8-60 y24-84、右值 x110-168、底条 y102-126）建议留相对平整的深色以免文字难读，装饰放边角（左缘 x2-6、右上角、底部）。
- **GuiEcoStorageController 重写**（EOH 风格前景绘制，纯 `drawRect`+vanilla 字体，无自定义字体）：
  - 调色板：霓虹青 `#4DC3FF`、标签浅蓝灰 `#A8C4D4`、标题白、成型绿青 `#4DE3A5`、未成型红 `#FF5C5C`、轨道近黑蓝 `#0C1118`。
  - 顶部标题栏：白字标题 + 右上角 L4/L6/L9 等级霓虹标签 + 双层辉光霓虹下划线（13%/40%/100% 三层）。
  - 左列标签/右列数值 5 行（Structure/Drives/Columns/Energy/EU Input），值右对齐亮青，结构行按成型/未成型变色；行间 8% 白色细分隔线。
  - 科技装饰：左缘双层霓虹竖线、右上角 3 个"电路节点"（2x2 亮芯 + 连接线）、两条 45° 斜线辉光（GL11 旋转 + drawRect）。
  - 底部能量条：深色轨道 + 描边 + 按 energyStored/energyMax 比例填充的霓虹条（亮芯双层）+ 左侧 "Energy" 标签与右侧百分比（%d%%）。
- **ContainerEcoStorageController**：新增 `voltageTier` 同步字段（progress-bar id 5，来自 `controller.getVoltageTier()`，螺丝刀可调 LV..IV），GUI 显示 LV/MV/HV/IV。
- **lang**：en/zh 新增 `ecoaegtnh.gui.storage_stats.voltage`（"EU Input:" / "EU 输入档："）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（36 class）；jar `build/libs/ecoaegtnh.jar`（84397 B）SHA256 = `FAFBA9CB344481D8BA7E6DCC81059E1463F6BEA2D653544F47FFA32FB10144ED`。
- 队长侧建议实测：右击控制器看 GUI——深底上白标题/青数值/霓虹能量条清晰可读；成型/未成型行红绿区分；能量条随存储量变化；EU 档随螺丝刀切换刷新。

## t18 控制器自定义贴图（替换 GT 机械方块材质，engineer-content，用户实测控制器是稳定钛材质）
- **问题**：`MTEEcoStorageArray.getTexture` 返回 `MACHINE_CASING_STABLE_TITANIUM`，控制器放置后是 GT 机械方块外观。队长已生成原创贴图 `assets/ecoaegtnh/textures/blocks/storage_array_controller.png`（16x16，暗色石墨面板+青色霓虹能量环，三档通用）。
- **实现**（GT5U 5.09.54.20 正确姿势，参考 bartworks `MTELESU` / GT5U `MTEMagicalMaintenanceHatch` 模式）：
  - 覆写 `@SideOnly(Side.CLIENT) registerIcons(IIconRegister)`（`CommonMetaTileEntity` 提供的钩子；`BlockMachines.registerBlockIcons` 会遍历 `GregTechAPI.METATILEENTITIES` 调用每个 MTE 的 registerIcons）→ `aBlockIconRegister.registerIcon("ecoaegtnh:storage_array_controller")`（对应 `assets/ecoaegtnh/textures/blocks/storage_array_controller.png`）。
  - 用匿名 `gregtech.api.interfaces.IIconContainer`（getIcon/getOverlayIcon/getTextureFile，均已 javap 验证 .20 存在）包装该 IIcon。
  - `getTexture` 返回 `TextureFactory.of(controllerIconContainer)`（.20 有 `of(IIconContainer)` 重载）；容器为 null（服务端/未注册）时回退稳定钛材质。
  - 不用旧 `TextureFactory.of(String, String)` 签名（编译不过）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（37 class，含新匿名 IIconContainer）；jar `build/libs/ecoaegtnh.jar`（85556 B）SHA256 = `DEA93E13773136D2C7EB51245B46625FBA0834BE7F4F843B753D91EC959E80BB`；jar 内含 `assets/ecoaegtnh/textures/blocks/storage_array_controller.png`。
- 队长侧建议实测：放置 L4/L6/L9 控制器，六面均应为自定义石墨面板+青色霓虹环贴图（不再是稳定钛）；若需三档分色贴图（storage_array_controller_a/b/c.png），通知队长生成后按 tier 分支返回不同 IIcon。

## t21 修复（存储盘物品名未本地化，engineer-content，用户截图实证 vision 成员定位）
- **根因**：`ItemEcoStorageCell.java:34` `setUnlocalizedName("ecoaegtnh." + base + "_" + size + "m")` 产出 `ecoaegtnh.item_64m` → 显示键 `item.ecoaegtnh.item_64m.name`；lang 里是 `item.ecoaegtnh.estorage_cell_item_64m.name`（多 `estorage_cell_` 前缀）→ 查不到 → 回退原始 key。
- **修复**：第 34 行改为 `setUnlocalizedName("ecoaegtnh.estorage_cell_" + getCellBaseName() + "_" + millionBytes + "m")`——与第 35 行 `setTextureName`（已是 `estorage_cell_` 前缀）及 9 张 PNG/9 条中英 lang 键完全一致（已逐一核对 item/fluid/essentia × 16/64/256m 全匹配）。
- **副作用**：注册名从 `ecoaegtnh.item_64m` 变为 `ecoaegtnh.estorage_cell_item_64m`（GameRegistry.registerItem 用 unlocalizedName 去 "item." 前缀）；旧测试存档中的盘会变丢失物品（FML 迁移处理，测试存档可接受）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（85552 B）SHA256 = `B0FCA147E3E04CE960E55A7CDE2A9C314A3EBB66D05DC213D3FD5B8B45217B09`。
- 队长侧建议实测：创造栏 9 个存储盘应显示正确中文名（"ECO 存储阵列物品盘 (16M)" 等）而非原始 key；英文同理。

## t22 控制器右键打开 EOH 风格 GUI（engineer-content，P2-6 闭合）
- **问题**：GUI/Container 已就绪（t17）但全工程无 openGui 入口——`MTEMultiBlockBase.onRightclick` 默认调 GT 的 `openGui(aPlayer)`（打开 GT 机器默认 MUI 面板，非我们的 EOH GUI）。
- **实现**（`MTEEcoStorageArray.java`）：
  - 覆写 `onRightclick(IGregTechTileEntity, EntityPlayer)`（5.09.54.20 签名；`BaseMetaTileEntity.onRightclick` 对非潜行点击在双端调用它，扳手/螺丝刀/锤子在到达前已被基类消费 → 螺丝刀调电压档位逻辑（onScrewdriverRightClick）不受影响）。
  - 先保留 GT 多方块输入配置存取物品（`GTUtil.hasMultiblockInputConfiguration`）→ `super` 处理；否则服务端 `aPlayer.openGui(EcoAEGTNHCore.instance, GUI_STORAGE_STATS, world, x, y, z)` 打开 EOH 统计 GUI 并返回 true 拦截。**仅服务端调用**（EntityPlayerMP.openGui 会把容器同步给客户端；客户端再调会多发一个 open-GUI 包导致双开）。
  - 手柄/空手/任意物品均可打开（成型与否都能看状态）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（85471 B）SHA256 = `8EA99D6F72348DD13E948BF644DCD305AAA67123F97723462DB76C0B2B28A611`。
- 队长侧建议实测：普通右键控制器（空手/任意物品）→ 打开 EOH GUI（Structure/Drives/Columns/Energy/EU Input 五行 + 底部霓虹能量条）；成型/未成型都能打开；螺丝刀右键仍循环 LV..IV 档位且 GUI 中 EU Input 同步；扳手仍旋转朝向；潜行右键不打开 GUI（GT 行为）。

## t25 修复（盘位放盘交互 shift+右键 + 放入后两态贴图切换，engineer-content，用户实测放盘仍无效）
### 问题1：交互不生效（根因）
- **根因**：vanilla 1.7.10 `ItemInWorldManager.activateBlockOrUseItem` 中 `flag = !player.isSneaking() || stack == null || stack.getItem().doesSneakBypassUse(...)`——**潜行 + 手持物品时跳过 block.onBlockActivated**，直接走 item.onItemUse（存储盘 Item 无 onItemUse → 无效果）。t16 的 onBlockActivated 因此根本不会被调用（空手取盘路径 stack==null 本来就走 flag=true，是好的；放盘路径被跳过）。GT 的全局 PlayerInteractEvent 只拦打火石，非问题。
- **修复**：`ItemEcoStorageCell.doesSneakBypassUse(World,x,y,z,EntityPlayer) → true`——潜行手持存储盘时强制先走方块激活 → 驱动盘位 onBlockActivated（服务端）正常处理放入；取出（空手）路径不变。参考 1.12.2 原版用 PlayerInteractEvent（因 1.12.2 无 doesSneakBypassUse 等价物），1.7.10 用该 Forge 钩子更简洁可靠。
### 问题2：两态贴图 + 方块朝向
- `BlockEcoStorageDrive` 重写：
  - **朝向**：metadata 存水平朝向（vanilla 熔炉约定 2=N/3=S/4=W/5=E），`onBlockPlacedBy` 按玩家 rotationYaw 设置；`damageDropped/getDamageValue` 返回 0（掉落为普通无朝向物品，放置时重设）。
  - **两态渲染**：`getIcon(IBlockAccess,x,y,z,side)` 正面（side==facing）按 TE 槽内是否有盘返回 `storage_array_drives_front_filled`（高亮）或 `storage_array_drives_front`（空）；其它面返回 `storage_array_drives_side`；`getIcon(side,meta)`（背包/默认）正面=空态；`registerBlockIcons` 注册三个图标（队长已生成三张 png，均已在 jar 内）。
  - **刷新**：t16 的 `onCellChanged → worldObj.markBlockForUpdate` 已触发客户端重渲染（TE 同步后 getIcon 自动切换空/满），另已发 forceCellArrayUpdate 通知 AE 网格。
  - **⚠️ 连带修复**：驱动盘 meta 不再恒为 0 → 结构 shape 的 'D' 元素从 `ofBlock(drive, 0)` 改为 `ofBlockAnyMeta(drive)`（否则放置后的盘位（meta 2-5）会让结构永远无法成型）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（88945 B）SHA256 = `74D0E57B1B76D047D185488087534A6382B9FB541CAB4219610670E03262C0B5`；jar 内含 4 张 drives 贴图（原 drives.png + front/front_filled/side）。
- 队长侧建议实测：（1）shift+右键空盘位+手持存储盘 → 放入且盘位正面贴图变高亮（filled）；（2）空手 shift+右键占用盘位 → 取出且贴图复原（empty）；（3）任意朝向放置盘位（正面朝放置者），结构成型/拆解正常；（4）ME 终端看到盘内容。

## t26 控制器/驱动盘位分面贴图（正面 vs 其它面，engineer-content，用户要求）
- **控制器（MTEEcoStorageArray）**：registerIcons 注册两个图标（`storage_array_controller_front` / `storage_array_controller_side`，各包一个匿名 IIconContainer）；`getTexture` 按 `side == facing`（facing = `getBaseMetaTileEntity().getFrontFacing()`，GT MTE 正面语义）返回正面/侧面 `TextureFactory.of(container)`；null（服务端/未注册）时回退稳定钛。原 t18 的单贴图 `storage_array_controller` 不再使用（文件保留）。
- **驱动盘位（BlockEcoStorageDrive）**：t25 已实现分面（`getIcon(IBlockAccess)` 正面=front_filled/front 按 TE 是否有盘、其它面=side；meta 存朝向 2N/3S/4W/5E），与 t26 要求一致，无需改动；坐标方案与 t25 统一（同一 metadata 位）。
- **其它方块**：casing/MEBus/capacitance/vent 保持现有单面贴图（队长未补充 side 变体，无需接线）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（89113 B）SHA256 = `685597FC216AFE2539E46D2000D2656C246631C9AADF027F55AD07F136B3E2F4`；jar 内含 controller_front/side 与 drives_front/front_filled/side 全部五张分面贴图。
- 队长侧建议实测：放置控制器，正面（朝放置者）= 石墨面板+霓虹环+LED+绿核，其它五面 = 简化侧面；驱动盘位正面空/有盘两态 + 侧面简化；放入盘后正面变高亮。

## t27 GUI 重写为 TecTech 风格（engineer-content，按 docs/TECTECH_GUI_RESEARCH.md t24 研究规范）
- **配色（研究 §4.1 实测 hex）**：面板底 `#000020`（贴图由队长按此重绘 estorage_controller.png，176×128 不变）、2px 灰边 `#808080`（代码 drawRect 描边，不依赖贴图）、标题 WHITE、标签 GRAY `#AAAAAA`、数值 AQUA `#03DEFF`、数字 GOLD `#FFAA00`、OK 绿 `#52FF42`、BAD 红 `#FF4242`、能量条轨道纯黑 `#000000`+灰边、填充 `#428AFF`（TecTech parameter_blue）+ 亮芯 `#8FE3FF`。
- **排版（研究 §4.2）**：顶部 WHITE 标题 + 右侧蓝 `#428AFF` L4/L6/L9 档位标签 + 蓝/青双层下划线（#428AFF 亮线 + #03DEFF 底纹 + 宽辉光层）；五行数据（Structure/Drives/Columns/Energy/EU Input）GRAY 标签左对齐 + 右对齐值（Structure 用 OK/BAD 色、Drives/Columns/Energy 数字用 GOLD、EU Input 文本用 AQUA）；行间 8% 灰分隔线。
- **能量条（TecTech 分段参数条）**：黑底 + 灰边轨道，4px 高、8px 段/2px 间隙的 `#428AFF` 分段填充 + `#8FE3FF` 亮芯，右侧百分比 AQUA、左侧 "Energy" 标签 GRAY；能量数值用紧凑格式（2.4M / 10M，formatCompact K/M/B）。
- **装饰**：左缘双层蓝霓虹竖线（#428AFF 40%/13%）、右上角 3 个电路节点 + 连线（#428AFF 系）、两条 45° 斜线辉光；无花哨满铺背景（研究 §7.3：TecTech 底就是纯色+灰边）。
- **保持**：176×128 尺寸（研究建议不扩）、Container 六字段同步（structureValid/driveCount/driveColumnLength/energyStored/energyMax/voltageTier）、onRightclick 打开（t22）不变。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（89507 B）SHA256 = `28333B95654A5EFE2D52F28544AF369EE3701EC2123A7B2F695366C2D484EDCE`。
- 队长侧建议实测：右键控制器 → 深海军蓝底 + 灰边 + 白标题 + 灰标签/青值/金数字 + 蓝色分段能量条；成型/未成型红绿区分；能量条随存储量变化；EU 档随螺丝刀刷新。贴图重绘为 `#000020`+2px 灰边后效果最佳（当前旧贴图下文字/描边/能量条仍正常）。

## t29 GUI 改用 ModularUI2 内置 TecTech 主题（engineer-content，用户要求"TecTech 风格是自带的纹理不用绘制"）
- **决策**：放弃 t27 的手绘 Forge GUI（estorage_controller.png 重绘 + GuiEcoStorageController/ContainerEcoStorageController/EcoAEGuiHandler），改用 GT5U 内置的 MUI2 管道 + **GTGuiThemes.TECTECH_STANDARD** 主题（screen_blue 终端底 + TecTech logo，全部自带纹理，零贴图绘制）。
- **路由链（已验证 .20 dev jar）**：`MTEEcoStorageArray.useMui2() → true`；`MTEMultiBlockBase.onRightclick`（保留基类行为，配置物 + openGui）→ `CommonMetaTileEntity.openGui`（服务端）→ `MetaTileEntityGuiHandler` → `getGui().build(...)`；`getGui()` 覆写返回自定义 `MTEEcoStorageArrayGui extends MTEMultiBlockBaseGui<MTEEcoStorageArray>`（参考 MTEActiveTransformerGui 的 sync 模式，但不能继承 TTMultiblockBaseGui——它要求 T extends TTMultiblockBase）。
- **MTEEcoStorageArrayGui（新增 `ecoaegtnh/gui/MTEEcoStorageArrayGui.java`）**：覆写 `createTerminalTextWidget(PanelSyncManager, ModularPanel)`，注册 6 个同步值（ecoStructureValid/ecoDriveCount/ecoDriveColumnLength/ecoVoltageTier 用 `IntSyncValue(IntSupplier)` 只读构造，ecoEnergyStored/ecoEnergyMax 用 `LongSyncValue(LongSupplier)`），再 `super.createTerminalTextWidget(...)` 后追加五行 statRow（`IKey.dynamic(...).asWidget().fullWidth().marginBottom(2).textAlign(Alignment.CenterLeft)`，GRAY 标签 + GREEN/RED/GOLD/AQUA 值，lang 键沿用 `ecoaegtnh.gui.storage_stats.*`）+ 能量条（`ProgressWidget.texture(GTGuiTextures.PROGRESSBAR_STORED_EU, 147).height(5).value(new DoubleValue.Dynamic(() -> clamp(stored/max), null)).expanded()`，Fusion GUI 同款）。辅助：formatCompact（K/M/B）、voltageName（LV/MV/HV/IV，tier 2/3/4 映射——注意：**项目编译器不支持 switch 表达式，须用 if/else**）。
- **MTEEcoStorageArray 改动**：新增 `useMui2()/getGuiTheme()（TECTECH_STANDARD）/getGui()` 三个覆写；**删除 t22 的 onRightclick 覆写**（MUI2 由基类 openGui 打开，不再走 `aPlayer.openGui`）与 GTUtil import；GUI_STORAGE_STATS 常量（EcoAEGTNHCore）与 `NetworkRegistry.registerGuiHandler`（EcoAERegistry.preInit）一并删除。
- **⚠️ 编译坑（本任务唯一耗时问题）**：`MTEEcoStorageArrayGui` 是**非泛型类**（extends 参数化父类但自身无类型参数），`new MTEEcoStorageArrayGui<MTEEcoStorageArray>(this)` 报 "type does not take params"（ECJ 显示为误导性的"找不到符号"，javac -XDrawDiagnostics 才给出真相：`compiler.err.type.doesnt.take.params`；`[wrote ...MTEEcoStorageArrayGui.class]` 证明 GUI 类本身编译成功）。正确写法：`new MTEEcoStorageArrayGui(this)`——不要加类型参数、也不要用菱形。早期 diamond 报 "cannot infer type arguments" 同因。
- **删除**：`ecoaegtnh/gui/GuiEcoStorageController.java`、`ContainerEcoStorageController.java`、`EcoAEGuiHandler.java`、`assets/ecoaegtnh/textures/gui/estorage_controller.png`；jar 内已确认无这些 class/资源。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（86160 B）SHA256 = `C17DF19285E49713007F884F2D6F97089E3D5D35BF247A851BDDA5B9FCD0C2EE`；jar 内含 `ecoaegtnh/gui/MTEEcoStorageArrayGui.class`。
- 队长侧建议实测：右键控制器 → 内置 TecTech 终端（蓝底 + TecTech logo + 标准 GT 多块 GUI 布局）+ 五行统计 + 能量条；成型/未成型红绿区分；能量条随存储量变化；EU 档随螺丝刀刷新（onScrewdriverRightClick 未动）。

## t30 结构方向修正：列往右扩、ME 总线在控制器背侧右角（engineer-content，用户确认"列往右扩，ME 总线在控制器背侧右角"）
- **决策（替换 t15 的"列朝玩家正面延伸"）**：驱动列改沿控制器正面朝向的**右手侧（R）**延伸 1..12 列；ME 总线保持在控制器背面那一层、右侧角落（世界坐标不变：朝东时 (−1,0,+1)）。
- **新 shape（buildDefinitions）**：轴语义不变（外层 C 切片=前后、内层 B 行=上→下、字符 A=左→右=列轴），但**旋转 90°**：C 只有 2 片（C=0 = 控制器平面、C=1 = 背面），A 变长轴（A=0 = 列端封口、A=1..n = 驱动列、A=n+1 = 头部右侧切片、A=n+2 = 控制器切片）。锚点 `~` 移到 **(A=n+2, B=1, C=0)**，检查/构建基准偏移 **(n+2, 1, 0)**。
- **每列横截面（2 深 × 3 高，沿 C×B）**：C=0 平面 = `D/D/D`（3 驱动盘位，与控制器同平面）；C=1 平面 = `E/V/E`（电容顶/底 + 通风口中，与 ME 总线同平面）——保持原版语义"驱动在控制器侧、E/V 与 ME 总线同侧"（原版 z=0 驱动 / z=1 E/V+ME channel；t15 版 A=1 驱动 / A=0 E/V+ME 总线，等价平移）。
- **scanStructureVolume**：遍历 a∈0..n+2、b∈0..2、c∈0..1，跳过 (a=n+2,b=1,c=0) 控制器；abc=(a-(n+2), b-1, c)。`construct`/`survivalConstruct` 偏移同步 (n+2,1,0)；`offsetCFor` 更名 `offsetAFor`（返回 n+2）。
- **tooltip/描述**：`beginVariableStructureBlock(4, MAX+3, 3, 3, 2, 2, false)`（尺寸显示 2x4-15x3：宽变 4..15、深固定 2）；addController/footer/getStructureDescription 文案改为"列在控制器右侧"（玩家面向控制器时是其左手边）、"ME 总线在背侧右角"。
- **几何验证（docs/verify）**：ShapeVerify 重写（新锚点/新偏移/新 35 格期望表 + ME 总线断言 (−1,0,+1)）→ **ALL CHECKS PASSED**；ShapeView 自动窗口渲染四个朝向，全部确认：列在控制器右手侧（NORTH→+x、SOUTH→−x、EAST→+z、WEST→−z），ME 总线恒在背侧右角。
- **⚠️ 编译坑**：`"D".repeat(n)` 是 Java 11+ **API**（不是语法），Jabel 只提供新语法不提供新 JDK 方法 → ECJ "找不到符号"；改用本地 `repeat(char,int)`（Arrays.fill + new String）辅助方法。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（86403 B）SHA256 = `06122DDA97ECB28A85B3D398EA8D214A5F11E9031637C6E4D04BE0DB9585F069`。
- **用户摆放说明**：控制器放地上，正面朝向自己（GT 放置默认正面朝玩家）；驱动列在控制器正面的**右手侧**延伸 1..12 格——玩家站在控制器正面看它时，列在自己**左手边**；列端封口在最右侧；ME 总线在控制器**背面那一层**（正后方）的**右侧角落**（朝东示例：列朝南扩、总线在 (−1,0,+1)）。
- 队长侧建议实测：按上述摆放（如控制器正面朝西/自己朝东站，列往北扩），结构应成型（GUI Columns 数正确、ME 终端可见盘位）；GUI 统计与 t29 相同。（t32 起能量舱不再属于结构，摆放说明中的"能量舱可替换任意外壳格"已作废。）

## t32 控制器迁移 TTMultiblockBase（真 TecTech GUI + 纯 AE 供电 + 免维护，engineer-content，用户选定路线 1）
- **基类迁移**：`extends MTEMultiBlockBase` → `extends TTMultiblockBase`（TecTech 已并入 gregtech jar，无新依赖；`TTMultiblockBase extends MTEExtendedPowerMultiBlockBase<TTMultiblockBase> extends MTEEnhancedMultiBlockBase<TTMultiblockBase>`）。差异与适配：
  - `getStructureDefinition()` 在 TT **final**（委托 `getStructure_EM()`）→ 删旧覆写、实现 `getStructure_EM()` 返回 STRUCTURE_DEFINITION。
  - `checkProcessing()` 在 TT **final**（委托 `checkProcessing_EM()`）→ 改覆写 `checkProcessing_EM()` 返回 NONE（本机无配方）。
  - `getDescription()` 在 `MTETooltipMultiBlockBase` **final**（按 MTE id 缓存 `createTooltip()`）→ 改实现 `createTooltip()`（只放静态行；动态列长已移到 `getStructureDescription`/GUI）。
  - `isFacingValid` 在 MTEEnhanced **final**（由 `getAlignmentLimits` 推导）→ 删除本类覆写；`IAlignment`/`IConstructable` 由 MTEEnhanced 提供，implements 子句只留 `ISurvivalConstructable`。
  - `setExtendedFacing` 改为先 `super.setExtendedFacing(alignment)`（结构失效 + 客户端对齐同步）再 `setFrontFacing`（保持派生 getExtendedFacing 一致；rotation/flip 被 limits 锁 NONE）。
  - TT 构造器自动初始化 `parametrization`/`parametersInstantiation_EM()`；本机不实现 `IParametrized`（GUI 参数按钮灰显，安全）。结构检查入口：TT 的 `onPostTick → checkMachine_TT → 本类 checkMachine`（签名不变）。
- **纯 AE 供电（用户确认）**：删除 `CASING_OR_ENERGY_HATCH` 元素（'C' 改纯外壳 `ofBlock(casing,0)`）、`scanStructureVolume` 里的 `addEnergyInputToMachineList`、`voltageTier`/`EU_PER_TICK`/`onScrewdriverRightClick`（EU 档）、NBT voltageTier、onPostTick 的 EU→AE 注入块、tooltip 的 EU 行与 `addEnergyHatch`。能量全部经 ME 总线（TileEcoStorageMEBus 的 IAEPowerStorage 端点）与 AE 网格交互，控制器只做电容聚合（injectPower/extractPower 保留）。
- **免维护（用户确认）**：`hasMaintenanceChecks = false`（MTEMultiBlockBase 继承字段，TT 同样生效 → `shouldCheckMaintenance()=false`，checkMaintenance 空转、加载时 fixAllIssues）。
- **结构任意列长 1-12**：新增 `docs/verify/LengthVerify.java` 全量仿真（对每种实际列长 1..12 建"世界"，重放 checkMachine 的 size12..size1 下降检查 + 偏移 (n+2,1,0)）→ **ALL LENGTHS CHECKED PASSED**（每种列长只被对应 size 命中）。结论：t30 的 shape/偏移无 bug，"只成型最长"应是旧 jar 或摆放差异；新 jar 任意列长均可成型。
- **autoplace 朝向修复（用户实测恒朝北）**：'D' 元素改为自定义 `DriveElement`——check 接受任意朝向 meta；`placeBlock`（创意/全息 autoplace）按控制器正面朝向写 meta（2N/3S/4W/5E，`facingToDriveMeta`）；`survivalPlaceBlock` 走 `StructureUtility.survivalPlaceBlock` 物品路径（`onBlockPlacedBy` 按玩家朝向自然朝向）；`getBlocksToPlace` 返回同 meta（NEI 预览一致）。
- **GUI（真 TecTech）**：`MTEEcoStorageArrayGui extends TTMultiblockBaseGui<MTEEcoStorageArray>`（原 extends MTEMultiBlockBaseGui）——继承真 TecTech 右侧按钮列（电源开关/功率直通/参数按钮+控制器槽）与蓝屏 terminal + TecTech logo；terminal 五行统计 + 存储能量条保留；sync 值 6→5（删除 ecoVoltageTier 与电压行），lang 键删除 `storage_stats.voltage`。`useMui2()=true` + `getGuiTheme()=TECTECH_STANDARD` 不变。
- **tooltip**：`createTooltip()` 增加 "Powered entirely by the connected ME network (pure AE power, no GT EU)" + "No maintenance required"；`getStructureDescription` 同步更新。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（86097 B）SHA256 = `5962E367FB06C3194DE377F5BA2CC325F1EB2D81D5BDAEA0CBC03643D2F51894`；ShapeVerify 仍 ALL CHECKS PASSED；jar 内含 MTEEcoStorageArrayGui.class 与 DriveElement。
- 队长侧建议实测：（1）任意列长（1/3/5/12）成型，GUI Columns 正确；（2）能量条随电容/网格电量变化；（3）无维护仓、不显示"需要维护"；（4）全息/创意 autoplace 驱动盘位正面朝向正确（不再恒朝北）；（5）右侧按钮列 = TecTech 风格（电源开关/功率直通/灰显参数按钮）。

## t32 补充（用户实测"只有最长 12 列能成型"排查结论 + 修复）
- **结论：checkMachine 的 shape/偏移/下降循环经全量证明无 bug**。新增 `docs/verify/StructureAllVerify.java`：
  - **A) 每个 size n=1..12**：锚点 `isContainedInStructure(n+2,1,0)`、元素数=格数+导航数、**逐格**世界坐标与公式比对（非抽查）、ME 总线 (n+1,1,1)→世界 (-1,0,+1)（背侧右角，所有 n 一致）、列端封口 A=0→z=n+2 —— 全部通过。
  - **B) 全 12×12 交叉矩阵**：对每种实际列长 B=1..12 建"玩家摆放的世界"，重放 checkMachine 的 size12..size1 下降检查 → **每种列长只命中且必命中对应 size**（B=3 时 size3 通过、其余 11 个 size 全部失败等）→ **ALL STRUCTURE CHECKS PASSED**。
  - 因此"只有 12 列成型"不是结构检查逻辑问题。
- **真正的根因（用户流程）**：`construct`/`survivalConstruct` 对未成型机器恒用 `MAX_DRIVE_COLUMNS` 兜底 → **全息投影/创意 autoplace 永远投射/建造 12 列**，玩家只会照 12 列搭；缩短时若没在新远端补列端封口（或残留旧封口），自然无法成型。GTNH 惯例（MTEAssemblyLine/MTEIndustrialCokeOven/MTEQuantumComputer）是用**结构通道**（`GTStructureChannels.STRUCTURE_LENGTH`，默认取物品 stackSize）决定构建长度。
- **修复**：新增 `structureLengthFor(stack)` = 已成型取 `driveColumnLength`，否则 `GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stack, 1, 12)`（手持 N 个控制器物品 → 投影/建造 N 列结构，N∈1..12）；`construct`/`survivalConstruct` 改用之；删除死代码 `pieceForStructure()`；`getStructureDescription` 增加 "hold a stack of N controller items (1-12) to preview or build an N-column structure" 说明。结构检查本身（`checkMachine` 全 size 下降循环）不动。
- **用户侧排查建议（给队长）**：（1）手持 N=1/2/3/6/12 个控制器物品逐一看全息投影，应分别显示 1/2/3/6/12 列结构；（2）按投影摆放（列在控制器右手侧、列端封口在最右、ME 总线背侧右角）→ 任意 N 均成型；（3）从 12 列缩短时：拆掉多余列后，必须**在缩短后的最右端重建列端封口**（旧封口在更远处可拆可不拆，不影响检查）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（86243 B）SHA256 = `44A7FB5F9558F2D24135D4C48CE9E9307F857E582815EE84486D8F81B179A96E`；StructureAllVerify / ShapeVerify / LengthVerify 全部 PASSED。

## t33 盘型分色高亮 + AE 存储 tooltip + WAILA 盘位信息（engineer-content，用户实测反馈）
- **① 有盘高亮贴图按盘型分色**：`BlockEcoStorageDrive.getIcon(IBlockAccess,...)` 有盘时按 `TileEcoStorageDrive.getCellStack().getItem()` 类型返回分色贴图——物品盘 `storage_array_drives_front_filled_item`（金）、流体盘 `_fluid`（蓝）、源质盘 `_essentia`（紫），其它→默认 `_filled`（青，保留作 fallback）；`registerBlockIcons` 注册 6 张贴图。贴图由队长生成，jar 内已确认 3 张分色 png 在包内。
- **② 存储盘 AE 风格 tooltip**：`ItemEcoStorageCell.addInformation` 追加两行：`Used: X / Y bytes`（`inv.getUsedBytes()/getTotalBytes()`，紧凑格式化 K/M/G）与 `Types: N / M`（`getStoredItemTypes()/getTotalItemTypes()`，M 为 CellInventory 按 cell item getTotalTypes 钳到 63 的有效上限）；通过 `EcoStorageCellHandler.getCellInventory(stack, null, stackType)` → `ICellInventoryHandler.getCellInv()` 从物品 NBT 构建（客户端安全，无网格依赖）；整个读取包 `catch(Throwable)`（TE4 缺失时源质盘跳过，tooltip 永不崩）。新增 lang 键 `estorage_cell.used/bytes/types.tip`（en/zh）。`getCellBaseName()` 改 public（供 WAILA 用）、新增 `getCapacityMB()`。
- **③ WAILA 盘位信息**：新增 `ecoaegtnh/waila/EcoStorageDriveWailaProvider`（`IWailaDataProvider`）——服务端 `getNBTData` 写盘名/盘型（ecoCellDisplay/ecoCellKind），客户端 `getWailaBody` 显示 `Cell: <盘名>` + `<Item/Fluid/Essentia> Cell 16M/64M/256M`，空盘显示 `Cell: Empty`（tile 描述包已同步 cellStack，另加 tile 直读 fallback）；注册走 IMC：`EcoAERegistry.init` 里 `FMLInterModComms.sendMessage("Waila","register","ecoaegtnh.waila.EcoStorageDriveWailaProvider.callbackRegister")`（Loader.isModLoaded("Waila") 守卫，同 GT5U Waila 模式）。**Waila 1.19.30-dev 已在编译类路径**（com.github.GTNewHorizons:waila:1.19.30 传递依赖），无新依赖。新增 lang 键 `waila.drive.*`（en/zh）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（90366 B）SHA256 = `2680548793FCE2D3D0AEDCEEE9C2580A02F44DF4072876C52548F83C40C1D4FE`；jar 内含 `EcoStorageDriveWailaProvider.class` 与 3 张分色 filled png。
- 队长侧建议实测：（1）物品盘/流体盘/源质盘分别放入盘位 → 正面高亮分别为金/蓝/紫（旧盘留空测试 fallback 青色）；（2）悬停任意存储盘 → 出现 Used/Types 两行，随放入物品实时变化（NBT 持久化）；（3）WAILA 对准盘位 → 显示插入的盘名 + 类型容量，空盘显示 Empty。

## t35 紧急修复：setExtendedFacing 无限递归崩溃（放置机器即 StackOverflowError，engineer-content）
- **症状**：玩家放置 ECO 控制器瞬间双端崩溃（用户实测 + 服务器同刻停止）。崩溃栈：`MTEEcoStorageArray.setExtendedFacing(:270) → checkedSetExtendedFacing(IAlignment:161) → toolSetDirection(IAlignment:70) → MTEEnhancedMultiBlockBase.onFacingChange(:137) → BaseMetaTileEntity.setFrontFacing(:854) → MTEEcoStorageArray.setExtendedFacing(:270) → …` 无限循环。
- **根因**：t32 迁移时在 `setExtendedFacing` 覆写里加了 `super.setExtendedFacing(alignment); getBaseMetaTileEntity().setFrontFacing(alignment.getDirection());` ——`setFrontFacing` 触发 `onFacingChange → toolSetDirection → checkedSetExtendedFacing → setExtendedFacing` 环。
- **修复**：**删除 `getExtendedFacing`/`setExtendedFacing` 两个覆写**（比"只删 setFrontFacing 行"更彻底且符合 TecTech 惯例——MTEDataBank/MTEQuantumComputer 均不覆写 facing 方法）。朝向完全交给 `MTEEnhancedMultiBlockBase` 管理：`mExtendedFacing` 字段（默认 `ExtendedFacing.DEFAULT`=NORTH）、放置时 `setFrontFacing → onFacingChange → toolSetDirection → setExtendedFacing` 同步、NBT `eRotation/eFlip` 持久化 + `getCorrectedAlignment` 兜底（t15 的 DOWN 默认问题由 limits 校验 + 纠正覆盖）、扳手变更 + `StructureLibAPI.sendAlignment` 客户端同步。只保留 `getAlignmentLimits`（水平、不旋转、不翻转）。
- **验证**：1) `gradlew spotlessApply build` → **BUILD SUCCESSFUL**；2) **javap 静态确认**：编译后的 `MTEEcoStorageArray` 只有 `getAlignmentLimits`（+lambda），**无 `setExtendedFacing`/`getExtendedFacing` 覆写**，递归环不存在；3) jar `build/libs/ecoaegtnh.jar`（90125 B）SHA256 = `10DFBFB55B3F6B641B81202FB7CE0BC7B5CD363AEC47DBBBE5C479502295365B`。
- 队长侧：立即装服 + 重启服务器 + 让用户复测放置机器（L4/L6/L9 三档控制器 + 全息投影）；放置后机器正面朝玩家、结构可成型、扳手旋转朝向正常。

## t37 修复"仍显示需要维护"（TTMultiblockBase 维护 GUI，engineer-content）
- **症状**：机器成型后 GUI 仍显示维护状态（t32 已设 `hasMaintenanceChecks=false`，但维护**显示**不受它控制）。
- **根因（两层）**：① `MTEMultiBlockBase.supportsMaintenanceIssueHoverable()` 返回**硬编码** `getDefaultHasMaintenanceChecks()`（恒 true）——不看实例字段 `hasMaintenanceChecks`；② MUI2 终端的右上角列（`MTEMultiBlockBaseGui.createTerminalParentWidget → createTerminalRightCornerColumn`）在该钩子为 true 时渲染维护状态图标（`createMaintIssueHoverableTerminal`：绿"no issues"/黄/红 + "needs X tool" tooltip，`maintCount` sync 值统计 mCrowbar/mHardHammer/mScrewdriver/mSoftMallet/mSolderingTool/mWrench 六个缺失位）。所以即使免维护，图标照样出现（且因 t32 从 NBT 加载后 `fixAllIssues()` 全置 true，图标显示绿"no issues"或直接提示维护）。
- **修复**：`MTEEcoStorageArray` 覆写 `supportsMaintenanceIssueHoverable()` → `return shouldCheckMaintenance();`（= `!disableMaintenance && hasMaintenanceChecks`，本机为 false）→ 终端**不再渲染维护图标**，也无需任何维护仓/维护物品。逻辑层本就免维护：`checkMaintenance()` 因 `shouldCheckMaintenance()=false` 空转、`doRandomMaintenanceDamage()` 不触发、无 NO_REPAIR 停机、`getRepairStatus()==getIdealStatus()`（TT 的 ideal=super+2、repair=super+1+1，含 eCertainStatus/eParameters）。
- **验证**：1) `gradlew spotlessApply build` → **BUILD SUCCESSFUL**；2) javap 确认编译类/jar 内类含 `supportsMaintenanceIssueHoverable()` 覆写；3) jar `build/libs/ecoaegtnh.jar`（90176 B）SHA256 = `92F16D987C4C56E2646C32E5BE4640535BAF3F67C24B0AE508B879165ADD571F`。
- 队长侧建议实测：成型后打开控制器 GUI——terminal 右上角不再有任何维护图标；不装维护仓、不使用维护工具一切正常；能量条/统计行如常。

## t38 多方块结构信息中文化（投影仪右键聊天英文提示，engineer-content）
- **症状**：StructureLib 全息投影仪右键控制器，聊天里的结构说明是英文（"Drive columns (1-12 units) extend to the RIGHT..." 等 6 行）。
- **改动**：
  - `getStructureDescription`（聊天 6 行）→ 全部改 `StatCollector.translateToLocal(Formatted)` lang 键：`ecoaegtnh.structure.desc.{columns,column_detail,head,power,length,current_length}`（current_length 带 `%s` 列长参数）。客户端聊天显示按玩家语言解析。
  - `createTooltip`（控制器 tooltip，GT 惯例构建时翻译）→ 全部改 lang 键：`ecoaegtnh.tooltip.{machinetype,info.mass_storage,info.power,info.no_maintenance,controller,footer.placement}`；`addCasing` 名称改用已有的 `tile.ecoaegtnh.storage_array_*.name` 键（方块名中英双语早已就位）。
  - 核对（无需改动）：方块悬停名 `tile.ecoaegtnh.storage_array_*`、聊天键 `ecoaegtnh.drive.cell.inserted/removed`、cell tooltip 键、WAILA 键——中英双语均已在 en_US/zh_CN。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（91079 B）SHA256 = `4F39D080B3EDC1C25539980C76BD0490A4AE8BD748CDF557C4A4F6F4D3166438`；jar 内 en_US.lang/zh_CN.lang 均含全部新键（已抽查 zh_CN 12 个新键）。
- 队长侧建议实测：中文客户端用投影仪右键控制器——聊天显示 6 行中文结构说明（含"当前驱动列长：N"）；悬停控制器看中文 tooltip（含方块名）。

## t40 修复存储盘物品名本地化未生效（vision t36 截图：方块名中文但盘名英文，engineer-content）
- **根因（真 bug，非键缺失）**：lang 键本身正确（`item.ecoaegtnh.estorage_cell_<type>_<size>m.name` 9 条中英双语均在、UTF-8 无 BOM；unlocalizedName 链 `setUnlocalizedName("ecoaegtnh.estorage_cell_...") → "item."+raw+".name"` 与键完全一致，无覆写）。真正问题在 **t33 的 WAILA provider 服务端预翻译**：`getNBTData`（服务端，语言默认 en_US）把 `cell.getDisplayName()` 的**已翻译英文串**写进 WAILA 数据包发给客户端——客户端方块名按自身 zh_CN 渲染，盘名却显示服务端发的英文串，与截图"方块中文+盘名英文"完全吻合。
- **修复**：
  1. `EcoStorageDriveWailaProvider`：服务端 `getNBTData` 只发 **lang 键**（`cell.getItem().getUnlocalizedName(cell)` + 盘型键 + 容量），客户端 `getWailaBody` 用 `StatCollector.translateToLocal(key + ".name")` **客户端翻译**；新增 `ecoaegtnh.waila.drive.kind.line=%s 盘 %dM`（en：`%s Cell %dM`）使盘型行也本地化；tile 直读 fallback 同样改走键。
  2. `RegistryItems.registerItem`：旧代码对无前缀的 raw unlocalizedName 强截 5 字符 → 注册名被弄成 `gtnh.estorage_cell_...`；改为"有 `item.` 前缀才去前缀"（不影响显示名，但修掉注册名 mangling）。
- **验证**：1) `gradlew spotlessApply build` → **BUILD SUCCESSFUL**；2) **javap -c 字节码确认** WAILA provider 内已无 `getDisplayName` 调用（服务端不再翻译）；3) jar `build/libs/ecoaegtnh.jar`（91468 B）SHA256 = `3D68852BA59AC65E042D8AEE7C25876B89EF47E15E9DADA6FEBA19285578D7C7`；jar 内 zh_CN 含 kind.line 键。
- 队长侧建议实测：中文客户端悬停驱动盘位（WAILA）→ 盘名显示"ECO 存储阵列物品盘 (16M)"、盘型行"物品 盘 16M"；悬停存储盘物品本身 → 中文名 + Used/Types 行。

## t43 GUI 主题切换为 INTERGALACTIC_STANDARD（engineer-content，用户选定）
- `MTEEcoStorageArray.getGuiTheme()` 返回值从 `GTGuiThemes.TECTECH_STANDARD` 改为 `GTGuiThemes.INTERGALACTIC_STANDARD`（GTNH-Intergalactic 风格：深蓝 + 星空 + 蓝黄 logo；字段已在 5.09.54.20 dev jar 的 `GTGuiThemes` 确认存在）。GUI 结构不变：`useMui2()=true` + `TTMultiblockBaseGui`（右侧按钮列 + 主题 logo/terminal）。类 javadoc 与 GUI 注释同步更新。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；**javap -c 字节码确认** `getGuiTheme` 引用 `GTGuiThemes.INTERGALACTIC_STANDARD`；jar `build/libs/ecoaegtnh.jar`（91477 B）SHA256 = `96E1FD3AE0EBEA4D9086FEB8A918B86E5D4F5E62EAAEF094AE745CC6701EB8A5`。
- 队长侧建议实测：右键控制器 → terminal 为 Intergalactic 主题（深蓝星空底 + 蓝黄 logo），五行统计 + 能量条不变。

## t44 深挖"仍显示需要维护"（机器是否真实受损，engineer-content）
- **用户证据**：t37（隐藏维护图标）之后仍见"需要维护"；上一张截图显示"机器损坏过于严重 / Shut down due to machine damage"——即 GT lang 的 `GT5U.gui.text.shutdown_reason.no_repair`（`§4Shut down due to machine damage.`）与 `GT5U.gui.hoverable.norepair`（"Machine too damaged."），**NO_REPAIR 停机原因**，由 terminal 的 shutdown-reason hoverable 显示。
- **根因（真受损路径 + 显示残留）**：
  1. **构造器字段初始化时序 bug**：t32 在构造器**方法体**里设 `hasMaintenanceChecks = false`，但 `MTEMultiBlockBase` 构造器内部会先执行 `if (!shouldCheckMaintenance()) fixAllIssues();`——此时字段仍是字段初始化器给的默认 **true**（`hasMaintenanceChecks = getDefaultHasMaintenanceChecks()` 在构造器体之前运行）→ `fixAllIssues()` 被跳过 → **六个维护位（mWrench..mCrowbar）在新建放置后全是 false**（直到下次块加载触发 loadNBTData 才修复）。
  2. 位全 false 时：t32 之前（MTEMultiBlockBase.onPostTick）`getRepairStatus()>0` 门失败 → `stopMachine(NO_REPAIR)` → 停机并 **NBT 持久化**（`shutDownReasonID`/`mWasShutdown`）；t32 之后（TT onPostTick）`getRepairStatus() >= 3` 门（0+1+1=2 < 3）静默禁用运行路径。
  3. **显示残留**：`mWasShutdown=true` + NO_REPAIR 原因持久化后，terminal 的 shutdown-reason hoverable（`supportsShutdownReasonHoverable`→true，独立于 t37 关掉的维护图标）**一直显示**"Shut down due to machine damage"，直到 enableWorking 清除。这就是 t37 之后仍见"需要维护/损坏"的原因。
- **修复**：
  1. **覆写 `getDefaultHasMaintenanceChecks() → false`**（根治）：字段初始化器在**构造器体之前**调用它 → super() 构造时 `shouldCheckMaintenance()` 已为 false → `fixAllIssues()` 在构造期执行 → 六个维护位从放置瞬间全 true → `getRepairStatus()`（TT）= 6+1+1 = 8 == `getIdealStatus()`（6+2=8）→ 无维护问题、`getRepairStatus()>=3` 满足、任何 NO_REPAIR 路径双重关闭。构造器里的 `hasMaintenanceChecks=false` 赋值删除（冗余）。
  2. **清除陈旧 NO_REPAIR 状态**（onPostTick，服务端、成型时）：若 `getLastShutDownReason()==NO_REPAIR` → `setShutDownReason(NONE)` + `setShutdownStatus(false)`——已有存档里的旧停机原因/标志被清掉，GUI 不再显示"机器损坏"（STRUCTURE_INCOMPLETE 等真实原因仍正常显示）。
- **验证**：1) `gradlew spotlessApply build` → **BUILD SUCCESSFUL**；2) javap 确认类内含 `getDefaultHasMaintenanceChecks()` 与 `supportsMaintenanceIssueHoverable()` 覆写；3) jar `build/libs/ecoaegtnh.jar`（91631 B）SHA256 = `8846BA109B0EB6160695AC8C45F1D05969A91819FD1CE0D9D4ACDF3F89F5B1EA`。
- 队长侧建议实测：（1）放新机器 → 成型后 GUI 无任何维护/损坏提示（含 terminal 右上角两个图标位）；（2）用户旧的受损机器装新 jar 后 → 成型时旧 NO_REPAIR 被清除，重启存档后不再显示"机器损坏"；（3）拆结构（真实结构错误）仍显示结构不完整（正常）。

## t46 紧急修复 readStack 构造期 NPE（有内容盘插回崩溃，engineer-content）
- **症状**（用户实测 + 服务器 20:12 日志）：盘里放东西 → 取出 → NEI 按 U 查看 → 重新插回驱动盘位 → 崩溃（服务器连接被致命错误终止）。NPE："Cannot invoke IAEStackType.loadStackFromNBT because this.stackType is null" at `EcoStorageCellInventory.readStack(43)` → `CellInventory.loadCellStacks(385)` → `CellInventory.<init>(99)` → `EcoStorageCellInventory.<init>(33)` → `TileEcoStorageDrive.buildHandler` → `getCellArray`。
- **根因**：`EcoStorageCellInventory` 构造器 `super(o, container)`（33 行）内部 `loadCellStacks()` 会按存储类型逐格调用**虚方法** `readStack(tag)`（43 行），而 readStack 用 `stackType` **字段**——该字段在 super() 返回后（38 行）才赋值 → 构造期间为 null。**只有盘里有内容**（NBT 有 `#0/#1...` 存储数据）时 loadCellStacks 才真正迭代 → NPE；空盘 storedTypes=0 不迭代 → 之前测空盘没崩。与 t8 的 getStackType 构造期 null 同类（t8 已给 getStackType() 做 null 安全回退，但 readStack 没用它）。
- **修复**：`readStack` 改 `return getStackType().loadStackFromNBT(tag);`——getStackType()（t8）构造期回退 `super.getStackType()` = 基类 `cellType.getStackType()`（基类在 loadCellStacks 之前已设好 cellType，非 null）。`getTypeWeight()` 同样改用 getStackType()（防御，虽只在构造后调用）。**核实无其它构造期 raw 字段路径**：基类构造器只调 getStackType()/loadCellStacks()/getUpgradesInventory()/getConfigAEInventory()（后两者来自 cell item，不依赖 stackType 字段）。`EcoStorageCellInventoryEssentia` 安全：静态 ESSENTIA_STACK_TYPE + 整体覆写 loadCellStacks（不走 readStack）。
- **附带修复效果**：① 盘数据 tooltip（t33 的 Used/Types 行）此前被同一 NPE 触发、被 catch(Throwable) 吞掉而不显示 → 现在恢复；② NEI U 查看盘内容同根因 → 恢复。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap -c 字节码确认 `readStack` 调用 `getStackType()`（不再读 raw 字段）；jar `build/libs/ecoaegtnh.jar`（91640 B）SHA256 = `0B79EFDAA1C4CD9F714D53BC91B2AD501CA7D79B4D38D4B9BEBE24BAED8E1A0D`。
- 队长侧建议实测（用户复测路径）：往盘放东西 → 取出 → NEI U 查看盘内容 → 重新插回驱动盘位 → 不崩；悬停盘显示 Used/Types 数据行。

## t47 调查 GUI 主题切换未生效（INTERGALACTIC 未显示，engineer-content）
- **调查结论（主题确实生效，但视觉差异只有 logo）**：
  1. 主题消费链确认：`useMui2()=true` → MUI2 `MetaTileEntityGuiHandler` 路径 → `CommonMetaTileEntity.createScreen` → `new GTModularScreen(mainPanel, getGuiTheme())` → `useTheme(themeId)`（字节码 + 源码确认，t43 的 INTERGALACTIC 引用在类内）。
  2. **为什么"还是上一版"**：`GTGuiThemes.TECTECH_STANDARD` 只把 terminal 背景映射到 `screen_blue`（`gregtech:bg_terminal_tectech` = `gui/background/screen_blue`，90×72 深海军蓝 `#000020`+灰边 `#808080`）+ TecTech logo；`INTERGALACTIC_STANDARD` = parent(TECTECH) + **仅换 logo**（`space_elevator_logo`，18×18）。所以 t43 前后差异 = 一个 18×18 logo；整体外观 = GT 标准亮灰面板框（STANDARD 的 `bg_standard`）+ screen_blue terminal（≈用户/vision 说的"GT 亮灰框架+黑面板"）。
  3. t39 画廊的"深蓝+星空"来自 **GTNH-Intergalactic 的 MUI1 自定义 GUI**（`IG_UITextures.BACKGROUND_SPACE_WITH_STARS` 等，MUI1 `com.gtnewhorizons.modularui`），**MUI2 主题系统不含星空**——`ModularScreen` 只画 darkBackground，主题只定义 widget 纹理/panel/按钮等。
- **修复（让主题选择真实可见）**：`MTEEcoStorageArrayGui` 新增静态 `STARFIELD`（`UITexture.builder().location("gtnhintergalactic","gui/background/space_with_stars").imageSize(32,32).tiled(32,32)`，复用合并 gregtech jar 内既有资源，零手绘），`createTerminalTextWidget` 里 `list.background(STARFIELD)`——terminal 文本区 = screen_blue 边 + **星空平铺底** + 太空电梯 logo（主题映射），与量子计算机（纯 screen_blue terminal + TecTech logo）明显不同。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap -c 字节码确认 GUI 类引用 `gtnhintergalactic/gui/background/space_with_stars`；jar `build/libs/ecoaegtnh.jar`（91966 B）SHA256 = `38291C132FD01EF78368588B76E3057C18187DFCB100048D7A877630BA9E921E`。
- 队长侧建议实测：右键控制器 → terminal 文本区为星空底（黑底白星平铺，screen_blue 边框保留），右上角为太空电梯蓝黄 logo；对比量子计算机 GUI（深蓝底无星空）差异明显；五行统计+能量条文字在星空上仍清晰。

## t49 存储盘字节计算完全对齐 GTNH-AE（AE2U，用户决策，engineer-content）
- **差异（用户实测 ECO 16M 与 GTNH-AE 显示字节数不一致）**：① `getTotalBytes()` 原 `MB×1000×1024`（16M=16,384,000）→ 改 **`MB×1024×1024`**（16M=16,777,216）；② `getBytesPerType/BytePerType` 原 `byteMultiplier×1024` → 改 **`getTotalBytes()/128`**（16M→131072，与 AE2U `perType=totalBytes/128` 一致）；③ `EcoStorageCellInventory` 原覆写 `getUsedBytes/getRemainingItemCount/getUnusedItemCount/getTypeWeight`（含 ×byteMultiplier 缩放）→ **全部删除**，字节公式交给 AE2U `CellInventory` 基类（`used=types×perType+(storedCount+unused)/typeWeight`、`remaining=freeBytes×typeWeight+unused`、`unused=typeWeight−storedCount%typeWeight`、`typeWeight=stackType.getAmountPerByte()`（物品=8），已对 998 源码逐行确认）；④ `ItemEcoStorageCellItem.MAX_TYPES` 315 → **63**（AE2U 钳制值，tooltip "Types: N/63" 与 GTNH-AE 一致）；流体 25/源质 60/80/100 保持（AE2U 同样钳 63）。
- **byteMultiplier 清理**：字段/构造参数/getter 删除（构造器改 `(millionBytes, stackType)`，子类与 RegistryItems 9 处调用同步）；`TileEcoStorageDrive.isCellSupported` 等级限制改按 **`getCapacityMB()`（16/64/256）**（原按 byteMultiplier 4/16/64，等价）；tooltip 的 L6/L9 提示本就基于 millionBytes 无需改；`getIdleDrain()` 随 totalBytes 自动变（16M→16.0）。
- **EcoStorageCellInventory 瘦身**：只保留 `readStack`（t46 构造安全）/`getStackTypeTag`/`getStackCountTag`/`getStackType`（t8 构造安全）；删除 `cellType` 字段、`ITEM_SLOT/ITEM_SLOT_COUNT`（死常量）与纯转发覆写。源质盘 inventory（EcoStorageCellInventoryEssentia）不受影响（静态类型 + 自覆写 loadCellStacks）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；**javap 确认** jar 内 `EcoStorageCellInventory` 仅剩 readStack/getStackTypeTag/getStackCountTag/getStackType 四个覆写（无字节覆写）；jar `build/libs/ecoaegtnh.jar`（91049 B）SHA256 = `A84FEE87ABFC9EA05754C956502E62B20B666770345517D80B3BFBD63422B9AE`。
- 队长侧建议实测（用户对比）：ECO 16M 与 GTNH-AE 16M（同口径）悬停——"Used X / Y bytes"与"Types N / M"数字一致（16M 盘 total=16,777,216、perType=131072、/63 类型）；同一批物品放入两边，已用字节数相同。

## t50 GUI 改为量子计算机同款布局（底部 IO 区，engineer-content，用户要求"下面有IO的那种"）
- **量子计算机 GUI 研读**：MTEQuantumComputer 不覆写 useMui2/getGui → 用 TTMultiblockBase 默认 **MUI1** GUI（`addUIWidgets`）：screen_blue 背景 + 可滚动文字屏（顶部/中部）+ **底部参数 LED 条**（166×12，20 个 LED）+ 不确定度监视器 + 右侧电源/直通/安全清空按钮 + 右下控制器槽+散热片。ECO 是纯 AE 机器（无 GT 物品/流体 IO），按任务要求保留 MUI2 星空主题（t47）与五行统计，把量子计算机的"底部 IO 条"复刻为**显示型状态条**。
- **实现**：
  1. `MTEEcoStorageArray` 新增 `isMEBusConnected()`（`meBus != null && meBus.getProxy().isActive()`，服务端 sync 源）。
  2. `MTEEcoStorageArrayGui.createTerminalTextWidget` 在五行统计 + 能量条之后追加 `createIoStrip()`（`Flow.row().mainAxisAlignment(SPACE_BETWEEN)`，terminal 底部横跨整宽，类似量子计算机底部 LED 行）；新增 sync 值 `ecoMeBusConnected`（IntSyncValue）。IO 条三个 LED 单元（`●` 颜色标记 + GRAY 标签）：
     - **ME 总线**：成型+已连接=绿●"已连接"、成型未连=红●"未连接"、未成型=灰●"缺失"；
     - **盘位**：绿●（有盘）/"N" 灰●（空）"0"；
     - **能量**：绿●/"N%" 灰●/"0%"。
  3. 新增 lang 键 `ecoaegtnh.gui.io.mebus(.connected/.offline/.missing)`（en/zh）；盘位/能量复用 `storage_stats.drives/energy` 键。
- **布局（ASCII 草图，MUI2 面板）**：
  ```
  ┌──────────────────────────────────────────┐  ← GT 亮灰面板框
  │ [星空 terminal（screen_blue 边框）]          │
  │   Structure: §2成型                          │
  │   Drives: 12                                │
  │   Columns: 3                                │
  │   Energy: 2.4M / 16.8M                      │
  │   ██████████░░ 能量条                        │
  │   ● ME 总线: 已连接   ● 盘位: 12   ● 能量: 14%│  ← t50 IO 区（底部状态行）
  ├──────────────────────────────────────────┤
  │ 玩家物品栏  + [按钮列：电源/直通/参数/控制器槽]   │  ← 量子计算机式底部（控制器槽在右下）
  └──────────────────────────────────────────┘
  ```
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap 确认 jar 内 GUI 类含 createIoStrip/ioCell/meBusConnectedSync、MTE 含 isMEBusConnected；jar `build/libs/ecoaegtnh.jar`（92244 B）SHA256 = `9441B2BE576B1AD505A0E9832555EE0DB5D6911DE4A572F53922A862B4563947`。
- 队长侧建议实测：右键控制器 → 星空 terminal 五行统计 + 能量条 + **底部 IO 状态行**（ME 总线连接/盘位/能量百分比三 LED）；拆 ME 总线/断网 → ME 总线变红"未连接"；未成型 → 灰"缺失"；控制器槽/按钮列保持。

## t53 移除 GUI 星空背景（engineer-content，用户明确要求"移除星空背景"）
- `MTEEcoStorageArrayGui` 删除 `STARFIELD` 静态字段（`gtnhintergalactic/gui/background/space_with_stars` 的 tiled UITexture）与 `createTerminalTextWidget` 里的 `list.background(STARFIELD)` 调用；类 javadoc 同步更新。terminal 回到主题默认背景——INTERGALACTIC_STANDARD（父主题 TECTECH）映射的 **screen_blue 深海军蓝**（`gregtech:bg_terminal_tectech`），星空彻底移除。保留量子计算机同款布局：五行统计 + 能量条 + t50 底部 IO 状态行（ME 总线/盘位/能量三 LED）+ 太空电梯 logo。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；**javap -c 确认 jar 内 GUI 类无任何 space_with_stars/STARFIELD 引用**；jar `build/libs/ecoaegtnh.jar`（91916 B）SHA256 = `7FB98A6D4C663A2C0D2FAEB3DB8ACC2B74E7642899F6189A24E847CD36D9244D`。
- 队长侧建议实测：右键控制器 → terminal 为纯深蓝 screen_blue 底（无星空），五行统计 + 能量条 + 底部 IO 条不变。

## t54 GUI 改为与量子计算机完全相同（MUI1 TTMultiblockBase 机制，engineer-content，用户要求"直接使用量子计算机相同的ui，查看量子计算机源码"）
- **研读结论**：MTEQuantumComputer **不覆写 useMui2/getGui/getGuiTheme** → 走 TTMultiblockBase 默认 **MUI1 GUI**。路由：`CommonMetaTileEntity.openGui`：`(GTGuis.GLOBAL_SWITCH_MUI2 && useMui2()) || forceUseMui2()` 为 false → **`GTUIInfos.openGTTileEntityUI`**（MUI1，`IAddUIWidgets.addUIWidgets` + `bindPlayerInventoryUI`；窗口 198×192）。量子计算机界面构成（TTMultiblockBase.addUIWidgets）：screen_blue 背景（190×91）、可滚动文字屏（Scrollable 182×79，内容来自 drawTexts）、电源直通/安全清空/电源开关三按钮、右下控制器槽+散热片、底部参数条（PICTURE_PARAMETER_BLANK 166×12）、参数 LED 与不确定度监视器。
- **改动（让 ECO 走完全相同的机制）**：
  1. **删除 MUI2 全部定制**：`useMui2()→true`、`getGuiTheme()`、`getGui()` 三个覆写删除（回到 TT 默认 useMui2=false → MUI1）；**删除 `ecoaegtnh/gui/MTEEcoStorageArrayGui.java`**（MUI2 类，jar 内已无该 class）。
  2. **新增 MUI1 `addUIWidgets` 覆写**（与量子计算机同构）：screen_blue 背景 + Scrollable 文字屏（调 drawTexts）+ `createPowerPassButton/createSafeVoidButton/createPowerSwitchButton` 三按钮（TT protected 方法 + FakeSyncWidget.BooleanSyncer 同步 ePowerPass/eSafeVoid/isAllowedToWork）+ 控制器槽（BaseSlot inventoryHandler[getControllerSlotIndex()] + OVERLAY_SLOT_MESH）+ 散热片 + 底部参数条背景（PICTURE_PARAMETER_BLANK）。**未复刻参数 LED/不确定度监视器**（它们读 `parametrization.eParamsInStatus[...]`，非 IParametrized 机器数组为空会越界崩溃——合理偏离）。
  3. **新增 MUI1 `drawTexts` 覆写**：`super.drawTexts`（基座状态行：idle/running/停机原因等，量子计算机同款；维护行因 t44 全修复位自动隐藏）+ ECO 内容（TextWidget.dynamicString + FakeSyncWidget 同步，§ 色码 GRAY 标签/GOLD 数值）：Structure 绿/红、Drives、Columns、Energy（formatCompact）+ **能量条**（`█`×n+`░`×(20-n)+pct% 文本条）+ **底部 IO 状态行**（t50 三 LED：ME 总线绿/红/灰 ● + 盘位 ●N + 能量 ●N%）。
  4. MUI1 不使用 GTGuiTheme（MUI2 专属）——TecTech 观感来自 TecTechUITextures 直绘（screen_blue 等），主题设置无可保留项（任务条款"若 MUI1 用 getGuiTheme 就保留"不适用）。
- **与量子计算机逐项对照表**：
  | 项目 | 量子计算机（TT 基类 MUI1） | ECO（t54） |
  |---|---|---|
  | GUI 机制 | useMui2=false → GTUIInfos→addUIWidgets | 相同（覆写删除后同路径） |
  | 背景 | screen_blue（190×91） | 相同 |
  | 文字屏 | Scrollable 182×79 + drawTexts | 相同（含 ECO 统计+能量条+IO 行） |
  | 按钮 | 电源直通/安全清空/电源开关 | 相同三按钮 |
  | 控制器槽+散热片 | (173,167)+(173,185) | 相同 |
  | 底部参数条 | PICTURE_PARAMETER_BLANK | 相同（无 LED：非参数化机器） |
  | 参数 LED/不确定度 | 有（IParametrized） | 无（非参数化，防越界） |
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap 确认 jar 内类含 `addUIWidgets`/`drawTexts` 且**无 useMui2/getGuiTheme/getGui 覆写**、jar 内无 MTEEcoStorageArrayGui.class；jar `build/libs/ecoaegtnh.jar`（92540 B）SHA256 = `CDAEC7D3F4A4E1DEC1D7315642F98D93F367831A1C30F5EB918216DF6DBCEB03`。
- 队长侧建议实测：右键控制器 → 界面与量子计算机**同款**（深蓝 screen_blue 底、左上文字屏五行统计+能量条+IO 状态行、右侧三按钮、右下控制器槽+散热片、底部暗色参数条）；power pass/安全清空按钮可用（纯标志位，无副作用）；拆 ME 总线 → IO 行 ME 总线红"未连接"。

## t55 盘位防共用（归属认领）+ 拆控制器断网（B）+ 控制器关机断网（C）（engineer-content，用户反馈）
- **A · 盘位防共用（归属认领）**：`TileEcoStoragePart.onAssembled(controller)` 由 void 改为 **boolean**——已被**其它**控制器认领的部件返回 false（同控制器重复认领幂等 true）；`MTEEcoStorageArray.scanStructureVolume` 只在 `onAssembled(this)` 成功时收集驱动盘/电容；ME 总线被其它控制器认领时**结构报错**（无法经他人总线接网）。效果：重叠结构的部件只归**先成型**的控制器，杜绝同一盘位/电容/总线被两个阵列双用（AE 终端重复显示/能量双计）。
- **B · 拆控制器断网**：原 bug——控制器方块被拆除后其 MTE 消亡，`checkMachine` 不再跑，部件永不 `onDisassembled` → ME 总线 `assembled=true` 保持 → `getCellArray` 继续返回盘位 handler → **AE 终端仍显示阵列**。修复：`TileEcoStorageMEBus.isOperational()` 每 8 tick 校验——`assembled && controller != null && worldObj.getTileEntity(控制器坐标) 仍为本控制器 MTE && isAllowedToWork()`；状态**翻转**时才动作（去抖）：失联 → `proxy.invalidate()`（节点离网：盘位与能量全部移除）；恢复 → `proxy.onReady()`（重连）+ `MENetworkCellArrayUpdate`。所有服务入口（getCellArray/injectAEPower/extractAEPower/postAlteration/forceCellArrayUpdate）统一改走 `isOperational()` 门控。
- **C · 控制器关机断网（并入 t55）**：与 B 同机制——`isAllowedToWork()==false`（电源开关关闭/机器 off）时 `isOperational()` 为 false → 同上翻转逻辑断网（AE 终端不再显示阵列内容）；重新开机 → 翻转重连。去抖：仅"连接状态翻转"才 invalidate/重连，结构重检瞬间不会反复断连。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap 确认：`TileEcoStoragePart.onAssembled` 返回 boolean、MEBus 含 `isOperational`/`onAssembled(boolean 覆写)`/SRG 名 tick 方法 `func_145845_h`（=updateEntity，含翻转逻辑与 MENetworkCellArrayUpdate）；jar `build/libs/ecoaegtnh.jar`（93214 B）SHA256 = `8A13CEC76DBB60821CA85ACFEBECF3FED4BEFC2B247AC301E4DF2336E0493817`。
- 队长侧建议实测：①**A**：摆两个重叠阵列 → 重叠盘位只归先成型的控制器，AE 终端不重复显示；②**B**：成型后拆掉控制器 → 数秒内 AE 终端消失该阵列内容；重新放回成型 → 内容恢复；③**C**：成型后按电源开关关机 → AE 终端内容消失；再开机 → 恢复；④关/开过程中无断连抖动（不反复闪烁）。

## t58 修复 GUI：统计全 0 + IO 行移到底部条（engineer-content，用户实测 + 截图）
- **① 统计全 0（根因 + 修复）**：MUI1 文字屏的正确同步姿势（对照量子计算机基类 drawTexts 与 YottaTank）是——`TextWidget.dynamicString(supplier).setSynced(false)` 的 supplier 在**客户端**执行，读取 **FakeSyncWidget setter 写回的字段**（`FakeSyncWidget.XSyncer(() -> 服务端值, val -> 字段 = val)` 把服务端值同步进客户端字段）。t54 的错误：supplier 直接读 `getDriveBays().size()/getEnergyStored()`（客户端这些列表为空 → 恒 0）且 setter 是空 `val -> {}` → 值从不落地。修复：MTE 新增 6 个 `sync*` 同步目标字段（syncStructureValid/syncDriveCount/syncDriveColumnLength/syncMeBusConnected/syncEnergyStored/syncEnergyMax），drawTexts 的 5 个 TextWidget（Structure/Drives/Columns/Energy/能量条）supplier 全部改读 sync 字段，FakeSyncWidget setter 全部写 sync 字段。
- **② IO 行移到底部条**：drawTexts 里的 IO 状态行（ME 总线/盘位/能量三 LED 文本）**移除**；改在 `addUIWidgets` 底部参数条区域（PICTURE_PARAMETER_BLANK 条，位置 (7, 97/177)）加一个 `TextWidget.dynamicString(() -> ioStatusLine()).setSynced(false)`（DynamicTextWidget 才有 setSynced）+ 5 个 FakeSyncWidget（isMEBusConnected/mMachine/driveBays.size/energyStored/energyMax → 写 sync 字段）；`ioStatusLine()` 改读 sync 字段。文字屏只保留五行统计 + 能量条。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap 确认 sync 字段存在、字节码确认 ioStatusLine 读 syncMeBusConnected/syncStructureValid/syncDriveCount、底部条用 PICTURE_PARAMETER_BLANK；jar `build/libs/ecoaegtnh.jar`（93467 B）SHA256 = `AD49C71479FE2497F2448EC949B61079238E40C770A0A90D79A5874D2C09F200`。
- 队长侧建议实测：成型（如 2 列 6 盘位）后右键控制器 → 文字屏 Structure=成型、Drives=6、Columns=2、Energy 实际值 + 能量条百分比正确（打开 GUI 后 1-2 tick 内同步到位）；**IO 状态行只在底部暗色参数条上**（ME 总线/盘位/能量三 LED），文字屏不再出现 IO 行。

## t59 修复归属认领未释放（拆控制器后重摆结构"不完善"，engineer-content，用户实测）
- **根因**：t55 的归属认领在控制器方块被**挖掉**时没有释放——控制器 MTE 消亡后 `checkMachine` 不再跑、`disassembleAll` 不触发，盘位/电容/ME 总线仍记住旧控制器（`controller` 引用 + `assembled=true`）；重放控制器后 `scanStructureVolume` 的 `onAssembled(newController)` 因"已被其它控制器认领"返回 false → 所有部件被排除 → `driveBays` 空 → 结构"不完善"。
- **修复（双保险）**：
  1. **控制器侧释放**：`MTEEcoStorageArray.onRemoval()`（GT 机器方块拆除钩子，BaseMetaTileEntity:917 → MTE.onRemoval）里调用 `disassembleAll()`——遍历当前+prev 列表对盘位/电容/ME 总线逐个 `onDisassembled()`（清 controller/assembled + markForUpdate），认领立即释放。
  2. **部件侧死 owner 识别**：`TileEcoStoragePart.onAssembled` 遇到"被其它控制器认领"时先查 `isCurrentOwnerAlive()`（worldObj 上原 owner 控制器坐标仍是同一 MTE）——原 owner 已消失（方块被拆/被替换）则**释放认领并接受新控制器**；owner 仍在世则维持拒绝（t55 防共用不破坏）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；javap 确认 MTE 含 `onRemoval()`/`disassembleAll()`、TileEcoStoragePart 含 `isCurrentOwnerAlive()` 与 boolean `onAssembled`；jar `build/libs/ecoaegtnh.jar`（93865 B）SHA256 = `C73342FB45B1FC266738030B80FA726284319835B71CFE30225AAF5CA3D43E01`。
- 队长侧建议实测：①成型 → 挖掉控制器 → 重放控制器 → 结构重新成型（不再"不完善"），AE 终端内容恢复；②两个阵列重叠防共用仍生效（先成型者保留盘位，后成型者排除）；③拆掉 A 控制器后 B 阵列（若重叠）可重新认领 A 的原部件。

## t60 核对 GTNH-AE 16M 的 perType 基准（用户怀疑"不耐用"，engineer-content）
- **基准确认（"GTNH-AE 16M" = AE2U Advanced(Xtreme) Storage Cell 16384k）**：整合包 mods 无 EC2（ExtraCells2 不在），AE2U rv3-beta-1000-GTNH 的 16M 级盘 = **16384k Xtreme 存储盘**（`ItemAdvancedStorageCell`，MaterialType.Cell16384kPart）。实测（998 源码 + 1000 jar 字节码双重确认）：
  - `totalBytes = 16384 × 1024 = 16,777,216`（字节码：`ldc2 1024l → putfield totalBytes`，构造 `kilobytes*1024`）
  - `perType = 131,072`（字节码：`ldc int 131072 → putfield perType`）
  - `totalTypes = 63`；idleDrain = 4.0
  - 全系 perType 规律 = `totalBytes/128`（1k=8、4k=32、16k=128、64k=512、256k=2048、1024k=8192、4096k=32768、16384k=131072）——**AE2U 通用公式**。
- **修正结论：无需改代码（t49 已完全对齐）**：
  | 项 | GTNH-AE 16384k（16M） | ECO 16M（当前） | 一致 |
  |---|---|---|---|
  | totalBytes | 16,777,216 | 16×1024×1024=16,777,216 | ✅ |
  | perType | 131,072 | totalBytes/128=131,072 | ✅ |
  | totalTypes | 63 | 63 | ✅ |
  | Used 公式 | types×perType+(count+unused)/8 | 基类 CellInventory 同公式（t49 已删自覆写） | ✅ |
  - `CellInventory.getBytesPerType()` 直接委托 `cellType.getBytesPerType(cellItem)`（998 源码确认）→ 耐用度完全由 perType 决定，两边一致。
- **"不耐用"解释**：AE2U 大容量盘**按设计**每类型占用 `capacity/128` 字节（16M 盘放 1 种类型即占 131,072 字节）——这是 AE2U 全系惯例（含 16384k Xtreme 盘），并非 ECO 未对齐。若用户对比的是 AE2U **基础 16k** 盘（perType=128，16 KB 级）则属量级差异（16KB vs 16MB），非公式差异。
- **附带说明（超出字节范围，未改）**：idleDrain——AE2U 16384k=4.0，ECO 16M=`totalBytes/1024/1024`=16.0（t49 遗留，影响网格待机功耗，如需完全一致可后续按 4.0 对齐）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**；jar `build/libs/ecoaegtnh.jar`（93865 B）SHA256 = `C73342FB45B1FC266738030B80FA726284319835B71CFE30225AAF5CA3D43E01`（无代码改动，与 t59 同产物）。
- 队长侧建议实测：ECO 16M 与 AE2U 16384k Xtreme 盘悬停对比——total 16,777,216、perType 131,072、Types /63 全一致；同一批物品放入两边，Used 字节数与"耐用度"（放满 63 类型前可存多少）完全一致。

## t61 修复 ECO 盘无法放入 ME 驱动器（engineer-content，用户实测："eco的盘无法放置在ME驱动器里"）
- **排查结论（关键：代码路径本已正确，逐环实证）**：
  1. **AE2U rv3-beta-1000-GTNH 字节码实证**（服务器实际运行版本，非 998 源码）：`TileDrive.func_94041_b`(=isItemValidForSlot) → `AEApi...cell().isCellHandled(stack)`；`CellRegistry.isCellHandled` = 遍历 handlers 调 `isCell` + **basicCellHandler 兜底**；`SlotRestrictedInput.isItemValid`（驱动器槽，ContainerDrive 用 `PlacableItemType.STORAGE_CELLS`）在 isEnabled/container/allowEdit 后同样走 `isCellHandled`；`AppEngSlot.isEnabled()` 恒 true（槽位永远可点）。
  2. **双重接受机制**：① 我们的 `EcoStorageCellHandler`（`EcoAERegistry.postInit` 的 `addCellHandler`，服务器 round-11 jar 实测字节码确认注册调用在）`isCell(stack)` = `getItem() instanceof ItemEcoStorageCell` → true；② 即使 handler 未注册，`basicCellHandler` → `CellInventory.isCell` → 我们的 `IStorageCell.isStorageCell(stack)` 返回 true → `isCellHandled` 照样 true。**两条独立路径都放行**。
  3. **getCellInventory 正常返回**：`CellInventory` 构造器 `cellType.isStorageCell(cellItem)`（cellType=item 本身，我们的 isStorageCell=true）不抛 AppEngException；t46 已修 readStack 构造期 NPE → 有内容盘放入驱动器也安全。
  4. **服务器日志回溯**：fml-server-1.log（19:54-20:12 会话，t46 前版本）有 97 处 readStack NPE（`EcoStorageCellInventory.readStack:43` ← `CellInventory.loadCellStacks` ← `EcoStorageCellHandler.getCellInventory:65` ← **E-存储阵列 ME 总线** `TileEcoStorageMEBus.getCellArray:93`）——即用户当时把**有内容的 ECO 盘**放进阵列盘位/驱动器后打开 AE 终端即 NPE（事件被 FML 捕获、终端无内容）——**该 bug 已由 t46（round-8+）修复，服务器现跑 round-11（8A13CEC7）已含修复**；当前会话（22:17-22:48）无任何 cell 相关异常。结论：用户实测大概率命中 t46 前的崩溃期（或客户端未重启仍是旧内存版本），非 round-11 的代码缺陷。
- **修复/加固（2 项）**：
  1. **ME 箱子支持**（原为空实现）：`EcoStorageCellHandler.openChestGui` 改为与 AE2U `BasicCellHandler` 完全一致——`Platform.openGUI(player, (TileEntity) chest, chest.getUp(), GuiBridge.GUI_ME)`，ECO 盘放进 ME 箱子后右键可打开标准存储界面（之前右键无反应）。
  2. **启动自检日志**：`EcoAERegistry.postInit` 注册后输出一行 INFO——`isCellHandled(item16M)/fluid16M/essentia16M` 与 `getHandler(item16M)==ours`，装服后服务器日志直接可见驱动器槽位过滤是否放行三种 ECO 盘，故障秒级定位。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t61.log）；javap 确认：openChestGui 字节码 = `Platform.openGUI(EntityPlayer, TileEntity, ForgeDirection, GuiBridge.GUI_ME)`、postInit 含 isCellHandled×3 + getHandler 自检；jar `build/libs/ecoaegtnh.jar`（94768 B）SHA256 = `334DA779BB673D96C15AEA6ECA09EA9E997B0608F07478AC4D8D9D1AF339C00A`。
- 队长侧建议：装服后看服务器日志的 `EcoStorageCellHandler registered; ME drive slot filter self-check ->` 行（应全 true）；让用户**完全退出游戏客户端重新启动**再复测（避免旧内存版本）：ECO 盘（物品/流体）可拖入 ME 驱动器槽位、驱动器亮起对应盘状态、AE 终端可见盘内容；ECO 盘放进 ME 箱子后右键可查看内容。

## t62 修复存储盘等级限制绕过（成型时校验，engineer-content，用户实测"成型前放高级盘、成型后依旧能用"）
- **根因**：`TileEcoStorageDrive.isCellSupported`（16M→L4+、64M→L6+、256M→L9）沿用 t6 逻辑——`controller == null`（未成型）时直接放行（成型前可插入任意盘）；而成型校验（checkMachine → scanStructureVolume）只检查结构形状与部件认领，**不检查盘位内容** → 成型前把 256M 塞进 L4 盘位、成型后照样用。
- **修复（成型门控，静态判断）**：
  1. `TileEcoStorageDrive` 新增两个**静态**方法（不依赖 controller 字段，供成型校验使用）：`requiredTier(ItemStack)`（按 `getCapacityMB()` 映射 16→TIER_A(0)/64→TIER_B(1)/256→TIER_C(2)，非 ECO 物品返回 -1）与 `isSupportedByTier(ItemStack, int tier)`（`required >= 0 && tier >= required`）；实例 `isCellSupported(stack)` 重构为：非 ECO 物品 false / controller==null 时 true（保持未成型放行，供盘位插入路径用）/ 否则委托静态 `isSupportedByTier(stack, controller.getTier())`。
  2. `MTEEcoStorageArray.scanStructureVolume` 在 `driveBays.isEmpty() || meBus == null` 检查之后、认领/装配段之前新增成型门控：遍历已收集的 driveBays（t55 只含本控制器认领成功的盘位），对每个有盘的盘位调 `TileEcoStorageDrive.isSupportedByTier(cell, getTier())`——超等级（256M 在 L4/L6、64M 在 L4）→ `disassembleAll()` + `errors.add(StructureErrors.of("ecoaegtnh.structure.error.cell_tier_exceeded", TranslatableText.literal(所需等级 L6/L9)))` + 返回（结构失败）。
  3. 错误显示链路验证：GT5U `MTEMultiBlockBase` 的 MUI1 `drawTexts`（我们覆写并调 super）→ `handleStructureErrorsMui1` → `GenericListSyncHandler` 同步 → 未成型时在文字屏显示本地化错误行（与 MTELESU/MTEWindmill 等自定义 StructureError 同一机制，5.09.54.20 jar 已确认 StructureErrors/TranslatableText/TranslatableStructureError 均在）。
- **不破坏 t55 防共用**：门控只遍历本控制器认领成功的 driveBays（被他人认领的盘位根本不进列表）；静态判断不读盘位的 controller 字段（任务注意点 a）。成型后插入超等级盘仍被盘位自身拒绝（controller 已指向本机 → isCellSupported false，保持）。
- **语言键**（中英，%s 由 TranslatableText.literal("L6"/"L9") 填充，LangText.translate = translateToLocalFormatted）：
  - en：`ecoaegtnh.structure.error.cell_tier_exceeded=Storage cell tier exceeds this array: requires an %s controller`
  - zh：`ecoaegtnh.structure.error.cell_tier_exceeded=盘位中的存储盘等级超过本阵列支持：需要 %s 控制器`
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t62.log）；javap 确认：TileEcoStorageDrive 含 static requiredTier/isSupportedByTier + 实例 isCellSupported；MTE 字节码含 `isSupportedByTier` 调用 + `cell_tier_exceeded` 字符串 + `StructureErrors.of`；jar 内 en/zh lang 均含新键；jar `build/libs/ecoaegtnh.jar`（95413 B）SHA256 = `601504C9CADEDB152D13C72E72FACDB20D644F7284904AE616EDE2A8AE26DECD`。
- 队长侧建议实测：①L4 阵列成型前把 256M 盘放进盘位 → 成型失败，文字屏出现"盘位中的存储盘等级超过本阵列支持：需要 L9 控制器"；换 16M 盘 → 正常成型；②L6 阵列放 64M 盘可成型、放 256M 盘失败（提示需要 L9）；③成型后往盘位插 256M（L4/L6）被拒绝（现状保持）；④两个重叠阵列（t55）防共用不回归。

## t62 修订（用户偏好方案：成型前禁止放盘 + 成型后等级校验，替代"成型时校验"为主方案；engineer-content）
- **用户明确偏好**：1) **成型前禁止放盘**——`TileEcoStorageDrive.interactWithCell` 插入路径先检查 `controller == null || !controller.isStructureValid()`（TTMultiblockBase mMachine 的已有访问器 `MTEEcoStorageArray.isStructureValid()`）→ 拒绝并聊天提示 `ecoaegtnh.drive.cell.not_formed`（zh"机器未成型，无法放置存储盘" / en"The array must be formed before inserting a storage cell"）；2) **成型后放盘时校验等级**——`isCellSupported` 不通过时拒绝并聊天提示 `ecoaegtnh.drive.cell.tier_not_supported`（zh"该存储盘需要 %s 控制器（当前阵列等级不足）" / en"This storage cell requires an %s controller (array tier too low)"，%s=requiredTier 映射的 L6/L9）；3) **取出不受限**——空手 shift+右键随时可取（提取路径未改）。
- **兜底保留**：原 t62 成型门控（scanStructureVolume 逐盘位静态复查）保留为双保险——其他插入路径（漏斗/管道走 `setInventorySlotContents`，未成型时 isCellSupported 仍放行）即使绕过了玩家交互，成型时也会被门控拒绝并显示结构错误。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t62b.log）；javap 确认 interactWithCell 含 `isStructureValid()` 调用 + `not_formed`/`tier_not_supported` 字符串 + requiredTier 映射；jar 内 en/zh lang 均含两新键；jar `build/libs/ecoaegtnh.jar`（95859 B）SHA256 = `033A1FD0DCB5E502531BF0072D37009E55285D14C0C6D44F6791FC3757C1E430`。
- 队长侧建议实测：①单独放一个盘位（无阵列）shift+右键放盘 → 聊天提示"机器未成型，无法放置存储盘"；②L4 阵列成型后 shift+右键放 64M/256M → 聊天提示"该存储盘需要 L6/L9 控制器"；③L4 成型后放 16M → 成功放入；④成型后空手 shift+右键取盘 → 正常取出；⑤漏斗强制塞 256M 进未成型 L4 盘位 → 成型时结构失败（兜底生效，文字屏显示"需要 L9 控制器"）。

## t63 对齐 idleDrain 到 AE2U 惯例（16M→4.0，用户要求完全对齐 GTNH-AE；engineer-content）
- **遗留差异（t60 记录）**：`ItemEcoStorageCell.getIdleDrain()` = `totalBytes/1024/1024`（16M→16.0），而 AE2U Advanced(Xtreme) 16384k 盘 = **4.0**（998 源码 `ItemAdvancedStorageCell` 构造 switch 实测：Cell256kPart→2.5 / Cell1024kPart→3.0 / Cell4096kPart→3.5 / Cell16384kPart→4.0）→ ECO 待机功耗是 AE2U 的 4 倍，影响 AE 网格待机功耗。
- **修复**：`getIdleDrain()` 改为 `totalBytes / 4,194,304`（= totalBytes/(4×1024×1024)，即 MB/4）——16M→**4.0**、64M→16.0、256M→64.0，与 16384k Xtreme 完全一致（64M/256M 无 AE2U 直接对照，按同一线性基准延伸，队长批准方案）。影响路径：①ME 驱动器/箱子的 `EcoStorageCellHandler.cellIdleDrain` → `cell.getIdleDrain()`；②E-存储阵列 ME 总线 `MTEEcoStorageArray` 的 idleDrain 汇总（64 基础 + Σ各盘 getIdleDrain → proxy.setIdlePowerUsage）。物品/流体/源质盘统一生效（基类方法）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t63.log）；javap -c 确认 getIdleDrain 字节码 = `getTotalBytes()` l2d → `ldc2 4194304.0d` → ddiv；jar `build/libs/ecoaegtnh.jar`（95863 B）SHA256 = `54554C897FA6FA37E2E7DD6FD248F5CC4D473E205CCE149D2EF9CB16B1435A12`。
- 队长侧建议实测：ECO 16M 盘放入 ME 驱动器后，与 AE2U 16384k Xtreme 盘并排对比网格待机功耗（AE 网络能量页/控制器）——两边单盘贡献一致（4.0）；E-存储阵列成型后 ME 总线 idle 功耗 = 64 + 4×盘数（16M）。

## t65 IO 状态改悬停 tooltip（量子计算机参数 LED 模式，engineer-content，用户要求"IO 状态应该是鼠标放到 io 方格上显示，详细的查看源码"）
- **参考（量子计算机参数 LED 机制，逐环研读）**：`MTEQuantumComputer`（tectech/thing/metaTileEntity/multi/MTEQuantumComputer.java）本身只用 TecTech parametrization（Parameters.Group.makeInParameter/makeOutParameter + LedStatus）声明参数；真正渲染 LED 的是 `TTMultiblockBase.addUIWidgets`（MUI1）：底部画 `PICTURE_PARAMETER_BLANK`（166×12）参数条 → 每参数 `addParameterLED(builder, hatch, param, input)`——**模式 = 状态纹理 widget + `dynamicTooltip(() -> List<String>)` + FakeSyncWidget `.setOnClientUpdate(w -> notifyTooltipChange())`**（tooltip 是客户端悬停时求值的 Supplier，读到新同步值即刷新）；tooltip 文本构造同 `getFullLedDescriptionIn/Out`：WHITE 标题行 + 彩色值行（StatCollector.translateToLocalFormatted + EnumChatFormatting）。LED 贴图：`TecTechUITextures.PICTURE_PARAMETER_{BLUE,CYAN,GREEN,ORANGE,RED}[i]`（158×4 贴图按 i*8 切出的 6×4 小块）与 `PICTURE_PARAMETER_GRAY`（独立 6×4，实测 jar 内 PNG 尺寸确认）。MUI1 1.3.4 API 实证：`Widget.dynamicTooltip(Supplier<List<String>>)`、`Widget.notifyTooltipChange()`、`FakeSyncWidget.setOnClientUpdate(Consumer<T>)`、`DrawableWidget.setDrawable(Supplier<IDrawable>)` 均存在。
- **改动（MTEEcoStorageArray.addUIWidgets 底部参数条，替换 t58 的 ioStatusLine 常驻文本行）**：参数条背景保留，其上放 **3 个 IO LED 方格**（6×4，位处参数条 LED 槽位 0/9/19：x=12/84/164，y=97 或 177（无物品栏）），每个 `DrawableWidget.setDrawable(Supplier<IDrawable>)` 按状态选色 + `dynamicTooltip` 悬停显示：
  1. **ME 总线格**：绿=已连接 / 红=成型但未连接 / 灰=无成型结构；tooltip：标题"ME 总线："+ 状态行（已连接/未连接/缺失）+ 结构行（成型/未成型）。
  2. **盘位格**：绿=盘位数>0 / 灰=0；tooltip：标题"盘位："+ 盘位数 + 列数（读 syncDriveCount/syncDriveColumnLength）。
  3. **能量格**：绿=存量>0 / 灰=空；tooltip：标题"能量："+ `formatCompact(当前) / formatCompact(上限) (百分比%)`。
  每个格子挂对应 FakeSyncWidget（isMEBusConnected/mMachine、driveBays.size/getDriveColumnLength、getEnergyStored/getMaxEnergyStore）并 `.setOnClientUpdate(val -> led.notifyTooltipChange())`——数值更新时 tooltip 即时刷新；**条上不再常驻任何文本**（ioStatusLine 方法已删除）。
- **偏离说明**：量子计算机的 LED 点击会打开参数配置弹窗（`createLEDConfigurationWindow`，读 parametrization 数组）——本机非 IParametrized，点击弹窗会越界崩溃（t54 已注），故只保留"悬停 tooltip"部分、无点击动作（与任务指引"非参数化机器可用带 tooltip 的 widget 实现悬停提示"一致）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t65.log）；javap 确认：类内含 meBusTooltip/drivesTooltip/energyTooltip、无 ioStatusLine；字节码含 dynamicTooltip×3、setOnClientUpdate×6（Boolean/Integer/Long 各 2）、notifyTooltipChange、PICTURE_PARAMETER_GREEN 引用；jar `build/libs/ecoaegtnh.jar`（96981 B）SHA256 = `FC32C8001439CDB911D74E42A114F09B0906AEA634CFFB6E26F1A4BB3ABCD5C9`。
- 队长侧建议实测：成型阵列右键控制器 → 底部参数条上三个 6×4 小方格（左=ME 总线、中=盘位、右=能量），**鼠标悬停各自显示详情 tooltip**（ME 总线：已连接/未连接/缺失+结构状态；盘位：盘位数/列数；能量：当前/上限/百分比），不悬停时条上无文字；拆 ME 总线/关机 → ME 总线格变红、悬停"未连接"；未成型 → 灰、悬停"缺失"；文字屏五行统计不变。

## t66 ECO 盘禁止放入 ME 驱动器/箱子（仅限 ECO 盘位；engineer-content，用户明确"应该是不可放入"）
- **需求**：ECO 盘**不可**放入 ME 驱动器/ME 箱子（t61 确认当时可放入），只能在 ECO 阵列盘位使用。
- **约束排查（为什么不能简单改 flag）**：①ME 驱动器/箱子所有放入路径（TileDrive/TileChest 的槽位过滤、SlotRestrictedInput.STORAGE_CELLS、漏斗走 isItemValidForSlot）最终都汇聚到 `CellRegistry.isCellHandled`，其 IStorageCell 兜底是**无条件**的（998 源码 + 1000 jar 字节码双重确认：`CellInventory.isCell` → `IStorageCell.isStorageCell(stack)`）；②`IStorageCell` 接口（1000 jar 全方法清单）**没有任何"禁止入驱动器"标志**；③`isStorageCell()` 不能返回 false——`CellInventory` 基类构造器**强制要求** `cellType.isStorageCell(cellItem)==true`（否则抛 AppEngException；998 源码 + 1000 构造器字节码 L157-174 确认），而 ECO 盘位正是通过 `EcoStorageCellInventory extends CellInventory` 构造 → 改 false 会废掉 ECO 盘位；移除 IStorageCell 同理（instanceof 检查 → cellType=null → 抛异常）。④重写自研 inventory（不继承 CellInventory）可行但涉及整套字节数学/NBT/类型跟踪（t49 对齐）重写，回归风险高。
- **实现（两个放置门上的 mixin，GTNH UniMixins 标准机制）**：
  1. **`MixinSlotRestrictedInput`**（`@Mixin(appeng.container.slot.SlotRestrictedInput)`，@Inject `isItemValid`=SRG func_75214_a，HEAD+cancellable）：栈为 `ItemEcoStorageCell` 实例 → 返回 false。覆盖**所有 GUI 放入**：ME 驱动器槽、ME 箱子槽、shift 点击/双击（客户端+服务端容器都过 Slot.isItemValid）。
  2. **`MixinTileDrive`**（`@Mixin(appeng.tile.storage.TileDrive)`，@Inject `func_94041_b`=isItemValidForSlot，HEAD+cancellable，**remap=false**——AE2U release jar 把该 vanilla 覆写重混淆为 func_94041_b，AP 无 AE2U 映射故用字面运行名）：栈为 ECO 盘 → 返回 false。覆盖**自动化路径**（漏斗/管道直插驱动器）。
  - ECO 盘位路径完全不经过这两处（`TileEcoStorageDrive.buildHandler/interactWithCell/setInventorySlotContents` 直调我们的 `EcoStorageCellHandler`，从不查 AE2U 槽位/isCellHandled）→ **盘位功能零影响**（含 t62 成型门控/放盘聊天提示等全部保持）。
- **构建接线（gtnhgradle 1.0.27 内置 MixinModule）**：gradle.properties 模板块启用 `usesMixins = true` + `mixinsPackage = mixin`（注意模板末尾已有 `usesMixins = false`/`mixinsPackage =`——.properties 同键后者覆盖，必须改模板行而非新增）；`./gradlew generateAssets` 生成 `src/main/resources/mixins.ecoaegtnh.json`（package=ecoaegtnh.mixin、refmap=mixins.ecoaegtnh.refmap.json、mixins=[] 包扫描）；unimixins 0.1.17 dev 依赖 + 注解处理器 + refmap + reobf 由约定自动配置。坑：mixin 目标若是 **AE2U 自有方法**（如 isCellHandled）AP 报 "Unable to locate obfuscation mapping"（mcp-srg 只覆盖 vanilla）；**vanilla 覆写方法**若映射所有者不在 srg（IInventory 接口不在表内）同样报错——解决：选映射所有者存在的 vanilla 方法（Slot.isItemValid ? 自动映射到 func_75214_a）或对 SRG 名用 remap=false（func_94041_b）。refmap 最终内容：Slot 映射 `isItemValid→SlotRestrictedInput;func_75214_a`（searge 段），TileDrive 无条目（字面名）。
- **t61 自检日志语义更新**：isCellHandled 仍为 true（handler 已注册、属正常）；实际拒绝发生在槽位层（mixin），自检文本注明；另加 "ECO bay inventory non-null" 校验盘位路径。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t66.log）；jar 内确认：MixinSlotRestrictedInput.class/MixinTileDrive.class/mixins.ecoaegtnh.json/mixins.ecoaegtnh.refmap.json（searge 映射正确）、TileDrive mixin 注解含 func_94041_b+remap；jar `build/libs/ecoaegtnh.jar`（99895 B）SHA256 = `0E43D9C76F5846FAA5543FAB20BC7DAE5655E0EFA5A89915714AFAD221D306C0`。
- 队长侧建议实测：①把 ECO 16M 盘拖进 ME 驱动器槽 → **放不进（红 X/弹回）**；拖进 ME 箱子同理；②漏斗对 ME 驱动器注入 ECO 盘 → 被拒；③ECO 阵列盘位 shift+右键放/取 ECO 盘 → 完全正常（含 t62 成型门控与等级聊天提示）；④AE2U 原版盘进驱动器不受影响。

## t67 电容 2,000,000 AE 统一容量 + 可跨阵列共享能量（engineer-content，用户要求）
- **① 容量统一**：`TileEcoStorageCapacitance.CAPACITY_A/B/C`（10M/100M/1G）→ 单一 `CAPACITY = 2_000_000D`（常量值 javap 实测 2000000.0d）；`setCapacityByMeta(meta)` 保留签名但恒设 CAPACITY（meta 0/1/2 的方块/物品/配方全部保留，仅容量与 meta 脱钩）；`readFromNBT` 读 NBT 时**忽略旧 A/B/C 容量值、恒设 CAPACITY 并把 energyStored 钳制到 ≤2M**（旧存档自动迁移）。GUI 能量行（Energy: X/Y，formatCompact 聚合）自动反映新容量（如 3 电容 = 6M）。
- **② 电容豁免 t55 防共用（可跨阵列共享）**：`TileEcoStorageCapacitance` 改为 **owner 列表**：`onAssembled(controller)` 覆写——先 `owners.removeIf(o -> o != controller && !isOwnerAlive(o))`（t59 死 owner 释放语义按 owner 保留）→ 加入列表 → 恒返回 true（电容永不拒认领，多阵列可同时拥有同一批电容）；新增 `onDisassembled(MTEEcoStorageArray controller)` 只摘除本控制器（拆 A 阵列不影响 B 的共享认领），无 owner 时 assembled=false；no-arg `onDisassembled()` 覆写为清空全部（防御兜底）；`isAssembled()` 覆写 = !owners.isEmpty()。
- **基类配套**：`TileEcoStoragePart` 新增 `onDisassembled(MTEEcoStorageArray)` 默认实现（委托 no-arg，驱动盘位/ME 总线保持单 owner 语义不变）与 `isOwnerAlive(MTEEcoStorageArray)`（t59 检查逻辑抽成可复用）；`MTEEcoStorageArray` 三处电容拆解调用（scanStructureVolume 的 prevCaps 循环 + disassembleAll 的 energyCellsMin/prevCaps 循环）改 `onDisassembled(this)`——驱动/总线调用点不变。
- **共享聚合**：每个阵列的 energyCellsMin/Max 各自持有同一批电容对象；`getEnergyStored/getMaxEnergyStore`（Σ）与 `injectPower/extractPower`（最空/最满优先堆）天然按共享池工作——每个控制器 GUI/ME 总线都看到同一批电容的总能量（任务注意点满足）。**防共用保持**：驱动盘位/ME 总线的 t55 单 owner 拒绝逻辑未动（drive.onAssembled/bus.onAssembled 仍走基类）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t67.log）；javap 确认：TileEcoStorageCapacitance 含 `CAPACITY=2000000.0d`、owners 列表、onAssembled/onDisassembled(MTEEcoStorageArray)/no-arg onDisassembled/isAssembled；MTE 字节码含 `TileEcoStorageCapacitance.onDisassembled:(LMTEEcoStorageArray;)V`×3（驱动/总线仍 no-arg）；jar `build/libs/ecoaegtnh.jar`（100720 B）SHA256 = `3CA86FB875BFDE8BB3215B17643D70D5FE1842D926AD6C34EF9F68F30605A524`。
- 队长侧建议实测：①单电容 GUI 能量上限显示 2,000,000（3 电容=6M）；②两个相邻阵列共享同一批电容：A 成型→注入能量→B 成型后 GUI 能量与 A 相同（同一池）；B 抽出→A 同步减少；③拆 A 控制器→B 仍保有电容（能量不丢），B 拆解→电容 assembled 释放；④驱动盘位/ME 总线防共用不回归（重叠只归先成型阵列）。

## t68 存储盘容量变回旧版 ECO 设计（byteMultiplier，engineer-content，用户明确"最后变回旧版eco的容量"）
- **背景**：t49 把容量对齐 AE2U/GTNH-AE（totalBytes=MB×10242、perType=totalBytes/128、ITEM 类型 63），用户觉"不耐用"，现恢复旧版 byteMultiplier 设计。
- **① totalBytes**：`millionBytes × 1000 × 1024`（16M=16,384,000；javap：`ldc2 1000l`×`1024l`）。
- **② perType**：`byteMultiplier × 1024`（16M→4096、64M→16384、256M→65536；javap `byteMultiplier × sipush 1024`）；恢复 `byteMultiplier` 字段（= millionBytes/4：16M→4、64M→16、256M→64，A/B/C 映射）与 `getByteMultiplier()`。
- **③ 盘内字节公式**（`EcoStorageCellInventory` 恢复 t49 删除的 4 个覆写，对照 1.12.2 参考 `EStorageCellInventory` 逐行）：
  - `getUsedBytes = types×getBytesPerType + (storedCount+unused) ÷ (typeWeight×byteMultiplier)`
  - `getRemainingItemCount = getFreeBytes×(typeWeight×byteMultiplier) + unused`（>0 钳）
  - `getUnusedItemCount = (typeWeight×byteMultiplier) ? storedCount%(typeWeight×byteMultiplier)`
  - `typeWeight = getStackType().getAmountPerByte()`（物品=8；流体=2048 与旧一致）
  - 验证数学：16M 盘 1 种×1000 个 → weight=8×4=32、unused=32?(1000%32=8)=24、used=1×4096+(1000+24)/32=**4128 B ≈ 4.1KB** ?（任务验收值）。
- **④ 类型数**：`ItemEcoStorageCellItem.MAX_TYPES` 63→**315**（javap 常量 315）；`EcoStorageCellInventory.getTotalItemTypes()` 覆写返回 item 声明值（315/25）——绕过 AE2U 基类 63 钳制（基类仅在 getTotalItemTypes 处限类型数：canHoldNewItem→getRemainingItemTypes→baseOnTotal；loadCellStacks 全量读 NBT、saveChanges 清理与 maxTypes 无关 → 315 真实可用且无数据丢失；sticky 卡 restrictionTypes 私有且 ECO 盘位不设，未镜像）。流体 25 / 源质 60/80/100 本就未改（源质 inventory 独立类不触碰）。
- **⑤ idleDrain**：**保留 4.0**（t63 值）——`getIdleDrain() = millionBytes / 4.0`（16M→4.0/64M→16.0/256M→64.0，javap `millionBytes / 4.0d`），与 t68 的 1000 制 totalBytes 解耦（若用旧 totalBytes/1MiB 会得 ≈15.625）；注释注明待 t69 耗电方案定夺。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t68.log）；javap 确认：MAX_TYPES=315、getTotalBytes=MB×1000×1024、getBytesPerType=byteMultiplier×1024、getIdleDrain=MB/4.0、EcoStorageCellInventory 含 getUsedBytes/getRemainingItemCount/getUnusedItemCount/getTotalItemTypes 四个覆写；t66 mixin 类与 refmap 仍在 jar；jar `build/libs/ecoaegtnh.jar`（101293 B）SHA256 = `8D3F3EE987724D53230CB695A6F808814A0910242C22522904333BBE39F66A30`。
- 队长侧建议实测（任务验收）：16M 盘放 1 种物品 1000 个 → 悬停 tooltip "Used: 4.1K / 16.4M bytes" + "Types: 1 / 315"；与 GTNH-AE 16384k 盘对比耐用度（ECO 每类型 4096B vs AE2U 131072B——旧版设计 32 倍耐用）；64M/256M 同理（perType 16384/65536）。

## t69 耗电方案 B+C（等级基础 + 已安装盘位计费，engineer-content，用户选定方案）
- **公式**（替换 `recalculateEnergyUsage()` 的 `64 + Σ cell.getIdleDrain()` 方案 D）：
  `idlePowerUsage = tierBase + 0.5 × installedCellCount + Σ idleDrain(已装 ECO 盘)`
- **实现**：`MTEEcoStorageArray.recalculateEnergyUsage()` 重写——①`tierBaseForPower()`：TIER_A/L4=2.0、TIER_B/L6=4.0、TIER_C/L9=8.0（javap：tier==2→8.0、tier==1→4.0、else 2.0）；②遍历 driveBays，`getCellStack()!=null` 且为 `ItemEcoStorageCell` 才计（installedCellCount++ 并累加 getIdleDrain）——空槽/非 ECO 盘（防御）不计；③`idleDrain += installedCells × 0.5`（javap：i2d × ldc2 0.5d dmul）；④meBus!=null 时 `setIdlePowerUsage`（未成型安全跳过，原逻辑保持）。
- **重算时机（双路径）**：①成型——scanStructureVolume 末尾已有 recalculateEnergyUsage()；②拆盘/放盘——`TileEcoStorageDrive.onCellChanged()` 在 `controller.getMEBus().forceCellArrayUpdate()` 旁**补 `controller.recalculateEnergyUsage()`**（队长 t69 线索；controller==null 未成型时判空保持）→ 拆盘后耗电立刻下降。
- **示例核对（用户口径）**：L6、6 盘位装 4 个 16M → 4.0 + 4×0.5 + 4×4.0 = **22 AE/t**；满 6 个 → 4.0+3.0+24.0 = 31 AE/t。tierBase 表：L4=2.0/L6=4.0/L9=8.0。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t69.log）；javap：recalculateEnergyUsage 调 tierBaseForPower + installedCells×0.5 累加 + setIdlePowerUsage；TileEcoStorageDrive.onCellChanged 调 controller.recalculateEnergyUsage；jar `build/libs/ecoaegtnh.jar`（101468 B）SHA256 = `5EEF5AB7A7A71F38BB9384B98911295BE2B0829BD9A751CC8A7DF652BA7E49E3`。
- **文档事故（REVIEW.md）**：更新 REVIEW.md 时一次损坏的 PowerShell 写操作把原文件覆盖为空（3 B），已按会话内保留的头部逐轮记录重建（含全部 12 轮复验记录 + t69 记录 + 事故说明），原 §1.1–§1.17 详细章节待 reviewer 重新生成补全。
- 队长侧建议实测：①L6、6 盘位装 4 个 16M → AE 网络能耗页面显示阵列 idle ≈22 AE/t；拆掉 1 个盘 → 立刻降为 22?0.5?4.0=17.5 AE/t；放回恢复；②L4 空阵列（无盘）→ 2.0 AE/t；L9 满 6 盘 → 8.0+3.0+24.0=35 AE/t；③未成型（无 ME 总线）不崩、无耗电。

## t71 修复 P2-15：mixins.ecoaegtnh.json 列表为空致 t66 禁入功能无效（engineer-content，reviewer t70 复验发现）
- **根因**：t66 用 gtnhgradle `generateAssets` 生成的 `mixins.ecoaegtnh.json` 其 `mixins` 数组为 `[]`（生成器模板本就输出空列表，不扫描包）；而 mixin 0.8 核心**只加载配置中显式列出的类**（`package` 字段不触发自动扫描，已解包服务器 unimixins 0.3.1 的 MixinConfig 确认其 mixinPackage 仅用于相对名解析）→ 两个 mixin 类（MixinSlotRestrictedInput/MixinTileDrive）运行时从未被加载 → t66 的"ECO 盘禁入 ME 驱动器/箱子"实际无效。
- **修复**：手动把两个全类名填入 `mixins` 数组：`["ecoaegtnh.mixin.MixinSlotRestrictedInput", "ecoaegtnh.mixin.MixinTileDrive"]`（json 文件已存在 → generateAssets 的 onlyIf（文件不存在才生成）不会覆盖，手工编辑持久有效）。mixin 类/映射零改动：Slot 的 refmap 映射（isItemValid→SlotRestrictedInput;func_75214_a，searge 段）与 TileDrive 的 `remap=false` 字面 func_94041_b 保持。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t71.log）；解压 jar 确认：mixins.ecoaegtnh.json 含两个类名、ecoaegtnh/mixin/MixinSlotRestrictedInput.class + MixinTileDrive.class 在包内、refmap 内容正确、@Mixin 注解目标（appeng/container/slot/SlotRestrictedInput、appeng/tile/storage/TileDrive）正确；jar `build/libs/ecoaegtnh.jar`（101509 B）SHA256 = `CC0242A94445A84618B5F871A7F6432890788F44D35A862BFA75AF8105117BA4`。
- 队长侧装服后验证（上次缺失的环节）：服务器日志应出现 mixin 加载/应用行（unimixins/mixinbooterlegacy：加载 mixins.ecoaegtnh.json、应用 MixinSlotRestrictedInput/MixinTileDrive——目标类在首次打开 ME 驱动器/箱子 GUI 时加载并打应用日志）；游戏内实测 ECO 盘拖进 ME 驱动器/箱子被拒（红 X/弹回）、ECO 盘位正常放取、AE2U 原版盘不受影响。

## t73 IO LED 优化：前面连续排列 + 总盘/分类数/类型%/字节% tooltip（engineer-content，用户实测反馈第 4 点）
- **用户需求**：①3 个 LED 从参数条**起始位置连续排列**（不再分散到槽位 0/9/19）；②悬停显示：总盘、物品/流体/源质各放几个、每类类型使用百分比、每类字节使用百分比。
- **布局**：3 个 6×4 LED 连续排在参数条左侧槽位 0/1/2（x=12/20/28，y=97 或 177 无物品栏），分别代表**物品盘/流体盘/源质盘**（该类有盘=绿、无=灰）。
- **tooltip（每个 LED 相同全文摘要，用户可读性设计）**：
  ```
  总盘: 5
  物品盘: 2  类型 2/315 (0.6%)  字节 4.1K/16.4M (0.03%)
  流体盘: 1  类型 1/25 (4.0%)   字节 1.2K/16.4M (0.01%)
  源质盘: 2  类型 2/63 (3.2%)   字节 0B/32.8M (0%)
  ME 总线: 已连接
  能量: 2.4M / 6M (40%)
  ```
  （源质类型分母为 AE2U 钳制 63——EcoStorageCellInventoryEssentia 未覆写 getTotalItemTypes，与 t68 决定一致；t65 的 ME 总线/能量状态行保留在 tooltip 尾部，功能不丢。）
- **数据/同步**：新增 15 个 sync 字段（sync{Item,Fluid,Essentia}{CellCount,StoredTypes,TotalTypes,UsedBytes,TotalBytes}）；服务端 supplier 遍历 driveBays 按 `ItemEcoStorageCellItem/Fluid/Essentia` 子类分类（`cellStacksOf`），经 `EcoStorageCellHandler.INSTANCE.getCellInventory(stack, null, type)`（只读模式，同物品 tooltip）聚合 `sumStat(Stat.{STORED_TYPES,TOTAL_TYPES,USED_BYTES,TOTAL_BYTES})`；每个 FakeSyncWidget `setOnClientUpdate -> notifyTooltipChange`；isMEBusConnected syncer 挂物品 LED 链（t65 移除后补回）。客户端 `cellStatsTooltip()` 读 sync 字段格式化（`percentOf` 整数百分比、`formatCompact` 字节缩写）。
- **新 lang 键（en/zh）**：total_cells/item_cells/fluid_cells/essentia_cells/types/bytes。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t73.log）；javap 确认 cellStatsTooltip/cellFamilyLine/cellStacksOf/sumStat(Stat 枚举)/15 个 sync 字段在类内、旧 meBusTooltip/drivesTooltip/energyTooltip 已删；jar 内 en/zh lang 各 6 新键；t71 mixin json 仍含两个类名；jar `build/libs/ecoaegtnh.jar`（106168 B）SHA256 = `98F8172A7CE83972DDBD12CC453EE223DCA3E2100CED08F6EE58FFD8617DC08C`。
- 队长侧建议实测：成型后右键控制器 → 参数条左侧三个相邻 LED（物品/流体/源质）；悬停任一 LED 显示总盘+三类计数+类型%/字节%+ME 总线/能量；放 1 物品盘 1 流体盘 1 源质盘 → 三 LED 全绿、tooltip 计数正确；拆盘 → LED 变色、tooltip 即时更新（sync 刷新）。

## t74 排查修复：电容不存电（网络有能量源但 Energy 恒 0；engineer-content，用户实测）
- **排查结论（代码链路逐环实证，与可用的 1.12.2 参考逐行对照）**：
  1. **AE2U 网格充电机制**（998 EnergyGridCache 源码）：能量接收器等 provider 调用 `grid.injectPower` → `EnergyGridCache.injectAEPower(MODULATE)` → **先充 requesters**（`getFirstRequester` 循环 `node.injectAEPower(amt, MODULATE)`，返回值>0 才把该 storage 移出 requesters）；`getEnergyDemand` 把 requester 的容量缺口计入需求 → provider 按需注入。**机制正确**。
  2. **我们注册为 power storage 的链路**：`GridNode.getMachine()` → `AENetworkProxy.getMachine()` → 返回宿主 tile（gp=this）→ `machine instanceof IAEPowerStorage` ?；`EnergyGridCache.addNode` 对 `isAEPublicPowerStorage() && current<max && flow≠READ` 的节点加入 **requesters**（我们 current=0<6M、READ_WRITE → 加入 ?）。`isInfinite()` 接口有 default false，无问题。
  3. **我们的实现**（TileEcoStorageMEBus.injectAEPower/extractAEPower + MTE.injectPower/extractPower + 电容 inject/extract）与 1.12.2 参考 EStorageMEChannel/EStorageController 逐行一致（含 `amt<0.000001 return 0` 与 PROVIDE_POWER/REQUEST_POWER 事件条件）；服务器日志实证 t71 mixin 已生效（"Mixing MixinTileDrive/MixinSlotRestrictedInput ... into appeng.*"），无任何运行时异常。
  4. **结论**：链路本身正确，"恒 0"最可能是**网络无盈余**（能量源输出 ≤ 全网需求含阵列自身 22 AE/t 待机；或其它 requester 如控制器电池先吸走），或总线未连到能量源所在网格段。**代码层发现的 3 个真实薄弱点已修**：
- **修复 ①（关键）requester 掉队自愈**：AE2U 网格在 `injectAEPower` 返回 >0（拒绝，如瞬时 !operational）时把 storage **移出 requesters**，而重新加入只有两条路（节点重连 / 离开满电时 post REQUEST_POWER）——若从未充满过则**永不重加 → 永久充不进**。修复：`stateChange(MENetworkPowerStatusChange)`（网络电源状态翻转事件，低频）里 `isOperational()` 时 post `REQUEST_POWER` 重新入队。
- **修复 ② 诊断日志**：MEBus.injectAEPower/extractAEPower 每 5 秒节流打一行 `MEBus injectAEPower amt=.. result=.. mode=.. stored=../..`——下次装服直接可见网格是否调用、电容接受多少（判别"无盈余"vs"未注入"）。
- **修复 ③ GUI 精度**：能量同步 `(long) getEnergyStored()` 截断改为 `Math.round`——微量充能（<1 AE）也能显示，避免"恒 0"误判。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t74.log）；javap 确认 logPower 方法+两处调用、stateChange 含 REQUEST_POWER、能量 syncer 用 Math.round；jar `build/libs/ecoaegtnh.jar`（106757 B）SHA256 = `5E169EFF74DF0E0BBB2216133278F10CD619C486A9B10CF8B75821361BB84A86`。
- 队长侧装服后判别流程：①看服务器日志是否出现 `MEBus injectAEPower` 行——**没有**：网格没把阵列当 requester（罕见，需再查节点注册）或能量源根本没注入（检查能量接收器是否连到总线同一网格）；**有且 result 下降**：充上了（GUI 应涨）；**有但 result=amt（全拒）**：isOperational 瞬时 false（查机器电源开关）或电容满。②GUI Energy 用新 jar 后 <1 AE 也会显示。③若确属"无盈余"：需用户增大能量源输出或减少负载，属网络设计而非代码。

## t76 新盘分级（k级/M级/大M级）+ 等级重映射 + 网络工具容量上报修复（engineer-content，用户确认方案）
- **① 新分级（27 种盘 = 9 尺寸 × 物品/流体/源质）**：`CellSize` 枚举（新文件）——L4(TIER_A)：256k/1024k/4096k；L6(TIER_B)：16M/64M/256M（原 L4/L6/L9 → 全部 L6）；L9(TIER_C)：1024M/4096M/16384M。容量：k 级 totalBytes=value×1024（256k=262,144），M 级保持 ×1000×1024（16M=16,384,000 … 16384M=16,777,216,000）；层级严格递增。perType=byteMultiplier×1024（k:1/4/16、M:4/16/64/256/1024/4096=value/256 或 value/4）。类型数：物品 315（t68 保持）、流体 25、源质按档 60/80/100。idleDrain：M 级=value/4（16M→4.0）；k 级=value/4000 下限 0.5（256k/1024k→0.5、4096k≈1.02）。
- **② 等级重映射**：`ItemEcoStorageCell.getTierRequired()`（k→A、16M..256M→B、1024M+→C）；`TileEcoStorageDrive.requiredTier` 改按 getTierRequired；tooltip l6.tip/l9.tip 按 tier（16M 现需 L6——旧存档 16M 在 L4 阵列会成型失败，t62 结构错误文案"需要 L6 控制器"自动生效）；t62 成型门控标签逻辑（L4/L6/L9 三分支）已兼容。
- **③ 注册/配方**：`RegistryItems` 改 3 个 `EnumMap<CellSize,...>` 循环注册 27 个物品；配方 27 条（物品/流体 housing+CertusQuartz、源质 housing+源质瓶，LV..ZPM 逐档；修复旧 64M/256M 配方因 CertusQuartzCharged init 时 null 被跳过的历史问题——改用 CertusQuartz 全部可注册）；lang 27×2 键；WAILA 容量改显示尺寸标签（k 级不再显示 0MB）。
- **④ 网络工具容量上报（t75 0 B/0 B 根因修复）**：AE2U `GridStorageCache.resetCellInfo()`（ME 终端/网络信息的"cells"视图数据源）只处理 TileDrive/TileChest 提供者——我们的 ME 总线（自定义 ICellContainer）从未注册 → ECO 盘显示 0 B/0 B。修复：`MixinGridStorageCache`（t66 同款机制，@Shadow private activeCellProviders/updateCellsStatusFromRegistry + @Inject resetCellInfo TAIL，remap=false 字面名——AE2U 私有方法 release jar 保持 MCP 名已实证）：遍历 activeCellProviders 找我们的总线，对每个 drive bay 的 cell handler（getInternal() instanceof ICellCacheRegistry）调 updateCellsStatusFromRegistry。mixins.ecoaegtnh.json 加入第 3 个 mixin。
- **⑤ 待队长**：27 张盘贴图 `assets/ecoaegtnh/textures/items/estorage_cell_{item,fluid,essentia}_{256k,1024k,4096k,16m,64m,256m,1024m,4096m,16384m}.png`（分级配色：k 蓝、16M 档紫、大 M 红；类型色保持：物品一色/流体一色/源质一色）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t76.log）；javap：CellSize 常量正确（262144/1048576/4194304/16384000/65536000/…/16777216000）、mixin json 三类、MixinGridStorageCache.class 在 jar、lang 27+27 键；jar `build/libs/ecoaegtnh.jar`（111007 B）SHA256 = `53612BFC6CED5DE4FD5A895597A130BF1BA430E0750F753DB285D20978055123`。
- 队长侧装服实测：①9 种物品盘可合成/创造拿取（27 种含流体/源质）；②L4 放 k 级正常、放 16M 被拒（提示需 L6）、L6 放 16M/64M/256M 正常、L9 放大 M 盘；③网络工具"网络信息"cells 视图显示 ECO 阵列容量（非 0 B/0 B——日志应有 "Mixing MixinGridStorageCache ... into appeng.me.cache.GridStorageCache"）；④盘 tooltip 类型数 315（物品）。

## t77 IO 条 4 格分离 LED（状态/物品/流体/源质，分类型彩色；engineer-content，用户选定方案 A）
- **现状问题**：t73 的 3 个 LED（物品/流体/源质）悬停都显示同一个 cellStatsTooltip() 全文摘要——3 格内容完全相同（用户反馈）。
- **新布局（4 格，槽位 0/1/2/3，x=12/20/28/36，6×4）**：
  1. **状态 LED**：绿=ME 总线已连接且成型 / 红=成型未连接 / 灰=未成型（t65 语义保持）；tooltip 专属=ME 总线状态 + 结构状态 + 能量（如 "ME总线: 已连接 / 结构: 成型 / 能量: 2.4M/6M (40%)"）；sync 字段 syncMeBusConnected/syncStructureValid/syncEnergyStored/syncEnergyMax。
  2. **物品 LED**：**ORANGE**（最接近物品盘金色纹理；TecTechUITextures 无金色，用 ORANGE 并注释）；tooltip=物品盘数 + 类型 X/315 (P%) + 字节 X/Y (P%)（syncItem*）。
  3. **流体 LED**：**BLUE**（流体盘蓝）；tooltip=流体盘数 + 类型 X/25 (P%) + 字节 X/Y (P%)（syncFluid*）。
  4. **源质 LED**：**RED**（无紫色纹理，RED 为最接近色并注释）；tooltip=源质盘数 + 类型 X/63 (P%) + 字节 X/Y (P%)（syncEssentia*；分母 63 为 AE2U 钳制值）。
- **实现**：删 cellStatsTooltip/cellFamilyLine；新增 statusTooltip + itemTooltip/fluidTooltip/essentiaTooltip（共用 cellTooltip(nameKey,...) 单类摘要辅助）；每格 dynamicTooltip + FakeSyncWidget setOnClientUpdate → notifyTooltipChange（t65/t73 机制不变）；状态格额外挂能量 LongSyncer×2（Math.round 精度，t74）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t77.log）；javap：statusTooltip/itemTooltip/fluidTooltip/essentiaTooltip/cellTooltip 在类内、旧 cellStatsTooltip/cellFamilyLine 已删、LED 色引用 ORANGE/BLUE/RED/GRAY/GREEN 齐全；jar `build/libs/ecoaegtnh.jar`（119493 B）SHA256 = `524ABD77B6545C597A44FE3445E561D8AF52E16989DA710A65422182564AF9BB`。
- 队长侧装服实测：成型后右键控制器 → 参数条左侧 4 个相邻 LED（状态/物品/流体/源质），悬停各自显示**不同**内容（状态格=总线+结构+能量；物品格=物品统计；流体格=流体统计；源质格=源质统计），颜色区分（绿/橙/蓝/红）；放 1 物品盘+1 流体盘+1 源质盘 → 物品/流体/源质 LED 点亮对应色；拆 ME 总线 → 状态格变红悬停"未连接"；未成型 → 灰。

## t79 GUI：隐藏软锤开启提示 + 删除能量百分比条（engineer-content，用户实测反馈）
- **① 隐藏"软锤开启"提示**：根因=ECO 纯 AE 机器无配方从不"运行"（checkProcessing_EM→NONE、isActive 恒 false）→ 基类 `MTEMultiBlockBase.drawTexts`（L3907-3924）在 `showMachineStatusInGUI()`（L4075，public 非 final，默认 true）为 true 时把 `gt.interact.desc.mb.idle.1/2/3` 三行常驻。修复：MTEEcoStorageArray 覆写 `showMachineStatusInGUI()` 返回 **false**（先例 kekztech MTELapotronicSuperCapacitor:1110、tectech MTEActiveTransformer:220）；已核实 TTMultiblockBase 无 showMachineStatusInGUI 覆写/无自带 idle 提示/drawTexts 继承基类——单覆写即可隐藏三行 + running 行（我们的 drawTexts 先调 super.drawTexts，基类分支读到该覆写）。
- **② 删除能量百分比条**：drawTexts 末段 `TextWidget.dynamicString(() -> energyBarLine(...))`（含 2 个 LongSyncer）整块删除；`energyBarLine()` 私有方法删除（无其它引用）；保留上方 "Energy: X / Y" 数值文字行（用户只要去掉百分比条）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t79.log）；javap：`showMachineStatusInGUI()` 覆写在类内、`energyBarLine` 已无引用/方法；jar `build/libs/ecoaegtnh.jar`（118979 B）SHA256 = `E37A3483EAB85B709149D68A7114C42DCF48228334B764F1738C4FBD7A46A0DB`。
- 队长侧装服实测：右键控制器 → 文字屏**无**"如果机器并未开始运行/请用软锤右键/以(重新)开启机器"三行、**无**能量百分比条（████?? 行）；Structure/Drives/Columns/Energy 数值行正常（Energy: X/Y 保留）；底部 IO 4 LED（状态/物品/流体/源质）不变。

## t81 修复网络工具存储统计混叠（getStorageChannel 未覆写致流体算进物品栏；engineer-content，用户实测）
- **根因（队长定位 + 实证）**：`GridStorageCache.updateCellsStatusFromRegistry`（L439-469）按 `iccr.getCellType()` 分组（item/fluid/essentia 三栏）；`CellInventoryHandler.getCellType()` = `getStorageChannel() == FLUIDS ? FLUID : ITEM`（L163-165）；`ICellCacheRegistry.getStorageChannel()` **默认 ITEMS**（L25-27）；AE2U 流体盘用 `FluidCellInventoryHandler` 覆写 getStorageChannel→FLUIDS（L55-57）。我们的 `EcoStorageCellInventoryHandler` 未覆写 → 物品盘/流体盘都返回 ITEMS → getCellType 恒 ITEM → **流体盘被统计进物品栏、物品容量被混算**。源质盘走 `EcoStorageCellInventoryEssentiaHandler`（覆写 getCellType→ESSENTIA）→ 源质容量/类型统计正常。
- **修复**：`EcoStorageCellInventoryHandler` 覆写 `getStorageChannel()`：构造器保存 `handlerType`，`handlerType == FLUID_STACK_TYPE ? StorageChannel.FLUIDS : StorageChannel.ITEMS`（与 FluidCellInventoryHandler 同款写法；javap 确认 if_acmpne→FLUIDS/ITEMS）。
- **复核 MixinGridStorageCache 无重复计数**：每个盘位对 `AEStackTypeRegistry.getAllTypes()` 迭代时，`TileEcoStorageDrive.getHandler(type)` 底层 `EcoStorageCellHandler.getCellInventory` 有 `cell.getStackType() != type → null` 门控 → 物品盘仅 ITEM 非空、流体盘仅 FLUID 非空、源质盘仅 ESSENTIA 非空 → 每盘恰一次 updateCellsStatusFromRegistry（无需 TileChest 式 break）。
- **源质"不显示元件"核查**：updateCellsStatusFromRegistry 的 ESSENTIA 分支含 `putItemStackIntoMap(essentiaCells, ...)`（998 源码 L460-466 实证），t76 mixin 已注册源质盘 → 网络工具 `ContainerNetworkStatus`（L305 `case ESSENTIA -> sg.getEssentiaCells()`）应显示；装服后验证（若仍缺，属渲染路径，另行排查）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（build-t81.log）；javap：getStorageChannel 覆写字节码=handlerType==FLUID_STACK_TYPE?FLUIDS:ITEMS；jar `build/libs/ecoaegtnh.jar`（119189 B）SHA256 = `8FB41A744442D8172F65B1934E8F80C4508390AB3F4013E4C48EA4A0B022A98D`。
- 队长侧装服实测：①高级网络工具 物品栏只含物品盘、流体栏只含流体盘、源质栏含源质盘；②各栏容量/类型与盘实际一致（16M 物品盘→物品栏 16.4M/315 等）；③IO 4 LED 与盘位交互无回归；④若源质元件仍不显示，抓网络工具截图再查渲染路径。

## t82 源质 LED 自绘紫色贴图（TecTech 无紫色 → 自制 6×4 紫块；engineer-content，用户选定）
- **背景**：t77 四格 IO LED 中源质格用 `PICTURE_PARAMETER_RED[0]`（红）——红色与源质盘的紫色视觉不符；TecTechUITextures 仅有 BLUE/CYAN/GREEN/ORANGE/RED/GRAY 六色，无紫色。
- **修复**：
  1. **自制贴图** `src/main/resources/assets/ecoaegtnh/textures/gui/picture/parameter_purple.png`：6×4 实心紫 RGB(176,108,255)（127 字节，PNG 头/尺寸已实证 6×4）。
  2. **注册** `MTEEcoStorageArray` 新增静态字段 `ECO_PARAMETER_PURPLE = UITexture.fullImage("ecoaegtnh", "gui/picture/parameter_purple")`（MUI1 1.3.4 `UITexture.fullImage(modid, path)` 解析 `assets/<modid>/textures/<path>.png`，javap 实证与 TecTech 同款 API）。
  3. **源质 LED**（t77 第 4 格）：setDrawable 的亮态由 `PICTURE_PARAMETER_RED[0]` 改为 `ECO_PARAMETER_PURPLE`；暗态仍 `PICTURE_PARAMETER_GRAY`；tooltip/同步机制不变。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（20s）；javap：`ECO_PARAMETER_PURPLE` 静态字段 + `<clinit>` 内 `UITexture.fullImage("ecoaegtnh","gui/picture/parameter_purple")` + lambda getstatic 引用在类内；jar 内 PNG 条目 `assets/ecoaegtnh/textures/gui/picture/parameter_purple.png`（127 B）；jar `build/libs/ecoaegtnh.jar`（119822 B）SHA256 = `6787677BAEB38270D84BC56E05BF0609F125EDC7C044D9F157BD2BFDF5D4F523`。
- 队长侧装服实测：成型 + 放 1 源质盘 → 第 4 格（x=36）LED 显示**紫色**，与物品格橙、流体格蓝、状态格绿/红明显区分；拆盘 → 变灰。

## t84 创造栏 ECO 物品首次卡顿优化（tooltip 不再重建 CellInventory；engineer-content，用户实测）
- **根因**：`ItemEcoStorageCell.addInformation → addStorageInformation` 每次悬停都走 `EcoStorageCellHandler.getCellInventory(stack, null, type)` → `new EcoStorageCellInventory`——AE2U `CellInventory` 构造器会：建 CellUpgrades/CellConfig 库存、`ItemStackNBT.get(o)`（缺 tag 时**创建** NBT）、`loadCellStacks()` 反序列化每个已存堆（readStack→loadStackFromNBT），必要时 saveChanges 修复。创造栏 27 个盘首次 hover 全部重建 → 只有 ECO 标签卡。
- **修复（方案 a：纯 NBT 读取，零 inventory 构建）**：addStorageInformation 改为直接读 CellInventory 自己写的 NBT 键 + t68 字节公式，显示逐位不变：
  - item/fluid：`tag.getShort("it")`（storedTypes）、`tag.getLong("ic")`（storedCount）——saveChanges 写入的键（EcoStorageCellInventory.ITEM_TYPE_TAG/ITEM_COUNT_TAG，常量内联进字节码）。
  - essentia：稀疏槽 `"Essentia#N"` 扫描（与 `EcoStorageCellInventoryEssentia.loadCellStacks` 同源：`AEEssentiaStack.writeToNBT` 把数量写进 `"Cnt"`），不建 inventory；分母/扫描上界沿用 AE2U 基类 63 钳制 `min(getTotalTypes,63)`（L4=60、L6/L9=63，与 t77 显示一致——原 inv.getTotalItemTypes 未覆写即钳制值）。
  - 字节公式：`weight = amountPerByte × byteMultiplier`（item/fluid；essentia 无 byteMultiplier，weight=amountPerByte=2，bytesPerType=0 覆写）→ `unused = count%weight → weight-div`，`usedBytes = types×bytesPerType + (count+unused)/weight`——与 EcoStorageCellInventory.getUsedBytes（t68）/基类数学完全一致。
  - 副作用更少：用 `stack.getTagCompound()` 且 null 安全，悬停不再创建 NBT。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（21s）；javap：addStorageInformation 字节码含 instanceof ItemEcoStorageCellEssentia、getTagCompound（SRG func_77978_p）、ldc "it"/"ic"、Essentia# 扫描、Math.min 钳制、getAmountPerByte/getBytesPerType/getTotalTypes/getTotalBytes；**无** EcoStorageCellHandler/getCellInventory 引用；jar `build/libs/ecoaegtnh.jar`（120228 B）SHA256 = `7D3D5F016D93803FBE085F90F3BD6BB8BCF620198F38DA36FF9B1221898A31E9`。
- 队长侧装服实测：①点开创造标签/首次悬停任意 ECO 盘——不再明显卡顿；②放/取物品前后悬停盘：Used/Types 数值与旧显示一致（如 16M 物品盘放 1 组石头 → Used 0B→约 0.1K、Types 0/315→1/315；流体 25 分母、源质 63 分母不变）；③源质盘悬停无 TE4 缺失崩溃（Throwable 兜底保留）。

## t84b 网络工具三栏图标同形排查（根因=AE2U 标签页选择不同步到服务端；engineer-content，队长补充方向）
- **现象**：用户确认网络工具"流体栏图标像物品盘、颜色也像金色"，三栏都像物品盘。
- **逐环排查（全部实证）**：
  1. **贴图正确**：仓库/本地 jar/已安装服务端+客户端 jar（SHA6C5C19C6…，含 t81+t76+t82 全部修复）内 27 张贴图逐像素采样——物品=金 avg(104,90,74)、流体=蓝 avg(61,85,117)、源质=紫 avg(86,71,117)，三类同模具设计仅内色不同。**非贴图问题**。
  2. **服务端分类正确**：t81 `EcoStorageCellInventoryHandler.getStorageChannel` 覆写（已安装 jar javap 实证存在）→ `CellInventoryHandler.getCellType`=FLUID/ITEM；essentia handler 覆写 ESSENTIA；`putItemStackIntoMap` key 保留 Item 身份（NBT 丢弃但不同 Item 恒分开）→ itemCells/fluidCells/essentiaCells 内容正确。
  3. **传输正确**：`AEItemStack.create`/`writeToPacket`（id short + Damage short）保留 Item 身份；ItemRepo 无图标缓存；`AEBaseGui.drawItem` → vanilla `RenderItem.renderItemIntoGUI` → `Item.getIconIndex(stack)` 逐 Item 取自己贴图（AppEngRenderItem 只覆写数字叠加层）。**渲染层可区分，不需要覆写 getIconIndex**。
  4. **真正根因（AE2U 自身 bug/局限）**：`GuiNetworkStatus.actionPerformed` 的 cell 按钮（Settings.CELL_TYPE）只调 `AEConfig.instance.nextCellType()` 改**客户端本地**字段 + `GuiImgButton.set`，**不发任何包**（998 源码与已安装 rv3-beta-1000 release jar 字节码双重实证：只有 OpenReshuffle/ToggleDiagnostics/ToggleFlowTracking/PacketNetworkStatusSelected(isConsume) 四个包）。`selectedCellType` 全库仅客户端写入（AEConfig:198/715）、服务端读取（ContainerNetworkStatus:301）、无同步通道。单机整合包客户端与服务端共享 JVM → AEConfig.instance 同一对象 → 切 tab 生效；**独立服务端（用户 M:\AA科技\GTNH\服务端）进程隔离 → 服务端 AEConfig.selectedCellType 恒为默认 ITEM → detectAndSendChanges 永远发 sg.getItemCells()**。
- **症状闭环**：客户端按自己选择画 Fluid/Essentia 标签页（头部字节/类型统计经 @GuiSync 正确——fluidBytesTotal 等来自 GridStorageCache 每类统计，正确），但**格子列表渲染的是服务端发的物品盘 map** → 流体/源质栏都显示金色物品盘；物品栏显示正确（本来也是物品盘）。与用户所见完全一致。
- **影响面**：AE2U 网络工具 cell 统计视图在**独立服务端**上对所有 mod 的盘都如此（非 ECO 独有）；此前 ECO 盘因 t76/t81 前不显示而未被注意。
- **修复方向（已设计未实施，待队长定夺开任务）**：A) 客户端 mixin `GuiNetworkStatus.actionPerformed` TAIL：当点击 cell 按钮时经自建 FML SimpleNetworkWrapper 通道把 `AEConfig.instance.selectedCellType()` 发给服务端；服务端 mixin `ContainerNetworkStatus`：@Unique 字段 selectedCellType(默认 ITEM)+setter，@Redirect `AEConfig.instance.selectedCellType()` 调用为该字段。B) 或服务端改为一次发送三类 map、客户端自选（改动更大）。C) getIconIndex 覆写**不需要**（贴图/渲染已正确）。
- **装服确认指纹**：流体标签页若头部统计正确（Fluid Cell Count/字节正确）但下方格子是金色物品盘 → 即此 bug（当前 jar 即可复现，无需换 jar）。

- **关闭决定（范围变更，队长指令）**：用户实测**原版 AE2U Xtreme 盘在网络工具三栏同样显示统一图标** → 判定为 AE 模组通用行为（与服务端恒发物品盘 map 的机制一致——任何 cell 类型在三栏都渲染物品盘图标），**非 ECO 缺陷**，按范围变更**关闭不修**，不实现标签页同步修复。t84 仅保留创造栏 tooltip 卡顿优化（纯 NBT 读取，已交付）。

## t85 AE 网络工具 cell 标签页选择同步（客户端→服务端；engineer-content，用户最终拍板修复）
- **背景**：t84b 排查确认 AE2U 网络工具 cell 标签按钮从不向服务端发包（998 源码 + rv3-beta-1000 jar 字节码双重实证：仅 OpenReshuffle/ToggleDiagnostics/ToggleFlowTracking/PacketNetworkStatusSelected 四包），独立服务端 AEConfig.selectedCellType 恒 ITEM → detectAndSendChanges 永远发 getItemCells() → 三栏全金。t84b 曾按"用户实测原版盘同样表现"关闭不修；**用户最终拍板：修**（原关闭指令作废）。
- **实施方案 A（2 mixin + 1 消息类 + 1 接口 + 通道注册）**：
  1. **客户端** `MixinGuiNetworkStatus`（mixins json "client" 列表）：`@Inject(method="func_146284_a"=actionPerformed, TAIL, remap=false)`——按钮处理链跑完后（nextCellType 已轮转本地值），若 `btn instanceof GuiImgButton && getSetting()==Settings.CELL_TYPE`，经自建通道 `EcoAEGTNHCore.NETWORK.sendToServer(new C2SNetworkCellTypeSelected(AEConfig.instance.selectedCellType()))` 发送新选择。释放 jar 覆写 vanilla 的方法名是 SRG 字面名 func_146284_a（javap 实证），remap=false 同 MixinTileDrive。
  2. **服务端** `MixinContainerNetworkStatus`（"mixins" 公共列表）：`@Unique` 字段 `ecoaegtnh$cellTypeSelection`（默认 ITEM）+ `@Redirect(method="func_75142_b"=detectAndSendChanges, target="Lappeng/core/AEConfig;selectedCellType()Lappeng/api/config/CellType;", remap=false)` 改为返回该字段；同时 `implements INetworkToolCellTypeHolder` 暴露 setter（避免反射/编译期引用 @Unique 方法）。
  3. **消息** `C2SNetworkCellTypeSelected`（IMessage，CellType ordinal 编解码，fromBytes 越界钳制 ITEM）+ `C2SNetworkCellTypeSelectedHandler`（Side.SERVER 注册；仿 AE2U PacketNetworkStatusSelected 直写 openContainer 模式——1.7.10 MinecraftServer 无 addScheduledTask，已实证编译错误后改直写）。
  4. **注册** `EcoAEGTNHCore.preInit`：`NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("ecoaegtnh")` + `registerMessage(Handler, Msg, 0, Side.SERVER)`（客户端只发不收；通道名 9 字符合法）。
- **编译/运行时命名实证**：dev jar（编译期，MCP 名 actionPerformed/detectAndSendChanges）与 release jar（运行时，SRG 名 func_146284_a/func_75142_b——服务器+客户端安装的 AE2U=BDCE6F77F270）不同名；remap=false 字面名方案与 t66 MixinTileDrive(func_94041_b) 同款（先例已装服验证）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（19s，第二次——首次因 MinecraftServer.addScheduledTask 不存在失败，已改直写模式）；javap：MixinGuiNetworkStatus 含 getSetting/CELL_TYPE/sendToServer/C2SNetworkCellTypeSelected、注解常量池含 func_146284_a+TAIL；MixinContainerNetworkStatus implements INetworkToolCellTypeHolder、@Unique 字段/setter、注解常量池含 func_75142_b+INVOKE+AEConfig.selectedCellType() 目标串；EcoAEGTNHCore 含 NETWORK/newSimpleChannel/registerMessage；jar 内 mixin json 5 类（common 4 + client 1）与 3 网络类齐备；jar `build/libs/ecoaegtnh.jar`（137935 B）SHA256 = `7660A9BD6D1C2139DB3F2ECE18010FC469F23E5ED5F74110FFA2849BEBE2F899`。
- 队长侧装服实测（独立服务端+客户端各换 jar）：网络工具点 cell 按钮切 物品/流体/源质 三标签页，格子分别显示各自盘图标（物品金/流体蓝/源质紫），不再全金；原版盘同样受益；未点按钮时行为不变（默认 ITEM）。

## t86 创造栏 ECO 标签卡顿：静态定位 + 诊断插桩（engineer-content；实测数据待装服）
- **现象**：打开创造栏 ECO 标签瞬间卡（半秒内）；t84 纯 NBT tooltip 优化后仍卡 → 根因不在 addInformation 内容构建。
- **静态排查结论（逐项实证排除）**：
  1. **vanilla/Forge 标签切换路径平凡**（patched MC 源码实证）：`GuiContainerCreative.setCurrentCreativeTab`（非物品栏标签）只做 `displayAllReleventItems`（我们 35 项：3 GT 控制器 + 5 方块 + 27 盘）+ 槽位列表复用，无 per-item 重活。
  2. **tooltip 构建（updateFilteredItems）只对搜索标签/搜索框输入触发**（源码实证：调用点仅 keyTyped 与带搜索栏标签的 setCurrentCreativeTab 分支）——普通标签打开**不**批量构建 tooltip。
  3. **AE2U 无 cell 联动**：1.7.10 无 `addCellInformation` API（全库 grep 无匹配）；AE2U 无 ItemTooltipEvent 监听（grep 无匹配）；`AEBaseItem.getSubItems` 平凡；NEI 模块无 cell 引用。
  4. **NEI/NEE/AE2FC 无创造栏 cell hook**：GuiExtendedCreativeInv 非默认 GUI（仅扩展创造模式），NEICreativeGuiHandler 只改可见性；NotEnoughEnergistics 是配方/终端集成无创造栏代码；AE2FC 无创造栏代码。
  5. **GT ItemTooltipEvent 监听对非 GT 物品是快速路径**（GTClient.onItemTooltip 实证：HazardProtection 快速判断 + 小枚举循环）。
  6. **剩余嫌疑（需运行时数据）**：A) 首次 hover 的完整 tooltip 链（ItemTooltipEvent 各监听器成本，我们的 addInformation 已被 t84 降至 ~0）；B) 3 个 GT 控制器机器物品的首次渲染（GT 机器 item 图标路径）；C) NEI/GTNH 客户端对标签切换的隐藏处理；D) 一次性初始化（首次打开后不再卡）。
- **诊断插桩（本 jar，5 个计时点，日志前缀 [T86]，logger=ECOAEGTNH）**：
  1. `EcoAEGTNHCore.creativeTab.displayAllReleventItems`：controller 循环 / super（全注册表迭代）/ 总计 + size（标签打开总成本 + 是否只首开慢）。
  2. `ItemEcoStorageCell.getSubItems`：每次调用耗时（默认实现应为 ~0us）。
  3. `ItemEcoStorageCell.getIconIndex`：前 60 次调用耗时（图标渲染成本，@SideOnly CLIENT）。
  4. `ItemEcoStorageCell.addInformation`：前 30 次 + >500us 调用耗时（我们的 tooltip 份额）。
  5. **`MixinItemStackTooltipProbe`（client 列表）**：`ItemStack.getTooltip`（SRG func_82840_a，remap=false）HEAD+RETURN 计时——完整 tooltip 链（显示名 + addInformation + **所有 ItemTooltipEvent 监听器**），前 40 次 + >1000us。
- **实测流程（装服后）**：①客户端换 jar 开游戏；②打开创造栏切到 ECO 标签，观察是否卡；③悬停几个盘；④关游戏，把 `logs/fml-client-latest.log`（或 latest.log）里 `[T86]` 行发给队长。判读：displayAllReleventItems total 大→列表构建（含 super 注册表迭代）；getIconIndex 大→图标渲染（首帧）；ItemStack.getTooltip 大→tooltip 链（再对比 addInformation 分拆我们的 vs 监听器）；全部 ~0→NEI/隐藏处理（第二轮需对 GuiContainerCreative 加计时 mixin）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（30s；首次因 1.7.10 无 Item.getTooltip 覆写点编译失败——1.7.10 tooltip 由 ItemStack.getTooltip 构建，改为 mixin 计时）；javap：ItemEcoStorageCell 含 [T86] getSubItems/getIconIndex/addInformation nanoTime 探针；MixinItemStackTooltipProbe 注解含 func_82840_a+HEAD/RETURN；EcoAEGTNHCore$1 含 [T86] displayAllReleventItems 探针；jar 内 mixin json common 4 + client 2；jar `build/libs/ecoaegtnh.jar`（136417 B）SHA256 = `061972FCB4E94695AA498140D866C5F50DC55D84C8F76E14BDDEC09536D7AE35`。
- **注意**：本 jar 为**诊断构建**（T86_DIAG=true 计时探针），定位后需移除探针再出修复版；t84 纯 NBT tooltip 未动。

## t86 结案：创造栏"卡顿"非 ECO 缺陷——客户端一次性初始化（用户实测数据）
- **实测数据（客户端 [T86] 日志）**：displayAllReleventItems=2.6ms（35 物品）、getSubItems=0-1us、getIconIndex=1-11us、完整 tooltip 链（ItemStack.getTooltip 含全部 Forge 监听器）=50-99us——**ECO 代码路径无任何卡点**。
- **用户确认**：只有"进游戏后第一次打开创造栏 + 第一次拿取"卡一下，之后全正常——MC 客户端一次性初始化（JVM/纹理图集/GUI 首次布局）；ECO 标签恰是用户第一个打开的标签，承担了全局初始化成本。
- **结论**：**无需修复**。t86 探针全部移除（ItemEcoStorageCell 计时、EcoAEGTNHCore displayAllReleventItems 插桩、MixinItemStackTooltipProbe 删除 + mixins json 恢复 client 仅 MixinGuiNetworkStatus）。
- **验证（干净版）**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（19s）；javap：ItemEcoStorageCell/EcoAEGTNHCore$1 无 T86/nanoTime 残留、jar 内无 MixinItemStackTooltipProbe.class、mixin json common 4 + client 1；jar `build/libs/ecoaegtnh.jar`（133523 B）SHA256 = `CA30E77602CB23B0441EBD6718756256C8C7C63A668F3EC3C5EAAE8B7564E463`（含 t85 网络工具标签同步 + t84 纯 NBT tooltip + t82 紫色 LED 全部修复）。

## t91 存储盘 byteMultiplier 新序列（严格 ×2 递增，消除重复；engineer-content，用户确认新规格）
- **新序列（用户确认）**：K_256=1 / K_1024=2 / K_4096=4 / M_16=8 / M_64=16 / M_256=32 / M_1024=64 / M_4096=128 / M_16384=256——每档严格 ×2，消除旧序列（k 级 value/256、M 级 value/4）导致的 1024k 与 16M 同为 4、4096k 与 64M 同为 16 的重复。
- **改动**：仅 `CellSize.java` 枚举常量第 4 个构造参数（byteMultiplier）+ 类注释。派生项自动生效：`getBytesPerType = multiplier×1024`（256k→1KB … 16384M→256KB）；t68 盘内字节公式 `weight = amountPerByte×byteMultiplier`（物品 8×m：16M→64）；t84 纯 NBT tooltip 自动用 `getByteMultiplier()`。idleDrain 不动（仍 value/4 与 value/4000）。
- **核对 ① essentia 路径不使用 multiplier**（无需改动）：t84 addStorageInformation 对 essentia 用 `1L` 代替 multiplier（weight=amountPerByte=2）；`EcoStorageCellInventoryEssentia` 无 getUsedBytes 覆写——走 AE2U 基类数学（typeWeight=amountPerByte，无 multiplier）；`ItemEcoStorageCellEssentia.getBytesPerType` 覆写=0。**② 流体盘 typeWeight 不变**（AEFluidStackType.getAmountPerByte 未动）。**③ tooltip 示例**：16M 放 1 种×1000 个 → perType=8×1024=8192、weight=8×8=64、unused=1000%64=40→64-40=24 → used=1×8192+(1000+24)/64=8192+16=**8208B≈8KB**。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（24s）；javap：CellSize 构造参数字节码 9 档 multiplier=1/2/4/8/16/32/64/128/256（sipush/bipush 实证）、totalBytes 不变（262144/1048576/…/16777216000）、label/tier 不变；jar `build/libs/ecoaegtnh.jar`（142449 B）SHA256 = `239BDB4F25266DDF1D453A74E9A6EB5058437A1DA32AAFE3A0A2F5294E98EE0C`。DESIGN.md §1.6 公式表已同步更新。未改任何渲染/模型/配方/语言代码。

## t95 修复 t66 误伤：ECO 盘可入 ME-IO 端口/元件工作台，仍禁入 ME 驱动器/箱子（engineer-content，用户要求）
- **背景**：t66 的 MixinSlotRestrictedInput 无条件在 SlotRestrictedInput.isItemValid HEAD 拦 ECO 盘——但 IO 端口（ContainerIOPort:55 也用 PlacableItemType.STORAGE_CELLS，宿主 TileIOPort 的 "cells" 子库存）和元件工作台（ContainerCellWorkbench:93 用 WORKBENCH_CELL）也被误伤。槽类型无法区分 IO 端口与驱动器（都是 STORAGE_CELLS）。
- **修复（白名单式，最稳）**：拦截条件改为 `which == STORAGE_CELLS && 槽 inventory instanceof TileDrive/TileChest`：
  - 实证：ContainerDrive:29 把 **TileDrive 自身**作槽 inventory；ContainerChest:30 把 **TileChest 自身**作槽 inventory；ContainerIOPort:56 传 `getInventoryByName("cells")` 子库存（非 TileIOPort）→ 自然放行；WORKBENCH_CELL 槽天然放行；未来其它 STORAGE_CELLS 用途（如空间 IO）也放行。
  - 实现：mixin 内用 `(SlotRestrictedInput)(Object)this` 转型访问（@Shadow 无法解析从 vanilla Slot 继承的字段——AP 报 "Cannot find target for @Shadow field"；目标方法也需 @Shadow——getItemType() 用转型直接调）；`getItemType()` 公共 getter 读 `which`；`slot.inventory` 是 vanilla public final 字段。
  - 元件工作台接受性另证：`IStorageCell extends ICellWorkbenchItem`（API 实证），我们的盘 `isEditable=true` → WORKBENCH_CELL 检查通过；IO 端口 STORAGE_CELLS 检查 `isCellHandled`（我们注册了 EcoStorageCellHandler）→ 通过。
- **保留**：MixinTileDrive（漏斗/管道直插驱动器）不动；ECO 盘位路径（TileEcoStorageDrive.buildHandler 直调 EcoStorageCellHandler）不走 AE2U 槽，不受影响。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（20s；两次失败：①@Shadow 继承字段 AP 报错 ②mixin 直调目标方法 getItemType 编译失败——均改转型解决）；javap：MixinSlotRestrictedInput 含 instanceof ItemEcoStorageCell + getItemType==STORAGE_CELLS + getfield field_75224_c(Slot.inventory) + instanceof TileDrive/TileChest + setReturnValue；jar `build/libs/ecoaegtnh.jar`（142935 B）SHA256 = `72CE615C343B180E80A622032E16FE39106DAEB8B901856D20466B8EA15D5462`。
- 队长侧装服实测：①ECO 盘可放入 ME-IO 端口（6 格）与元件工作台（cell 槽）；②仍不可放入 ME 驱动器/ME 箱子（GUI 与漏斗/管道）；③ECO 阵列盘位正常；④原版盘不受影响。

## t96 IO 状态 LED tooltip 添加耗能显示（AE/t；engineer-content，用户要求）
- **需求**：控制器 GUI 底部参数条第 1 格状态 LED（t77 四格之一）的 tooltip 增加当前耗能显示。
- **实现**：
  1. **服务端存储**：`MTEEcoStorageArray` 新增字段 `idlePowerUsage`（double）；`recalculateEnergyUsage()`（t69 B+C 公式：tierBase + 0.5×已装盘数 + Σ盘 idleDrain，L6+4×16M=22 AE/t）算出 idleDrain 后赋值 `this.idlePowerUsage`（再 setIdlePowerUsage 到 meBus proxy——原逻辑保留）。
  2. **同步**：新增 sync 字段 `syncIdlePowerUsage`（double）+ 状态 LED 链挂 `FakeSyncWidget.DoubleSyncer(() -> idlePowerUsage, val -> syncIdlePowerUsage = val).setOnClientUpdate(notifyTooltipChange)`（MUI1 1.3.4 有 DoubleSyncer，实证；double 直传保留 0.5 精度）。
  3. **tooltip**：`statusTooltip()` 在能量行后追加 `耗能: <值> AE/t`（内部 AE 值口径，与 t69 公式一致；`formatPower` 帮助方法：整数直显 "22"、0.5 小数显示一位 "2.5"）。
  4. **lang**：`ecoaegtnh.gui.io.power_usage` 中=耗能：/ 英=Power Usage:（两文件各加一行，注释 t96）。
  5. 其它 LED/同步机制未动。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（20s）；javap：idlePowerUsage/syncIdlePowerUsage 字段、recalculateEnergyUsage 赋值+setIdlePowerUsage、DoubleSyncer 在 addUIWidgets、statusTooltip 含 power_usage 键+formatPower+" AE/t"；jar 内 en/zh lang 均有新键；jar `build/libs/ecoaegtnh.jar`（143443 B）SHA256 = `71223675C6FF8512EF929720DE5B44E002C8DD1A67400E7A02DE7183B64D1DC8`。
- 队长侧装服实测：悬停第 1 格 LED → tooltip 末行 "耗能: X AE/t"；放盘/拆盘后数值随 0.5×盘数+ΣidleDrain 变化（如 L6 空=4.0、放 1 个 16M=4.0+0.5+4.0=8.5）。

## t97 全部配方重写：L4=EV / L6=ZPM / L9=UHV 解锁（engineer-content，用户确认）
- **新体系（用户确认）**：L4 组=EV 解锁、L6 组=ZPM 解锁、L9 组=UHV 解锁——按控制器等级分组，每组电压/材料/电路全部对齐该组解锁档。
- **分档设计（判断并注释）**：
  - **EV 基础套件**：外壳（Titanium Plate×4 + Titanium Frame）、通风口（外壳+Titanium Plate×2）、驱动盘位（外壳×2+Titanium Plate×2）、ME 总线（外壳×2+CertusQuartz Dust×4+Titanium Plate）——L4 控制器输入含它们，必须 ≤EV 才能兑现 "L4=EV 解锁"。
  - **每档专属门控**：电容 A=EV（Titanium+Redstone）/ B=ZPM（TungstenSteel×2+Naquadah Dust×4）/ C=UHV（NaquadahAlloy×2+TungstenSteel Block）；盘 k 组=EV+Titanium 板、16M..256M=ZPM+TungstenSteel 板、1024M..16384M=UHV+NaquadahAlloy 板；控制器 L4=EV（含电容A）/ L6=ZPM（含B）/ L9=UHV（含C）。
  - **盘内递进**：同组内小→大用石英/源质瓶数量（k: 2/4/8｜M16-256: 8/16/16｜M1024+: 32/32/64；瓶 1/2/4｜4/8/8｜16/16/32）+ 电路号递增（物品 21..29、流体 61..69、源质 81..89，避免旧式 +40/+47 重叠）；时长 10..100s。
- **保留**：tryAdd null 安全；ME Storage Housing（AEApi definitions cellContainer）依赖；源质盘 TE4 门控；输出物品不变（27 盘 + 5 部件 + 3 控制器）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（22s）；javap：RECIPE_EV×12（部件5+盘数组3+源质数组3+控制器L4）、RECIPE_ZPM×8、RECIPE_UHV×8——与设计精确一致；旧 TierEU.LV/MV/HV/IV/LuV 引用 0 残留、旧材料 Steel/Aluminium/Chrome 0 残留；电路号 1-7 部件 / 11-13 控制器 / 21-29 物品 / 61-69 流体 / 81-89 源质；jar `build/libs/ecoaegtnh.jar`（143496 B）SHA256 = `F29D4EF224A841AE11B89B3840F9D87BCC5D12497C82CEA150BB78C7B0382295`。DESIGN.md §4.2 配方总表已同步。
- 队长侧装服实测：NEI 查看配方——L4 组（外壳/通风口/驱动/ME 总线/电容A/k 级盘/控制器L4）全部 EV；L6 组（电容B/16M/64M/256M 盘/控制器L6）ZPM；L9 组（电容C/1024M+/控制器L9）UHV；合成产物正确。

## t97b 配方材料按 GTNH 官方阶段文档核对修正（engineer-content，队长要求对照 wiki 阶段页）
- **核对方**：浏览器不可用（无 provider），huijiwiki/atwiki/fandom 直连均 403——改用 **GTNH 官方开发文档仓库 GTNewHorizons/GTNH-Dev-Doc**（GitHub raw 可抓取）的 tech tree 分档文档（权威）。
- **官方档位材料（GTNH-Dev-Doc tech tree）**：EV 主材料=**Titanium**（档末里程碑：获得一叠钨钢）；IV 主材料=TungstenSteel；LuV 主材料=Rhodium-Plated Palladium（Naquadah 线开始）；**ZPM 主材料=Iridium**（完成 Naquadah/Naquadria 线，档末出 Americium+首 UV 部件）；UV 主材料=Osmium（NaquadahAlloy 为**次要**材料）；**UHV 主材料=Neutronium**（档末出 Tengam+首 UEV 部件）。
- **修正内容（Recipes.java）**：
  - ZPM 组（L6）：TungstenSteel → **Iridium**（电容 B = Iridium Plate×2 + Naquadah Dust×4；16M/64M/256M 盘档位板 = Iridium Plate）——Iridium 是 ZPM 主材料、Naquadah 线是 ZPM 重点。
  - UHV 组（L9）：NaquadahAlloy → **Neutronium**（电容 C = Neutronium Plate×2 + NaquadahAlloy Block×1；1024M+ 盘档位板 = Neutronium Plate）——Neutronium 是 UHV 主材料；NaquadahAlloy（UV 次要）作电容 C 补充降低难度。
  - EV 组（L4）：Titanium 保持（官方 EV 主材料，初版选择正确）。
  - 材料可用性实证：GT5U MaterialsInit 中 Iridium/Neutronium 均 `addMetalItems()`（含板材），getPlates 非 null。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（21s）；javap：Iridium 引用×7（电容B+3物品+3源质）、Neutronium×7（电容C+3+3）、NaquadahAlloy×1（电容C 方块）、Naquadah Dust×1、TungstenSteel 残留 0、Titanium×12；档位数组不变（EV×12/ZPM×8/UHV×8）；jar `build/libs/ecoaegtnh.jar`（143493 B）SHA256 = `F963EFB6EA7CACEB74C5400C2B94027B1F31DD9ABEAB79B994A4286419575670`。DESIGN.md §4.2 已同步材料。

## t98 配方升级为装配线复杂配方（研究前置+流体+多部件；engineer-content，用户要求"配方太简单了，想复杂点，装配线用起来"）
- **API（官方 AssemblyLineRecipes 实证 + 5.09.54.20 编译期依赖验证）**：`GTRecipeConstants.{AssemblyLine, RESEARCH_ITEM, SCANNING}` + `gregtech.api.util.recipe.Scanning(int, long)` + `GTRecipeBuilder.{MINUTES, SECONDS, INGOTS}`——`RA.stdBuilder().metadata(RESEARCH_ITEM, ...).metadata(SCANNING, new Scanning(...)).itemInputs(...).fluidInputs(...).itemOutputs(...).eut(...).duration(...).addTo(AssemblyLine)`。
- **设计**：
  1. **研究物品按组**：EV=Circuit_Data、ZPM=Circuit_Ultimatecrystalcomputer、UHV=Circuit_Biowaresupercomputer（GT5U 5.09.54 档位 tooltip 实证：MetaGeneratedItem03.registerTieredTooltip——ZPM=Ultimatecrystalcomputer、UHV=Biowaresupercomputer）；scanning=1min@组档；每条配方需数据棒扫描解锁。
  2. **eut=组档**（装配线须运行在 EV/ZPM/UHV 电压才能制作该组物品——官方"低一档"惯例用于通用 GT 件，ECO 整机按解锁档门控，注释说明）。
  3. **部件=同档 GT 通用件**（马达/泵/传送带/活塞/传感器/发射器/场发生器/机械臂 + 档位电路）；**UHV 组用 UV 件**（Electric_Motor_UHV/Pump_UHV 在 GT5U 无配方——官方 AssemblyLineRecipes 只输出到 UV 马达/泵，实证防鸡生蛋）。
  4. **流体=组档熔融金属**（Titanium/Iridium/Neutronium getMolten）+ **焊锡合金**（SolderingAlloy getMolten——官方用 gt++ INDALLOY_140，非依赖不可用）+ 润滑剂（Lubricant getFluid）；null 安全：研究物品/物品输入/流体输入/输出任一 null 跳过并告警（tryAddAL）。
  5. **结构**：部件 7 条（外壳/驱动/电容A/B/C/ME总线/通风口）+ 盘 27 条（物品/流体/源质，k=EV/16M-256M=ZPM/1024M+=UHV，输入=housing+石英/源质瓶+档位板+档位电路消耗）+ 控制器 3 条（L4=EV/L6=ZPM/L9=UHV，全套部件+同档马达×2/泵/传感器/发射器/机械臂）；产物/档位/材料（钛/铱/中子素）与 t97 一致；旧 assembler 配方全部移除。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（22s）；javap：AssemblyLine/RESEARCH_ITEM/SCANNING+Scanning 引用、getMolten×32/getFluid×12、Circuit_Data×9/Ultimatecrystalcomputer×7/Biowaresupercomputer×7、Electric_Motor×6、**assemblerRecipes/RecipeMaps 残留 0**；jar `build/libs/ecoaegtnh.jar`（144960 B）SHA256 = `F4BEFD392F21233DC005CC195EBA0B0317369E8347FF2FB102C00600C1924D96`。DESIGN.md §4.2 已重写。
- 队长侧装服实测：NEI 显示装配线配方（研究物品+流体输入）；装配线机器可制作（先数据棒扫描研究解锁）；L4/L6/L9 档位（EV/ZPM/UHV）正确；产物与旧版一致。

## t98b 配方分机调整：EV 组=复杂组装机、ZPM/UHV 组=装配线（engineer-content，队长关键点：装配线 LuV 才解锁）
- **关键约束（用户指出）**：装配线机器（MTEAssemblyLine）是 **LuV 解锁**的机器——EV 阶段玩家没有装配线，L4=EV 组不能用装配线。
- **调整后分机**：
  - **EV 组（组装机，复杂版）**：外壳/通风口/驱动盘位/ME 总线/电容 A（L4 基础套件，L4 控制器输入需要、必须 EV 可做）+ k 级盘 9 条（物品/流体/源质）+ 控制器 L4——多 EV 部件（马达/泵/传送带/活塞/传感器/发射器/场发生器/机械臂/Data 电路）+ 流体输入（`GTRecipeBuilder.fluidInputs`：焊锡合金熔融+润滑剂——组装机支持流体），比旧"4 物品+1 电路"复杂；`circuit()` 区分配方；**无研究前置**。
  - **ZPM 组（装配线）**：电容 B + 16M/64M/256M 盘 9 条 + 控制器 L6——RESEARCH_ITEM=Circuit_Ultimatecrystalcomputer + SCANNING 1min@ZPM + Iridium 熔融/焊锡/润滑剂 + ZPM 部件。
  - **UHV 组（装配线）**：电容 C + 1024M/4096M/16384M 盘 9 条 + 控制器 L9——RESEARCH_ITEM=Circuit_Biowaresupercomputer + SCANNING 1min@UHV + Neutronium 熔融/焊锡/润滑剂 + UV 部件（GT5U 无 UHV 马达/泵配方防鸡生蛋）。
  - 电路号不变（1-7 部件 / 11-13 控制器 / 21-29 物品 / 61-69 流体 / 81-89 源质）；产物/档位/材料（钛/铱/中子素）与 t97 一致。
- **实现**：Recipes.java 双 helper——`tryAddAssembler(name, inputs, fluids, output, circuit, eut, duration)`（组装机+电路+流体，无研究）+ `tryAddAL(...)`（装配线+研究，t98 原样）；EV 组走前者、ZPM/UHV 组走后者的 addTo(assemblerRecipes/AssemblyLine)。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（20s）；javap：assemblerRecipes（+circuit builder）与 AssemblyLine 双 addTo 目标、RESEARCH_ITEM/SCANNING/Scanning、getMolten×17/getFluid×13、Circuit_Data×5（仅 EV 组装机）/Ultimatecrystalcomputer×4（ZPM 研究+3 盘）/Biowaresupercomputer×4（UHV 研究+3 盘）；jar `build/libs/ecoaegtnh.jar`（145328 B）SHA256 = `7546CAA85B31C012C708412317CF8FF66A4F4718476DF96F12E9189576C81F30`。DESIGN.md §4.2 已更新（分机总表）。
- 队长侧装服实测：NEI——L4 组（外壳/驱动/电容A/ME总线/通风口/k 盘/控制器L4）显示**组装机**配方（含流体输入，无研究）；L6/L9 组（电容B/C/大盘/控制器L6/L9）显示**装配线**配方（研究物品+流体，需数据棒扫描解锁）；档位 EV/ZPM/UHV 正确。

## t98c 装配线配方与官方在线配方库惯例核对（engineer-content，队长提供 2.9.0-beta-1 真实数据）
- **核对结论：无需修改，当前实现与官方惯例一致**（L4 组装机 EV 可做、L6/L9 装配线可解锁，已满足交付门槛）。
- **① eut（官方混合惯例，我们的组档与"整机同档"范例一致）**：数据库显示马达类低一档（ZPM 马达 @LuV 30,720、UHV 马达 @UV 491,520），但**整机/装甲板类同档**（ZPM 重装甲板 @122,880=ZPM、UHV 装甲板 @1,966,080=UHV）。ECO 控制器/盘是整机（类比装甲板）→ **保持 eut=组档**（ZPM 组 @ZPM、UHV 组 @UHV），代码注释已说明；研究物品（档位电路）+ scanning@组档 保持 ZPM/UHV 解锁门。
- **② 焊料**：GT5U **无 Indalloy140**（gt++ MaterialsAlloy.INDALLOY_140 非依赖；Materials 仅有 BismuthBronze，非焊料）→ **SolderingAlloy 焊锡合金是 GT5U 原生焊料，正确**（队长确认"若无则用 SolderingAlloy"）；熔融钛/铱/中子素 + 润滑剂保留（ECO 特色），量随档位递增（焊料 1-4 INGOTS、润滑剂 125-1000L，与物品体积相称）。
- **③ 研究前置**：RESEARCH_ITEM=档位电路（比"扫低级物品"门槛更高，队长认可保持）；数据棒不消耗、扫描 1min@组档。
- **④ 结构/输入序**：ZPM 组 8 固体+2 流体、UHV 组 13 固体+3 流体 ≤ 官方上限（16 固体+4 流体）?。
- **⑤ 时长**：30..150s 在官方 30s~2min 区间内（马达 30-50s 是单件，ECO 整机含多部件输入略长，合理）。
- 无需改代码 → 交付 jar 维持 `7546CAA85B31C012C708412317CF8FF66A4F4718476DF96F12E9189576C81F30`（145328 B）。

## t100 ECO 盘配方重构：中间材料（组件+外壳）+ 低档扫描前置 + 创造页整理（engineer-content，用户大改）
- **① 新增中间材料（36 物品）**：
  - **ECO ME 存储组件 ×27**（`ItemEcoStorageComponent`，容量档 × 类型）：物品/流体/源质 × 9 档（256k..16384M）。参考 AE2U 真实组件配方（服务端 jar `assets/.../recipes/network/cells/storage-components.recipe` 实证：1k=红石+石英+LogicProcessor，每档=上一档组件+处理器+石英玻璃）——**链式**：每档组件输入上一档组件 + 档位材料（k=钛+石英+红石+Data 电路；16M..256M=铱+锘；1024M+=中子素+硅岩合金）。
  - **ECO 存储外壳 ×9**（`ItemEcoStorageHousing`，控制器档 × 类型）：L4/L6/L9 × 物品/流体/源质；外壳=机器外壳（casing+档位板+石英，源质+源质瓶）。
  - 贴图：36 张 16×16 程序生成（组件=类型色+档位条，外壳=暗框+类型色内芯+档位点，与盘家族色一致）；lang 36×2 键。
- **② 盘配方 = 外壳 + 组件**（27 条）：`housing(type,tier) + component(type,size)`；k 组 EV 组装机、16M..256M ZPM 装配线、1024M+ UHV 装配线；流体保留（档位熔融金属+润滑剂）。
- **③ 研究前置改为"低一档同类产物"**（用户明确，GTNH 惯例）：盘 M/big 研究=低一档同类型盘（16M←4096k、64M←16M、256M←64M、1024M←256M、4096M←1024M、16384M←4096M）；组件同链（16M 组件←4096k 组件…）；L6 控制器←L4 控制器、L9←L6；电容 B←A、C←B；外壳 L6←L4、L9←L6。取代 t98 的档位电路研究。
- **④ 创造页整理**：单页拆为 **4 个 CreativeTabs**（GTNH 多 tab 做法）：`ecoaegtnh.machines`（方块+控制器）/`ecoaegtnh.cells`（27 盘）/`ecoaegtnh.components`（27 组件）/`ecoaegtnh.housings`（9 外壳）；lang 4×2 标签键。
- **电路号**：部件 1-7、控制器 11-13、盘 21-29/61-69/81-89（不变）；组件 31-39/41-49/51-59；外壳 71/74/77。结构/机器分机/档位/材料（t97/t98b）不变；总配方 73 条（27 组件+9 外壳+27 盘+10 部件控制器）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（24s）；jar：StorageType/ItemEcoStorageComponent/ItemEcoStorageHousing 类、27+9 贴图、en/zh lang 各 27 组件+9 外壳+4 标签键；javap：EcoAEGTNHCore 4 个 CreativeTabs（ecoaegtnh.machines/cells/components/housings）、Recipes 双 helper（组装机 9 调用点+装配线 10 调用点=73 条运行时配方）、RESEARCH_ITEM 链；jar `build/libs/ecoaegtnh.jar`（165389 B）SHA256 = `BE620526EE9486542CC8BF441F86C4E0E1DA25782F28D999A9E1C6575FD9E083`。
- 队长侧装服实测：①创造页 4 标签分类清晰；②盘=外壳+组件可合成（组件/外壳各自可合成）；③装配线研究前置=低一档盘/控制器/组件/电容（NEI 研究物品显示）；④档位 EV/ZPM/UHV 正确；⑤旧档期盘（t97 直做配方）作废。

## t100b 配方经济学核对修正（engineer-content，队长提供 GTNH-AE 组件研究 docs/ME_STORAGE_COMPONENT_RESEARCH.md）
- **官方经济学（2.9.0-beta-1 实证）**：①组件 8 档两路线——电路组装机主线（需超净间、72L 焊锡合金、编程电路1、10s、电路递进 ULV→UV、处理器核心 金→钻石→绿宝石→高级绿宝石、GT 电路基板 32100→32107、功率每档×4）+ 工作台升级（4×上级组件+4×本级电路板+1×处理器核心）；②**存储外壳极便宜（LV 15 EU/t 5s，玻璃+少量 GT 材料），成本大头在组件**。
- **核对结果：外壳经济学倒挂需修正**——t100 原外壳配方消耗昂贵 casing 机器块（5 部件+流体产物），导致"外壳贵过组件"，与 GTNH-AE"外壳便宜、组件贵"相反。
- **修正（registerHousings）**：外壳改为便宜配方——L4=钛板×2+石英×2（源质+瓶）、L6=铱板×1+石英×4（研究=L4 外壳）、L9=中子素板×1+石英×8（研究=L6 外壳）；无 casing、无内部件；流体减量（焊锡 1 INGOTS、润滑剂 125/250/500）；时长 20/30/40s；eut/扫描保持组档。组件链保持成本大头（每档=上一档组件+档位材料+电路）。
- **机器决策（注释说明）**：组件保持 组装机/装配线 而非 GTNH-AE 电路组装机主线——电路组装机需超净间 + GT 电路基板/处理器核心体系，与 ECO 专属材料（钛/铱/中子素）不匹配；用户未指定机器，t98b 分机合理保持。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（23s）；源码核查 registerHousings 无 BlockEcoStorageCasing 引用（0）、档位板+石英在；jar `build/libs/ecoaegtnh.jar`（165387 B）SHA256 = `22A03D91A034036EE38674C6880770768E0AD334566F07848F971274B5065971`（新 jar，BE620526 已装机可先用的基础上更新）。
- 装服实测：外壳合成便宜（EV 20s 两板+石英），组件为成本主体；盘=外壳+组件不变。

## t101 修复外壳显示/组件贴图 AE 风/创造页排序（engineer-content，用户 4 项反馈）
- **① 外壳紫黑块 + 本地化名缺失：代码无误，客户端缓存**——程序化核对：27 组件 + 9 外壳的 setUnlocalizedName?lang 键（item.ecoaegtnh.storage_{component,housing}_...）?setTextureName?贴图文件名 **1:1 完全一致**（0 缺失/0 多余）；setCreativeTab 在构造器设置正确（cells→TAB_CELLS、components→TAB_COMPONENTS、housings→TAB_HOUSINGS）。结论：客户端需**完全退出重启**（旧缓存）。装服验证。
- **② 组件贴图改 AE 风（用户明确）**：参考 tools/ae2-ref/ItemBasicStorageCell.1k.png 实证样式（灰白菱形芯片 + 中心彩色标记）重生成 27 张 storage_component_*.png——灰色芯片体（180,185,190）+ 类型色中心点（物品金 176,150,90 / 流体蓝 61,85,117 / 源质紫 86,71,117）+ 容量条（1-9 档）；像素级验证（中心色/芯片体/容量条）通过。
- **③ 创造页排序（用户明确：物品→流体→源质，各自小→大）**：根因=注册顺序是"尺寸优先"（每尺寸物品+流体交错），vanilla 创造栏按注册顺序显示 → 乱。修复：RegistryItems.register() 改为**类型优先**注册——全部物品（256k→16384M）→ 全部流体 → 全部源质（组件/外壳同）；四个标签页内自然呈现"物品小→大、流体小→大、源质小→大"。machines 标签保持（控制器+方块，已分组）。
- **④ 验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（21s）；jar 内组件贴图像素采样=AE 风（灰体+金色中心）；RegistryItems 字节码注册顺序=类型优先（ItemEcoStorageCellItem 循环在前）；jar `build/libs/ecoaegtnh.jar`（167723 B）SHA256 = `5833859E9489DE6C643253BD0B3A9C9EB0540ED99551808AF0F2097F1A8C78F9`。
- 队长侧装服实测：①完全退出客户端重启后外壳/组件正常显示（中文名、无紫黑块）；②组件贴图 AE 芯片风（金/蓝/紫中心点区分类型）；③cells/components/housings 三标签内按 物品小→大、流体小→大、源质小→大 排列。

## t101b 配方缺失排查：服务端实证全部 73 条已注册（engineer-content，队长补充）
- **用户反馈**："配方部分有部分没有"（t100 重构后）。
- **排查（服务端日志实证，装服 jar=5833859E）**：
  1. **null 跳过**：fml-server-3.log（21:25 启动）grep `Skipping ECO recipe` = **0 条**——没有配方因 null 被跳过；`Done (2.139s)` 完整启动、无配方相关异常。
  2. **链式组件**：27 组件全部注册（无中间档断裂）；每档=上一档组件+材料，前置恒为低档已注册物品。
  3. **注册顺序**：preInit=方块+物品 → init=MTE+Recipes（EcoAERegistry 实证）→ postInit=AE handler；盘配方（外壳+组件）在组件/外壳注册之后 ?。
  4. **门控**：服务端 TE4 加载（mods 列表含 thaumicenergistics），postInit 自检 `isCellHandled(essentia16M)=true`——源质物品/配方全注册；流体无条件注册。
  5. **研究链无环**（逐条核）：组件 256k(组装机)→1024k→4096k→16M(ZPM AL,扫 4096k 组件)→64M→256M→1024M(UHV AL,扫 256M)→4096M→16384M；盘同链（16M 扫 4096k 盘…）；外壳 L4→L6(扫 L4)→L9(扫 L6)；控制器 L4→L6(扫 L4)→L9(扫 L6)；电容 B←A、C←B——每条 AL 研究物品恒为低档已注册产物，无环。
  6. 配方总数：27 组件+9 外壳+27 盘+10 部件控制器 = **73 条全注册**。
- **结论**：服务端无缺失；用户所见"部分配方没有"与 t101#1 紫黑块同根——**客户端 NEI/缓存未完全重启**（客户端 jar 已为 5833859E，完全退出重启后 NEI 应显示全部配方）。若重启后仍缺，抓客户端日志/NEI 截图定位具体缺哪条。
- 无需代码改动；交付 jar 维持 `5833859E9489DE6C643253BD0B3A9C9EB0540ED99551808AF0F2097F1A8C78F9`（t101）。

## t102 配方缺失排查 + 注册汇总日志（engineer-content，队长：逐条核对 73 条）
- **排查结论（延续 t101b，追加逐条审计 + 运行时证据）**：
  1. **注册顺序**：Recipes.register() = 组件(27)→外壳(9)→盘(27)→部件控制器(10)（源码实证）；盘配方输入=外壳+组件（先注册）?；前置物品任一 null → tryAdd 跳过并告警。
  2. **服务端日志（装服 jar 68D18B56，21:33 启动）**：`Skipping ECO recipe` = **0 条**；`FMLServerStartedEvent` 送达（完整启动）；无配方异常。
  3. **研究链逐条核无环**：组件 256k(组装机 EV)→1024k→4096k→16M(ZPM AL 扫 4096k 组件)→64M→256M→1024M(UHV AL 扫 256M)→4096M→16384M；盘同链（16M 扫 4096k 盘…）；外壳 L4→L6(扫 L4)→L9(扫 L6)；控制器 L4→L6(扫 L4)→L9(扫 L6)；电容 B←A、C←B——每条 AL 研究物品恒为低档已注册产物，无环；最低档 AL 物品（16M 盘/16M 组件）的前置（4096k）是组装机产物 ?。
  4. **NEI 可见性**：GTNH NEI 装配线页显示 AL 配方（研究物品作需求展示，无需先研究即可见）；"缺失"与 t101#1 同根=客户端缓存，完全重启后齐全。
  5. **门控**：TE4 加载时源质组件/外壳/盘全注册（服务端实证 isCellHandled(essentia16M)=true）；流体无条件注册；未加载 TE4 时源质 27 条跳过为预期。
- **新增（t102）：配方注册汇总日志**——Recipes 加 registeredRecipes/skippedRecipes 计数（tryAddAssembler/tryAddAL 成功+1、跳过+1），register() 末尾打印 `ECO recipes registered: {} total (components=27, housings=9, cells=27, parts/controllers=10), skipped={}`——每次启动服务器日志给出**明确注册证据**（预期 73/0）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（19s）；javap：registeredRecipes/skippedRecipes 字段 + 汇总日志字符串在类内；jar `build/libs/ecoaegtnh.jar`（168311 B）SHA256 = `C817B9DBAA7F48FF36A290593300EA9F5D260D29D677AE81B9E6607F1567A04F`。
- 装服实测：重启后服务器日志出现 "ECO recipes registered: 73 total ..., skipped=0"；客户端完全重启后 NEI 各类配方齐全（组装机+装配线+研究物品显示）。

## t104 修复创造页排序未生效 + 外壳名本地化（engineer-content，vision t103 截图实证）
- **问题 1：创造页排序仍乱（shot-8/9）**——t101 改注册顺序为类型优先，但显示仍类型交错。
  - **排查**：①jar 内 RegistryItems 注册顺序确为类型优先（javap 实证 ItemEcoStorageCellItem→Fluid→Component→Housing→Essentia）；②GTNH patched GuiContainerCreative（mcp_patched 源码实证）**无任何排序逻辑**——vanilla 按 displayAllReleventItems 加入顺序显示；但注册序在客户端显示未生效（可能客户端旧 jar 未重启，或注册序被环境覆盖）。
  - **修复（最稳方案）**：三个存储标签（TAB_CELLS/TAB_COMPONENTS/TAB_HOUSINGS）**覆写 displayAllReleventItems 显式排序**——物品 256k→16384m、再流体、再源质（CellSize 递增；源质 null 安全跳过）——顺序与注册/registry 迭代顺序完全解耦，免疫任何覆盖。
- **问题 2：外壳名未本地化（shot-10 显示原始 key）**——**jar 内 lang 与代码一致**：zh_CN.lang/en_US.lang 各 9 条 `item.ecoaegtnh.storage_housing_<type>_l<tier>.name`（如 `item.ecoaegtnh.storage_housing_item_l9.name=ECO L9 存储外壳（物品）`），与 ItemEcoStorageHousing unlocalizedName（`ecoaegtnh.storage_housing_item_l9`）精确匹配（jar 解包实证 9/9）——**客户端缓存问题**（旧 jar 未完全重启加载新 lang），非代码缺陷。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（22s）；javap：Core$2/$3/$4 的 displayAllReleventItems 按 item→fluid→essentia 顺序调用对应 RegistryItems helper；jar zh lang 外壳键 9 条；jar `build/libs/ecoaegtnh.jar`（169430 B）SHA256 = `13AD5B564732CFDAFEEF21185F1091FCE49F398A092FE7F189CC29455DD85DC2`。
- 装服实测：①完全退出客户端重启 → cells/components/housings 三标签各自"物品 256k→16384m、流体 256k→16384m、源质 256k→16384m"（截图复核）；②外壳悬停中文名正常（不再显示原始 key）。

## t104b 外壳名原始 key 补充排查（engineer-content，队长三个可能原因逐一排除）
- **① 客户端 jar**：`M:\AA科技\GTNH\客户端\...\mods\ecoaegtnh.jar` hash = **13AD5B564732CFDAFEEF21185F1091FCE49F398A092FE7F189CC29455DD85DC2** = 最新 t104 构建（含 9 条外壳 lang 键 + 显式排序）——客户端磁盘已是新版。
- **② txloader 语言覆盖**：客户端 `config/txloader` 全量 2854 个 .lang 文件 SimpleMatch 搜索 `storage_housing|estorage_cell|storage_component` = **0 命中**——无 txloader 覆盖遮蔽我们的键。
- **③ 显示名路径**：`ItemEcoStorageHousing` javap 实证**无** getItemStackDisplayName/getUnlocalizedName/getUnlocalizedNameInefficiently 覆写——走 vanilla `Item.getItemStackDisplayName` → `StatCollector.translateToLocal("item." + unlocalizedName + ".name")` → 查询键 `item.ecoaegtnh.storage_housing_item_l9.name`，与 jar lang 键精确匹配（shot-10 显示的原始 key 恰是这个构造出的键——说明键构造正确，只是该客户端会话未加载到新 lang）。
- **结论**：三个可能原因全部排除——代码/资源/lang 均正确；shot-10 的原始 key 来自**旧客户端会话**（截图时客户端进程仍在运行旧 jar 的内存，lang 在启动时加载）。修复动作=**完全退出客户端进程后重启**（非资源重载）；当前 jar 13AD5B56 即最终交付，无需代码改动/重建。
- 创造页排序主问题已在 t104 用显式 displayAllReleventItems 修复（本 jar 含）。

## t105 修复装配线配方静默失败（固体输入 <4 被 validateInputCount 拒绝；engineer-content，队长定位根因）
- **根因（实证）**：GTRecipeConstants.AssemblyLine 的 IRecipeMap 用 `validateInputCount(4, 16)`（GT5U 5.09.54.20 源码 L562 + 字节码 bipush 16 实证）——装配线配方**固体输入必须 4~16 个**。t100 部分装配线配方固体输入 <4 → addTo(AssemblyLine) 时被**静默丢弃**（tryAddAL 的 registeredRecipes 仍 +1，所以日志显示 73 但装配线表里没有）——这正是"部分配方没有"的真根因（t101b/t102 的 0-skip 判断被计数误导）。
- **受影响配方（固体输入 <4）与修复（补足到 4+，保持链式/研究/流体/eut/时长不变）**：
  - 组件 16M..256M（3→4）：+`Circuit_Ultimatecrystalcomputer×1`
  - 组件 1024M..16384M（3→4）：+`Circuit_Biowaresupercomputer×1`
  - 外壳 L6（2→4）：+`Electric_Motor_ZPM×1` + `Electric_Pump_ZPM×1`（源质 5）
  - 外壳 L9（2→4）：+`Electric_Motor_UV×1` + `Electric_Pump_UV×1`（源质 5）
  - 盘 16M..256M（2→4）：+`Electric_Motor_ZPM×1` + `Sensor_ZPM×1`
  - 盘 1024M..16384M（2→4）：+`Electric_Motor_UV×1` + `Sensor_UV×1`
  - 顺带核查：电容 B/C（6 固体 ?）、控制器 L6/L9（9 固体 ?）本就 ≥4。
- **装配线配方总数**：组件 M+big 18 + 外壳 L6/L9 6 + 盘 M+big 18 + 电容 2 + 控制器 2 = **46 条装配线**（全部 ≥4 固体）；组装机 27 条；总 73。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（24s）；javap：新增部件引用全在（Ultimatecrystalcomputer/Biowaresupercomputer/Motor_ZPM×3/Pump_ZPM×2/Motor_UV×3/Pump_UV×2/Sensor_ZPM×2/Sensor_UV×2）；jar `build/libs/ecoaegtnh.jar`（169523 B）SHA256 = `0428E1B7E3B52029EBD66894D1A52662D181868414C359C22F142BC7D06B5F61`。
- 装服实测：服务器日志 `ECO recipes registered: 73 total ..., skipped=0`；NEI 装配线页实际出现全部 46 条装配线配方（L6/L9 外壳、16M..16384M 盘/组件、控制器、电容，含研究物品+流体输入）；k 盘/组件组装机页正常。

## t105b 装配线配方覆盖审计 + 真实入库验证（engineer-content，队长要求确认覆盖）
- **覆盖审计（全部 46 条装配线配方固体输入 ≥4，逐类核对）**：
  | 配方 | 数量 | 固体输入 | ≥4 |
  |---|---|---|---|
  | 组件 16M..256M | 9 | 上一档组件+铱板+锘尘+Ultimatecrystal 电路 | ? 4 |
  | 组件 1024M..16384M | 9 | 上一档组件+中子素板+硅岩合金尘+Bioware 电路 | ? 4 |
  | 外壳 L6 | 3 | 铱板+石英+马达ZPM+泵ZPM（源质+瓶） | ? 4-5 |
  | 外壳 L9 | 3 | 中子素板+石英+马达UV+泵UV（源质+瓶） | ? 4-5 |
  | 盘 16M..256M | 9 | 外壳+组件+马达ZPM+传感器ZPM | ? 4 |
  | 盘 1024M..16384M | 9 | 外壳+组件+马达UV+传感器UV | ? 4 |
  | 电容 B / C | 2 | 外壳×2+档位板×2+尘/块+发射器+场发生器 | ? 6 |
  | 控制器 L6 / L9 | 2 | 外壳×4+驱动+电容+ME总线+马达×2+泵+传感器+发射器+机械臂 | ? 9 |
  ZPM 档用 ZPM 件、UHV 档用 UV 件（GT5U 无 UHV 马达/泵——t98 已用 UV 件集，防鸡生蛋）；研究链（低一档同类型）、流体、eut、时长不变。
- **真实入库验证（t105b 升级）**：`GTRecipeBuilder.addTo(IRecipeMap)` 返回**实际加入的配方集合**——tryAddAssembler/tryAddAL 现按返回值计数：空集合 → `skippedRecipes++` + 日志 "was NOT added to the ... map (input validation or duplicate)"（任何配方被静默丢弃都会显式告警，不再被成功计数掩盖）；非空 → 按集合大小计入 registeredAssemblerRecipes/registeredALRecipes。汇总日志改为 `ECO recipes registered: {} assembler + {} assembly-line = {} total ..., skipped={}`——下次启动服务器日志直接证明 **27 组装机 + 46 装配线 = 73、skipped=0**（装配线配方真实进入 AssemblyLine 表）。
- **验证**：`gradlew spotlessApply build`（JDK21 守护）→ **BUILD SUCCESSFUL**（19s）；javap：registeredAssemblerRecipes/registeredALRecipes 字段、addTo 返回值计数、"NOT added" 告警字符串在类内；jar `build/libs/ecoaegtnh.jar`（169804 B）SHA256 = `2094B3FF0C99892BE02B4C92A37A3EF83CAF1F7A42F1161F738B52226C229276`。
- 装服实测：重启后服务器日志出现 `ECO recipes registered: 27 assembler + 46 assembly-line = 73 total ..., skipped=0`（无 "NOT added" 告警）；NEI 装配线页 46 条配方全现（L6/L9 外壳、16M..16384M 盘/组件、控制器、电容），k 档组装机页正常。

## t106 (2026-08-29): 外壳紫黑块+原始key 根因 = tierLabel 双 l bug
- 现象：NEI 搜'存储外壳' 9 槽全紫黑棋盘格，tooltip 显示 item.ecoaegtnh.storage_housing_fluid_l4.name 原始 key（此前多轮误判为客户端缓存）。
- 根因：ItemEcoStorageHousing.tierLabel() 返回 'l4'/'l6'/'l9'（自带 l 前缀），而调用处 '_l'+tierLabel() 拼接成 '_ll4'（双 l）。注册名 storage_housing_item_ll4 与 lang 键/贴图文件名 storage_housing_item_l4（单 l）不匹配 → lang 键找不到显示原始 key、贴图找不到显示紫黑块。组件/盘用 size 后缀无此 bug，故仅外壳异常。
- 修复：tierLabel() 改返回纯数字 '4'/'6'/'9'，'_l'+'4'='_l4' 与 lang/贴图匹配。
- 证据：客户端 fml 日志 Fixed item id mismatch ecoaegtnh:ecoaegtnh.storage_housing_item_ll4/ll6/ll9；javap 反编译确认。
- 新 jar SHA=06C831A532BA24476170E995E4E12D8713331AD33D3853F3EEFE97B50D362AFE，双端已装，服务器 12:38 重启 Done。

## t106b (2026-08-29): 外壳复测通过（用户确认，t106 闭环）
- 用户按流程（任务管理器杀净 javaw → 重启客户端）复测：NEI 搜"存储外壳"**9 个外壳贴图 + 中文名全部正常**，无紫黑块、无原始 key。
- t106 双 l bug 正式结案；外壳显示问题全部关闭（t101/t104/t104b/t106 系列到此为止）。
- 当前交付 jar 维持 `06C831A532BA24476170E995E4E12D8713331AD33D3853F3EEFE97B50D362AFE`，无需重新构建。

## t107 (2026-08-29): 用户拍板 4 项待决 + P2-13 落地 GPL-3.0（队长）
- 用户决定：**① 盘贴图不换**（7 选 1 作废，维持现状 27 张）；**② 组件贴图 AE 风确认 OK**（t101 的 27 张不动）；**③ P2-11 不修**（拆电容丢 AE 能量可接受，MVP 阶段不处理，结案）；**④ P2-13 代码按 GPL-3.0 发布**。
- P2-13 落地：
  - 新增根目录 `LICENSE` = gnu.org 官方 GPL-3.0 全文（35,149 B，2026-06-29 版正文）。
  - `README.md` 许可说明重写：本项目（代码+原创贴图）GPL-3.0；贴图原创声明改指 `textures/README.txt`（原引用的 `blocks/ATTRIBUTION.txt` 路径不存在，一并修正）；存储盘描述同步更新为 27 种（9 尺寸×3 类型，含源质盘）。
  - `mcmod.info` 增加 `"license": "GPL-3.0"`。
- 未改任何运行时代码，交付 jar 维持 `06C831A5...62AFE`，无需重构建/重装机。
- 剩余待办仅：MVP 后续扩展（未排期，用户后续再定）。

## t108 (2026-08-29): 新项目启动 — E-Calculator（可扩展计算子系统主机）阶段 1 调研 + 实现（团队 gtnh-eco-calc）
- **项目**：把 1.12.2 参考仓库 ECOAEExtension 的 E-Calculator（可扩展计算子系统主机，C4/C6/C9，原版 lang 显示名 CE4/CE6/CE9）移植到 GTNH 1.7.10。用户拍板 3 条设计约束：UI 仿 E-Storage（MUI1 量子计算机同款）、结构仿 E-Storage（TTMultiblockBase+StructureLib 西向扩展列）、方块外观仿 1.12.2 原版（原创贴图规避 GPL）；等级命名 **C4/C6/C9**（非 E-Storage 的 L4/L6/L9，注册名 _c4/_c6/_c9、显示名 "ECO xxx (C4)"）。
- **阶段 1（团队，全部通过双轮评审）**：t1 源码调研（docs/ECALCULATOR_RESEARCH.md，核心结论：ECalculator=AE2 合成 CPU"增强+宿主替换"——mixin 注入 ECPUCluster 接口、vCPU 实例经 CraftingGridCache.updateCPUClusters 混入 AE2 调度器、submitJob 拦截分配线程核心，复用原版任务语义）；t2 网络查证（docs/ECALCULATOR_WEB_NOTES.md：原版无独立发布，属整合包「新星工程：世界」ECO 技术线）；t3 实现前备份（D:\DeepSeek\GTNH-ECO-backups\GTNH-ECO-mvp-20260829-130529\，工作区无 git 的唯一回滚保险）；t4 移植方案 v1.5（docs/ECALCULATOR_PORT_PLAN.md，评审 t5 needs_revision → t6 修复 → t7 复审 pass）。
- **实现阶段（用户批准，决策：只做 C4 / 拆方块取消任务 / 新建「计算」tab / 纯 AE 供电）**：t8 阶段 A（12 新类：6 方块+TileEcal* 4+MTEEcalArray TTMultiblockBase+StructureLib 12 shape 24~90 格+RegistryEcal MTE 32033=ecoaegtnh.ecalculator.array.c4+lang，唯一既有改动 EcoAERegistry +2 行钩子）；t9 阶段 B（ECPUCluster+EcoTimeRecorder+M1 MixinCraftingCPUCluster priority=2000 remap=false 宿主重定向 5 处+M2 MixinCraftingGridCache+vCPU 生命周期（创建→分配→执行→销毁→网格通知，拆解取消不持久化，40-tick 补位）+核算（并行 256→accelerator、晶阵 C4 64M 门控、10% 红线 long）+ItemEcalCell c4）；t10 贴图（model-artist 10 张原创 16×16：6 blocks ecal_*+3 控制器 front/off/side+1 item，原版比对最高一致率 ≤41.4% 零拷贝，vision 两轮像素级验收，脚本 tools/gen-textures-ecal.ps1）；t11 GUI（MUI1 量子计算机同款：screen_blue 198×192、Scrollable+底部参数条+4 LED 悬停、FakeSyncWidget 零自定义网络包、任务行物品名按查看者语言翻译）；t12 配方（8 条：7 EV 组装机+1 LuV 装配线控制器 research=外壳，并入 'ECO recipes registered' 计数管线）+「计算」tab（displayAllReleventItems 显式排序）+lang/tooltip 打磨（N1/N2 落实）。
- **装机（t108 进行中）**：jar SHA `CC672332A27F116B7DC70CBE83E0152A97023A922271CD5459CEDB7FD25EE812`（231,862 B，14:45 构建），双端已装三方一致，服务器 14:5x 重启中。验证项：'ECO recipes registered' 计数（预期 34 assembler + 93 assembly-line = 127 total skipped=0）、mixin apply 日志、无 id 冲突；客户端实测：结构成型（1~12 段）、GUI 布局、合成终端提交任务→vCPU 运行→完成/取消、创造页「计算」tab、贴图。

## t109 (2026-08-29): E-Calculator 首轮实测反馈修复（用户 5 点，t13-t17）
- 用户实测反馈：①结构方向与存储不一致（存储左、计算右→拍板**统一向右**，以面朝控制器正面为准扩展列在右手边，与 t30 存储右扩一致）；②C4 控制器蓝色 vs 整体白色不协调→拍板**整体白色系**；③晶阵驱动器应分面（正面≠其它面）；④闪存晶阵无法 shift+右键 放/取（参考存储阵列）；⑤功能方块需 tooltip（如并行核心提供多少并行）。
- 修复（团队，全部 build 通过 + acceptance 全过）：
  - **t13**：晶阵驱动器 shift+右键 放/取（BlockEcalCellDrive.onBlockActivated sneaking + TileEcalCellDrive.interactWithCell：成型门控→等级门控 C4+→放入/取出；字节核算链路 onCellChanged→controller.onCellDriveChanged 不变；拆方块掉晶阵回归保持；lang ±4 聊天提示键）。
  - **t14**：功能方块 tooltip（新增 ItemBlockEcal addInformation 按类型分发；并行 256/线程 1 动态取值（C6/C9 自动适配）；RegistryEcal 6 块改 registerBlock(block, ItemBlockEcal.class, name)；lang ±6 键）。期间一次瞬时增量编译失败=并行编辑陈旧状态（MTEEcalArray 被 t15/t17 并行改），clean 重建稳定，非缺陷。
  - **t15**：晶阵驱动器分面渲染（朝向 meta 2-5 + onBlockPlacedBy + getIcon 正面 ecal_cell_drive_front 其余 ecal_cell_drive；registerBlockIcons 图集注册）；必要配套：MTEEcalArray 'D' 结构元素 ofBlock(Drive,0)→DriveElement（meta 无关 check + 按控制器正面朝向放置，E-Storage t25/t32 模式——否则带朝向驱动器无法成型）。
  - **t16**：贴图白色系统一（控制器 3 张改浅灰白系 light 84-98.8% dark 0%，与 casing 同族；成型/非成型区分由屏幕内容承载）+ 新增 ecal_cell_drive_front.png；vision 抽查 11/11 PASS（含像素级原创性 0% vs 原版）；预览图 11 槽 100% 同步。
  - **t17**：结构方向统一向右（shape 镜像到锚点 A- 侧=机器右手侧（DESIGN §2.5 映射）；锚点 (n+1,1,0)；ME 通道 (n,1,1)=背侧列侧右角与存储一致；checkMachine/construct/scan 偏移同步；ShapeCheckT17 harness 1..12 全过；lang 方向键改"右手侧"）。
- **装机（t109 进行中）**：jar SHA `9027A7216A255C25E7D7763C94FF7CD906B5747626C967A9A4AD5F766E31F560`（238,380 B，15:19 构建），双端已装三方一致，服务器 15:2x 重启中。
- 待办：方案文档 docs/ECALCULATOR_PORT_PLAN.md §5.1"西向"已被新拍板取代，需后续修订（t17 建议）。

## t110 (2026-08-29): E-Calculator 实测反馈修复轮全记录（t13-t34，用户实测驱动）
- **首轮 5 项反馈（t13-t17）**：①方向统一向右（与存储一致，t17 shape 镜像 harness 验证）；②贴图整体白色系（控制器改浅色，t16 vision 11/11）；③晶阵驱动器分面（t15 朝向 meta + 正面贴图）；④shift+右键 放/取晶阵（t13 交互 + t19 filled 两态贴图）；⑤功能方块 tooltip（t14 ItemBlockEcal）。
- **交互无效根因（t20）**：手持物品 shift+右键 时 vanilla 跳过 block.onBlockActivated——晶阵物品缺 `doesSneakBypassUse→true`（E-Storage t25 同款修复，此前漏在 ItemEcalCell）。
- **控制器贴图两连坑**：t18 接线后放置显示钛——①t21 改实例字段（registerIcons 只在原型实例调用→世界实例 null）→ t26 改静态数组按 tier；②静态数组带内联初始化触发 **SideTransformer 剥离 @SideOnly(CLIENT) 字段后 <clinit> 悬空 putstatic → 服务端类加载即崩（NoSuchFieldError）**→ t30 去内联初始化 + registerIcons 惰性分配（全项目审计仅此 3 字段命中）。
- **C6/C9 扩档（t21-t24）**：控制器 32034/32035、并行 2048/16384、线程 2/4、超线程核心（+2/+4/+8，C9 含 1 普通，任务字节 +10%）、B/C 晶阵（1024M/16384M 门控 C6+/仅 C9）、结构档位匹配 harness 3 档×12 段、12 条配方（ZPM/UHV AL）、GUI 档位行。
- **AE 终端 CPU 区分（t25）**：M3 MixinCraftingCPUStatus（ecLevel+线程数 NBT 双路径，双侧组）+ M4 MixinGuiCraftingCPUTable（档位色行 青/金/紫 + "ECO Cx CPU" 名称 + 线程 tooltip）——M4 目标按 rv3 实际渲染路径落在 GuiCraftingCPUTable。
- **晶阵 9 尺寸（t27-t29）**：k 级 256k/1024k/4096k（×1024）+ M 级 16M/64M/256M + 大M级 1024M/4096M/16384M（×1000×1024）；门控 k→C4+/M→C6+/大M→仅 C9；旧 3 档晶阵迁移；9 条链式配方（EV 组装机/ZPM/UHV AL）；贴图 k 青/M 金/大M 紫分级。
- **「可用线程」语义三连改（t31-t33）**：0（待命无线程核心）→ 主机单核心线程数 → 主机总线程数 → **动态剩余（总-占用）+ 运行中行隐藏该行**（hyper 占用标志区分普通/超线程槽）。
- **CraftingAllow 持久化（t34，用户确认正常）**：GTNH AE2「接受请求」模式存 vCPU 实例内存，虚拟 vCPU 补位重建即重置——提升到控制器 NBT 持久化（saveNBTData/loadNBTData）+ createVirtualCPU 应用 + M1 @Inject changeCraftingAllowMode 回写。
- **装机 SHA 链**：t20=9027A721 → t18=D74D9C78 → C6/C9=7BA9FA19 → 全功能=8F923231 → t30=94657A4D → t31=A4A8223B → t32=819F4F16 → t33=4028482C → **t34=21C5AEAB（当前装机版）**。
- **启动脚本**：服务端根目录 `start-server.bat`（GBK+CRLF，Java 25 完整参数，双击启动、stop 关服）——T30 踩坑：pwsh 直调 java 拆坏 -Dfml.readTimeout 参数，必须 cmd /c 或脚本启动。
- **玩家在线实测**：vCPU 补位循环（assigned→created→destroyed）、4096k 晶阵插入、CraftingAllow 三种模式持久化全部正常。





## t111 材料窗口 shift+单击背包物品直入暂存（godforge 对齐；engineer-core，用户问"神锻能做到吗"）
- **需求**：神锻的材料窗口支持 shift+单击背包物品 → 直接进入暂存槽（MUI 快速转移）。我们的 16 格暂存已是真实槽（SlotWidget + BaseSlot + ItemStackHandler），需确认 MUI1 转移链路是否走通。
- **机制核实（MUI1 1.3.4 源码 + 字节码）**：SlotWidget 非 phantom 点击返回 DELEGATE → ModularGui.mouseClicked 走 vanilla super.mouseClicked → GuiContainer 槽点击（NEI mixin 转发）→ PlayerControllerMP.windowClick（shift → QUICK_MOVE）→ ModularUIContainer.slotClick → vanilla Container.slotClick QUICK_MOVE → transferStackInSlot（MUI1 覆写）→ transferItem 遍历 sortedShiftClickSlots（shiftClickPriority > MIN_VALUE 的 canInsert BaseSlot）按优先级升序分组填槽。**背包槽天然存在**：GT CommonBaseMetaTileEntity.createWindow 先 bindPlayerInventoryUI 再调 addUIWidgets。
- **发现的问题**：主窗口 controller 槽（canInsert=true、priority=0、先注册）会**截胡** shift 转移——背包物品会先进 controller 槽而非暂存槽。
- **改动（3 处）**：① UpgradeTreeGui.createMaterial 暂存槽 setShiftClickPriority(-1)（负优先级 → 转移最先尝试暂存，且不会破坏同 handler 跳过逻辑）；② MTEEcoStorageArray.addUIWidgets controller 槽 .disableShiftInsert()；③ MTEEcalArray.addUIWidgets controller 槽 .disableShiftInsert()（手动点击不受影响，仅 QUICK_MOVE 转移禁用）。
- **结果**：shift+单击背包物品 → 暂存槽（16 格自动找空位/堆叠同类）；shift+单击暂存槽物品 → 回背包（反向同机制）。需求槽是 ButtonWidget（非槽）不参与。
- **验证**：gradlew compileJava --offline → BUILD SUCCESSFUL（22s）。

## t112 存储升级树改为"1 个盘 1 个节点"（27 节点；engineer-core，用户要求）
- **需求**：存储升级树从 12 节点（3 类型 × 4 档：K级/M级/大M/16384M，一档管 2-3 个尺寸）改为 **每盘一节点** —— 3 类型 × 9 尺寸 = 27 节点。
- **新结构**：I1..I9 / F1..F9 / E1..E9（物品/流体/源质三条独立链），I1/F1/E1=256k 免费基础节点，链式依赖（I9 ← I8 ← … ← I2 ← I1）。节点序号 = CellSize 枚举序号+1。
- **改动**：① StorageUpgradeTree.java 重写为循环生成 27 节点 + 8 档成本阶梯（铁→铝→钛→铱→中子素 + 处理器/逻辑处理器/电路板，随深度递增，装机后调）；② ItemEcoStorageCell.getRequiredUpgradeNode() 改为 prefix + (size.ordinal()+1)；③ UpgradeTreeGui.storagePositions() 每列 4 行 → 9 行（3 列 × 9 行 = 324px 高，overview 窗口本就可滚动）；④ en/zh lang 各 27 节点 × name/effect/short = 81 条（替换旧 12 节点 36 条）。
- **NBT 兼容**：旧 I1/F1/E1（已激活）在新树仍是免费节点 → 自动保持激活；旧 I2-I4 激活记录被 readFromNBT 忽略（未知 ID）→ 1024k 及以上需重新解锁（装机后注意）。
- **验证**：gradlew spotlessApply build（JDK21 守护）。

## t113 新增"人造宇宙"档盘（物品/流体/源质 3 个；engineer-core，用户要求 + 参考 AE2U 宇宙盘）
- **需求**：新增 3 个盘（物品/流体/源质），名为"人造宇宙xx盘"，容量 576,460,752,303,423,487 字节（= 2^59-1）。用户随后指出 GTNH AE2U 本身就有同容量宇宙盘，参考其参数。
- **AE2U 参考（rv3-beta-1000-GTNH ApiItems 字节码实证）**：ItemExtremeStorageCell 四档 —— Container(65536B, 1type, perType 8, drain 2.0) / Quantum(134217727B, 1type, 8, 1000) / Singularity(576460752303423487B, 1type, 4096, 15000) / **Universe(576460752303423487B, 63 types, perType 16384, drain 600000.0)**。用户给的字节数 = AE2U Universe 容量（完全一致）。
- **我们的实现（对齐策略：容量完全一致，perType/类型数/功耗保留 ECO 体系）**：
  - CellSize.UNIVERSE("universe", 16384, false, 512, 2, 576_460_752_303_423_487L) —— 容量与 AE2U Universe 一致；byteMultiplier 512（延续 t91 ×2 阶梯，perType=512×1024=524288 B/类型，与 AE2U 的 16384 不同但符合 ECO 内部一致性）；value=16384 → idleDrain 4096 AE/t（同 16384m 档，AE2U 是 600000 AE/t，装机后可按需调）；tier=2（L9）。
  - 类型数保持 ECO 体系（用户上一轮"类型和eco其他盘一样"）：物品 315 / 流体 25 / 源质 L9=100（AE2U Universe 是 63）。
  - 注册：RegistryItems 的 CellSize.values() 循环自动注册 3 盘 + 3 组件；创造栏自动排末尾。**无配方**（用户自改配方）。
  - 升级树：I10/F10/E10（前置 I9/F9/E9，成本 = 铱 48 + 中子素 16 + 逻辑处理器 24，占位值）；GUI 每列 10 行（可滚动）。
  - lang：3 盘名（人造宇宙物品盘/流体盘/源质盘）+ 3 组件名 + 3 节点 × name/effect/short（中英各一套）；effect 注明字节数。
  - 贴图：6 张（3 盘 + 3 组件）从 16384m 版复制并金色化（avgRGB 金调），占位可再换。
  - formatBytes 增加 P（1e15）级显示 → tooltip 显示 "576.5P"。
- **验证**：gradlew spotlessApply build（JDK21）→ BUILD SUCCESSFUL；javap 确认 CellSize.UNIVERSE 常量、getRequiredUpgradeNode=ordinal+1、StorageUpgradeTree 30 节点循环。

## t114 三新元件 + 宇宙盘改名（engineer-core，用户逐项拍板 + 参考 AE2U/TE4/AE2FC 字节码实证）
- **① ECO 奇点闪存晶阵**（ECal 晶阵第 10 档）：容量 **Long.MAX_VALUE**（对齐 AE2U 奇点合成存储器 BlockSingularityCraftingStorage.getStorageBytes=9223372036854775807，字节码实证）；升级树 **N11**（前置 N10，N11 激活后字节池 cap=Long.MAX）；tier C9。
- **② 宇宙盘改名**：人造宇宙物品/流体/源质盘 → **ECO 人造宇宙物品/流体/源质盘**（中英）。
- **③ ECO 魔导源质盘**（estorage 源质链第 11 档 E11，前置 E10）：复刻 TE4 **创造源质元件**（Type_Creative 字节码实证：capacity=0 特殊库存、maxStoredTypes=Aspect.aspects.size()、idleDrain=0、epic、无配方）→ 我们的盘位盘实现：容量 Long.MAX_VALUE（等价无限）、getTotalTypes=Aspect.aspects.size()（全部源质）、idleDrain=0、不可编辑。
- **④ ECO 无限水流体盘**（estorage 流体链第 11 档 F11，前置 F10）：复刻 AE2FC **ItemInfinityWaterStorageCell**（字节码实证：FluidCellInventoryHandler(CreativeCellInventory) + InfinityConfig 固定水桶 + 1 type + isEditable=false）→ 我们的盘位盘实现：容量 Long.MAX_VALUE、getConfigAEInventory 返回 FixedWaterConfig（IAEStackInventory 单槽预置水 → CellInventory partition 只存水）、getTotalTypes=1、idleDrain=0、不可编辑。
- **架构改动**：estorage CellSize 加 INF_WATER/ARCANE 两档并引入 **CellSize.allowed(StorageType)** 家族门控（INF_WATER→仅流体、ARCANE→仅源质；注册/创造栏/升级树三处过滤）——否则共享枚举会让物品链也冒出这两个盘。升级树 30→32 节点（I 链 10 + F 链 11 + E 链 11）；GUI storagePositions 每列行数按链（10/11/11），calculatorPositions N 列 11 行。formatBytes 加 E（1e18）级。全部新元件**无配方**（用户说配方都不要，测试阶段创造栏拿）。升级树成本保持 t113c 的 1 铁锭测试档。
- **验证**：gradlew spotlessApply build（JDK21）→ BUILD SUCCESSFUL（修了一处枚举大括号提前闭合的语法错）；javap：CellSize 12 常量+allowed、ecal SINGULARITY、F11/E11/N11 常量、lang 新 key 5+ 条；部署两端 jar 355129 B（13:39）。

## t114b/c 奇点晶阵容量溢出修复 + 多余奇点转 vCPU（engineer-core，用户反馈驱动）
- **t114b**：两个奇点晶阵（Long.MAX_VALUE）相加 long 溢出成负数 → recalculateTotalBytes 改**饱和求和**（到达 Long.MAX 封顶），放几个都正常。
- **t114c（用户需求）**："超出的部分变成新的 vCPU，共享线程"——第 2 个及以后的奇点晶阵不再加容量，每个贡献 1 个额外 standby vCPU：
  - 新字段 singularityVCPUs（List<CraftingCPUCluster>），配额 = max(0, 激活奇点晶阵数 - 1)；
  - efreshSingularityVCPUs（createVirtualCPU 尾部调用）按配额创建/销毁差额；
ewStandbyCluster 提取公共创建（bytes/parallelism/CraftingAllow 继承）；
  - countSingularityCells（ItemEcalCell 新增 getSize()）；提交任务的配额 vCPU 从列表移除（onVirtualCPUSubmitJob + singularityVCPUs.remove）后自动补位；
  - getClusterList 暴露配额 vCPU；disassembleAll 统一走 destroyStandbyVCPU（顺带补了 channel==null 的空指针保护）。
  - "共享线程"语义：所有 standby vCPU 不占槽，提交时抢占线程槽（一槽一任务，其余排队）——现有机制天然支持。
- **验证**：gradlew spotlessApply build → BUILD SUCCESSFUL；javap 确认 singularityVCPUs/newStandbyCluster/countSingularityCells/refreshSingularityVCPUs/ItemEcalCell.getSize 全部在 jar；部署两端 355964 B（13:53）。

## t114h vCPU 命名改造收尾（engineer-core，用户拍板：下单界面/待命="ECO vCPU"，运行中="ECO vCPU #编号"，待命 tooltip 保留可用线程）
- **已有改动（本条目开工前工作区）**：接口 ECPUCluster/ECPUStatus 已加 vCPU id 方法（getVCPUId/setVCPUId）；MixinCraftingCPUCluster 已加 vcpuId 字段 + getName 注入（`standby ? "ECO vCPU" : "ECO vCPU #" + vcpuId`，standby=virtualCPUOwner.isStandbyVCPU(本实例)）+ 接口实现；MTEEcalArray 已加 vcpuIdCounter + newStandbyCluster 分配（`++vcpuIdCounter` → setVCPUId）+ isStandbyVCPU；MixinCraftingCPUStatus 已加 ecVCPUId 字段 + NBT 读写（ecVCPUId key）。
- **①补齐 MixinCraftingCPUStatus 接口实现**：新增 `@Unique @Override public int ecoaegtnh$getVCPUId()`（返回 ecVCPUId 字段）——此前接口已声明但 mixin 未实现，运行行 tooltip/行名取 id 会走接口调用，必须补上。
- **②GUI 行名/tooltip 改造（MixinGuiCraftingCPUTable.ecoaegtnh$ecName）**：行名从 "ECO C4/C6/C9 CPU" 改为 **待命 = "ECO vCPU"、运行 = "ECO vCPU #id"**（tier 色保留：AQUA/GOLD/LIGHT_PURPLE）；tooltip 逻辑不变——**待命保留可用线程行**（ecoaegtnh.gui.ecal.cpu.threads[_hyper]，GRAY），运行行只显示名（占用的线程不算"可用"）；Javadoc 同步更新。
- **验证**：JDK21 `gradlew spotlessApply build --offline` → **BUILD SUCCESSFUL**（20s）；javap 逐项确认——①MixinCraftingCPUStatus 含 `ecoaegtnh$getVCPUId()` + ecVCPUId 字段；②MixinCraftingCPUCluster.injectGetName 字节码含 `String ECO vCPU` / `String ECO vCPU #` + vcpuId 拼接 + isStandbyVCPU 调用；③MixinGuiCraftingCPUTable.ecName 字节码含 `ECPUStatus.ecoaegtnh$getVCPUId()` 接口调用 + 两段字符串；④MTEEcalArray 含 vcpuIdCounter 字段（ctor 清零）+ newStandbyCluster 内 `++vcpuIdCounter → setVCPUId` 调用 + isStandbyVCPU；⑤ECPUStatus/ECPUCluster 接口含 getVCPUId（+setVCPUId）抽象方法；jar 条目时间戳 16:36:54 与 jar 文件一致。部署两端（服务端 + 客户端 2.9.0-beta-2）359030 B，SHA256 = `A2662E11A4B679D38FEA25AEF2C77876DD1BC1E50BB10A987F577E87290AD52C`。

## t114i vCPU 编号无限增长修复：最小可用编号池 + 全销毁路径回收（engineer-core，用户反馈"编号一直往下"驱动）
- **问题**：t114h 的 `vcpuIdCounter` 只增不减——待命 vCPU 每次重建（createVirtualCPU → newStandbyCluster）都 `++vcpuIdCounter` 且**从不释放**；任务完成后簇销毁编号不回池，导致运行中 vCPU 显示 "ECO vCPU #N" 的 N 无限增长。
- **修复（最小可用编号池）**：
  - `MTEEcalArray` 新字段 `freeVCPUIds`（PriorityQueue<Integer>，被释放的编号，最小者先出）+ 复用 `vcpuIdCounter`（只记录已发出的最大编号，仅在无释放编号可复用时递增 → 池大小受**同时运行 vCPU 峰值**约束，与重建次数无关）；
  - `allocateVCPUId()`：释放编号优先（poll 最小），否则 `++vcpuIdCounter`；`releaseVCPUId(cluster)`：把簇编号回池并清零（id<=0 幂等返回）；
  - **待命不占编号**（比"待命销毁即释放"更强）：`newStandbyCluster` 不再分配，`setVCPUId(0)`——待命永远显示 "ECO vCPU"，重建/销毁零开销零泄漏；
  - **运行才编号**：`onVirtualCPUSubmitJob` 开头分配最小可用编号（仅当簇原本无编号；若最终无槽可分配则原样退回 `releaseVCPUId`）；
  - **统一释放钩子**：`MixinCraftingCPUCluster.injectDestroy` HEAD 统一调 `owner.onClusterReleased(cluster)`（合并原 onBuiltinClusterDestroyed 职责：回池编号 + 释放内置槽 + 按需发网格变更事件；原方法删除）。覆盖全部销毁路径：外置槽（core!=null → onCPUDestroyed 前编号已回池）、内置槽（core==null 分支）、待命（id=0 幂等空操作）、任务完成/取消（updateCraftingLogic→destroy / cancel→destroy）、拆机（disassembleAll 末尾清空池 + 复位计数器）；
  - 显示语义不变：待命 "ECO vCPU"、运行 "ECO vCPU #id"（id 为池内最小可用，同时活跃编号从 1 连续）、待命 tooltip 可用线程行不变；`getName` 加 `id<=0` 兜底（未分配簇不显示 "#0"）。
- **改动文件**：MTEEcalArray.java（字段/分配/回收/拆机复位/onVirtualCPUSubmitJob）、MixinCraftingCPUCluster.java（injectDestroy 统一钩子 + getName 兜底）、ECPUCluster.java（javadoc）、docs（本条）。
- **GUI 段（t2 同条目追加）——线程行拆分**：`threadRow()` 只保留线程段（"线程：内置 u/t · 外置 u/t · 总计 u/t"），超线程段拆到新 `hyperRow()`（"超线程：内置 u/t · 外置 u/t · 总计 u/t"）；drawTexts 追加第二个 `TextWidget.dynamicString(() -> hyperRow())`（setSynced(false)），**复用现有 syncBuiltin*/syncHyper* 字段，无新增 sync**；**无超线程槽时不出现空行**——widget 用 `.setEnabled(w -> syncHyperTotal > 0)` 动态禁用，MUI1 1.3.4 `DynamicPositionedColumn`（ctor `skipDisabledChild=true`，`Column.layoutChildren` 直接按 `Widget.isEnabled()` 跳过禁用子项，字节码实证）在布局中完全跳过该行；hyperRow() 内部另有 `syncHyperTotal <= 0` 返回空串兜底。lang 键沿用现有 `ecoaegtnh.gui.ecal.threads(.builtin/.external/.total/.hyper)`，无 lang 改动。
- **GUI 段验证（t2）**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → **BUILD SUCCESSFUL**（20s）；javap 确认——`threadRow()`/`hyperRow()` 两个私有方法均在；drawTexts 字节码含 `lambda$drawTexts$82`（读 `syncHyperTotal` 返回 >0 布尔）→ `TextWidget.setEnabled(Function)` 调用、`lambda$drawTexts$81` → `hyperRow()` 文本供应、以及既有 `lambda$drawTexts$80/$79`（syncBuiltinHyperTotal 同步器，复用未增）；最终部署两端（服务端 + 客户端 2.9.0-beta-2）jar 359665 B，SHA256 = `FEBEEF4B04618D5A0832A68BFCDF3E22C14AD14B692D635D65808895C963F098`（两端一致，覆盖 t1 的 359464 B 部署）。
- **保留的非阻塞观察（t114h 审查记录，本次不处理）**：编号不持久化（重启清零，虚拟簇本就不持久，一致）；AE2 行名 12 字符截断；待命簇被网格外部 destroy 不置空 virtualCPU。
- **验证**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → **BUILD SUCCESSFUL**（21s，spotless/checkstyle 全过）；javap 确认 MTEEcalArray 含 `freeVCPUIds`（PriorityQueue<Integer>）字段 + `allocateVCPUId/releaseVCPUId/onClusterReleased` 方法且 `onBuiltinClusterDestroyed` 已删除；MixinCraftingCPUCluster.injectDestroy 字节码在 HEAD 调 `MTEEcalArray.onClusterReleased`、injectGetName 含 "ECO vCPU"/"ECO vCPU #" 两段字符串 + id 拼接；部署两端（服务端 + 客户端 2.9.0-beta-2）jar 359464 B，SHA256 = `4B517D39DEC1092D113A57DE6214BF98A753A63A46355DD99125DFE3F7526473`（两端一致）。

## t114j 内置线程槽任务完成/取消后不释放修复（engineer-core，用户实测"内置线程下单结束后可用线程变 0"驱动）
- **问题**：内置线程槽（builtinThreadClusters/builtinHyperClusters）上的任务完成后，可用线程数变成 0 且永不恢复——内置槽 cluster（core==null 但 virtualCPUOwner!=null）被两处注入的守卫 `ecoaegtnh$core == null → return` 排除在回收逻辑外：
  1. `injectUpdateCraftingLogicStoreItems`（updateCraftingLogic @HEAD，cancellable）：`isComplete && inventory.isEmpty()` → `destroy()` 的回收只对外置槽（core!=null）生效；
  2. `injectCancel`（cancel @RETURN）：`inventory.isEmpty()` → `destroy()` 同样只对外置槽。
  内置槽 cluster 任务完成后 isComplete=true、inventory 清空后永不走 destroy → `builtinThreadClusters`/`builtinHyperClusters` 残留占用 → `getBuiltinThreadsUsed()` 恒为满 → 可用线程 0。
- **修复（守卫放宽，两处）**：`if (this.ecoaegtnh$core == null && (this.ecoaegtnh$virtualCPUOwner == null || this.ecoaegtnh$virtualCPUOwner.isStandbyVCPU((CraftingCPUCluster) (Object) this))) return;` ——
  - **内置槽 cluster**（core==null、virtualCPUOwner!=null、非待命）→ 通过守卫 → 完成/取消路径走 `destroy()` → `injectDestroy` HEAD → `owner.onClusterReleased`（内置槽列表移除 + 编号回池 + 网格变更事件）；
  - **待命 vCPU 必须排除**（这是与"仅放宽到 virtualCPUOwner!=null"的关键差异）：AE2U rv3-beta-1000 字节码实证 `CraftingCPUCluster.<init>` 把 `isComplete` 初始化为 **true**（javap：`iconst_1 → putfield isComplete`），待命 vCPU 是 `new CraftingCPUCluster(pos,pos)` 且 inventory 为空、myLastLink 为 null——若守卫只放宽到 `virtualCPUOwner==null`，待命簇第一个 tick 就会命中 `isComplete && inventory.isEmpty()` → 被误销毁 → 控制器失去待命 vCPU、下单全挂。用 `isStandbyVCPU`（`virtualCPU == cluster`，MTEEcalArray:991）精确排除待命；
  - **vanilla cluster**（两者皆 null）→ 守卫原样 return，行为不变。
- **改动文件**：src/main/java/ecoaegtnh/mixin/MixinCraftingCPUCluster.java（两处守卫 + 注释）、docs（本条）。
- **验证**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → **BUILD SUCCESSFUL**（23s，spotless/checkstyle 全过）；javap 验证两处注入字节码含 `getfield ecoaegtnh$virtualCPUOwner` 判空 + `MTEEcalArray.isStandbyVCPU` 调用（injectCancel 与 injectUpdateCraftingLogicStoreItems 均为：core==null → owner==null → return / isStandbyVCPU → return，其余走 inventory.isEmpty → destroy）；部署两端（服务端 + 客户端 2.9.0-beta-2）jar 359691 B，SHA256 = `49E670E5212A6F5B457CAD060630EC22F03CA2DDDA12DEBF7DFEA0B52D71AE8D`（两端一致，时间戳 17:33:51）。

## t114k 内置线程不释放 + vCPU 编号跳号修复（engineer-core，用户实测"内置线程依旧没释放"+"编号 123 后变 5 没有 4"驱动）
- **问题（两个独立根因，日志 + AE2U rv3-beta-1000 字节码双证）**：
  1. **内置槽任务冻结**：AE2 网格每 tick 只驱动 `CraftingGridCache.craftingCPUClusters` 集合内的 cluster 执行 `updateCraftingLogic`（`onUpdateTick` 字节码实证——这是任务推进的唯一入口）；该集合由 `updateCPUClusters()` 在 `MENetworkCraftingCpuChange` 事件后重建 = vanilla 物理 CPU + M2 mixin 追加的 `channel.getCPUs()`（= `controller.getClusterList()` = 外置槽 + 待命 vCPU，**不含内置槽列表**）。内置槽 cluster 提交后一旦任何外置任务完成触发事件重建（服务器日志 17:44:07 实证：内置任务 17:43:57 分配后无任何 destroy 记录，而 3 个外置任务全部正常 destroy），就永久脱离驱动集合 → `isComplete` 永不置位 → t114j 的 destroy 注入永不执行 → `onClusterReleased` 永不触发 → **内置槽占用 + 编号丢失**；t114j 只修了销毁侧，没修驱动侧。
  2. **外置槽编号不回池**：`injectDestroy` 只在 `virtualCPUOwner != null` 时 `releaseVCPUId`；外置槽 cluster 只有 `threadCore`（owner==null）→ 外置任务完成 destroy 后编号从不回池 → 编号池只增不减。
- **修复**：
  - `MTEEcalArray.getClusterList()` 纳入 `builtinThreadClusters`/`builtinHyperClusters`（M2 注册后内置任务持续被驱动；顺带内置任务 vCPU 正确显示在 AE 终端 CPU 列表）；
  - `MixinCraftingCPUCluster.injectDestroy`：`owner==null && core!=null`（外置槽）分支经 `core.getController().releaseVCPUId(cluster)` 回池编号（幂等，拆机 `disassembleAll` 后 controller 为 null 时跳过）；
  - `onClusterReleased` 补日志（此前内置槽释放无日志，冻结场景日志留白无法排查）。
- **改动文件**：MTEEcalArray.java（getClusterList + onClusterReleased 日志）、MixinCraftingCPUCluster.java（injectDestroy 外置分支）、docs（本条）。
- **验证**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → BUILD SUCCESSFUL；javap 确认 injectDestroy 含 `TileEcalThreadDrive.getController()` + `releaseVCPUId` 调用、getClusterList 含两个 builtin 列表 addAll；部署两端，SHA256 两端一致。

## t114l vCPU 槽位分配顺序调整（用户指定：内置优先、普通先于超线程）
- **用户要求**：下单时 vCPU 槽位优先级 = ①内置线程 → ②外置线程 → ③内置超线程 → ④外置超线程（此前为外置优先：外置普通 → 外置超线程 → 内置普通 → 内置超线程）。
- **改动**：`MTEEcalArray.onVirtualCPUSubmitJob` 四个分配块按新顺序重排（内置普通块移到最前、内置超线程块移到外置超线程块之前）；Javadoc 同步；超线程 +10% 额外存储/OC 免费逻辑不变；`if (!assigned)` 链与编号分配/退回逻辑不变。
- **验证**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → BUILD SUCCESSFUL（22s）；javap 确认字节码顺序：builtinThreadClusters.size()<getBuiltinThreads() 判断（内置普通）→ threadCores 遍历 addCPU(false)（外置普通）→ builtinHyperClusters 判断（内置超线程）→ addCPU(true)（外置超线程）；部署两端 jar 359860 B，SHA256 = `75B0CF601AA91D3DC2458C9F9FF64C7FB1EF462B0EB9BC155C5E58DD1599971F`（两端一致）。

## t114m 存储阵列方块配方重做（用户逐条提供：5 组装机 + 1 工作台，全部 EV 1920 EU/t 10s 无电路 + 焊锡 576mb）
- **用户规格**（输入=物品 id/矿词，输出=物品 id；metaitem.01 四个 meta 经 IDMetaItem01 字节码实证：damage=ID+32000——32603=马达EV、32673=力场发生器EV、32683=发射器EV、32693=传感器EV；AE2 处理器 damage 22/23/24=逻辑/计算/工程处理器，MaterialType 构造显式 id 实证；AE2 方块经 IBlocks API + lang 交叉验证）：
  1. **casing**（替换旧×2配方）：钛框架1+钛板6+精英电路2+AE2三色处理器各8 → 外壳×1
  2. **me_bus**（替换）：外壳1+AE2 IO端口1+传感器EV1+发射器EV1+大师电路1 → ME总线×1
  3. **capacitance_a**（替换）：外壳1+数据电路4+`batteryData`矿词1+`gt.blockmachines/2360`×16 → 电容A×1
  4. **vent**（替换，旧输出×2→×1）：外壳1+`gt.blockmachines/5153`1+`gt.metaitem.02/21028`1+马达EV1 → 通风口×1
  5. **drive**（替换）：外壳1+AE2 ME驱动器1+力场发生器EV1+大师电路1+精英电路2+传感器EV1+发射器EV1+工程处理器4 → 驱动器×1
  6. **工作台 3×3**（新增 vanilla 合成，GameRegistry.addRecipe + ShapedOreRecipe）：`CAC/FSF/CDC`（C=circuitMaster 矿词、A=AE2 ME控制器、F=力场发生器EV、S=外壳、D=AE2致密能源元件）→ `gt.blockmachines/32030`（RegistryMTE.L4，E-Storage 控制器）×1
- **实现要点**：新增 `tryAddAssemblerNoCircuit` 重载（GTRecipeBuilder.circuit() 必加集成电路，无电路必须不调用）；2360/5153/21028 无 ItemList/IDMeta 映射（非 GT5U 核心注册），用 `cpw.mods.fml.common.registry.GameRegistry.findBlock/findItem("gregtech", ...)` 运行时按数字 damage 构造（null 时配方跳过+警告，与 t7 null-safe 一致）；registerPartsAndControllers 的 soldering/lube 局部变量删除。
- **验证**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → BUILD SUCCESSFUL（22s；首轮失败=1.7.10 的 GameRegistry 在 `cpw.mods.fml.common.registry` 而非 `net.minecraftforge.fml`，已修正）；javap 确认 5 处 tryAddAssemblerNoCircuit 调用、gtMachineBlockStack/gtMetaItem02Stack、registerCraftingRecipes 含 ShapedOreRecipe + "CAC"/"FSF"/"CDC" + "circuitMaster"、GameRegistry.findBlock/findItem（cpw.mods.fml 包）；部署两端 jar 361159 B，SHA256 = `A83F0EA3599EFB2043F4615F3820BB9F221D464EB9B2C3E792246C9D9DCF5EF5`（两端一致）。

## t114n/t114o/t114p 配方体系精简与重建（用户逐条驱动）
- **t114n（用户）**：删除存储阵列组件(27)/外壳(9)/旧控制器(1) + E-Calculator 全部配方（ecal.* 31 条）；保留存储盘配方；Recipes.java 注释全面中文化 + 新增 docs/RECIPE_WRITING_GUIDE.md（配方写法教学）。
- **t114o（用户）**：存储盘 27 条改为**工作台无序合成**（外壳+组件，ShapelessOreRecipe，L4/L6/L9 外壳对应 k/M/大M 级）；新增 256k 组件 + L4 外壳 6 条组装机配方（物品/流体/源质，EV 10s 焊锡 144mb；外壳带电路 1/2/3）；新增 `findItemStack(modid, name, damage, count)`（GameRegistry.findItem 运行时解析 NEI 复制 id）与 `gtFluid(id, amount)`；删除装配线支持（tryAddAL/registeredALRecipes/相关 import），后于 t114p 恢复。
- **t114p（用户）**：存储组件全链 1024k→16384m + 宇宙（物品/流体/源质变种 26 条）：
  - 1024k/4096k：组装机 IV/LuV 10s 无电路；输入 ae2 58/59（Cell256kPart damage 57、1024k=58、4096k=59、16384k=60 字节码实证）+ dreamcraft 处理器（ItemEmeraldCore=物品处理器 III、ItemAdvEmeraldCore=物品处理器 IV、FluidEmeraldCore=流体处理器 II、EssentiaPulsatingCore=源质处理器 I，注册在 GTNHCoreMod 的 dreamcraft assets）+ 工程处理器(24) + 电路矿词；
  - 16m..16384m：装配线（恢复 tryAddAL；研究物品=同类型低一档组件 GTNH 惯例）；ZPM/UV/UHV，60/120s，流体 1080（×576..2592）与 432+1080 双输入（4096m/16384m），GT 部件 32675/32676/32677（力场发生器 LuV/ZPM/UV，IDMetaItem01 damage=ID+32000 实证）、blockmachines 1766/1748/1808、OpenComputers item/103、miscutils MU-metaitem.01/32105（全部 findItem 数字方案）；
  - 宇宙（物品/流体）：**太空组装模块 MK-III** = gtnhintergalactic（GTNH 太空电梯 mod，类打包在 GT5U）的 `IGRecipeMaps.spaceAssemblerRecipes`，`IG_RecipeAdder.addSpaceAssemblerRecipe(输入,流体,输出, MODULE_TIER, duration, eut, project, location)`——MODULE_TIER=3（MK-III）、UXV 120s、流体 818×36864；物品版输入 AE2 人工宇宙盘（ItemExtremeStorageCell 注册序 Container/Quantum/Singularity/Universe → damage 3）+ metaitem.03/6581×64、metaitem.01/32047×6、tectech 时空压缩/稳定场发生器/8×12×2、metaitem.03/4143/4141×2；流体版输入 ae2fc 宇宙流体盘（fluid_storage 注册序 1k..16384k+Universe → damage 8）+ bartworks 超密板/10112×64 + GoodGenerator yotta 流体罐/9×6 + kekztech TFFT/10×6；
  - **变种规则（用户）**：dreamcraft 处理器 物品1 → 流体2 → 源质4（III 系）、1:4:8（IV 系）；其余输入与物品版相同（ae2fc/TE4 组件对应同款）。
- **验证**：JDK21 `gradlew.bat spotlessApply build --offline --console=plain` → BUILD SUCCESSFUL（21s；首轮失败=RegistryItems 方法单参数（itemComponent(CellSize)/itemHousing(int)），已修正 4 处误传 StorageType）；javap 确认 registerComponentChain（26 条配方名）、tryAddSpaceAssembler（IG_RecipeAdder 调用）、tryAddAL、gtFluid；部署两端 jar 359828 B，SHA256 = `8AD8FB855104C45FA00F25E6A7E8DD0B285F0C8025EFA31D19CDC046E22E25B2`（两端一致）。

## t114r/t114s 太空电梯 MK 修正 + 超线程核心线程数翻倍（用户驱动）
- **t114r（用户）**：太空电梯 MK 档位修正——`IG_RecipeAdder.addSpaceAssemblerRecipe` 不写 MODULE_TIER（默认 1 = MK-I），改用 GTRecipeBuilder + `.metadata(IGRecipeMaps.MODULE_TIER, tier)`；宇宙配方 MODULE_TIER=3（MK-III）、奇点=2（MK-II）；NEI 经 ig.nei.module 显示档位。此前宇宙配方因输入 null 被静默跳过（AE2 注册名 ≠ "item.ItemExtremeStorageCell"，改用 `AEApi.definitions().items().cellUniverse()`；ae2fc 用 `ItemAndBlockHolder.ARTIFICIAL_UNIVERSE_CELL`）；tryAddSpaceAssembler 警告改为打印完整输入数组。
- **t114s（用户，当前）**：超线程核心**提供的线程数全部 ×2**（非配方）——hyper_2 = 0+2→**0+4**、hyper_4 = 2+4→**4+8**、hyper_8 = 4+8→**8+16**；注册后缀保持 hyper_2/4/8 不变（`ItemEcalThreadCore` 构造函数改为显式 `(threads, hyperThreads, suffix)`，避免翻倍后从 hyperThreads 推导后缀碰撞；`RegistryEcal.registerThreadCore` 传入 suffix）。升级树节点门槛为数值驱动（≥4→H2、≥8→H3）：新值下 hyper_2→H2、hyper_4/hyper_8→H3，符合档位；里程碑门槛同理（hyperThreads≥4→Lv5，三个超线程核心全部 Lv5+）。物品名改为 (4)/(8)/(16)（zh_CN/en_US 双语）。

## t114t 线程核心 6 档配方（用户确认方案后执行）
- **方案（用户拍板）**：普通线程核心 1/4/16 按并行核心"递推规则"（每档电路 +1 级、电压 +1 级；无流体、10 秒、无编程电路）：基准 = AE2 工程处理器×8 + 数据电路×4 + 传感器EV(32693) + 发射器EV(32683) → 核心1（HV）；1:HV/精英×2、4:EV/大师×2、16:IV/终极×2。
- 超线程核心 hyper_2/4/8（0+4/4+8/8+16）按闪存晶阵风格：多一个 AE2 合成加速器 + 工程处理器×16（双倍）+ 焊锡 576mb；电压 EV/IV/LuV，电路 大师/终极/超导×2。电压起点 EV（与线程核心 4 同级）——超线程每任务 +10% 字节，实际产出低于同槽数普通核心，故不做贵。
- 32/64 线程核心暂未配（用户只要 3+3；后续可延伸 32:LuV/超导、64:ZPM/无限）。
- **实现**：`Recipes.registerEcalThreadCores()`（新方法，register() 插入 registerEcalParallelCores 之后），配方名 `ecal.thread_core_1/4/16`、`ecal.thread_core_hyper_2/4/8`；输出经 `RegistryEcal.THREAD_CORES_BY_SUFFIX` 取物品。
- **验证**：JDK21 spotlessApply build → BUILD SUCCESSFUL（23s）；javap 确认 registerEcalThreadCores 存在、字符串常量（circuitElite/Master/Ultimate、hyper_2/4/8、circuitSuperconductor、32693/32683、200tick）；部署两端 jar 363839 B，SHA256 = `724FC06B0D7FD1CCCB0851E40978B8B0142C1C3341AF05054B3AE98622EB8029`（三端一致）。
- **t114u（用户）**：递推改为每档"电压 +2 级、电路板 +2 级、传感器/发射器 +2 级"——普通 1:HV/精英/部件EV → 4:IV/终极/部件IV → 16:LuV/超导/部件LuV；超线程 hyper_2:EV/大师/部件EV → hyper_4:LuV/超导/部件LuV → hyper_8:ZPM/无限/部件ZPM。部件 damage（ID+32000 实证）：传感器 32693/32694/32695/32696 = EV/IV/LuV/ZPM，发射器 32683/32684/32685/32686 同序。javap 确认 6 组 sipush 全对；部署两端 jar 364035 B，SHA256 = `2D8028FF15F4BC85D9FD06F72F8034D9866BDC28517B2CF9A1BA51D6227023E6`（三端一致）。
- **t114v（用户纠正）**：跳档规则修正——每档电压 **+2 级**（跳过一档）：HV→**IV**→**ZPM**（GT 电压序 LV=1..HV=3,IV=5,ZPM=7）；电路板同理 +2 档（精英→终极→无限），传感器/发射器 **+2 级且与电压同级匹配**。最终：普通 1:HV/精英/部件HV → 4:IV/终极/部件IV → 16:ZPM/无限/部件ZPM；超线程（基准 EV）hyper_2:EV/大师/部件EV → hyper_4:LuV/超导/部件LuV → hyper_8:UV/生物/部件UV。部件 damage：发射器 HV..UV = 32682/32683/32684/32685/32686/32687，传感器 32692..32697 同序。javap 确认 circuitElite/Ultimate/Infinite、circuitMaster/Superconductor/Bio、12 组 sipush 全对；部署两端 jar 364046 B，SHA256 = `50408049C1C87FC88D7186896CC944B63F498357A40672E08C6852194258D679`（三端一致）。
- **t114w（用户）**：并行核心 9 档传感器/发射器随档升级——原代码写死 EV(32693/32683)，改为与电压同级匹配（每档 +1）：核心1:HV(32692/32682) → 4:EV(32693/32683) → 16:IV(32694/32684) → 64:LuV(32695/32685) → 256:ZPM(32696/32686) → 1024:UV(32697/32687) → 4096:UHV(32698/32688) → 16384:UEV(32699/32689) → 65536:UIV(32700/32690)（ID+32000 规律，FieldGen/Emitter/Sensor 三段已验证）。javap 确认 18 组 sipush 全对；部署两端 jar 364147 B，SHA256 = `25BBFD94EC6220A1E8F5C850523CCC04247A5BCC08CAA47EC29D53A143880CCA`（三端一致）。
- **t114x（用户）**：电路板输入改真正的矿典——根因字节码实证：`GTOreDictUnificator.get("circuitAdvanced", n)` 查 sName2StackMap 返回**统一后的单个具体物品**（circuitAdvanced 统一到 IC2 高级电路板 → NEI 显示 IC2:itemPartCircuitAdv），且 `GTRecipeBuilder.itemInputs(ItemStack...)` 只填 inputsBasic、不还原矿典；真正矿典输入 = `itemInputs(Object...)` + `OreDictItemStack("矿典名", 数量)`（GT 展开该矿典全部注册物品生成替代配方，NEI 显示矿典多物品）。改动：helper（tryAddAssembler/NoCircuit/addAssembler/tryAddAL/tryAddSpaceAssembler）inputs 参数 `ItemStack[]`→`Object[]`（ItemStack[] 数组协变自动兼容），新增 `setItemInputs`（纯 ItemStack 走原 itemInputs(ItemStack...) 路径，含 OreDictItemStack 走 Object 路径）；6 处 circuitAdvanced（3 组件 ×4 + 3 外壳 ×2）改 `new OreDictItemStack("circuitAdvanced", n)`。javap 确认 6 处 OreDictItemStack 构造器（4/4/4/2/2/2）；部署两端 jar 364486 B，SHA256 = `ACF43FCB4EA4C97BEF39509C5CDD05F5D13FED110EF2A1E43115AA4D6CFEF30E`（三端一致）。
- **t114y（用户）**：并行核心 core_1 之后 8 档传感器/发射器全部降一级（core_1 保持 HV 不动）——4:HV、16:EV、64:IV、256:LuV、1024:ZPM、4096:UV、16384:UHV、65536:UEV（原为同级匹配 EV..UIV）。数组改为 sensors = {32692,32692,32693,32694,32695,32696,32697,32698,32699}、emitters = {32682,32682,32683,32684,32685,32686,32687,32688,32689}；javap 确认 18 组 sipush 全对；部署两端 jar 364482 B，SHA256 = `1DE41540B16745873D446BF343D8DFB3D059B55571BA0A421D1E7BB283A71CF1`（三端一致）。
- **t114z（用户基准配方）**：并行核心基准改为——AE2 合成加速器（BlockCraftingUnit/1）+ circuitElite×2 + circuitData×4 + **传感器MV(32691) + 发射器MV(32681)** → 核心1（HV，10s 无电路无流体）；递推每档"电路板 +1 级、电压 +1 级、传感器/发射器 +1 级、输出 +1 级"→ 传感器/发射器 MV..UEV（32691..32699 / 32681..32689），**始终比电压低 1 级**（1:MV/HV、4:HV/EV、16:EV/IV、64:IV/LuV、256:LuV/ZPM、1024:ZPM/UV、4096:UV/UHV、16384:UHV/UEV、65536:UEV/UIV）。用户矿词列表（IV→MAX）给 10 个（Elite..Transcendent），9 档取前 9（Elite..Cosmic），Transcendent 备用。并行/线程核心电路板统一改 OreDictItemStack（矿典输入，贯彻 t114x）。
- **t114aa（用户）**：`ecal.casing`（E-Calculator 外壳）第二个 ×8 电路板从 精英电路×8 改为 **数据电路×8**（第一个精英电路×4 不动），一并改 OreDictItemStack。
- **t114x 补**：按钮纹理 PNG 重编码（去掉 sRGB/gAMA/pHYs 辅助 chunk，249B→200B，与 TecTech 同构 IHDR+IDAT+IEND）——根因排查见 t114q 崩溃记录；部署两端 jar 364436 B，SHA256 = `E9EBD75AAABF2BB10A1100A17C4D0EA3AA724457BCCDA60376B83F257D7430BB`。
- **t114z/t114aa 部署**：两端 jar 364440 B，SHA256 = `6E013A8E78971548099F8DAB4130F728155FF91964AB6BFE5DD3DC024CFB2C8B`（三端一致）。
- **t114ab（用户纠正）**：并行核心**两个电路板输入都要逐档升级**——我只升了 ×2 的（Elite..Cosmic），×4 的 circuitData 固定没动。修正：新增 circuits4 链 = {circuitData, circuitElite, circuitMaster, circuitUltimate, circuitSuperconductor, circuitInfinite, circuitBio, circuitOptical, circuitExotic}（Data 起每档 +1，始终比 ×2 链低 1 档）。javap 确认两条链 18 个矿词 + 18 组部件 damage 全对；部署两端 jar 364492 B，SHA256 = `7E0434A58D2D997ABD73AFB6E18F0E4A68BCEED1D83F6ACE3441D0DEDA5471FC`（三端一致）。

## t115 多人性能优化第一批（调研驱动）
- **调研**：ae2fc/NovaEngineering/GT++/NeoECOAE/TST 源码 + AE2U 源码 + GTNH 服务器性能文档（详见工作区根 GTNH-服务器性能优化建议.md）。
- **优化 1（高）**：E-Storage drive handler 缓存改**纯事件驱动**——MTEEcoStorageArray.onPostTick 去掉每 5 tick 的 invalidateHandlers 周期重建（cellStack 变化路径 setInventorySlotContents/decrStackSize/interactWithCell/readFromNBT 均已触发 onCellChanged；getStackInSlotOnClosing 补上 onCellChanged）。参考 ae2fc"只按 cell 变化缓存"。
- **优化 2（低）**：Ecal 性能日志 200t→600t（多主机时降日志噪音，30s 一条）。
- **评估后跳过**：isActive/getGrid 注入的 controllerOf() 缓存——controllerOf 只是字段访问链，开销极小，缓存失效管理（结构重建）带来的 mixin 复杂度风险大于收益。
- **验证**：javap 确认 onPostTick 无 invalidateHandlers、onCellChanged 4 处调用（+getStackInSlotOnClosing）；BUILD SUCCESSFUL；部署两端 SHA256 = DE0A90FE2CD0F7EC6EBE715F9DE34360B899872ADDF073A7EEA94A02930B0D24（364395 B，三端一致）；git commit。

## t116 vCPU 相同配方合并（用户需求，备份后实现）
- **需求**：用 vCPU 下单 A 后再次下单 A，若池字节够则合并进正在运行的 vCPU（不占新线程槽）——仿 GTNHAE（AE2U fork）的合成请求合并。
- **根因**：AE2U 原版 mergeJob 条件 availableStorage >= usedStorage + newBytes 对 vCPU 恒不成立（onVirtualCPUSubmitJob 把 availableStorage 设为任务字节语义）→ 原版合并路径永不触发。
- **方案 B（实施）**：submitJob HEAD 注入在 t114g 预检之前加合并分支——isBusy + myLastLink.isStandalone + getFinalMultiOutput().isSameType(job.getOutput()) + 池剩余（getAvailableBytes）>= newBytes(+hyper 10%) → 直接调原版 public mergeJob()；记账：usedExtraStorage += extra、availableStorage += newBytes+extra（保持任务字节语义，池记账 Σ availableStorage 自动正确）；@Unique mergedJob 标志让 RETURN 注入跳过 onVirtualCPUSubmitJob（防重复分配线程槽/vCPU 号）。availableStorage 语义与池记账零改动。
- **验证**：BUILD SUCCESSFUL；javap 确认 mergeJob 调用 + isBusy/getFinalMultiOutput @Shadow + mergedJob 标志读写；部署两端 SHA256 = 1D968CB22BE4440FDDB1B9EB1313E3666024AB9FA565B68F54C0512057191D36（364843 B，三端一致）；git commit。游戏内验证待用户：下单 A→再下单 A→线程槽不增、输出累计。

## t116b vCPU 合并 UI 打通（用户反馈"字节不够"驱动）
- **现象**：再下单 A 显示"字节不够"，Merge 按钮不可用。
- **根因**：ContainerCraftConfirm.cpuMatches 的 busy 合并分支 c.getStorage() >= usedBytes + c.getUsedStorage() 对 vCPU 恒不成立（getStorage=任务字节语义）→ busy vCPU 在确认界面不可选 → Merge 不可点。
- **修复**：新增 MixinContainerCraftConfirm（server，priority 2000）：@Redirect cpuMatches/onCPUUpdate 的 getStorage/getAvailableStorage 调用 → vCPU 返回 effectiveAvailableStorage（= 控制器池剩余 + 当前任务 usedStorage，ECPUCluster 接口新 default 方法，非 vCPU 返回 -1 走原版）；MixinCraftingGridCache 加 @Redirect（6 参 submitJob 内 getAvailableStorage）——自动选择路径（不点 Merge 直接 Start）同样优先合并 busy vCPU。条件变为 池剩余 >= 新任务字节。
- **配合 t116**：Merge 提交 target=busy vCPU → submitJob HEAD 注入合并分支（实时池剩余判断）→ 原版 mergeJob。全链路：UI 可选 → 按钮变 Merge → 提交 → 合并 → 不占新线程。
- **验证**：BUILD SUCCESSFUL；refmap/jar 含 MixinContainerCraftConfirm；部署两端 SHA256 = FF629BECA861087C1422DE71C8A6C64931D5F045E08DD7EAC7EF4EC5CF6FDEB0（366371 B，三端一致）；备份 备份/ECOGTNH-源码备份-2026-09-01-t116b/。游戏内验证待用户。

## t116c/t116d vCPU 合并问题修复（用户实测驱动）
- **t116c（字节不减少）**：getAvailableBytes() 只统计外置线程驱动器，内置槽（builtinThreadClusters/HyperClusters）任务字节未计入池占用 → 内置线程下单后池剩余不扣减。修复：getAvailableBytes() 追加统计两个 builtin 列表（availableStorage 任务字节语义一致）。
- **t116d（红色/不可选）**：用户实测 CPU 列表 busy vCPU 红色、点不了。根因：cpuMatches 在**客户端容器实例**也执行，客户端 status 行由同步包 NBT 重建（serverCluster=null）→ 原 @Redirect 经 getServerCluster() 取 cluster 失效 → fallback 原版任务字节 → busy 合并条件恒假。修复：MixinCraftingCPUStatus 增加 ecEffStorage（vCPU 有效字节 = 池剩余+任务已用，服务端构造时算好，writeToNBT/NBT 构造同步，客户端读回）；MixinContainerCraftConfirm 的 cpuMatches @Redirect 改读 status 自身字段（ECPUStatus.isVCPU/getEffectiveStorage），不再依赖 serverCluster；MixinContainerCraftConfirm 从 server 组移到双端 mixins 组（客户端也要跑）。
- **验证**：BUILD SUCCESSFUL；部署两端 SHA256 = 556650917268654BB8D32205E21844CC67AB4E6CFBA379A3B85C276D8358AEF2（366709 B，三端一致）；备份 备份/ECOGTNH-源码备份-2026-09-01-t116c/。待用户游戏内复测：busy vCPU 应可选（白色）→ Merge 按钮 → 点击合并。

## t118 挖主机吞材料修复（用户报告，290+284 同源 bug）
- **现象**：挖掉 ECO 主机器后，内置线程槽上正在跑的任务直接消失，原料全被吞。
- **根因**：MTEEcalArray.disassembleAll()（机器拆除时）对 builtinThreadClusters/builtinHyperClusters 只调 ecoaegtnh() + clear——没有 cluster.cancel()。AE2U cancel() 会 postChange 把 CPU inventory 原料退还回网格；外置线程（TileEcalThreadDrive.onControllerDisassembled）是 cancel→destroy 所以正常。290/284 同源问题。
- **修复（290）**：disassembleAll 内置循环改调新 helper cancelAndDestroyBuiltin（try-catch cancel → markDestroyed → destroy，destroy 走 M1 injectDestroy → onClusterReleased 释放槽/编号，幂等）；已构建部署 290 两端（SHA256 CF20613A...，366936 B），备份 备份/ECOGTNH-源码备份-2026-09-01-t118/；284 由 port-engineer 同步修复（t9）。
- **注意**：2.9.0 服务端目录已改为 M:\AA科技\GTNH\服务端\GT_New_Horizons_2.9.0-beta-2\mods（与 2.8.4 命名一致）。

## t119 审计高危/中危修复（audit-290 报告驱动，M4/M5/M8 按用户指示跳过）
- H1：EcoStorageCellInventory.getRemainingItemCount 乘法饱和钳制（UNIVERSE 盘不再恒拒写）
- H2：FMLServerStoppingEvent + WorldEvent.Unload → cancelAllInFlight（重启/卸载退款；ACTIVE_CONTROLLERS 弱引用注册表，newMetaEntity 注册）
- H3：onVirtualCPUSubmitJob 无槽 → cluster.cancel()+destroy()（不再冻结）
- H4：MixinGuiCraftingCPUTable ScreenColor owner 改 appeng/client/gui（消除客户端启动崩溃隐患）
- M1：injectDestroy 补 CraftingNotificationManager.unregister（@Shadow unreadNotifications）
- M2：池只扣真实字节（ecoaegtnh\）；预检改用实时 getAvailableBytes 且不 ×1.1；merge 判断同步
- M3：MTEEcoStorageArray GUI 统计 20t 缓存（12 个 supplier 读缓存）
- M6：percentOf/bytesLedTooltip 百分比饱和
- M7：channel 断连 ≥100t 自动 cancelAllInFlight（20t 检测）
- M9：onVirtualCPUSubmitJob contains 去重（isClusterAssigned）+ RETURN 注入双保险
- 构建 BUILD SUCCESSFUL（jar 369305 B）；部署 290 两端 SHA256 7864B302...；备份 备份/ECOGTNH-源码备份-2026-09-01-t119/；284 同源同步派 port-engineer。
