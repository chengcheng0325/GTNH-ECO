package ecoaegtnh.item.estorage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.data.IAEStackType;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.util.Platform;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.ae2.EcoStorageCellInventory;

/**
 * Abstract ECO E-Storage cell item — ten sizes in three capacity bands (t76):
 * k-level = 256k/1024k/4096k, M-level = 16M/64M/256M, big-M level = 1024M/4096M/16384M
 * (+ t113 Artificial Universe, also big-M). Implements AE2U's {@link IStorageCell}.
 * <p>
 * Capacity follows the old ECO design (t68): k-level totalBytes = value x 1024, M-level
 * totalBytes = value x 1000 x 1024; perType = byteMultiplier x 1024 (see {@link CellSize}).
 */
public abstract class ItemEcoStorageCell extends Item implements IStorageCell {

    protected final CellSize size;
    protected final IAEStackType<?> stackType;

    public ItemEcoStorageCell(CellSize size, IAEStackType<?> stackType) {
        this.size = size;
        this.stackType = stackType;
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

    @Override
    public IAEStackType<?> getStackType() {
        return stackType;
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
     * t114: the infinite-water fluid cell accepts ONLY water — a fixed one-slot config holding
     * a water bucket (AE2FC ItemInfinityWaterStorageCell.InfinityConfig 复刻). The AE cell
     * inventory reads this config as its partition list, so the bay only stores water. The
     * arcane essentia cell uses TE4's CreativeEssentiaCellConfig — EVERY aspect, so the
     * creative inventory advertises all of them into the network (TE4 creative-cell parity).
     */
    @Override
    public appeng.tile.inventory.IAEStackInventory getConfigAEInventory(ItemStack is) {
        if (size == CellSize.INF_WATER) {
            return new FixedWaterConfig();
        }
        if (size == CellSize.ARCANE) {
            return new thaumicenergistics.common.inventory.CreativeEssentiaCellConfig();
        }
        return new CellConfig(is);
    }

    /** t114: fixed one-slot water config for the infinite-water cell (AE2FC InfinityConfig parity). */
    private static final class FixedWaterConfig extends appeng.tile.inventory.IAEStackInventory {

        FixedWaterConfig() {
            super(null, 1);
            putAEStackInSlot(
                0,
                appeng.util.item.AEFluidStack.create(
                    new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000)));
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
        // t115: the old "requires ME network power" and per-tier line-level hints were removed
        // (t122 naming pass) — the insert gate already reports the exact required upgrade-tree
        // node, and cells need no power to sit in a drive bay.
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
     * t84: the readout no longer constructs the AE cell-inventory chain
     * (EcoStorageCellHandler.getCellInventory -> new EcoStorageCellInventory, which builds the
     * upgrade/config inventories and deserializes every stored stack). The creative-tab first-open
     * lag (user-reported: only the ECO tab stuttered — 27 cells, each hover rebuilding that chain)
     * is gone because the same numbers are now derived directly from the cell NBT keys that
     * CellInventory itself writes ("it"/"ic" short/long, or the sparse "Essentia#N" slots for
     * essentia cells) plus the t68 byte formula — a pure tag read, zero inventory construction,
     * and no NBT side effect on hover (the old path created the tag when missing).
     * A {@code Throwable} guard keeps the tooltip from ever crashing the client (e.g. an essentia
     * cell hovered while ThaumicEnergistics is absent — items only exist then, but the path stays
     * defensive regardless).
     */
    private void addStorageInformation(java.util.List<String> lines, ItemStack stack) {
        try {
            final boolean essentia = this instanceof ItemEcoStorageCellEssentia;
            final NBTTagCompound tag = stack.getTagCompound();
            // Essentia cells are clamped by AE2U's base CellInventory to 63 max types (no
            // getTotalItemTypes override), so the tooltip denominator/scan bound mirrors that.
            final long totalTypes = essentia ? Math.min(getTotalTypes(stack), 63) : getTotalTypes(stack);
            long storedTypes;
            long storedCount;
            if (tag == null) {
                storedTypes = 0;
                storedCount = 0;
            } else if (essentia) {
                // Sparse "Essentia#N" slots, exactly like
                // EcoStorageCellInventoryEssentia.loadCellStacks (AEEssentiaStack.writeToNBT stores
                // the size under "Cnt") — matches the inventory path without building one.
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
            } else {
                // "it" (short stored types) / "ic" (long stored count) written by
                // CellInventory.saveChanges (EcoStorageCellInventory.ITEM_TYPE_TAG/ITEM_COUNT_TAG).
                storedTypes = tag.getShort(EcoStorageCellInventory.ITEM_TYPE_TAG);
                storedCount = tag.getLong(EcoStorageCellInventory.ITEM_COUNT_TAG);
            }

            // t68 byte formula: weight = amountPerByte x byteMultiplier (item/fluid); essentia has
            // no byteMultiplier (TE4 weight = amountPerByte) and bytesPerType = 0.
            final long weight = getStackType().getAmountPerByte() * (essentia ? 1L : getByteMultiplier());
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

    /** t76: the capacity band this cell belongs to (0 = k-level, 1 = M-level, 2 = big-M level). */
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
     * t128b (merged groups, docs §3 revision): the storage-tree node this cell requires on its
     * line — one node per capacity GROUP, unlocking three cell tiers at once
     * ({@link CellSize#upgradeGroupIndex}; 256k..4096k → I1/F1/E1, 16M..256M → I2/F2/E2,
     * 1024M..16384M → I3/F3/E3, 人造宇宙 → I4/F4/E4, 无限水 → F5, 魔导源质 → E5). Activating the
     * group node allows every cell of that group.
     */
    public String getRequiredUpgradeNode() {
        String prefix = getStorageType() == StorageType.FLUID ? "F"
            : getStorageType() == StorageType.ESSENTIA ? "E" : "I";
        return prefix + size.upgradeGroupIndex();
    }
}
