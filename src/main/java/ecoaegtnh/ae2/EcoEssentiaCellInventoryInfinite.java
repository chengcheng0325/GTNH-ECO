package ecoaegtnh.ae2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.fluids.GaseousEssentia;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;

/**
 * 284 移植版魔导源质盘（ARCANE，t114 复刻 TE4 创造源质元件）：无限容量，网络侧列出
 * 全部源质（每个 2e9，TE 1.7.14 HandlerItemEssentiaCellCreative 同款），只出不进。
 */
public class EcoEssentiaCellInventoryInfinite extends EcoEssentiaCellInventory {

    private final List<Aspect> allAspects;

    public EcoEssentiaCellInventoryInfinite(ItemStack cell, ISaveProvider provider) {
        super(cell, provider);
        this.allAspects = new ArrayList<>(Aspect.aspects.values());
    }

    @Override
    public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable mode, final BaseActionSource src) {
        return input == null ? null : input.copy(); // 创造盘不可注入
    }

    @Override
    public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null || request.getFluid() == null || !(request.getFluid() instanceof GaseousEssentia)) {
            return null;
        }
        Aspect aspect = ((GaseousEssentia) request.getFluid()).getAspect();
        if (aspect != null && this.allAspects.contains(aspect)) {
            return request.copy();
        }
        return null;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(final IItemList<IAEFluidStack> out, int iteration) {
        for (Aspect aspect : this.allAspects) {
            GaseousEssentia gas = GaseousEssentia.getGasFromAspect(aspect);
            if (gas != null) {
                out.add(EssentiaConversionHelper.INSTANCE.createAEFluidStackInEssentiaUnits(gas, 2000000000L));
            }
        }
        return out;
    }

    @Override
    public boolean canAccept(final IAEFluidStack input) {
        return false;
    }

    @Override
    public boolean isPrioritized(final IAEFluidStack input) {
        return false;
    }

    @Override
    public boolean validForPass(final int pass) {
        return false;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    @Override
    public boolean canGetInv() {
        return false;
    }

    @Override
    public long getTotalBytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getFreeBytes() {
        return 0;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalTypes() {
        return this.allAspects.size();
    }

    @Override
    public long getFreeTypes() {
        return 0;
    }

    @Override
    public long getUsedTypes() {
        return this.allAspects.size();
    }

    @Override
    public int getCellStatus() {
        return 4;
    }
}
