# ThaumicEnergistics 源质盘（Essentia Cell）实现研究报告

- 版本：v1.0（researcher 产出，任务 t10）
- 目标：GTNH 1.7.10 ECO E-Storage 用**神秘4源质盘**替代气体盘（1.7.10 无 MekanismEnergistics）
- 结论先行：**可行且路径清晰**——TE4（ThaumicEnergistics）已在 AE2U 网格里注册了源质 `IAEStackType`，并自带完整可照抄的 Essentia Cell 实现（`ItemEssentiaCell` 等）。我们的 `EcoStorageCellEssentia` 只需在 DESIGN.md 的 `EcoStorageCell*` 家族里加一个源质分支，复用 TE4 的 stack type + AE2U 998 的 `CellInventory/CellInventoryHandler` 子类化模式。

---

## 1. 结论摘要（给工程师的直接答案）

| 项 | 结论 |
|----|------|
| 源质 stack type 静态入口 | `thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE`（public static final；TE4 在 **preInit** 用 `AEStackTypeRegistry.register(...)` 注册） |
| 源质栈类 | `thaumicenergistics.common.storage.AEEssentiaStack`（`extends AEStack<AEEssentiaStack>`；构造 `new AEEssentiaStack(Aspect)` / `(Aspect, long)`；`getAspect()`） |
| cell inventory 基类 | AE2U 998 的 `appeng.me.storage.CellInventory`（正是 TE4 用的基类；我们的物品/流体盘也用它） |
| cell handler 基类 | AE2U 998 的 `appeng.me.storage.CellInventoryHandler`（abstract，protected 构造器，必须子类化） |
| TE4 参考实现 | `thaumicenergistics.common.items.ItemEssentiaCell`（Item 自己 implements `ICellHandler, IStorageCell` 并自注册）、`common.inventory.EssentiaCellInventory`、`EssentiaCellInventoryHandler`、`EssentiaCellConfig`、`common.storage.EnumEssentiaStorageTypes` |
| 依赖坐标（TE4） | `com.github.GTNewHorizons:ThaumicEnergistics:1.7.60-GTNH:dev`（nexus 已验证 200；1.7.56-GTNH 为整合包运行版，也可用）——**必须 `{ transitive = false }`** |
| 依赖坐标（Thaumcraft） | ⚠️ `thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev` 是 TE4/AE2U 源码里写的坐标，但**实测当前无法从标准仓库解析**（详见 §5.2，有已验证的本地 jar 替代方案） |
| 兼容性 | TE4 1.7.56-GTNH 源码里 pin 的正是 **AE2U rv3-beta-998-GTNH**（与我们的 dev pin 完全一致）；整合包运行 AE2U rv3-beta-1000-GTNH（已证 998↔1041 存储 API 逐文件一致，1000 居中）→ **完全匹配** |
| 源质盘容量/类型数建议 | 照 TE4 的 `EnumEssentiaStorageTypes`：16M→60 类型、64M→80、256M→100（上限参考 `Aspect.aspects.size()`，约 60+）；`getBytesPerType()=0`（源质按 `getAmountPerByte()=2` 计字节，`CellInventory.typeWeight=2`） |

---

## 2. TE4 在 AE2U 网格里的源质存储机制

### 2.1 IAEStackType 注册（FQCN 与时机）

```java
// thaumicenergistics/common/ThaumicEnergistics.java (preInit, ~L191)
AEStackTypeRegistry.register(ESSENTIA_STACK_TYPE);   // = AEEssentiaStackType.ESSENTIA_STACK_TYPE

// thaumicenergistics/common/storage/AEEssentiaStackType.java
public class AEEssentiaStackType implements IAEStackType<AEEssentiaStack> {
    public static final AEEssentiaStackType ESSENTIA_STACK_TYPE = new AEEssentiaStackType();
    public static final String ESSENTIA_STACK_ID = "essentia";
    @Override public String getId() { return ESSENTIA_STACK_ID; }
    @Override public int getAmountPerByte() { return 2; }      // 每字节 2 点源质 → CellInventory.typeWeight=2
    @Override public int getAmountPerUnit() { return 1; }
    @Override public IItemList<AEEssentiaStack> createList() { return new EssentiaList(); }
    @Override public AEEssentiaStack loadStackFromNBT(NBTTagCompound tag) { return AEEssentiaStack.loadStackFromNBT(tag); }
    // 容器(罐/瓶)互转：isContainerItemForType / getStackFromContainerItem / drainStackFromContainer / fillContainer / clearFilledContainer
    //   —— 全部委托 thaumicenergistics.common.integration.tc.EssentiaItemContainerHelper（我们不需要，照抄 TE4 的 cell 即可）
}
```

要点：
- 源质不是 `StorageChannel`（`AEEssentiaStack.getChannel()` 返回 **null**，1.7.56 源码 L255-257 已验证）——**必须走 `IAEStackType` 体系**，与 DESIGN.md §3.1 的判断一致。
- `ThEApi`（`thaumicenergistics.api.ThEApi.instance()`，preInit 后可用）**没有**暴露 stack type 的访问器（只有 blocks/config/interact/items/parts/transportPermissions）→ 直接引用 common 包静态字段 `AEEssentiaStackType.ESSENTIA_STACK_TYPE`（依赖 TE4 的 **dev jar（全类）**即可，不是 api-only jar）。
- 注册时机在 TE4 的 **preInit**，且 AE2U 998 的 `GridStorageCache`/`TileChest` 都用 `AEStackTypeRegistry.getAllTypes()` 遍历所有已注册类型——**我们的 mod 只需在 TE4 加载之后引用该类型，无需自己注册**。若我们要在 TE4 缺席时优雅降级，用 `Loader.isModLoaded("ThaumicEnergistics")`（modid 见 §5.1）门控。

### 2.2 栈类

```java
// thaumicenergistics/common/storage/AEEssentiaStack.java
public class AEEssentiaStack extends AEStack<AEEssentiaStack> {   // AEStack = appeng.util.item.AEStack，故它是 IAEStack<AEEssentiaStack>
    public AEEssentiaStack(@Nonnull Aspect aspect);               // 构造
    public AEEssentiaStack(@Nonnull Aspect aspect, long amount);
    @Nonnull public Aspect getAspect();
    @Override public IAEStackType<AEEssentiaStack> getStackType(); // → ESSENTIA_STACK_TYPE
    @Override public StorageChannel getChannel() { return null; }
    public static AEEssentiaStack loadStackFromNBT(NBTTagCompound tag);
    // NBT 键：AspectTag / Amount / Craftable
}
```

### 2.3 AE2U 998 侧的配套（已在 DESIGN.md §3 验证，这里补充源质相关）

- `appeng.api.storage.ICellCacheRegistry.TYPE` 枚举**已含 `ESSENTIA`**（998 源码：`enum TYPE { ITEM, FLUID, ESSENTIA }`）→ 我们的 handler 直接返回 `TYPE.ESSENTIA`，与 AE2U 内建缓存对接。
- `appeng.me.storage.CellInventory`（998）：`protected CellInventory(ItemStack o, ISaveProvider container) throws AppEngException`；抽象钩子 `readStack(NBTTagCompound)`、`getStackTypeTag()`、`getStackCountTag()`；可覆写 `saveChanges()`/`loadCellStacks()`；字段 `protected final NBTTagCompound tagCompound; protected final ISaveProvider container; protected short storedTypes; protected long storedCount; protected final IItemList<StackType> cellStacks;`；`typeWeight = getStackType().getAmountPerByte()`（源质=2）。
  - 字节公式：`getUsedBytes() = storedTypes * getBytesPerType() + (storedCount + getUnusedItemCount()) / typeWeight`；源质 `getBytesPerType()=0` → 纯按数量/2 计字节。
  - `getRemainingItemTypes()`：`bytesPerType > 0 ? freeBytes/bytesPerType : getMaxTypes()` → 源质盘类型数上限只受 `getTotalTypes()`（item 的 `maxStoredTypes`）约束。
- `appeng.me.storage.CellInventoryHandler<StackType>`（998）：`protected CellInventoryHandler(IMEInventory<StackType> c, IAEStackType<StackType> type)`（abstract）；构造器自动扫描升级/配置槽（`getUpgradesInventory/getConfigAEInventory/getFuzzyMode/getOreFilter`）并调 `setPriorityList(...)`（可覆写）。

---

## 3. TE4 自己的 Essentia Cell 参考实现（逐类解剖）

### 3.1 `ItemEssentiaCell`（thaumicenergistics.common.items）——最重要的参考

```java
public class ItemEssentiaCell extends Item implements ICellHandler, IStorageCell, IItemGroup {
    private static final int BYTES_PER_ESSENTIA_TYPE = 0;   // 源质盘不用固定 bytes-per-type

    public ItemEssentiaCell() {
        AEApi.instance().registries().cell().addCellHandler(this);  // ★ 物品自身注册为 cell handler（无需独立 handler 类）
        this.setMaxStackSize(1); this.setMaxDamage(0); this.setHasSubtypes(true);
    }

    @Override public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, IAEStackType<?> type) {
        if (is == null || type != ESSENTIA_STACK_TYPE || !(is.getItem() instanceof ItemEssentiaCell)) return null;
        // 创造版: new EssentiaCellInventoryHandler(new CreativeCellInventory<>(is))
        try { return new EssentiaCellInventoryHandler(new EssentiaCellInventory(is, host)); }
        catch (AppEngException e) { return null; }
    }
    @Override public boolean isCell(ItemStack is) { return is != null && is.getItem() == this; }
    @Override public boolean isStorageCell(ItemStack i) { return i != null && i.getItem() == this; }
    @Override public IAEStackType<?> getStackType() { return ESSENTIA_STACK_TYPE; }
    @Override public int getBytes(ItemStack cellItem) { return (int) min(getBytesLong(cellItem), MAX_VALUE); }
    @Override public long getBytesLong(ItemStack cellItem) { return EnumEssentiaStorageTypes.fromIndex[cellItem.getItemDamage()].capacity; }
    @Override public int getBytesPerType(ItemStack cellItem) { return BYTES_PER_ESSENTIA_TYPE; }  // 0
    @Override public int BytePerType(ItemStack cellItem) { return BYTES_PER_ESSENTIA_TYPE; }        // 旧接口，同步返回 0
    @Override public int getTotalTypes(ItemStack cellItem) { return EnumEssentiaStorageTypes.fromIndex[cellItem.getItemDamage()].maxStoredTypes; }
    @Override public double getIdleDrain() { return 0; }                                            // 每 tick 耗电走 cellIdleDrain
    @Override public double cellIdleDrain(ItemStack is, IMEInventory handler) { return EnumEssentiaStorageTypes.fromIndex[is.getItemDamage()].idleAEPowerDrain; }
    @Override public boolean isEditable(ItemStack is) { return !isCreative(is); }
    @Override public IInventory getUpgradesInventory(ItemStack is) { return new CellUpgrades(is, 5); }
    @Override public IAEStackInventory getConfigAEInventory(ItemStack is) { return new EssentiaCellConfig(is); }
    @Override public FuzzyMode getFuzzyMode(ItemStack is) { return FuzzyMode.IGNORE_ALL; }
    @Override public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {}
    @Override public void openChestGui(EntityPlayer p, IChestOrDrive chest, ICellHandler h, IMEInventoryHandler inv, ItemStack is, StorageChannel chan) { Platform.openGUI(p, (TileEntity) chest, chest.getUp(), GuiBridge.GUI_ME); }
    @Override public boolean storableInStorageCell() { return false; }
    // getSubItems 按 EnumEssentiaStorageTypes 出所有等级；meta=index；getUnlocalizedName 按类型
}
```

### 3.2 `EssentiaCellInventory`（common.inventory）——cell inventory（照抄模板）

```java
public class EssentiaCellInventory extends CellInventory<AEEssentiaStack> {
    private static final String NBT_ESSENTIA_NUMBER_KEY = "Essentia#";
    public EssentiaCellInventory(ItemStack cell, ISaveProvider provider) throws AppEngException { super(cell, provider); }
    @Override protected AEEssentiaStack readStack(NBTTagCompound tag) { return AEEssentiaStack.loadStackFromNBT(tag); }
    @Override protected String getStackTypeTag() { return "et"; }
    @Override protected String getStackCountTag() { return "ec"; }
    @Override protected void saveChanges() { /* 遍历 cellStacks 写 "Essentia#"+i；storedTypes/storedCount 维护；container.saveChanges(this) */ }
    @Override protected void loadCellStacks() { /* 按 getMaxTypes() 上限读 "Essentia#"+i */ }
    @Override public @Nonnull IAEStackType<?> getStackType() { return ESSENTIA_STACK_TYPE; }
}
```

⚠️ 1.7.60-GTNH 对 `saveChanges()` 有一个**真实 bugfix**：清理循环上界从 `storedTypes` 改为 `getMaxTypes()`（“Legacy cells can store stacks sparsely, so storedTypes is not a valid upper bound for cleanup.”）。**照抄 1.7.60 版本。**

### 3.3 `EssentiaCellInventoryHandler`（common.inventory）

```java
public class EssentiaCellInventoryHandler extends CellInventoryHandler<AEEssentiaStack> {
    public EssentiaCellInventoryHandler(IMEInventory<AEEssentiaStack> c) { super(c, ESSENTIA_STACK_TYPE); }
    @Override public TYPE getCellType() { return TYPE.ESSENTIA; }                       // ICellCacheRegistry.TYPE
    @Override public @Nonnull IAEStackType<?> getStackType() { return ESSENTIA_STACK_TYPE; }
    @Override protected void setPriorityList(boolean hasFuzzy, IAEStackInventory config, FuzzyMode fzMode) {
        // 遍历 config 槽，把 AEEssentiaStack（stackSize 置 1）加入 EssentiaList → setPartitionList(new PrecisePriorityList<>(list))
    }
}
```

### 3.4 其他

- `EnumEssentiaStorageTypes`：`Type_1K(0, 1<<10, 12, 0.5 AE/t) / 4K(12) / 16K(12) / 64K(12) / 256K(24) / 1024K(36) / 4096K(48) / 16384K(60) / Creative / Quantum / Singularity`（index、capacity 字节、maxStoredTypes、idleAEPowerDrain）。
- `EssentiaCellConfig extends appeng.items.contents.CellConfig`：分区配置（`putAEStackInSlot(new AEEssentiaStack(aspect))`，NBT 兼容迁移）。
- `EssentiaList implements IItemList<AEEssentiaStack>`：源质专用列表（stack type 按 Aspect 相等比较）。

---

## 4. 我们的 `EcoStorageCellEssentia` 精确实现方案

### 4.1 类清单与继承关系（并入 DESIGN.md §5.1 的 ecoaegtnh 包结构）

```
ecoaegtnh/ae2/
├── EcoStorageCellHandler.java            // 已有：extends BasicCellHandler —— 增加 essentia 分支
├── EcoStorageCellInventoryEssentia.java  // 新增：extends appeng.me.storage.CellInventory<AEEssentiaStack>
├── EcoStorageCellInventoryEssentiaHandler.java // 新增：extends CellInventoryHandler<AEEssentiaStack>
└── EcoCellDriveWatcher.java              // 已有（泛型 T extends IAEStack<T>，AEEssentiaStack 满足）
ecoaegtnh/item/estorage/
└── ItemEcoStorageCellEssentia.java       // 新增：extends Item implements IStorageCell（+ IItemGroup 可选）
```

### 4.2 决策：handler 合并进现有 `EcoStorageCellHandler`（推荐）

TE4 让 Item 自注册 `ICellHandler`；我们的物品/流体盘已经用共享 `EcoStorageCellHandler extends BasicCellHandler`（DESIGN.md §3.3）。为保持一致，**在 `EcoStorageCellHandler` 里加 essentia 分支**（一个注册点，一个 `isCell`）：

```java
public class EcoStorageCellHandler extends BasicCellHandler {
    @Override public boolean isCell(ItemStack is) {
        return is != null && (is.getItem() instanceof ItemEcoStorageCell        // item/fluid/gas
                || is.getItem() instanceof ItemEcoStorageCellEssentia);        // essentia
    }
    @Override public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, IAEStackType<?> type) {
        if (is == null) return null;
        if (type == ESSENTIA_STACK_TYPE) {                                     // thaumicenergistics...AEEssentiaStackType.ESSENTIA_STACK_TYPE
            if (!(is.getItem() instanceof ItemEcoStorageCellEssentia)) return null;
            try { return new EcoStorageCellInventoryEssentiaHandler(new EcoStorageCellInventoryEssentia(is, host)); }
            catch (AppEngException e) { return null; }
        }
        return super.getCellInventory(is, host, type);                         // item/fluid 走原逻辑
    }
}
```

### 4.3 代码骨架（可直接实现的精确签名）

```java
package ecoaegtnh.ae2;

import static thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.EssentiaList;

// ---- inventory ----
public class EcoStorageCellInventoryEssentia extends appeng.me.storage.CellInventory<AEEssentiaStack> {
    private static final String NBT_ESSENTIA_NUMBER_KEY = "Essentia#";
    public EcoStorageCellInventoryEssentia(ItemStack cell, ISaveProvider provider) throws AppEngException {
        super(cell, provider);
    }
    @Override protected AEEssentiaStack readStack(NBTTagCompound tag) { return AEEssentiaStack.loadStackFromNBT(tag); }
    @Override protected String getStackTypeTag() { return "et"; }
    @Override protected String getStackCountTag() { return "ec"; }
    @Override protected void saveChanges() {
        // 照抄 TE4 1.7.60-GTNH 版本（cleanup 上界用 getMaxTypes()，见 §3.2）
    }
    @Override protected void loadCellStacks() { /* 照抄 TE4 1.7.60 */ }
    @Override public @Nonnull IAEStackType<?> getStackType() { return ESSENTIA_STACK_TYPE; }
}

// ---- handler ----
public class EcoStorageCellInventoryEssentiaHandler extends appeng.me.storage.CellInventoryHandler<AEEssentiaStack> {
    public EcoStorageCellInventoryEssentiaHandler(IMEInventory<AEEssentiaStack> c) { super(c, ESSENTIA_STACK_TYPE); }
    @Override public TYPE getCellType() { return TYPE.ESSENTIA; }               // appeng.api.storage.ICellCacheRegistry.TYPE
    @Override public @Nonnull IAEStackType<?> getStackType() { return ESSENTIA_STACK_TYPE; }
    @Override protected void setPriorityList(boolean hasFuzzy, IAEStackInventory config, FuzzyMode fzMode) {
        // 照抄 TE4：遍历 config 槽收集 AEEssentiaStack → PrecisePriorityList<EssentiaList>
    }
}

// ---- item ----
public class ItemEcoStorageCellEssentia extends Item implements IStorageCell {
    public enum Tier { A(16 << 20, 60), B(64 << 20, 80), C(256 << 20, 100);  // 建议值，见 §6
        public final long bytes; public final int types;
        Tier(long b, int t) { bytes = b; types = t; } }
    private final Tier tier;
    public ItemEcoStorageCellEssentia(Tier tier) { this.tier = tier; setMaxStackSize(1); }
    @Override public IAEStackType<?> getStackType() { return ESSENTIA_STACK_TYPE; }
    @Override public long getBytesLong(ItemStack is) { return tier.bytes; }
    @Override public int getBytes(ItemStack is) { return (int) Math.min(tier.bytes, Integer.MAX_VALUE); }
    @Override public int getBytesPerType(ItemStack is) { return 0; }           // 源质不按类型计字节
    @Override public int BytePerType(ItemStack is) { return 0; }               // 旧接口
    @Override public int getTotalTypes(ItemStack is) { return tier.types; }
    @Override public boolean storableInStorageCell() { return false; }
    @Override public boolean isStorageCell(ItemStack i) { return i != null && i.getItem() == this; }
    @Override public double getIdleDrain() { return tier.bytes / 1024d / 1024d; } // 与现有盘一致（MB 数）；控制器 recalculateEnergyUsage 用 cellInv.getIdleDrain()
    @Override public boolean isEditable(ItemStack is) { return true; }
    @Override public IInventory getUpgradesInventory(ItemStack is) { return new CellUpgrades(is, 2); }  // 与现有盘一致
    @Override public IAEStackInventory getConfigAEInventory(ItemStack is) { return new EcoEssentiaCellConfig(is); }
    @Override public FuzzyMode getFuzzyMode(ItemStack is) { return FuzzyMode.IGNORE_ALL; }
    @Override public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {}
}
```

- **`getConfigAEInventory`**：建议自写 `EcoEssentiaCellConfig extends appeng.items.contents.CellConfig`（照抄 TE4 的 `EssentiaCellConfig`，去掉旧 NBT 迁移也可；`putAEStackInSlot(new AEEssentiaStack(aspect))`）。若想少写代码，可直接引用 TE4 dev jar 里的 `thaumicenergistics.common.inventory.EssentiaCellConfig`（public 类）——两者都可行。
- **`getAvailableItems`/`injectItems`/`extractItems`**：全部复用 `CellInventory` 父类逻辑（父类已按 `getBytesPerType()`/`getTotalTypes()`/`typeWeight` 抽象），**无需覆写**——这正是 TE4 的做法（其 `EssentiaCellInventory` 只覆写 NBT 读写钩子 + `getStackType`）。
- **驱动盘位集成**（DESIGN.md §1.4 对应改动）：
  - `DriveStorageType` 枚举加 `ESSENTIA`；`EStorageCellDrive.getCellType(cell)` 加 `instanceof ItemEcoStorageCellEssentia → ESSENTIA`。
  - `getMaxTypes(data)`：essentia → 对应 tier 的 `types`；`getMaxBytes(data)`：对应 tier 的 `bytes`。
  - `getHandler(IAEStackType)` 的 map 键天然支持（1.7.10 版按 `IAEStackType` 键控）。
  - 等级限制照旧：A 盘 → L4/L6/L9、B → L6/L9、C → 仅 L9。
  - 渲染：驱动盘位动态图标加 "essentia" 态（可先用流体盘贴图占位，或画一张源质盘贴图）。
- **watcher**：`EcoCellDriveWatcher<T extends IAEStack<T>>` 泛型满足（`AEEssentiaStack extends AEStack<AEEssentiaStack>`）；写入上报 `postAlterationOfStoredItems` 用 `ESSENTIA_STACK_TYPE` 的重载（`IStorageGrid.postAlterationOfStoredItems(IAEStackType, Iterable, BaseActionSource)`，998 有 default 实现）。

---

## 5. 依赖声明（dependencies.gradle 精确写法 + 已验证的坑）

### 5.1 推荐写法（本项目实测可行的组合）

```groovy
dependencies {
    api('com.github.GTNewHorizons:GT5-Unofficial:5.09.54.111:dev')
    api('com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-998-GTNH:dev')

    // ★ TE4：nexus 已验证存在（dev jar 1.4MB）。必须 transitive=false，
    //   否则会拉 TE4 的传递依赖 thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev（当前不可解析，见 5.2）→ 构建失败
    implementation('com.github.GTNewHorizons:ThaumicEnergistics:1.7.60-GTNH:dev') { transitive = false }

    // ★ Thaumcraft：本地运行时 jar（已从整合包复制到项目 libs/）作为 compileOnly
    //   方案 A（推荐）：rfg.deobf 本地 jar（GTNH 构建脚本支持，1.12.2 参考仓库同样用过 files 本地依赖）
    compileOnly(rfg.deobf(files('libs/Thaumcraft-1.7.10-4.2.3.5.jar')))
    //   方案 B（最简单，因为我们只碰 thaumcraft.api.*，该包未混淆）：
    //   compileOnly(files('libs/Thaumcraft-1.7.10-4.2.3.5.jar'))
}
```

- modid 门控：`Loader.isModLoaded("ThaumicEnergistics")`（TE4 的 `@Mod` modid，mcmod.info 里 dependencies 为 `appliedenergistics2` + `thaumcraft`；modid 见其主类 `ThaumicEnergistics.MOD_ID`）。
- 运行时：整合包已含 `thaumicenergistics-1.7.56-GTNH.jar` + `Thaumcraft-1.7.10-4.2.3.5.jar`（M:\AA科技\GTNH\服务端\mods\，客户端同），无需我们分发。

### 5.2 已验证的“坑”（写代码前必读）

| 尝试 | 结果 | 说明 |
|------|------|------|
| `thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev` 从 GTNH maven public 解析 | ❌ 404（HEAD+GET 均试） | TE4/AE2U/ThaumcraftMobAspects 源码都写这个坐标，但**当前** GTNH nexus（nexus.gtnewhorizons.com）各仓库（public/thirdparty/releases/maven-releases/falsepattern-proxy/glease-proxy）都没有；旧 host `jenkins.usrv.eu` 已死；K4U/modmaven/cleanroom/maven central 均 404。**用独立 Gradle 探针工程（仅 gtnhconvention 同款仓库集）实测解析失败**。→ 不要依赖此坐标 |
| CurseMaven `curse.maven:thaumcraft-223628:2227552`（CF 文件 id 2227552，CFWidget API 查得） | ❌ 400 Bad Request | **Thaumcraft 已被从 CurseForge 下架**（作者要求），文件不可下载 |
| 本地运行时 jar（整合包 mods 目录） | ✅ 已验证 | `Thaumcraft-1.7.10-4.2.3.5.jar`（12MB）已复制到 `D:\DeepSeek\GTNH-ECO\libs\` 和 `.research\libs\`；`thaumcraft/api/aspects/Aspect|AspectList|IEssentiaContainerItem.class` 均在 jar 内（API 包未混淆） |
| TE4 dev jar | ✅ `https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/ThaumicEnergistics/1.7.56-GTNH/...dev.jar` 200（1.7.60-GTNH 同理，sources jar 也已拉取） | 使用 `:dev` classifier |

---

## 6. 容量/类型数取值建议

- TE4 原版（`EnumEssentiaStorageTypes`）：1K→12 类型、256K→24、1024K→36、4096K→48、16384K→60；`Type_Creative.maxStoredTypes = Aspect.aspects.size()`（1.7.10 源质种类约 60+，动态）。
- 我们的 ECO 三档建议（与 16M/64M/256M 字节档位对齐，类型数取 TE4 同规模档附近）：
  - A（16M 字节）：**60** 类型（=TE4 16384K 档）
  - B（64M 字节）：**80** 类型
  - C（256M 字节）：**100** 类型
  - 上限注意：类型数超过 `Aspect.aspects.size()` 的部分只是“理论容量”，实际只能装已注册的源质种类；若想严格对齐神秘4，可把 A/B/C 都设为 60（≥aspects.size() 即可），数值为建议、可在平衡阶段调整。
- `getBytesPerType()=0` + `getAmountPerByte()=2` 是 TE4 验证过的组合（`getUsedBytes = storedCount/2 + 余量修正`），**不要**照抄物品盘的 `bytesPerType = byteMultiplier*1024` 公式——那会与 `typeWeight=2` 冲突导致字节算法异常。

---

## 7. 兼容性结论（任务第 5 问）

1. **TE4 1.7.56-GTNH 依赖的 AE2U**：其 `dependencies.gradle` 写的是 `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-998-GTNH:dev` —— **与我们 dev pin 完全一致**。
2. **整合包运行 AE2U**：`appliedenergistics2-rv3-beta-1000-GTNH.jar`（M:\AA科技\GTNH\服务端\mods\ 实测）。已在 t10 前一轮验证：AE2U **998↔1041** 的 `IMEInventory/ICellHandler/StorageChannel/IStorageGrid/MEInventoryHandler` 逐字节一致、`ICellInventory` 仅新增 default `isOverflow()`；1000 位于两者之间 → **TE4（按 998 编译）在 AE2U 1000 下二进制/源码兼容，我们的盘在 998 dev / 1000 runtime 下均兼容**。
3. **TE4 1.7.56 vs 1.7.60**（两个 sources 逐文件对比）：`AEEssentiaStackType / AEEssentiaStack / EssentiaCellInventoryHandler / EnumEssentiaStorageTypes` 完全一致；`ItemEssentiaCell` 仅 tooltip 颜色写法（cosmetic）；`EssentiaCellInventory.saveChanges()` 有 cleanup 上界 bugfix（1.7.60 修）。→ **建议 dev pin 1.7.60-GTNH**（带修复、API 不变），运行时整合包 1.7.56 亦可（无签名变化）；若想与整合包完全一致则 pin 1.7.56-GTNH，两者均可用。
4. **TE4 的 AE2U mixin**：TE4 的 mixin 全部打在 **thaumcraft 侧**（golem/aspect 等，见其 mixins 目录），**不打 AE2U** → 与 AE2U 998/1000 无 mixin 冲突风险。
5. **Thaumcraft 4.2.3.5**：整合包运行版；API 类未混淆可直接 compileOnly 引用（§5.1）。

---

## 8. 与现有设计（DESIGN.md）的集成清单

| DESIGN.md 位置 | 改动 |
|----------------|------|
| §3.3 存储盘实现 | `EcoStorageCellHandler` 增加 essentia 分支（§4.2） |
| §1.4/§3.4 驱动盘位 | `DriveStorageType` 加 ESSENTIA；`getCellType/getMaxTypes/getMaxBytes` 加 essentia 分支；图标态 |
| §3.5 网格行为 | 无改动（`AEStackTypeRegistry` 已注册源质类型；`GridStorageCache` 自动发现） |
| §6.3 FQCN 清单 | 增加 §9 的 TE4/Thaumcraft FQCN |
| §4 GT 配方 | 源质盘配方草案：ME Storage Housing + 源质瓶（`ItemEssentiaContainer`）/神秘部件 + circuit → 源质盘（三档）；数值由 engineer-content 定稿 |
| 里程碑 | 源质盘并入 M3（AE2U 集成）或作为 M3.5 增量；依赖 TE4+Thaumcraft 后 M0 编译需先过 §5.1 |

---

## 9. 关键 FQCN 清单（新增）

### ThaumicEnergistics（1.7.60-GTNH dev）
```
thaumicenergistics.common.storage.AEEssentiaStackType        // .ESSENTIA_STACK_TYPE（public static final）
thaumicenergistics.common.storage.AEEssentiaStack            // new (Aspect) / (Aspect,long); getAspect(); loadStackFromNBT
thaumicenergistics.common.storage.EssentiaList               // IItemList<AEEssentiaStack>
thaumicenergistics.common.items.ItemEssentiaCell             // 参考实现（Item=ICellHandler 模式）
thaumicenergistics.common.inventory.EssentiaCellInventory    // 参考实现（extends CellInventory<AEEssentiaStack>）
thaumicenergistics.common.inventory.EssentiaCellInventoryHandler // 参考实现（extends CellInventoryHandler<AEEssentiaStack>）
thaumicenergistics.common.inventory.EssentiaCellConfig       // 参考实现（extends CellConfig）；可复用
thaumicenergistics.common.storage.EnumEssentiaStorageTypes   // 容量/类型数/耗电参考
thaumicenergistics.api.ThEApi                               // instance() 在 preInit 后可用；无 stack type 访问器
```
### Thaumcraft 4.2.3.5（本地 jar，API 未混淆）
```
thaumcraft.api.aspects.Aspect        // Aspect.aspects (Map<String,Aspect>) / Aspect.AIR / getTag() / getChatcolor()
thaumcraft.api.aspects.AspectList
thaumcraft.api.aspects.IEssentiaContainerItem
```

---

## 10. 已验证的本地资源（工程师直接用）

```
D:\DeepSeek\GTNH-ECO\libs\Thaumcraft-1.7.10-4.2.3.5.jar          # 已复制（来自整合包，12MB）
D:\DeepSeek\GTNH-ECO\.research\libs\thaumicenergistics-1.7.56-GTNH.jar   # 运行 jar（来自整合包）
D:\DeepSeek\GTNH-ECO\.research\libs\appliedenergistics2-rv3-beta-1000-GTNH.jar  # 运行 jar（来自整合包）
D:\DeepSeek\GTNH-ECO\.research\te4-1.7.56-GTNH\   # TE4 1.7.56 sources（nexus -sources.jar 解包）
D:\DeepSeek\GTNH-ECO\.research\te4-1.7.60-GTNH\   # TE4 1.7.60 sources
D:\DeepSeek\GTNH-ECO\.research\Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH\  # AE2U 998 源码
```

## 11. 风险与开放项

1. **TE4 缺席时降级**：源质盘物品/方块在无 TE4 的实例中应隐藏（crafting 依赖 TE4 存在时注册配方；物品本身引用 TE4 类，加载即需 TE4——建议 `@Optional`/modid 门控注册，或在 mod 加载前置 `Loader.isModLoaded` 检查）。
2. **`thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev` 解析失败**：已用本地 jar 方案绕过（§5.1）；若未来 GTNH 恢复该坐标，可换回标准写法。
3. **AEEssentiaStackType 属于 TE4 common 包而非 api 包**：一旦 TE4 重构内部类有风险；但 1.7.56/1.7.60 两版完全一致，稳定性好。
4. **类型数/字节公式**：§6 的 A/B/C 数值为建议，平衡阶段可调；不要改 `getBytesPerType=0` 与 `typeWeight=2` 的组合。
5. **GUI/渲染**：源质盘 tooltip 可借用 `AEEssentiaStack.getChatColor()/getDisplayName(player)`（TE4 提供）；驱动盘位 essentia 图标态待补贴图（可先复用 fluid 态）。
