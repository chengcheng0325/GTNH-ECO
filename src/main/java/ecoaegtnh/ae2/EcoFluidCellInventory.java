package ecoaegtnh.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;

/**
 * 284 移植版 ECO 流体存储盘。695 没有泛型 CellInventory，流体盘按 AE2FC 1.4.120 的
 * FluidCellInventory 独立实现模式重写：NBT 键 ft/fc/#N/@N，t68 旧 ECO 字节算法
 * （weight = amountPerByte(2048) × byteMultiplier），类型上限 25（无限水盘 = 1）。
 * INF_WATER 无限盘由子类 {@link EcoFluidCellInventoryInfinite} 提供（AE2FC
 * CreativeFluidCellInventory 同款：按 config 灌 2^52-1）。
 */
public class EcoFluidCellInventory implements IMEInventoryFluid {

    /** 流体单位（mb）/字节（AE2U AEFluidStackType.AMOUNT_PER_BYTE = 2048）。 */
    public static final int FLUID_AMOUNT_PER_BYTE = 2048;

    private static final String FLUID_TYPE_TAG = "ft";
    private static final String FLUID_COUNT_TAG = "fc";
    private static final String FLUID_SLOT = "#";
    private static final String FLUID_SLOT_COUNT = "@";

    /** t84 tooltip 读取同一对 NBT 键（公开给 ItemEcoStorageCell.addStorageInformation）。 */
    public static final String TYPE_TAG = FLUID_TYPE_TAG;
    public static final String COUNT_TAG = FLUID_COUNT_TAG;

    private static String[] fluidSlots;
    private static String[] fluidSlotCount;

    private final NBTTagCompound tagCompound;
    private final ISaveProvider container;
    private final int maxFluidTypes;
    private short storedFluidTypes = 0;
    private long storedFluidCount = 0;
    protected IItemList<IAEFluidStack> cellFluids;
    private final ItemStack cellItem;
    private final ItemEcoStorageCell cellType;
    private boolean cardVoidOverflow = false;
    private boolean cardDistribution = false;

    public EcoFluidCellInventory(ItemStack o, ISaveProvider container) throws AppEngException {
        if (fluidSlots == null) {
            fluidSlots = new String[25];
            fluidSlotCount = new String[25];
            for (int x = 0; x < 25; x++) {
                fluidSlots[x] = FLUID_SLOT + x;
                fluidSlotCount[x] = FLUID_SLOT_COUNT + x;
            }
        }
        if (o == null || !(o.getItem() instanceof ItemEcoStorageCell)) {
            throw new AppEngException("ItemStack was used as an ECO fluid cell, but was not one!");
        }
        this.cellItem = o;
        this.cellType = (ItemEcoStorageCell) o.getItem();
        this.maxFluidTypes = this.cellType.getTotalTypes(o);
        if (this.maxFluidTypes < 1) {
            throw new AppEngException("ECO fluid cell declares no fluid types!");
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
        this.storedFluidTypes = this.tagCompound.getShort(FLUID_TYPE_TAG);
        this.storedFluidCount = this.tagCompound.getLong(FLUID_COUNT_TAG);
        this.cellFluids = null;
    }

    // ------------------------------------------------------------------
    // t68 字节算法
    // ------------------------------------------------------------------

    private long getTypeWeight() {
        return FLUID_AMOUNT_PER_BYTE * cellType.getByteMultiplier();
    }

    @Override
    public long getUsedBytes() {
        final long weight = getTypeWeight();
        final long bytesForFluidCount = (this.getStoredFluidCount() + this.getUnusedFluidCount()) / weight;
        return this.getStoredFluidTypes() * this.getBytesPerType() + bytesForFluidCount;
    }

    @Override
    public long getRemainingFluidCount() {
        final long remaining = this.getFreeBytes() * getTypeWeight() + this.getUnusedFluidCount();
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getUnusedFluidCount() {
        final long weight = getTypeWeight();
        final long div = this.getStoredFluidCount() % weight;
        if (div == 0) {
            return 0;
        }
        return (int) (weight - div);
    }

    // ------------------------------------------------------------------
    // IMEInventory<IAEFluidStack>
    // ------------------------------------------------------------------

    @Override
    public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable mode, final BaseActionSource src) {
        if (input == null || input.getStackSize() == 0) {
            return null;
        }
        if (mode == Actionable.MODULATE && input.isCraftable()) {
            input.setCraftable(false);
        }
        final IAEFluidStack l = this.getCellFluids()
            .findPrecise(input);
        if (l != null) {
            long remainingFluidSlots;
            if (cardDistribution) {
                remainingFluidSlots = this.getRemainingFluidCountDist(l);
            } else {
                remainingFluidSlots = this.getRemainingFluidCount();
            }
            if (remainingFluidSlots <= 0) {
                if (cardVoidOverflow) {
                    return null;
                }
                return input;
            }
            if (input.getStackSize() > remainingFluidSlots) {
                final IAEFluidStack r = input.copy();
                r.setStackSize(r.getStackSize() - remainingFluidSlots);
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + remainingFluidSlots);
                    this.updateFluidCount(remainingFluidSlots);
                    this.saveChanges();
                }
                return r;
            } else {
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + input.getStackSize());
                    this.updateFluidCount(input.getStackSize());
                    this.saveChanges();
                }
                return null;
            }
        }
        if (this.canHoldNewFluid()) {
            long remainingFluidCount;
            if (cardDistribution) {
                remainingFluidCount = this.getRemainingFluidCountDist(null);
            } else {
                remainingFluidCount = this.getRemainingFluidCount() - this.getBytesPerType() * FLUID_AMOUNT_PER_BYTE;
            }
            if (remainingFluidCount > 0) {
                if (input.getStackSize() > remainingFluidCount) {
                    final IAEFluidStack toReturn = input.copy();
                    toReturn.decStackSize(remainingFluidCount);
                    if (mode == Actionable.MODULATE) {
                        final IAEFluidStack toWrite = input.copy();
                        toWrite.setStackSize(remainingFluidCount);
                        this.cellFluids.add(toWrite);
                        this.updateFluidCount(toWrite.getStackSize());
                        this.saveChanges();
                    }
                    return toReturn;
                }
                if (mode == Actionable.MODULATE) {
                    this.updateFluidCount(input.getStackSize());
                    this.cellFluids.add(input);
                    this.saveChanges();
                }
                return null;
            }
        }
        return input;
    }

    @Override
    public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null) {
            return null;
        }
        final long size = request.getStackSize();
        IAEFluidStack results = null;
        final IAEFluidStack l = this.getCellFluids()
            .findPrecise(request);
        if (l != null) {
            results = l.copy();
            if (l.getStackSize() <= size) {
                results.setStackSize(l.getStackSize());
                if (mode == Actionable.MODULATE) {
                    this.updateFluidCount(-l.getStackSize());
                    l.setStackSize(0);
                    this.saveChanges();
                }
            } else {
                results.setStackSize(size);
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() - size);
                    this.updateFluidCount(-size);
                    this.saveChanges();
                }
            }
        }
        return results;
    }

    protected IItemList<IAEFluidStack> getCellFluids() {
        if (this.cellFluids == null) {
            this.loadCellFluids();
        }
        return this.cellFluids;
    }

    private void updateFluidCount(final long delta) {
        this.storedFluidCount += delta;
        this.tagCompound.setLong(FLUID_COUNT_TAG, this.storedFluidCount);
    }

    private void saveChanges() {
        long fluidCount = 0;
        int x = 0;
        for (final IAEFluidStack v : this.cellFluids) {
            fluidCount += v.getStackSize();
            final NBTTagCompound g = new NBTTagCompound();
            v.writeToNBT(g);
            this.tagCompound.setTag(fluidSlots[x], g);
            this.tagCompound.setLong(fluidSlotCount[x], v.getStackSize());
            x++;
        }
        final short oldStoredFluids = this.storedFluidTypes;
        this.storedFluidTypes = (short) this.cellFluids.size();
        if (this.cellFluids.isEmpty()) {
            this.tagCompound.removeTag(FLUID_TYPE_TAG);
        } else {
            this.tagCompound.setShort(FLUID_TYPE_TAG, this.storedFluidTypes);
        }
        this.storedFluidCount = fluidCount;
        if (fluidCount == 0) {
            this.tagCompound.removeTag(FLUID_COUNT_TAG);
        } else {
            this.tagCompound.setLong(FLUID_COUNT_TAG, fluidCount);
        }
        for (; x < oldStoredFluids && x < this.maxFluidTypes; x++) {
            this.tagCompound.removeTag(fluidSlots[x]);
            this.tagCompound.removeTag(fluidSlotCount[x]);
        }
        if (this.container != null) {
            this.container.saveChanges(this);
        }
    }

    protected void loadCellFluids() {
        if (this.cellFluids == null) {
            this.cellFluids = AEApi.instance()
                .storage()
                .createFluidList();
        }
        this.cellFluids.resetStatus();
        final int types = (int) this.getStoredFluidTypes();
        for (int x = 0; x < types; x++) {
            final IAEFluidStack ias = AEFluidStack
                .loadFluidStackFromNBT(this.tagCompound.getCompoundTag(fluidSlots[x]));
            if (ias != null) {
                ias.setStackSize(this.tagCompound.getLong(fluidSlotCount[x]));
                if (ias.getStackSize() > 0) {
                    this.cellFluids.add(ias);
                }
            }
        }
        if (this.cellFluids.size() != types) {
            this.saveChanges();
        }
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(final IItemList<IAEFluidStack> out, int iteration) {
        for (final IAEFluidStack i : this.getCellFluids()) {
            out.add(i);
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    // ------------------------------------------------------------------
    // Accessors（handler 经 IMEInventoryFluid 读取统计）
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
    public boolean canHoldNewFluid() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
            || (bytesFree == this.getBytesPerType() && this.getUnusedFluidCount() > 0))
            && this.getRemainingFluidTypes() > 0;
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
    public long getTotalFluidTypes() {
        return this.maxFluidTypes;
    }

    @Override
    public long getStoredFluidCount() {
        return this.storedFluidCount;
    }

    @Override
    public long getStoredFluidTypes() {
        return this.storedFluidTypes;
    }

    @Override
    public long getRemainingFluidTypes() {
        final long basedOnStorage = this.getBytesPerType() <= 0 ? this.getTotalFluidTypes()
            : this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalFluidTypes() - this.getStoredFluidTypes();
        return basedOnStorage > baseOnTotal ? baseOnTotal : basedOnStorage;
    }

    @Override
    public long getRemainingFluidCountDist(IAEFluidStack l) {
        long remaining;
        long types = 0;
        for (int i = 0; i < this.getTotalFluidTypes(); i++) {
            if (this.getConfigInventory()
                .getStackInSlot(i) != null) {
                types++;
            }
        }
        if (types == 0) types = this.getTotalFluidTypes();
        if (l != null) {
            remaining = (((getTotalBytes() / types) - getBytesPerType()) * FLUID_AMOUNT_PER_BYTE) - l.getStackSize();
        } else {
            remaining = ((this.getTotalBytes() / types) - this.getBytesPerType()) * FLUID_AMOUNT_PER_BYTE;
        }
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getStatusForCell() {
        if (this.getStoredFluidCount() == 0) {
            return 1;
        }
        if (this.canHoldNewFluid()) {
            return 2;
        }
        if (this.getRemainingFluidCount() > 0) {
            return 3;
        }
        return 4;
    }
}
