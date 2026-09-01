package ecoaegtnh.item.estorage;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.item.ItemStack;

/**
 * ECO E-Storage item cell — 10 sizes (256k … 人造宇宙), 315 item types (t68; exposed via
 * EcoStorageCellInventory.getTotalItemTypes so the tooltip shows "Types: N / 315" and up to 315
 * types are accepted).
 */
public class ItemEcoStorageCellItem extends ItemEcoStorageCell {

    public static final int MAX_TYPES = 315;

    public ItemEcoStorageCellItem(CellSize size) {
        super(size, ITEM_STACK_TYPE);
    }

    @Override
    public String getCellBaseName() {
        return "item";
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return MAX_TYPES;
    }
}
