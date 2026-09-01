package ecoaegtnh.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStackType;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;
import ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia;

/**
 * AE2U cell handler for ECO E-Storage cells. Registered via
 * {@code AEApi.instance().registries().cell().addCellHandler(...)} so cells work in ME
 * drives/chests and in the E-Storage drive bay.
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
        if (channel == StorageChannel.ITEMS) {
            return getCellInventory(is, host, appeng.util.item.AEItemStackType.ITEM_STACK_TYPE);
        }
        if (channel == StorageChannel.FLUIDS) {
            return getCellInventory(is, host, appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE);
        }
        return null;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, IAEStackType<?> type) {
        if (!isCell(is)) return null;
        ItemEcoStorageCell cell = (ItemEcoStorageCell) is.getItem();
        // t114f: infinite family-exclusive cells use AE2U's CreativeCellInventory, exactly like
        // the AE2FC infinite water (FluidCellInventoryHandler(CreativeCellInventory)) and the
        // TE4 creative essentia cell (EssentiaCellInventoryHandler(CreativeCellInventory)) —
        // the network sees the configured items (water / every aspect) at ~2^52 each, infinitely
        // extractable, and injects of those items are accepted forever. MUST run before the
        // essentia branch below (the arcane cell is an ItemEcoStorageCellEssentia).
        if (cell.isInfinite()) {
            if (type != cell.getStackType()) return null;
            appeng.me.storage.CreativeCellInventory creative = new appeng.me.storage.CreativeCellInventory(is);
            if (is.getItem() instanceof ItemEcoStorageCellEssentia) {
                return new thaumicenergistics.common.inventory.EssentiaCellInventoryHandler(creative);
            }
            if (cell.getStackType() == appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE) {
                return new appeng.me.storage.FluidCellInventoryHandler(creative);
            }
            return null;
        }
        // Essentia cells (TE4's AEEssentiaStackType). The type is compared through the item's own
        // stack type field so no TE4 class is loaded for item/fluid cells; the essentia inventory
        // classes are only touched when an essentia cell is actually processed (TE4 present).
        if (is.getItem() instanceof ItemEcoStorageCellEssentia) {
            if (type != ((ItemEcoStorageCell) is.getItem()).getStackType()) return null;
            try {
                EcoStorageCellInventoryEssentia inv = new EcoStorageCellInventoryEssentia(is, host);
                return new EcoStorageCellInventoryEssentiaHandler(inv);
            } catch (AppEngException e) {
                return null;
            }
        }
        if (cell.getStackType() != type) return null;
        try {
            EcoStorageCellInventory inv = new EcoStorageCellInventory(is, host);
            if (inv.getStackType() != type) return null;
            return new EcoStorageCellInventoryHandler(inv, type);
        } catch (AppEngException e) {
            return null;
        }
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
        if (handler instanceof ICellInventoryHandler cellInvHandler && cellInvHandler.getCellInv() != null) {
            return cellInvHandler.getCellInv()
                .getStatusForCell();
        }
        return 1;
    }

    @Override
    public double cellIdleDrain(ItemStack is, IMEInventory handler) {
        return is.getItem() instanceof ItemEcoStorageCell cell ? cell.getIdleDrain() : 0;
    }
}
