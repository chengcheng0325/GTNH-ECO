package ecoaegtnh.ae2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.fluids.GaseousEssentia;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;

/**
 * 284 移植版 ECO 源质存储盘。695 世界没有源质通道——源质盘骑在 FLUIDS 通道上，把源质
 * 以 GaseousEssentia 流体形式暴露给 ME 网络（与 TE 1.7.14 原生
 * {@code HandlerItemEssentiaCell} 完全一致：IAEFluidStack 注入/提取 + 2 源质/字节）。
 * NBT 沿用 290 的稀疏 "Essentia#N"（{tag, Cnt}）布局（物品 tooltip 读取同一格式）。
 * 类型上限按档位 60/80/100（ItemEcoStorageCellEssentia），不复刻 TE 的 63 截断。
 */
public class EcoEssentiaCellInventory implements IMEInventoryHandler<IAEFluidStack>, ICellCacheRegistry {

    /** 2 源质 / 字节（TE4 ESSENTIA_PER_BYTE，docs/ESSENTIA_CELL_RESEARCH.md §6）。 */
    public static final long ESSENTIA_PER_BYTE = 2;

    private static final String NBT_ESSENTIA_NUMBER_KEY = "Essentia#";

    private final ItemStack cellItem;
    private final ItemEcoStorageCellEssentia cellType;
    private final ISaveProvider container;
    private final NBTTagCompound tagCompound;

    /** 每槽一个 Aspect 的存量（稀疏）；空槽为 null。 */
    private final Aspect[] storedAspects;
    private final long[] storedAmounts;
    private final int totalTypes;
    private long usedEssentiaStorage = 0;

    public EcoEssentiaCellInventory(ItemStack cell, ISaveProvider provider) {
        if (!(cell.getItem() instanceof ItemEcoStorageCellEssentia type)) {
            throw new IllegalArgumentException("ItemStack was used as an ECO essentia cell, but was not one!");
        }
        this.cellItem = cell;
        this.cellType = type;
        this.container = provider;
        this.totalTypes = type.getTotalTypes(cell);
        this.storedAspects = new Aspect[this.totalTypes];
        this.storedAmounts = new long[this.totalTypes];
        this.tagCompound = Platform.openNbtData(cell);
        this.loadCellData();
    }

    private void loadCellData() {
        for (int i = 0; i < this.totalTypes; i++) {
            if (!this.tagCompound.hasKey(NBT_ESSENTIA_NUMBER_KEY + i, 10)) continue;
            NBTTagCompound t = this.tagCompound.getCompoundTag(NBT_ESSENTIA_NUMBER_KEY + i);
            String aspectTag = t.getString("Aspect");
            Aspect aspect = aspectTag.isEmpty() ? null : Aspect.aspects.get(aspectTag);
            long amount = t.getLong("Cnt");
            if (aspect != null && amount > 0) {
                this.storedAspects[i] = aspect;
                this.storedAmounts[i] = amount;
                this.usedEssentiaStorage += amount;
            }
        }
    }

    private void saveChanges() {
        int index = 0;
        for (int i = 0; i < this.totalTypes; i++) {
            if (this.storedAspects[i] != null && this.storedAmounts[i] > 0) {
                NBTTagCompound t = new NBTTagCompound();
                t.setString("Aspect", this.storedAspects[i].getTag());
                t.setLong("Cnt", this.storedAmounts[i]);
                this.tagCompound.setTag(NBT_ESSENTIA_NUMBER_KEY + i, t);
                index++;
            } else {
                this.tagCompound.removeTag(NBT_ESSENTIA_NUMBER_KEY + i);
            }
        }
        if (this.container != null) {
            this.container.saveChanges(this);
        }
    }

    /** 找指定 Aspect 的槽位；无则找第一个空槽；-1 = 满。 */
    private int getSlotFor(Aspect aspect) {
        int empty = -1;
        for (int i = 0; i < this.totalTypes; i++) {
            if (this.storedAspects[i] == null) {
                if (empty == -1) empty = i;
                continue;
            }
            if (this.storedAspects[i] == aspect) return i;
        }
        return empty;
    }

    // ------------------------------------------------------------------
    // IMEInventory<IAEFluidStack>
    // ------------------------------------------------------------------

    @Override
    public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable mode, final BaseActionSource src) {
        if (input == null || input.getFluid() == null || !(input.getFluid() instanceof GaseousEssentia)) {
            return input == null ? null : input.copy();
        }
        Aspect aspect = ((GaseousEssentia) input.getFluid()).getAspect();
        long amountToStore = EssentiaConversionHelper.INSTANCE.convertFluidAmountToEssentiaAmount(input.getStackSize());
        if (amountToStore == 0) {
            return input.copy();
        }
        int slot = this.getSlotFor(aspect);
        if (slot == -1) {
            return input.copy(); // 类型满
        }
        long remaining = this.getFreeEssentia();
        if (remaining <= 0) {
            return input.copy();
        }
        amountToStore = Math.min(amountToStore, remaining);
        if (mode == Actionable.MODULATE) {
            if (this.storedAspects[slot] == null) {
                this.storedAspects[slot] = aspect;
            }
            this.storedAmounts[slot] += amountToStore;
            this.usedEssentiaStorage += amountToStore;
            this.saveChanges();
        }
        long notStored = input.getStackSize()
            - EssentiaConversionHelper.INSTANCE.convertEssentiaAmountToFluidAmount(amountToStore);
        if (notStored <= 0) {
            return null;
        }
        IAEFluidStack result = input.copy();
        result.setStackSize(notStored);
        return result;
    }

    @Override
    public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null || request.getFluid() == null || !(request.getFluid() instanceof GaseousEssentia)) {
            return null;
        }
        Aspect requestAspect = ((GaseousEssentia) request.getFluid()).getAspect();
        long essentiaRequested = EssentiaConversionHelper.INSTANCE
            .convertFluidAmountToEssentiaAmount(request.getStackSize());
        if (essentiaRequested == 0) {
            return null;
        }
        long extracted = this.extractEssentiaFromCell(requestAspect, essentiaRequested, mode);
        if (extracted == 0) {
            return null;
        }
        IAEFluidStack result = request.copy();
        result.setStackSize(EssentiaConversionHelper.INSTANCE.convertEssentiaAmountToFluidAmount(extracted));
        return result;
    }

    private long extractEssentiaFromCell(Aspect aspect, long amount, Actionable mode) {
        int slot = this.getSlotFor(aspect);
        if (slot == -1 || this.storedAspects[slot] == null) {
            return 0;
        }
        long toExtract = Math.min(this.storedAmounts[slot], amount);
        if (mode == Actionable.MODULATE) {
            this.storedAmounts[slot] -= toExtract;
            this.usedEssentiaStorage -= toExtract;
            if (this.storedAmounts[slot] == 0) {
                this.storedAspects[slot] = null;
            }
            this.saveChanges();
        }
        return toExtract;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(final IItemList<IAEFluidStack> out, int iteration) {
        for (int i = 0; i < this.totalTypes; i++) {
            if (this.storedAspects[i] == null) continue;
            GaseousEssentia gas = GaseousEssentia.getGasFromAspect(this.storedAspects[i]);
            if (gas != null) {
                out.add(
                    EssentiaConversionHelper.INSTANCE.createAEFluidStackInEssentiaUnits(gas, this.storedAmounts[i]));
            }
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    // ------------------------------------------------------------------
    // IMEInventoryHandler
    // ------------------------------------------------------------------

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final IAEFluidStack input) {
        return false;
    }

    @Override
    public boolean canAccept(final IAEFluidStack input) {
        if (input == null || input.getFluid() == null || !(input.getFluid() instanceof GaseousEssentia)) {
            return false;
        }
        return this.getSlotFor(((GaseousEssentia) input.getFluid()).getAspect()) != -1;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int pass) {
        boolean hasStored = this.usedEssentiaStorage > 0;
        if (pass == 1) {
            return hasStored;
        }
        return !hasStored;
    }

    @Override
    public boolean getSticky() {
        return false;
    }

    // ------------------------------------------------------------------
    // ICellCacheRegistry（盘位状态 / 网络统计）
    // ------------------------------------------------------------------

    @Override
    public boolean canGetInv() {
        return true;
    }

    @Override
    public long getTotalBytes() {
        return this.cellType.getTotalBytes();
    }

    @Override
    public long getFreeBytes() {
        return Math.max(0, this.getTotalBytes() - this.getUsedBytes());
    }

    @Override
    public long getUsedBytes() {
        return (long) Math.ceil(this.usedEssentiaStorage / (double) ESSENTIA_PER_BYTE);
    }

    @Override
    public long getTotalTypes() {
        return this.totalTypes;
    }

    @Override
    public long getFreeTypes() {
        return this.getTotalTypes() - this.getUsedTypes();
    }

    @Override
    public long getUsedTypes() {
        long count = 0;
        for (Aspect a : this.storedAspects) {
            if (a != null) count++;
        }
        return count;
    }

    @Override
    public int getCellStatus() {
        if (this.usedEssentiaStorage == 0) {
            return 1;
        }
        if (this.getFreeTypes() > 0 && this.getFreeBytes() > 0) {
            return 2;
        }
        if (this.getFreeBytes() > 0) {
            return 3;
        }
        return 4;
    }

    @Override
    public TYPE getCellType() {
        return TYPE.ESSENTIA;
    }

    private long getFreeEssentia() {
        return this.getTotalBytes() * ESSENTIA_PER_BYTE - this.usedEssentiaStorage;
    }

    /** 调试/工具用：当前存量（Aspect → 数量）。 */
    public List<Object[]> getStoredEssentia() {
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < this.totalTypes; i++) {
            if (this.storedAspects[i] != null) {
                result.add(new Object[] { this.storedAspects[i], this.storedAmounts[i] });
            }
        }
        return result;
    }
}
