package ecoaegtnh.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.exceptions.AppEngException;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEFluidStack;

/**
 * 284 移植版无限水流体盘（INF_WATER，t114f 复刻 AE2FC 无限水）：
 * 只存水、容量 Long.MAX_VALUE、config 固定一格水桶（ItemEcoStorageCell.FixedWaterConfig），
 * 网络侧按 config 把水以 2^52-1 暴露（AE2FC CreativeFluidCellInventory 同款）。
 */
public class EcoFluidCellInventoryInfinite extends EcoFluidCellInventory {

    public EcoFluidCellInventoryInfinite(ItemStack o, ISaveProvider container) throws AppEngException {
        super(o, container);
    }

    @Override
    public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable mode, final BaseActionSource src) {
        if (input == null || input.getStackSize() == 0) {
            return null;
        }
        if (this.getCellFluids()
            .findPrecise(input) != null) {
            return null; // the configured fluid is accepted forever
        }
        return input;
    }

    @Override
    public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null) {
            return null;
        }
        if (this.getCellFluids()
            .findPrecise(request) != null) {
            return request.copy();
        }
        return null;
    }

    @Override
    protected void loadCellFluids() {
        if (this.cellFluids == null) {
            this.cellFluids = appeng.api.AEApi.instance()
                .storage()
                .createFluidList();
        }
        this.cellFluids.resetStatus();
        IInventory inv = this.getConfigInventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack is = inv.getStackInSlot(i);
            FluidStack fs = fluidFromItem(is);
            if (fs == null) continue;
            IAEFluidStack iae = appeng.util.item.AEFluidStack.create(fs);
            if (this.cellFluids.findPrecise(iae) == null) {
                iae.setStackSize((long) (Math.pow(2, 52) - 1));
                this.cellFluids.add(iae);
            }
        }
    }

    /** 水桶 → FluidStack(WATER, 1000)；其他容器走 Forge 注册表。 */
    private static FluidStack fluidFromItem(ItemStack is) {
        if (is == null) return null;
        if (is.getItem() == net.minecraft.init.Items.water_bucket) {
            return new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
        }
        return net.minecraftforge.fluids.FluidContainerRegistry.getFluidForFilledItem(is);
    }

    @Override
    public long getTotalBytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalFluidTypes() {
        return 1;
    }

    @Override
    public boolean canHoldNewFluid() {
        return true;
    }

    @Override
    public int getStatusForCell() {
        return 4;
    }

    @Override
    public long getStoredFluidCount() {
        return 0;
    }

    @Override
    public long getStoredFluidTypes() {
        return 1;
    }

    @Override
    public long getRemainingFluidCount() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getRemainingFluidTypes() {
        return Long.MAX_VALUE;
    }
}
