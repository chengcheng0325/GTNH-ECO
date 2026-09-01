package ecoaegtnh.item.estorage;

import net.minecraft.item.ItemStack;

/**
 * ECO E-Storage essentia cell — 10 sizes (256k … 人造宇宙), 60/80/100 essentia types by controller
 * tier (t76: L4→60, L6→80, L9→100). Stores Thaumcraft 4 essentia.
 * <p>
 * 284 移植版：695 世界没有源质通道——源质盘以 GaseousEssentia 流体形式走 FLUIDS 通道
 * （与 TE 1.7.14 原生源质盘一致），存储实现见
 * {@code ecoaegtnh.ae2.EcoEssentiaCellInventory}（TE4 类只在处理源质盘时才被加载）。
 * Byte 语义沿用 TE4（docs/ESSENTIA_CELL_RESEARCH.md §6）：bytesPerType = 0、2 源质/字节。
 */
public class ItemEcoStorageCellEssentia extends ItemEcoStorageCell {

    /** Max stored essentia types by controller tier (t76): L4→60, L6→80, L9→100. */
    public static final int MAX_TYPES_L4 = 60;
    public static final int MAX_TYPES_L6 = 80;
    public static final int MAX_TYPES_L9 = 100;

    public ItemEcoStorageCellEssentia(CellSize size) {
        super(size);
    }

    @Override
    public String getCellBaseName() {
        return "essentia";
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        // t114: the arcane (创造源质元件复刻) cell accepts EVERY essentia aspect, exactly like the
        // TE4 creative cell. Only reachable when TE4 is loaded (the item is registered then).
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
}
