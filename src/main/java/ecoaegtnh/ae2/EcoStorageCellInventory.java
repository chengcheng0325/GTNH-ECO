package ecoaegtnh.ae2;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;

/**
 * 284 移植版 ECO 物品存储盘。695 的 {@code appeng.me.storage.CellInventory} 是非泛型且构造
 * private（只能走工厂），无法继承做 t68 字节算法与 315 类型上限，因此按 AE2FC 1.4.120
 * （2.8.4 包内同款）的独立实现模式重写：直接实现 AE2U 的 {@link ICellInventory}，NBT 布局
 * 与 AE2U 一致（"it"/"ic" short/long + "#N" 槽位物品 + "@N" 槽位数量），t68 老 ECO 字节
 * 算法（weight = amountPerByte(8) × byteMultiplier），类型上限取物品声明的 315（不做 AE2U
 * 的 63 截断——那是 290 版要 override 的原因，本类自持上限）。
 */
public class EcoStorageCellInventory implements ICellInventory {

    /** 物品单位/字节（AE2U AEItemStackType.AMOUNT_PER_BYTE = 8）。 */
    public static final int ITEM_AMOUNT_PER_BYTE = 8;

    private static final String ITEM_TYPE_TAG = "it";
    private static final String ITEM_COUNT_TAG = "ic";
    private static final String ITEM_SLOT = "#";
    private static final String ITEM_SLOT_COUNT = "@";

    /** t84 tooltip 读取同一对 NBT 键（公开给 ItemEcoStorageCell.addStorageInformation）。 */
    public static final String TYPE_TAG = ITEM_TYPE_TAG;
    public static final String COUNT_TAG = ITEM_COUNT_TAG;

    private static String[] itemSlots;
    private static String[] itemSlotCount;

    private final NBTTagCompound tagCompound;
    private final ISaveProvider container;
    private final int maxItemTypes;
    private short storedItemTypes = 0;
    private long storedItemCount = 0;
    private IItemList<IAEItemStack> cellItems;
    private final ItemStack cellItem;
    private final ItemEcoStorageCell cellType;
    private boolean cardVoidOverflow = false;
    private boolean cardDistribution = false;

    public EcoStorageCellInventory(ItemStack o, ISaveProvider container) throws AppEngException {
        if (itemSlots == null) {
            itemSlots = new String[315];
            itemSlotCount = new String[315];
            for (int x = 0; x < 315; x++) {
                itemSlots[x] = ITEM_SLOT + x;
                itemSlotCount[x] = ITEM_SLOT_COUNT + x;
            }
        }
        if (o == null || !(o.getItem() instanceof ItemEcoStorageCell)) {
            throw new AppEngException("ItemStack was used as an ECO cell, but was not one!");
        }
        this.cellItem = o;
        this.cellType = (ItemEcoStorageCell) o.getItem();
        // t68: 物品盘声明上限 315（ItemEcoStorageCellItem.MAX_TYPES）；不做 AE2U 的 63 截断。
        this.maxItemTypes = this.cellType.getTotalTypes(o);
        if (this.maxItemTypes < 1) {
            throw new AppEngException("ECO cell declares no item types!");
        }
        final IInventory upgrades = this.getUpgradesInventory();
        for (int x = 0; x < upgrades.getSizeInventory(); x++) {
            final ItemStack is = upgrades.getStackInSlot(x);
            if (is != null && is.getItem() instanceof IUpgradeModule) {
                final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                if (u != null) {
                    switch (u) {
                        case VOID_OVERFLOW -> cardVoidOverflow = true;
                        case DISTRIBUTION -> cardDistribution = true;
                        default -> {}
                    }
                }
            }
        }
        this.container = container;
        this.tagCompound = Platform.openNbtData(o);
        this.storedItemTypes = this.tagCompound.getShort(ITEM_TYPE_TAG);
        this.storedItemCount = this.tagCompound.getLong(ITEM_COUNT_TAG);
        this.cellItems = null;
    }

    // ------------------------------------------------------------------
    // t68 旧 ECO 字节算法（weight = amountPerByte × byteMultiplier）
    // ------------------------------------------------------------------

    private long getTypeWeight() {
        return ITEM_AMOUNT_PER_BYTE * cellType.getByteMultiplier();
    }

    @Override
    public long getUsedBytes() {
        final long weight = getTypeWeight();
        final long bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / weight;
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    @Override
    public long getRemainingItemCount() {
        final long weight = getTypeWeight();
        final long freeBytes = this.getFreeBytes();
        // H1 (audit): freeBytes can be 2^59-1 (UNIVERSE) and weight ~4096+ → freeBytes*weight
        // overflows long → negative → clamped to 0 → AE2U addItems permanently rejects the cell
        // ("always full"). Saturate the multiplication instead.
        final long remaining;
        if (weight <= 0) {
            remaining = freeBytes;
        } else if (freeBytes > (Long.MAX_VALUE - this.getUnusedItemCount()) / weight) {
            remaining = Long.MAX_VALUE;
        } else {
            remaining = freeBytes * weight + this.getUnusedItemCount();
        }
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getUnusedItemCount() {
        final long weight = getTypeWeight();
        final long div = this.getStoredItemCount() % weight;
        if (div == 0) {
            return 0;
        }
        return (int) (weight - div);
    }

    // ------------------------------------------------------------------
    // IMEInventory
    // ------------------------------------------------------------------

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final BaseActionSource src) {
        if (input == null || input.getStackSize() == 0) {
            return null;
        }
        if (this.cellType.isBlackListed(this.cellItem, input)) {
            return input;
        }
        if (mode == Actionable.MODULATE && input.isCraftable()) {
            input.setCraftable(false);
        }
        final IAEItemStack l = this.getCellItems()
            .findPrecise(input);
        if (l != null) {
            long remainingItemSlots;
            if (cardDistribution) {
                remainingItemSlots = this.getRemainingItemsCountDist(l);
            } else {
                remainingItemSlots = this.getRemainingItemCount();
            }
            if (remainingItemSlots <= 0) {
                if (cardVoidOverflow) {
                    return null;
                }
                return input;
            }
            if (input.getStackSize() > remainingItemSlots) {
                final IAEItemStack r = input.copy();
                r.setStackSize(r.getStackSize() - remainingItemSlots);
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + remainingItemSlots);
                    this.updateItemCount(remainingItemSlots);
                    this.saveChanges();
                }
                return r;
            } else {
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + input.getStackSize());
                    this.updateItemCount(input.getStackSize());
                    this.saveChanges();
                }
                return null;
            }
        }
        if (this.canHoldNewItem()) {
            long remainingItemCount;
            if (cardDistribution) {
                remainingItemCount = this.getRemainingItemsCountDist(null);
            } else {
                remainingItemCount = this.getRemainingItemCount() - this.getBytesPerType() * ITEM_AMOUNT_PER_BYTE;
            }
            if (remainingItemCount > 0) {
                if (input.getStackSize() > remainingItemCount) {
                    final IAEItemStack toReturn = input.copy();
                    toReturn.decStackSize(remainingItemCount);
                    if (mode == Actionable.MODULATE) {
                        final IAEItemStack toWrite = input.copy();
                        toWrite.setStackSize(remainingItemCount);
                        this.cellItems.add(toWrite);
                        this.updateItemCount(toWrite.getStackSize());
                        this.saveChanges();
                    }
                    return toReturn;
                }
                if (mode == Actionable.MODULATE) {
                    this.updateItemCount(input.getStackSize());
                    this.cellItems.add(input);
                    this.saveChanges();
                }
                return null;
            }
        }
        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null) {
            return null;
        }
        final long size = request.getStackSize();
        IAEItemStack results = null;
        final IAEItemStack l = this.getCellItems()
            .findPrecise(request);
        if (l != null) {
            results = l.copy();
            if (l.getStackSize() <= size) {
                results.setStackSize(l.getStackSize());
                if (mode == Actionable.MODULATE) {
                    this.updateItemCount(-l.getStackSize());
                    l.setStackSize(0);
                    this.saveChanges();
                }
            } else {
                results.setStackSize(size);
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() - size);
                    this.updateItemCount(-size);
                    this.saveChanges();
                }
            }
        }
        return results;
    }

    private IItemList<IAEItemStack> getCellItems() {
        if (this.cellItems == null) {
            this.loadCellItems();
        }
        return this.cellItems;
    }

    private void updateItemCount(final long delta) {
        this.storedItemCount += delta;
        this.tagCompound.setLong(ITEM_COUNT_TAG, this.storedItemCount);
    }

    private void saveChanges() {
        long itemCount = 0;
        int x = 0;
        for (final IAEItemStack v : this.cellItems) {
            itemCount += v.getStackSize();
            final NBTTagCompound g = new NBTTagCompound();
            v.writeToNBT(g);
            this.tagCompound.setTag(itemSlots[x], g);
            this.tagCompound.setLong(itemSlotCount[x], v.getStackSize());
            x++;
        }
        final short oldStoredItems = this.storedItemTypes;
        this.storedItemTypes = (short) this.cellItems.size();
        if (this.cellItems.isEmpty()) {
            this.tagCompound.removeTag(ITEM_TYPE_TAG);
        } else {
            this.tagCompound.setShort(ITEM_TYPE_TAG, this.storedItemTypes);
        }
        this.storedItemCount = itemCount;
        if (itemCount == 0) {
            this.tagCompound.removeTag(ITEM_COUNT_TAG);
        } else {
            this.tagCompound.setLong(ITEM_COUNT_TAG, itemCount);
        }
        for (; x < oldStoredItems && x < this.maxItemTypes; x++) {
            this.tagCompound.removeTag(itemSlots[x]);
            this.tagCompound.removeTag(itemSlotCount[x]);
        }
        if (this.container != null) {
            this.container.saveChanges(this);
        }
    }

    private void loadCellItems() {
        if (this.cellItems == null) {
            this.cellItems = AEApi.instance()
                .storage()
                .createPrimitiveItemList();
        }
        this.cellItems.resetStatus();
        final int types = (int) this.getStoredItemTypes();
        for (int x = 0; x < types; x++) {
            final ItemStack t = ItemStack.loadItemStackFromNBT(this.tagCompound.getCompoundTag(itemSlots[x]));
            final IAEItemStack ias = AEItemStack.create(t);
            if (t != null) {
                ias.setStackSize(this.tagCompound.getLong(itemSlotCount[x]));
                if (ias.getStackSize() > 0) {
                    this.cellItems.add(ias);
                } else {
                    // Dirty compact (EC2 legacy): count lives in the item tag.
                    ias.setStackSize(
                        this.tagCompound.getCompoundTag(itemSlots[x])
                            .getLong("Cnt"));
                    if (ias.getStackSize() > 0) {
                        this.cellItems.add(ias);
                    }
                }
            }
        }
        if (this.cellItems.size() != types) {
            this.saveChanges();
        }
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out, int iteration) {
        for (final IAEItemStack i : this.getCellItems()) {
            out.add(i);
        }
        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request, int iteration) {
        long count = 0;
        for (final IAEItemStack is : this.getCellItems()) {
            if (is != null && is.getStackSize() > 0 && is.isSameType(request)) {
                count += is.getStackSize();
                if (count < 0) {
                    count = Long.MAX_VALUE;
                    break;
                }
            }
        }
        return count == 0 ? null
            : request.copy()
                .setStackSize(count);
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    // ------------------------------------------------------------------
    // ICellInventory / ICellCacheRegistry-style accessors
    // ------------------------------------------------------------------

    @Override
    public ItemStack getItemStack() {
        return this.cellItem;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain();
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.cellItem);
    }

    @Override
    public String getOreFilter() {
        return this.cellType.getOreFilter(this.cellItem);
    }

    @Override
    public IInventory getConfigInventory() {
        return this.cellType.getConfigInventory(this.cellItem);
    }

    @Override
    public IInventory getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.cellItem);
    }

    @Override
    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.cellItem);
    }

    @Override
    public boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
            || (bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0))
            && this.getRemainingItemTypes() > 0;
    }

    @Override
    public long getTotalBytes() {
        return this.cellType.getBytesLong(this.cellItem);
    }

    @Override
    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    @Override
    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getMaxItemTypes() {
        return this.maxItemTypes;
    }

    @Override
    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    @Override
    public long getStoredItemTypes() {
        return this.storedItemTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getBytesPerType() <= 0 ? this.getTotalItemTypes()
            : this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return basedOnStorage > baseOnTotal ? baseOnTotal : basedOnStorage;
    }

    @Override
    public long getRemainingItemsCountDist(IAEItemStack l) {
        long remaining;
        long types = 0;
        for (int i = 0; i < this.getTotalItemTypes(); i++) {
            if (this.getConfigInventory()
                .getStackInSlot(i) != null) {
                types++;
            }
        }
        if (types == 0) types = this.getTotalItemTypes();
        if (l != null) {
            remaining = (((getTotalBytes() / types) - getBytesPerType()) * ITEM_AMOUNT_PER_BYTE) - l.getStackSize();
        } else {
            remaining = ((this.getTotalBytes() / types) - this.getBytesPerType()) * ITEM_AMOUNT_PER_BYTE;
        }
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getStatusForCell() {
        if (this.getStoredItemCount() == 0) {
            return 1;
        }
        if (this.canHoldNewItem()) {
            return 2;
        }
        if (this.getRemainingItemCount() > 0) {
            return 3;
        }
        return 4;
    }

    /** Restriction cards are not used by ECO cells (AE2U parity hook, always empty). */
    public List<Object> getRestriction() {
        return Arrays.asList(0L, (byte) 0);
    }
}
