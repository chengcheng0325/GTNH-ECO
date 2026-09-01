package ecoaegtnh.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;
import ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia;
import ecoaegtnh.item.estorage.ItemEcoStorageCellFluid;
import ecoaegtnh.item.estorage.ItemEcoStorageCellItem;
import ecoaegtnh.item.estorage.StorageType;

/**
 * AE2U cell handler for ECO E-Storage cells. 284 移植版：695 没有 IAEStackType，
 * 只有 StorageChannel（ITEMS/FLUIDS）——源质盘并入 FLUIDS 通道（TE 1.7.14 原生做法）。
 * 注册方式不变（{@code AEApi.instance().registries().cell().addCellHandler(...)}）。
 */
public class EcoStorageCellHandler implements ICellHandler {

    public static final EcoStorageCellHandler INSTANCE = new EcoStorageCellHandler();

    @Override
    public boolean isCell(ItemStack is) {
        return is != null && is.getItem() instanceof ItemEcoStorageCell;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, StorageChannel channel) {
        if (!isCell(is)) return null;
        ItemEcoStorageCell cell = (ItemEcoStorageCell) is.getItem();
        try {
            if (channel == StorageChannel.ITEMS) {
                // 物品盘只挂 ITEMS 通道（源质/流体盘都不在这里）。
                if (cell.getStorageType() != StorageType.ITEM) return null;
                EcoStorageCellInventory inv = new EcoStorageCellInventory(is, host);
                return new EcoStorageCellInventoryHandler(inv);
            }
            if (channel == StorageChannel.FLUIDS) {
                // 源质盘（ARCANE 无限盘优先：T114f 复刻 TE4 创造源质元件）。
                if (is.getItem() instanceof ItemEcoStorageCellEssentia) {
                    if (cell.isInfinite()) {
                        return new EcoEssentiaCellInventoryInfinite(is, host);
                    }
                    return new EcoEssentiaCellInventory(is, host);
                }
                // 流体盘（INF_WATER 无限盘：复刻 AE2FC 无限水）。
                if (cell.getStorageType() != StorageType.FLUID) return null;
                if (cell.isInfinite()) {
                    return new EcoFluidCellInventoryHandler(new EcoFluidCellInventoryInfinite(is, host));
                }
                return new EcoFluidCellInventoryHandler(new EcoFluidCellInventory(is, host));
            }
        } catch (AppEngException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    @Override
    public IIcon getTopTexture_Light() {
        return null;
    }

    @Override
    public IIcon getTopTexture_Medium() {
        return null;
    }

    @Override
    public IIcon getTopTexture_Dark() {
        return null;
    }

    @Override
    public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler,
        IMEInventoryHandler inv, ItemStack is, StorageChannel chan) {
        // t61: open the standard ME cell viewer so ECO cells are usable from an ME chest, exactly
        // like AE2U's BasicCellHandler (previously a no-op, so right-clicking an ME chest that
        // contained an ECO cell did nothing).
        Platform.openGUI(player, (TileEntity) chest, chest.getUp(), GuiBridge.GUI_ME);
    }

    @Override
    public int getStatusForCell(ItemStack is, IMEInventory handler) {
        if (handler instanceof appeng.api.storage.ICellCacheRegistry iccr) {
            return iccr.getCellStatus();
        }
        return 1;
    }

    @Override
    public double cellIdleDrain(ItemStack is, IMEInventory handler) {
        return is.getItem() instanceof ItemEcoStorageCell cell ? cell.getIdleDrain() : 0;
    }

    /** 便捷：按物品家族取通道（ITEMS / FLUIDS），非 ECO 物品返回 null。 */
    public static StorageChannel channelOf(ItemStack is) {
        if (is == null || !(is.getItem() instanceof ItemEcoStorageCell cell)) return null;
        if (is.getItem() instanceof ItemEcoStorageCellItem) return StorageChannel.ITEMS;
        if (is.getItem() instanceof ItemEcoStorageCellFluid) return StorageChannel.FLUIDS;
        if (is.getItem() instanceof ItemEcoStorageCellEssentia) return StorageChannel.FLUIDS;
        return cell.getStorageType() == StorageType.ITEM ? StorageChannel.ITEMS : StorageChannel.FLUIDS;
    }
}
