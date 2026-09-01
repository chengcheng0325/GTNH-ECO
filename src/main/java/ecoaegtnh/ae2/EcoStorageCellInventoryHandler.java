package ecoaegtnh.ae2;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.CellInventoryHandler;

/**
 * Handler wrapper for {@link EcoStorageCellInventory}. CellInventoryHandler has a protected
 * constructor and is abstract, so it must be subclassed.
 * <p>
 * t81: {@code getStorageChannel()} must be overridden — {@code CellInventoryHandler.getCellType()}
 * derives ITEM/FLUID from it and {@code ICellCacheRegistry.getStorageChannel()} defaults to ITEMS,
 * so without this override fluid cells were counted into the item column of the network-tool /
 * ME-terminal cell statistics (same pattern as AE2U's FluidCellInventoryHandler).
 */
public class EcoStorageCellInventoryHandler<StackType extends IAEStack<StackType>>
    extends CellInventoryHandler<StackType> {

    private final IAEStackType<StackType> handlerType;

    public EcoStorageCellInventoryHandler(IMEInventory<StackType> c, IAEStackType<StackType> type) {
        super(c, type);
        this.handlerType = type;
    }

    @Override
    public StorageChannel getStorageChannel() {
        return handlerType == FLUID_STACK_TYPE ? StorageChannel.FLUIDS : StorageChannel.ITEMS;
    }
}
