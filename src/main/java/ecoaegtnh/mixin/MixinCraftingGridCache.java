package ecoaegtnh.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.crafting.CraftingLink;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import ecoaegtnh.ecalculator.ECPUCluster;
import ecoaegtnh.ecalculator.EcoTimeRecorder;
import ecoaegtnh.tile.ecalculator.TileEcalMEChannel;

/**
 * M2: registers the E-Calculator vCPUs with the AE2 grid's {@code CraftingGridCache} — the only
 * entry into the vanilla crafting scheduler (reference S:MixinCraftingGridCache.java, plan §6.3):
 * <ul>
 * <li>{@code updateCPUClusters()} @RETURN (A998:369-386): after the vanilla TileCraftingStorageTile
 * scan, append every channel's CPU list ({@link TileEcalMEChannel#getCPUs()}) to
 * {@code craftingCPUClusters} and re-link non-null last links;
 * <li>{@code onUpdateTick} WrapOperation (A998:171-173): time each owned cluster's
 * {@code updateCraftingLogic} with {@link EcoTimeRecorder}.
 * </ul>
 * priority = 2000 (R14 mitigation, same rationale as M1).
 */
@Mixin(value = CraftingGridCache.class, priority = 2000, remap = false)
public abstract class MixinCraftingGridCache {

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    public abstract void addLink(final CraftingLink link);

    @Inject(method = "updateCPUClusters()V", at = @At("RETURN"), remap = false)
    private void ecoaegtnh$injectUpdateCPUClusters(final CallbackInfo ci) {
        for (final IGridNode ecNode : grid.getMachines(TileEcalMEChannel.class)) {
            final TileEcalMEChannel channel = (TileEcalMEChannel) ecNode.getMachine();
            for (final CraftingCPUCluster cpu : channel.getCPUs()) {
                this.craftingCPUClusters.add(cpu);

                if (cpu.getLastCraftingLink() != null) {
                    this.addLink((CraftingLink) cpu.getLastCraftingLink());
                }
            }
        }
    }

    @WrapOperation(
        method = "onUpdateTick",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;updateCraftingLogic(Lappeng/api/networking/IGrid;Lappeng/api/networking/energy/IEnergyGrid;Lappeng/me/cache/CraftingGridCache;)V"))
    private void ecoaegtnh$wrapOnUpdateTick(final CraftingCPUCluster instance, final IGrid grid, final IEnergyGrid eg,
        final CraftingGridCache cc, final Operation<Void> original) {
        ECPUCluster ec = ECPUCluster.from(instance);
        if (ec.ecoaegtnh$getController() != null) {
            EcoTimeRecorder recorder = ec.ecoaegtnh$getTimeRecorder();
            final long start = System.nanoTime() / 1000;
            original.call(instance, grid, eg, cc);
            recorder.addUsedTime((int) (System.nanoTime() / 1000 - start));
        } else {
            original.call(instance, grid, eg, cc);
        }
    }
}
