package ecoaegtnh.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.item.AEFluidStack;
import appeng.util.prioitylist.PrecisePriorityList;

/**
 * 284 移植版 ECO 流体盘 handler（AE2FC FluidCellInventoryHandler 同款模式）：
 * extends MEInventoryHandler + ICellCacheRegistry（FLUID）；统计委托给内部
 * IMEInventoryFluid；无限水盘的 canGetInv() = false（创造盘语义）。
 * <p>
 * t8：同 EcoStorageCellInventoryHandler——695 的 MEInventoryHandler 构造会把非
 * IMEInventoryHandler 的内部库存包一层 MEPassThrough，getInternal() 拿不到原库存；
 * 本类直接持有构造参数里的 {@link IMEInventoryFluid} 引用，统计方法带 null 防御。
 */
public class EcoFluidCellInventoryHandler extends MEInventoryHandler<IAEFluidStack> implements ICellCacheRegistry {

    private final IMEInventoryFluid fluidInv;

    public EcoFluidCellInventoryHandler(final IMEInventory<IAEFluidStack> c) {
        super(c, StorageChannel.FLUIDS);
        this.fluidInv = c instanceof IMEInventoryFluid f ? f : null;
        final IMEInventoryFluid ci = this.fluidInv;
        if (ci != null) {
            final IInventory config = ci.getConfigInventory();
            final IItemList<IAEFluidStack> priorityList = AEApi.instance()
                .storage()
                .createFluidList();
            for (int x = 0; x < config.getSizeInventory(); x++) {
                final ItemStack is = config.getStackInSlot(x);
                final FluidStack fluid = fluidFromItem(is);
                if (fluid != null) {
                    AEFluidStack stack = AEFluidStack.create(fluid);
                    stack.setStackSize(1);
                    priorityList.add(stack);
                }
            }
            if (!priorityList.isEmpty()) {
                this.setPartitionList(new PrecisePriorityList<>(priorityList));
            }
            final IInventory upgrades = ci.getUpgradesInventory();
            boolean hasSticky = false;
            boolean hasInverter = false;
            for (int x = 0; x < upgrades.getSizeInventory(); x++) {
                final ItemStack is = upgrades.getStackInSlot(x);
                if (is != null && is.getItem() instanceof IUpgradeModule) {
                    final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                    if (u == Upgrades.STICKY) {
                        hasSticky = true;
                    } else if (u == Upgrades.INVERTER) {
                        hasInverter = true;
                    }
                }
            }
            this.setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            if (hasSticky) {
                setSticky(true);
            }
        }
    }

    /** 水桶 → 水；其他容器走 Forge 注册表。 */
    private static FluidStack fluidFromItem(ItemStack is) {
        if (is == null) return null;
        if (is.getItem() == net.minecraft.init.Items.water_bucket) {
            return new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
        }
        return net.minecraftforge.fluids.FluidContainerRegistry.getFluidForFilledItem(is);
    }

    private IMEInventoryFluid getFluidCellInv() {
        return this.fluidInv;
    }

    @Override
    public boolean canGetInv() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv != null && !(inv instanceof EcoFluidCellInventoryInfinite);
    }

    @Override
    public long getTotalBytes() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv == null ? 0 : inv.getTotalBytes();
    }

    @Override
    public long getFreeBytes() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv == null ? 0 : inv.getFreeBytes();
    }

    @Override
    public long getUsedBytes() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv == null ? 0 : inv.getUsedBytes();
    }

    @Override
    public long getTotalTypes() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv == null ? 0 : inv.getTotalFluidTypes();
    }

    @Override
    public long getFreeTypes() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv == null ? 0 : inv.getRemainingFluidTypes();
    }

    @Override
    public long getUsedTypes() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        return inv == null ? 0 : inv.getStoredFluidTypes();
    }

    @Override
    public int getCellStatus() {
        final IMEInventoryFluid inv = this.getFluidCellInv();
        if (inv == null) {
            return 1;
        }
        int val = inv.getStatusForCell();
        if ((val == 1 || val == 2) && !this.getPartitionList()
            .isEmpty()) {
            val = 3;
        }
        return val;
    }

    @Override
    public TYPE getCellType() {
        return TYPE.FLUID;
    }
}
