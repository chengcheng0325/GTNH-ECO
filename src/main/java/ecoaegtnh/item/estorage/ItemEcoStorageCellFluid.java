package ecoaegtnh.item.estorage;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import net.minecraft.item.ItemStack;

/**
 * ECO E-Storage fluid cell — 10 sizes (256k … 人造宇宙), 25 fluid types.
 */
public class ItemEcoStorageCellFluid extends ItemEcoStorageCell {

    public static final int MAX_TYPES = 25;

    public ItemEcoStorageCellFluid(CellSize size) {
        super(size, FLUID_STACK_TYPE);
    }

    @Override
    public String getCellBaseName() {
        return "fluid";
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        // t114: the infinite-water cell holds exactly one type (water), like the AE2FC original.
        if (size == CellSize.INF_WATER) {
            return 1;
        }
        return MAX_TYPES;
    }
}
