package ecoaegtnh.item.estorage;

import net.minecraft.item.ItemStack;

/**
 * ECO E-Storage fluid cell — 10 sizes (256k … 人造宇宙), 25 fluid types.
 * 284 版：走 FLUIDS 通道（无限水盘 = INF_WATER 固定 1 型）。
 */
public class ItemEcoStorageCellFluid extends ItemEcoStorageCell {

    public static final int MAX_TYPES = 25;

    public ItemEcoStorageCellFluid(CellSize size) {
        super(size);
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
