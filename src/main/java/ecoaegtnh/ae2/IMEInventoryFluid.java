package ecoaegtnh.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;

/**
 * 284 移植版流体盘对外接口：统计与访问器（AE2FC IFluidCellInventory 的角色），
 * handler 经此接口实现 ICellCacheRegistry 委托。
 */
public interface IMEInventoryFluid extends IMEInventory<IAEFluidStack> {

    ItemStack getItemStack();

    double getIdleDrain();

    IInventory getConfigInventory();

    IInventory getUpgradesInventory();

    int getBytesPerType();

    boolean canHoldNewFluid();

    long getTotalBytes();

    long getFreeBytes();

    long getUsedBytes();

    long getStoredFluidCount();

    long getRemainingFluidCount();

    long getRemainingFluidCountDist(IAEFluidStack l);

    long getRemainingFluidTypes();

    int getUnusedFluidCount();

    int getStatusForCell();

    long getStoredFluidTypes();

    long getTotalFluidTypes();
}
