package ecoaegtnh.mixin;

import java.util.HashSet;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.me.cache.GridStorageCache;
import ecoaegtnh.metatileentity.MTEEcoStorageArray;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;
import ecoaegtnh.tile.estorage.TileEcoStorageMEBus;

/**
 * t76: the ME terminal / network-info "cells" view reads per-cell capacity from
 * {@link GridStorageCache#resetCellInfo()}, which only handles AE2U's own TileDrive / TileChest
 * cell providers — our E-Storage ME bus (a custom ICellContainer) was never registered, so ECO
 * cells showed as "0 B / 0 B" in the network info tool (t75). This mixin registers our bus's
 * drive-bay cells through the same private registry call the base uses for drives/chests.
 */
@Mixin(GridStorageCache.class)
public abstract class MixinGridStorageCache {

    // AE2U's own class keeps MCP names in the release jar, but the mixin AP has no SRG mapping
    // for them — remap = false keeps the literal runtime names (same approach as MixinTileDrive).
    @Shadow(remap = false)
    private HashSet<ICellProvider> activeCellProviders;

    @Shadow(remap = false)
    private void updateCellsStatusFromRegistry(ICellCacheRegistry iccr, ItemStack newCellStack) {}

    @Inject(method = "resetCellInfo", at = @At("TAIL"), remap = false)
    private void ecoaegtnh$registerEcoCellInfo(CallbackInfo ci) {
        for (ICellProvider icp : activeCellProviders) {
            if (icp instanceof TileEcoStorageMEBus bus && bus.isOperational()) {
                MTEEcoStorageArray controller = bus.getController();
                if (controller == null) continue;
                for (TileEcoStorageDrive drive : controller.getDriveBays()) {
                    ItemStack cell = drive.getCellStack();
                    if (cell == null) continue;
                    for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                        IMEInventoryHandler<?> handler = drive.getHandler(type);
                        if (handler != null && handler.getInternal() instanceof ICellCacheRegistry iccr
                            && iccr.canGetInv()) {
                            updateCellsStatusFromRegistry(iccr, cell);
                        }
                    }
                }
            }
        }
    }
}
