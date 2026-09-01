package ecoaegtnh.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
import ecoaegtnh.metatileentity.MTEEcalArray;
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
        // t122 (user): orphaned built-in clusters (their controller was removed while the grid
        // was unreachable — see EcoaegtnhOrphanClusters) are re-adopted by a live grid, so the
        // isComplete branch of updateCraftingLogic retries the storeItems() refund and destroys
        // them — the materials come back instead of being swallowed. Only orphans whose owner
        // channel is still grid-connected are driven here; orphans whose channel block is gone
        // are re-homed by a rebuilt controller (MTEEcalArray.createVirtualCPU).
        for (final CraftingCPUCluster orphan : ecoaegtnh.EcoaegtnhOrphanClusters.all()) {
            final MTEEcalArray owner = ECPUCluster.from(orphan)
                .ecoaegtnh$getVirtualCPUOwner();
            if (owner == null) {
                continue; // thread-drive clusters are re-exposed via channel.getCPUs() instead
            }
            final TileEcalMEChannel channel = owner.getChannel();
            if (channel == null || channel.getProxy() == null
                || channel.getProxy()
                    .getNode() == null) {
                continue; // channel gone — wait for a rebuilt machine to re-home the orphan
            }
            // T-M1 (t122 audit): only drive orphans whose channel node belongs to THIS grid — a
            // live node in another grid would cross-drive the orphan (double drive / wrong-grid
            // execution). Same-network multi-controller stays safe (one grid cache per grid).
            if (channel.getProxy()
                .getNode()
                .getGrid() != this.grid) {
                continue;
            }
            this.craftingCPUClusters.add(orphan);

            if (orphan.getLastCraftingLink() != null) {
                this.addLink((CraftingLink) orphan.getLastCraftingLink());
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

    /**
     * t116b: in the auto CPU-selection loop of the 6-arg submitJob, a busy vCPU must be considered
     * for the vanilla job-merge branch — redirect its availableStorage to the EFFECTIVE value
     * (pool remaining + current task bytes), so {@code available >= used + newJobBytes} becomes
     * {@code poolFree >= newJobBytes}. Non-vCPU clusters keep the vanilla field.
     */
    @Redirect(
        method = "submitJob(Lappeng/api/networking/crafting/ICraftingJob;Lappeng/api/networking/crafting/ICraftingRequester;Lappeng/api/networking/crafting/ICraftingCPU;ZLappeng/api/networking/security/BaseActionSource;Z)Lappeng/api/networking/crafting/ICraftingLink;",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getAvailableStorage()J",
            remap = false),
        remap = false)
    private long ecoaegtnh$redirectGridAvailableStorage(final CraftingCPUCluster cpu) {
        ECPUCluster ec = ECPUCluster.from(cpu);
        if (ec != null) {
            long eff = ec.ecoaegtnh$effectiveAvailableStorage();
            if (eff >= 0) {
                return eff;
            }
        }
        return cpu.getAvailableStorage();
    }
}
