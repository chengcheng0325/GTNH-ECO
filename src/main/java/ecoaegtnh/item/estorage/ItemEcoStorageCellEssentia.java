package ecoaegtnh.item.estorage;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStackType;

/**
 * ECO E-Storage essentia cell — 10 sizes (256k … 人造宇宙), 60/80/100 essentia types by controller
 * tier (t76: L4→60, L6→80, L9→100). Stores Thaumcraft 4 essentia through the
 * ThaumicEnergistics (TE4) stack type.
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

    /** Max stored essentia types by controller tier (t76): L4→60, L6→80, L9→100. */
    public static final int MAX_TYPES_L4 = 60;
    public static final int MAX_TYPES_L6 = 80;
    public static final int MAX_TYPES_L9 = 100;

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
        // t114e: fall back to the L9 count if the aspect table is not ready yet (tooltip safety).
        if (size == CellSize.ARCANE) {
            try {
                int n = thaumcraft.api.aspects.Aspect.aspects.size();
                return n > 0 ? n : MAX_TYPES_L9;
            } catch (Throwable t) {
                return MAX_TYPES_L9;
            }
        }
        if (size.tier == 2) {
            return MAX_TYPES_L9;
        }
        if (size.tier == 1) {
            return MAX_TYPES_L6;
        }
        return MAX_TYPES_L4;
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
