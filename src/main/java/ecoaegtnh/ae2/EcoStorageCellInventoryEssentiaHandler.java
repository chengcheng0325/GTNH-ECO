package ecoaegtnh.ae2;

import static thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE;

import javax.annotation.Nonnull;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellCacheRegistry.TYPE;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.CellInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.prioitylist.PrecisePriorityList;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.EssentiaList;

/**
 * Handler wrapper for {@link EcoStorageCellInventoryEssentia}, copied from TE4's
 * {@code thaumicenergistics.common.inventory.EssentiaCellInventoryHandler}: exposes the AE2U
 * cache registry type ESSENTIA and builds the partition list from the cell's config slots.
 */
public class EcoStorageCellInventoryEssentiaHandler extends CellInventoryHandler<AEEssentiaStack> {

    public EcoStorageCellInventoryEssentiaHandler(IMEInventory<AEEssentiaStack> c) {
        super(c, ESSENTIA_STACK_TYPE);
    }

    @Override
    public TYPE getCellType() {
        return TYPE.ESSENTIA;
    }

    @Nonnull
    @Override
    public IAEStackType<?> getStackType() {
        return ESSENTIA_STACK_TYPE;
    }

    @Override
    protected void setPriorityList(boolean hasFuzzy, IAEStackInventory config, FuzzyMode fzMode) {
        final EssentiaList partitionList = new EssentiaList();
        for (int x = 0; x < config.getSizeInventory(); x++) {
            final IAEStack<?> aes = config.getAEStackInSlot(x);
            if (aes instanceof AEEssentiaStack essentiaStack) {
                essentiaStack = essentiaStack.copy();
                essentiaStack.setStackSize(1);

                partitionList.add(essentiaStack);
            }
        }
        if (!partitionList.isEmpty()) {
            this.setPartitionList(new PrecisePriorityList<>(partitionList));
        }
    }
}
