package ecoaegtnh.item.estorage;

import net.minecraft.item.ItemStack;

/**
 * ECO E-Storage item cell — 10 sizes (256k … 人造宇宙), 315 item types (t68; exposed via
 * EcoStorageCellInventory so the tooltip shows "Types: N / 315" and up to 315 types are accepted).
 * 284 版：无 IAEStackType，家族由 instanceof / getStorageType() 判定，走 ITEMS 通道。
 */
public class ItemEcoStorageCellItem extends ItemEcoStorageCell {

    public static final int MAX_TYPES = 315;

    public ItemEcoStorageCellItem(CellSize size) {
        super(size);
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
