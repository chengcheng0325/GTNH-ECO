package ecoaegtnh.item.estorage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.data.IAEItemStack;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.ae2.EcoStorageCellInventory;

/**
 * Abstract ECO E-Storage cell item — ten sizes in three controller tiers (t76):
 * L4 = 256k/1024k/4096k, L6 = 16M/64M/256M, L9 = 1024M/4096M/16384M (+ t113 Artificial
 * Universe, also L9). Implements AE2U's {@link IStorageCell}.
 * <p>
 * Capacity follows the old ECO design (t68): k-level totalBytes = value x 1024, M-level
 * totalBytes = value x 1000 x 1024; perType = byteMultiplier x 1024 (see {@link CellSize}).
 * <p>
 * 284 移植版：695 无 IAEStackType——物品家族用 {@link #getStorageType()} 判定，
 * 单位/字节由 {@link #getAmountPerByte()} 提供（物品 8 / 流体 2048 / 源质 2）。
 */
public abstract class ItemEcoStorageCell extends Item implements IStorageCell {

    protected final CellSize size;

    public ItemEcoStorageCell(CellSize size) {
        this.size = size;
        setMaxStackSize(1);
        setCreativeTab(EcoAEGTNHCore.TAB_STORAGE);
        // Keep the estorage_cell_ prefix in sync with setTextureName and the lang keys
        // (item.ecoaegtnh.estorage_cell_<type>_<size>.name), otherwise the display name falls
        // back to the raw key (t21).
        setUnlocalizedName("ecoaegtnh.estorage_cell_" + getCellBaseName() + "_" + size.label);
        setTextureName("ecoaegtnh:estorage_cell_" + getCellBaseName() + "_" + size.label);
    }

    /** Total byte capacity (t68/t76): k-level value×1024, M-level value×1000×1024. */
    public long getTotalBytes() {
        return size.totalBytes;
    }

    /** t68/t76: byte multiplier used by the inventory byte math (perType = multiplier x 1024). */
    public int getByteMultiplier() {
        return size.byteMultiplier;
    }

    /** 284：单位/字节（t68 weight 的 amountPerByte 项）——物品 8 / 流体 2048 / 源质 2。 */
    public long getAmountPerByte() {
        if (getStorageType() == StorageType.FLUID) {
            return ecoaegtnh.ae2.EcoFluidCellInventory.FLUID_AMOUNT_PER_BYTE;
        }
        if (getStorageType() == StorageType.ESSENTIA) {
            return ecoaegtnh.ae2.EcoEssentiaCellInventory.ESSENTIA_PER_BYTE;
        }
        return EcoStorageCellInventory.ITEM_AMOUNT_PER_BYTE;
    }

    public CellSize getSize() {
        return size;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return (int) Math.min(Integer.MAX_VALUE, getTotalBytes());
    }

    @Override
    public long getBytesLong(ItemStack cellItem) {
        return getTotalBytes();
    }

    @Override
    public int BytePerType(ItemStack cellItem) {
        return getBytesPerType(cellItem);
    }

    /** t68/t76: perType = byteMultiplier x 1024. */
    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return size.byteMultiplier * 1024;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, IAEItemStack requestedAddition) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(ItemStack i) {
        return true;
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        // t25: vanilla skips block activation for sneak + held item, so shift+right-click with a
        // cell in hand never reached the drive bay's onBlockActivated. Returning true routes the
        // sneak click to the block (the cell itself has no right-click use).
        return true;
    }

    /**
     * t63/t68/t76: idle drain — M-level keeps the AE2U-aligned MB/4 (16M→4.0, 64M→16.0, ...);
     * k-level scales the same way with a 0.5 floor (256k→0.5, 1024k→0.5, 4096k≈1.02). See
     * {@link CellSize#idleDrain()}.
     */
    @Override
    public double getIdleDrain() {
        return size.idleDrain();
    }

    // ------------------------------------------------------------------
    // ICellWorkbenchItem
    // ------------------------------------------------------------------

    @Override
    public boolean isEditable(ItemStack is) {
        // t114: the family-exclusive infinite cells are not editable, matching the AE2FC
        // infinite water cell / TE4 creative essentia cell.
        return !isInfinite();
    }

    /**
     * t114: true for the family-exclusive infinite cells (INF_WATER fluid cell / ARCANE essentia
     * cell) — Long.MAX_VALUE capacity, fixed config, no idle drain, not editable.
     */
    public boolean isInfinite() {
        return size == CellSize.INF_WATER || size == CellSize.ARCANE;
    }

    /**
     * t114 (284 版)：INF_WATER 的 config 固定一格水桶（AE2FC InfinityConfig 复刻）——
     * 无限水盘的 creative 库存按此 config 把水以 2^52-1 暴露给网络；ARCANE 源质盘不需要
     * config（creative 库存自带全部源质，TE4 creative 同款），返回普通 CellConfig。
     */
    @Override
    public net.minecraft.inventory.IInventory getConfigInventory(ItemStack is) {
        if (size == CellSize.INF_WATER) {
            return new FixedWaterConfig();
        }
        return new CellConfig(is);
    }

    /** t114: fixed one-slot water config for the infinite-water cell (AE2FC InfinityConfig parity). */
    public static final class FixedWaterConfig extends AppEngInternalInventory {

        public FixedWaterConfig() {
            super(null, 1);
            this.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.water_bucket, 1, 0));
        }

        @Override
        public void markDirty() {}
    }

    @Override
    public net.minecraft.inventory.IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        String fz = Platform.openNbtData(is)
            .getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        NBTTagCompound data = Platform.openNbtData(is);
        data.setString("FuzzyMode", fzMode.name());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List lines,
        boolean advanced) {
        super.addInformation(stack, player, lines, advanced);
        lines.add(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.estorage_cell.insert.tip"));
        lines.add(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.estorage_cell.interact.tip"));
        lines.add(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.estorage_cell.extract.tip"));
        // Tier hints: 256M (C) needs L9; 64M (B) needs L6/L9.
        String tierHint = getTierHintKey();
        if (tierHint != null) {
            lines.add(net.minecraft.util.StatCollector.translateToLocal(tierHint));
        }
        // t114e: the infinite family-exclusive cells always show their capacity/type summary up
        // front (even if the inventory readout below hits an unexpected exception).
        if (isInfinite()) {
            lines.add(
                net.minecraft.util.StatCollector.translateToLocal(
                    size == CellSize.INF_WATER ? "ecoaegtnh.estorage_cell.infinite_water.tip"
                        : "ecoaegtnh.estorage_cell.arcane.tip"));
        }
        addStorageInformation(lines, stack);
    }

    /**
     * AE-style storage readout on the item tooltip (t33): "Used: X / Y bytes" and
     * "Types: N / M", built from the cell's NBT inventory (client-safe, no grid required).
     * <p>
     * 284 版：物品/流体盘读 "it"/"ic"（EcoStorageCellInventory 写），源质盘读稀疏
     * "Essentia#N" 槽（EcoEssentiaCellInventory 写，大小在 "Cnt"）。284 的自写库存
     * 不再有 AE2U 的 63 类型截断——源质盘分母用档位真实值（60/80/100）。
     */
    private void addStorageInformation(java.util.List<String> lines, ItemStack stack) {
        try {
            final boolean essentia = this instanceof ItemEcoStorageCellEssentia;
            final NBTTagCompound tag = stack.getTagCompound();
            final long totalTypes = getTotalTypes(stack);
            long storedTypes;
            long storedCount;
            if (tag == null) {
                storedTypes = 0;
                storedCount = 0;
            } else if (essentia) {
                storedTypes = 0;
                storedCount = 0;
                for (int i = 0; i < totalTypes; i++) {
                    if (!tag.hasKey("Essentia#" + i, 10)) continue;
                    final long sz = tag.getCompoundTag("Essentia#" + i)
                        .getLong("Cnt");
                    if (sz > 0) {
                        storedTypes++;
                        storedCount += sz;
                    }
                }
            } else if (this instanceof ItemEcoStorageCellFluid) {
                // "ft" (short stored types) / "fc" (long stored count) written by
                // EcoFluidCellInventory.
                storedTypes = tag.getShort(ecoaegtnh.ae2.EcoFluidCellInventory.TYPE_TAG);
                storedCount = tag.getLong(ecoaegtnh.ae2.EcoFluidCellInventory.COUNT_TAG);
            } else {
                // "it" (short stored types) / "ic" (long stored count) written by
                // EcoStorageCellInventory.
                storedTypes = tag.getShort(ecoaegtnh.ae2.EcoStorageCellInventory.TYPE_TAG);
                storedCount = tag.getLong(ecoaegtnh.ae2.EcoStorageCellInventory.COUNT_TAG);
            }

            // t68 byte formula: weight = amountPerByte x byteMultiplier (item/fluid); essentia has
            // no byteMultiplier (TE4 weight = amountPerByte) and bytesPerType = 0.
            final long weight = getAmountPerByte() * (essentia ? 1L : getByteMultiplier());
            final long div = weight > 0 ? storedCount % weight : 0;
            final long unused = div == 0 ? 0 : weight - div;
            final long usedBytes = storedTypes * getBytesPerType(stack)
                + (weight > 0 ? (storedCount + unused) / weight : 0);

            lines.add(
                net.minecraft.util.EnumChatFormatting.GRAY
                    + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.estorage_cell.used.tip")
                    + " "
                    + net.minecraft.util.EnumChatFormatting.WHITE
                    + formatBytes(usedBytes)
                    + net.minecraft.util.EnumChatFormatting.GRAY
                    + " / "
                    + net.minecraft.util.EnumChatFormatting.WHITE
                    + formatBytes(getTotalBytes())
                    + net.minecraft.util.EnumChatFormatting.GRAY
                    + " "
                    + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.estorage_cell.bytes.tip"));
            lines.add(
                net.minecraft.util.EnumChatFormatting.GRAY
                    + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.estorage_cell.types.tip")
                    + " "
                    + net.minecraft.util.EnumChatFormatting.WHITE
                    + storedTypes
                    + net.minecraft.util.EnumChatFormatting.GRAY
                    + " / "
                    + net.minecraft.util.EnumChatFormatting.WHITE
                    + totalTypes);
        } catch (Throwable ignored) {
            // Never let a tooltip crash the client.
        }
    }

    /** Compact byte formatting for the tooltip readout (e.g. 2.4M / 16.4M / 576.5P / 9.2E). */
    private static String formatBytes(long v) {
        if (v >= 1_000_000_000_000_000_000L) {
            return String.format("%.1fE", v / 1e18);
        }
        if (v >= 1_000_000_000_000_000L) {
            return String.format("%.1fP", v / 1e15);
        }
        if (v >= 1_000_000_000L) {
            return String.format("%.1fG", v / 1e9);
        }
        if (v >= 1_000_000L) {
            return String.format("%.1fM", v / 1e6);
        }
        if (v >= 1_000L) {
            return String.format("%.1fK", v / 1e3);
        }
        return String.valueOf(v);
    }

    /** Cell kind for WAILA / display purposes ("item"/"fluid"/"essentia"). */
    public abstract String getCellBaseName();

    /** Size label for display ("256k", "16m", "16384m", ...). */
    public String getSizeLabel() {
        return size.label;
    }

    /** Capacity in MB-equivalent for WAILA / display (k-levels are sub-MB: 256k → 0, 1024k → 1). */
    public int getCapacityMB() {
        return size.capacityMB();
    }

    /** t76: the controller tier this cell requires (k → L4, 16M..256M → L6, 1024M..16384M → L9). */
    public int getTierRequired() {
        return size.tier;
    }

    /**
     * t51 (milestone): storage-family selector for the three main lines (item/fluid/essentia).
     */
    public StorageType getStorageType() {
        if (this instanceof ItemEcoStorageCellFluid) return StorageType.FLUID;
        if (this instanceof ItemEcoStorageCellEssentia) return StorageType.ESSENTIA;
        return StorageType.ITEM;
    }

    /**
     * t112/t114d (one cell per node, docs §3): the storage-tree node this cell requires on its
     * line — one node per SIZE, numbered by the size's position WITHIN the family chain
     * ({@link CellSize#chainIndex}; 256k → I1/F1/E1 … 人造宇宙 → I10/F10/E10, 无限水 → F11,
     * 魔导源质 → E11). Node N unlocks exactly the cell of that size.
     */
    public String getRequiredUpgradeNode() {
        String prefix = getStorageType() == StorageType.FLUID ? "F"
            : getStorageType() == StorageType.ESSENTIA ? "E" : "I";
        return prefix + size.chainIndex(getStorageType());
    }

    /** @return the lang key of the controller tier requirement, or null for L4-compatible cells. */
    protected String getTierHintKey() {
        if (size.tier == 2) {
            return "ecoaegtnh.estorage_cell.l9.tip";
        }
        if (size.tier == 1) {
            return "ecoaegtnh.estorage_cell.l6.tip";
        }
        return null;
    }
}
