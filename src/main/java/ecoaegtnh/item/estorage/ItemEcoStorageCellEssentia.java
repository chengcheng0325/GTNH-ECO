package ecoaegtnh.item.estorage;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStackType;

/**
 * ECO E-Storage essentia cell — 10 sizes (256k … 人造宇宙), 60/80/100 essentia types by capacity
 * band (t76, t122 naming: k-level→60, M-level→80, big-M level→100). Stores Thaumcraft 4 essentia
 * through the ThaumicEnergistics (TE4) stack type.
 * <p>
 * The TE4 reference ({@code thaumicenergistics.common.storage.AEEssentiaStackType}) is only
 * touched inside {@link EssentiaStackTypeHolder}, so this class can be loaded (and its
 * {@code instanceof} checks evaluated) without ThaumicEnergistics present. The items are only
 * instantiated when TE4 is loaded (see RegistryItems), which is when the holder is resolved.
 * <p>
 * Byte semantics follow TE4 (docs/ESSENTIA_CELL_RESEARCH.md §6): {@code getBytesPerType() = 0},
 * {@code getAmountPerByte() = 2} (from the stack type), so capacity counts essentia by amount.
 */
public class ItemEcoStorageCellEssentia extends ItemEcoStorageCell {

    /** Max stored essentia types by capacity band (t76, t122 naming): k→60, M→80, big-M→100. */
    public static final int MAX_TYPES_K = 60;
    public static final int MAX_TYPES_M = 80;
    public static final int MAX_TYPES_BIGM = 100;

    public ItemEcoStorageCellEssentia(CellSize size) {
        super(size, EssentiaStackTypeHolder.ESSENTIA);
    }

    @Override
    public String getCellBaseName() {
        return "essentia";
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        // t114: the arcane (创造源质元件复刻) cell accepts EVERY essentia aspect, exactly like the
        // TE4 creative cell (EnumEssentiaStorageTypes.Type_Creative maxStoredTypes =
        // Aspect.aspects.size()). Only reachable when TE4 is loaded (the item is registered then).
        // t114e: fall back to the big-M count if the aspect table is not ready yet (tooltip safety).
        if (size == CellSize.ARCANE) {
            try {
                int n = thaumcraft.api.aspects.Aspect.aspects.size();
                return n > 0 ? n : MAX_TYPES_BIGM;
            } catch (Throwable t) {
                return MAX_TYPES_BIGM;
            }
        }
        if (size.tier == 2) {
            return MAX_TYPES_BIGM;
        }
        if (size.tier == 1) {
            return MAX_TYPES_M;
        }
        return MAX_TYPES_K;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        // Essentia cells do not reserve bytes per type (TE4 BYTES_PER_ESSENTIA_TYPE = 0).
        return 0;
    }

    @Override
    public int BytePerType(ItemStack cellItem) {
        return 0;
    }

    /** Lazy reference to TE4's essentia stack type; resolved only when a cell is constructed. */
    private static final class EssentiaStackTypeHolder {

        static final IAEStackType<?> ESSENTIA = thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE;

        private EssentiaStackTypeHolder() {}
    }
}
