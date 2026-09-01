package ecoaegtnh.mixin;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.config.CraftingAllow;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import ecoaegtnh.ecalculator.ECPUCluster;
import ecoaegtnh.ecalculator.EcoTimeRecorder;
import ecoaegtnh.metatileentity.MTEEcalArray;
import ecoaegtnh.tile.ecalculator.TileEcalMEChannel;
import ecoaegtnh.tile.ecalculator.TileEcalThreadDrive;

/**
 * M1: E-Calculator core mixin on AE2U {@code CraftingCPUCluster} — implements {@link ECPUCluster}
 * and redirects the "host" (tiles) of a vCPU cluster to the E-Calculator ME channel. Adapted from
 * the 1.12.2 reference (S:MixinCraftingCPUCluster.java) to rv3 per plan §6.2:
 * <ul>
 * <li>{@code submitJob} is intercepted at RETURN with a guard (rv3 has a merge branch + two
 * getOutput() call sites — the 1.12.2 @At(INVOKE) point would not fire for normal submissions);
 * <li>{@code inventory.getItemList()} → {@code isEmpty()} (rv3 API, J1000/JREL verified);
 * <li>{@code IActionSource} → {@code BaseActionSource};
 * <li>all AE2 members keep MCP names in the release jar → {@code remap=false} + literal names.
 * </ul>
 * priority = 2000 (R14 mitigation: applied before ae2fc's default-priority mixins on the same
 * target classes; FML log verifies the apply state at install).
 */
@Mixin(value = CraftingCPUCluster.class, priority = 2000, remap = false)
public abstract class MixinCraftingCPUCluster implements ECPUCluster {

    @Unique
    private TileEcalThreadDrive ecoaegtnh$core = null;

    @Unique
    private MTEEcalArray ecoaegtnh$virtualCPUOwner = null;

    @Unique
    private long ecoaegtnh$usedExtraStorage = 0;

    /** t33: slot kind of this cluster's assignment (true = hyper slot; the core's cpus list is mixed). */
    @Unique
    private boolean ecoaegtnh$hyperAssigned = false;

    /** t114i: vCPU number from the controller's smallest-available pool (0 = standby/unnumbered). */
    @Unique
    private int ecoaegtnh$vcpuId = 0;

    /**
     * t116: set when this submitJob call was a merge into the running job — the RETURN injector
     * must skip the thread-slot assignment (the cluster is already assigned).
     */
    @Unique
    private boolean ecoaegtnh$mergedJob = false;

    @Unique
    private final EcoTimeRecorder ecoaegtnh$timeRecorder = new EcoTimeRecorder();

    @Unique
    private final EcoTimeRecorder ecoaegtnh$parallelismRecorder = new EcoTimeRecorder();

    @Shadow
    private long availableStorage;

    /** t116b: current job's used bytes (mergeJob adds to it; pool accounting is Σ availableStorage). */
    @Shadow
    private long usedStorage;

    @Shadow
    private boolean isDestroyed;

    @Shadow
    private int accelerator;

    @Shadow
    private MECraftingInventory inventory;

    @Shadow
    private boolean isComplete;

    @Shadow
    private ICraftingLink myLastLink;

    @Shadow
    private MachineSource machineSrc;

    @Shadow
    public abstract void destroy();

    @Shadow
    public abstract long getAvailableStorage();

    @Shadow
    public abstract void cancel();

    /** t116: busy/current-output accessors for the merge branch (AE2U public methods). */
    @Shadow
    public abstract boolean isBusy();

    /**
     * 284 移植版：695 的 CraftingCPUCluster 没有 getFinalMultiOutput()——submitJob 的
     * merge 分支直接读字段 {@code finalOutput}（IAEItemStack），照抄 695 的写法。
     */
    @Shadow
    private appeng.api.storage.data.IAEItemStack finalOutput;

    @Final
    @Shadow
    private int[] usedOps;

    // ------------------------------------------------------------------
    // submitJob: assignment hook. Fires at RETURN (job confirmed loaded into the cluster —
    // usedStorage = job.getByteTotal() has run, A998:1140); guarded to the standby vCPU only
    // (merge-job paths and already-assigned clusters are excluded, §6.2).
    // ------------------------------------------------------------------

    /**
     * t114g (user, plan C): pre-check at submit time — the hyper-thread +10% surcharge is
     * reserved on EVERY job, so a job is only accepted when bytes × 1.1 fit into the vCPU's
     * available storage (the pool). A 255k job on a 256k pool needs 280.5k → rejected with a
     * chat notice instead of overdrawing the pool and stalling every later submit (the AE2
     * original check only compares the raw job bytes).
     */
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$precheckSubmitJob(final IGrid g, final ICraftingJob job, final BaseActionSource src,
        final ICraftingRequester requestingMachine, final CallbackInfoReturnable<ICraftingLink> cir) {
        if (this.ecoaegtnh$virtualCPUOwner == null || job == null) {
            return;
        }
        // t116 (user): merge a repeated request for the SAME output into the running vCPU instead of
        // occupying another thread slot — mirrors AE2U's vanilla merge condition, but against the
        // controller byte POOL (vCPU availableStorage is task-bytes semantics, so the vanilla
        // condition can never hold for us). Must run BEFORE the t114g byte precheck.
        if (this.isBusy() && this.myLastLink != null
            && this.myLastLink.isStandalone()
            && this.finalOutput != null
            && this.finalOutput.isSameType(job.getOutput())) {
            long extra = this.ecoaegtnh$hyperAssigned && !this.ecoaegtnh$virtualCPUOwner.isOverclocked()
                ? job.getByteTotal() / 10
                : 0;
            // M2 (audit): the merge check compares REAL bytes against the LIVE pool — the hyper
            // +10% reserve is virtual capacity on the AE2 side and does not overdraw the pool,
            // so no extra surcharge here (overclock mode never had one either).
            if (this.ecoaegtnh$virtualCPUOwner.getAvailableBytes() >= job.getByteTotal()) {
                ICraftingLink link = ((CraftingCPUCluster) (Object) this).mergeJob(g, job, src, requestingMachine);
                if (link != null) {
                    // Keep the "task-bytes" availableStorage semantics so the thread-drive pool
                    // accounting (Σ availableStorage) stays correct: add the merged bytes (+ hyper
                    // reserve) to this cluster's share.
                    this.ecoaegtnh$usedExtraStorage += extra;
                    this.availableStorage += job.getByteTotal() + extra;
                    this.ecoaegtnh$mergedJob = true;
                    cir.setReturnValue(link);
                    return;
                }
            }
        }
        // M2 (audit): precheck against the LIVE pool (not the standby's stale availableStorage
        // snapshot) and charge only real bytes — the hyper +10% reserve is virtual capacity on the
        // AE2 side and no longer overdraws the pool, so no ×1.1 surcharge here (overclock mode
        // never had one either).
        long need = job.getByteTotal();
        long poolFree = this.ecoaegtnh$virtualCPUOwner.getAvailableBytes();
        if (need > poolFree) {
            cir.setReturnValue(null);
            ecoaegtnh$virtualCPUOwner.notifyJobRejected(src, job.getByteTotal(), poolFree);
        }
    }

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true)
    private void ecoaegtnh$injectSubmitJob(final IGrid g, final ICraftingJob job, final BaseActionSource src,
        final ICraftingRequester requestingMachine, final CallbackInfoReturnable<ICraftingLink> cir) {
        if (this.ecoaegtnh$core != null || this.ecoaegtnh$virtualCPUOwner == null) {
            return;
        }
        if (this.ecoaegtnh$mergedJob) {
            // t116: this call was a merge into the running job — the cluster is already assigned to
            // its thread slot; do NOT re-assign (would double-add / double-count the vCPU).
            this.ecoaegtnh$mergedJob = false;
            return;
        }
        // M9 (audit): belt-and-braces — if the RETURN injector ran although the cluster is already
        // assigned (e.g. merge path where HEAD set the return value and this injector still fired,
        // or a stale cluster), skip assignment instead of double-adding it.
        if (this.ecoaegtnh$virtualCPUOwner.isClusterAssigned((CraftingCPUCluster) (Object) this)) {
            return;
        }
        if (cir.getReturnValue() == null) {
            return; // job rejected by the cluster (busy/storage/unsupported)
        }
        this.ecoaegtnh$virtualCPUOwner.onVirtualCPUSubmitJob((CraftingCPUCluster) (Object) this, job.getByteTotal());
    }

    /**
     * t34: persist AE2's "accept requests" mode (CraftingAllow) for ECO vCPUs. The mode lives on
     * the cluster instance; the standby vCPU is recreated on every refill (createVirtualCPU), so a
     * user change through the AE terminal CPU detail GUI is written back to the owner controller,
     * which stores it in its NBT and applies it to every new vCPU. Vanilla clusters (no owner)
     * keep AE2's original behavior untouched.
     */
    @Inject(method = "changeCraftingAllowMode", at = @At("RETURN"))
    private void ecoaegtnh$injectChangeCraftingAllowMode(final CraftingAllow mode, final CallbackInfo ci) {
        if (this.ecoaegtnh$virtualCPUOwner != null) {
            this.ecoaegtnh$virtualCPUOwner.setCraftingAllowMode(mode);
        }
    }

    // ------------------------------------------------------------------
    // cancel / updateCraftingLogic lifecycle (reference S:MixinCraftingCPUCluster.java:92-127)
    // ------------------------------------------------------------------

    @Inject(method = "cancel", at = @At("RETURN"))
    private void ecoaegtnh$injectCancel(final CallbackInfo ci) {
        // t114j: reclaim built-in slot clusters too — they are core==null but
        // virtualCPUOwner!=null (assigned to the controller's built-in thread/hyper lists).
        // The standby vCPU must NOT be reclaimed here: vanilla initializes isComplete=true with
        // an empty inventory, so a fresh standby would be destroyed on its first tick.
        if (this.ecoaegtnh$core == null && (this.ecoaegtnh$virtualCPUOwner == null
            || this.ecoaegtnh$virtualCPUOwner.isStandbyVCPU((CraftingCPUCluster) (Object) this))) {
            return;
        }
        // 284：695 的 MECraftingInventory 没有 isEmpty()——getItemList() 为内部列表。
        if (this.inventory.getItemList()
            .size() == 0) {
            destroy();
        }
    }

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectUpdateCraftingLogicStoreItems(final IGrid grid, final IEnergyGrid eg,
        final CraftingGridCache cgc, final CallbackInfo ci) {
        // t114j: same widened guard as injectCancel — built-in slot clusters (core==null,
        // virtualCPUOwner!=null, not the standby) must reach the isComplete/inventory check so a
        // finished job destroys the cluster and frees the built-in slot. The standby vCPU is
        // excluded: vanilla initializes isComplete=true with an empty inventory, so a fresh
        // standby would otherwise be destroyed on its first tick.
        if (this.ecoaegtnh$core == null && (this.ecoaegtnh$virtualCPUOwner == null
            || this.ecoaegtnh$virtualCPUOwner.isStandbyVCPU((CraftingCPUCluster) (Object) this))) {
            return;
        }
        if (this.myLastLink != null) {
            if (this.myLastLink.isCanceled()) {
                this.myLastLink = null;
                this.cancel();
            }
        }
        if (this.isComplete) {
            // Ensure inventory is empty before reclaiming the thread slot.
            // 284：695 的 MECraftingInventory 没有 isEmpty()——getItemList() 为内部列表。
            if (this.inventory.getItemList()
                .size() == 0) {
                destroy();
                ci.cancel();
            }
        }
    }

    @Inject(method = "updateCraftingLogic", at = @At("TAIL"))
    private void ecoaegtnh$injectUpdateCraftingLogicTail(final IGrid grid, final IEnergyGrid eg,
        final CraftingGridCache cgc, final CallbackInfo ci) {
        // usedOps[0] = operations started this tick (A998:772,785-787).
        ecoaegtnh$parallelismRecorder.addUsedTime(this.usedOps[0]);
    }

    /**
     * rv3 A998:746 calls getCore().isActive() — redirect to the channel proxy (no NPE: the
     * wrapped isActive() call is intercepted before the null core is dereferenced).
     */
    @WrapOperation(
        method = "updateCraftingLogic",
        at = @At(value = "INVOKE", target = "Lappeng/tile/crafting/TileCraftingTile;isActive()Z"))
    private boolean ecoaegtnh$redirectUpdateCraftingLogicIsActive(final TileCraftingTile instance,
        final Operation<Boolean> original) {
        if (this.ecoaegtnh$core != null) {
            MTEEcalArray controller = this.ecoaegtnh$core.getController();
            return controller != null && controller.getChannel() != null
                && controller.getChannel()
                    .getProxy()
                    .isActive();
        }
        if (this.ecoaegtnh$virtualCPUOwner != null) {
            return this.ecoaegtnh$virtualCPUOwner.getChannel() != null && this.ecoaegtnh$virtualCPUOwner.getChannel()
                .getProxy()
                .isActive();
        }
        return original.call(instance);
    }

    // ------------------------------------------------------------------
    // Host redirection (reference S:MixinCraftingCPUCluster.java:148-226)
    // ------------------------------------------------------------------

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectDestroy(final CallbackInfo ci) {
        // t122: a destroyed orphan (adopted while its controller was removed on a dead grid)
        // leaves the registry — self-cleanup, idempotent.
        ecoaegtnh.EcoaegtnhOrphanClusters.release((CraftingCPUCluster) (Object) this);
        // t114i: unified release hook on EVERY destroy path — returns the vCPU number to the
        // controller's smallest-available pool and frees any built-in thread slot the cluster
        // held. Idempotent (id 0 → no-op), so it is safe for the standby vCPU (never numbered),
        // for already-destroyed clusters and for the thread-core path below.
        if (this.ecoaegtnh$virtualCPUOwner != null) {
            this.ecoaegtnh$virtualCPUOwner.onClusterReleased((CraftingCPUCluster) (Object) this);
        } else if (this.ecoaegtnh$core != null) {
            // t114k: an EXTERNAL thread-slot cluster has no owner (only a thread core) — the
            // number it took while running must still return to the controller's pool, or every
            // finished external job leaks its number and the pool only grows.
            final MTEEcalArray controller = this.ecoaegtnh$core.getController();
            if (controller != null) {
                controller.releaseVCPUId((CraftingCPUCluster) (Object) this);
            }
        }
        if (this.ecoaegtnh$core == null) {
            // t114g: a cluster assigned to a BUILT-IN thread slot (or a standby vCPU) has no
            // thread drive; the owner controller handled both release duties above.
            return;
        }
        if (this.isDestroyed) {
            ci.cancel();
            return;
        }
        // Vanilla destroy() body continues after this (sets isDestroyed, iterates the EMPTY tile
        // list of a vCPU); the thread core removes the cluster and notifies the controller.
        this.ecoaegtnh$core.onCPUDestroyed((CraftingCPUCluster) (Object) this);
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectIsActive(final CallbackInfoReturnable<Boolean> cir) {
        if (this.ecoaegtnh$core == null && this.ecoaegtnh$virtualCPUOwner == null) {
            return;
        }
        MTEEcalArray controller = controllerOf();
        cir.setReturnValue(
            controller != null && controller.getChannel() != null
                && controller.getChannel()
                    .getProxy()
                    .isActive());
    }

    @Inject(method = "getGrid", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectGetGrid(final CallbackInfoReturnable<IGrid> cir) {
        MTEEcalArray controller = controllerOf();
        if (controller == null) {
            return;
        }
        TileEcalMEChannel channel = controller.getChannel();
        if (channel == null) {
            return;
        }
        IGridNode node = channel.getProxy()
            .getNode();
        cir.setReturnValue(node == null ? null : node.getGrid());
    }

    @Inject(method = "getCore", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectGetCore(final CallbackInfoReturnable<TileCraftingTile> cir) {
        if (this.ecoaegtnh$core != null || this.ecoaegtnh$virtualCPUOwner != null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getWorld", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectGetWorld(final CallbackInfoReturnable<World> cir) {
        if (this.ecoaegtnh$core != null) {
            cir.setReturnValue(this.ecoaegtnh$core.getWorldObj());
        } else if (this.ecoaegtnh$virtualCPUOwner != null) {
            cir.setReturnValue(
                this.ecoaegtnh$virtualCPUOwner.getBaseMetaTileEntity() != null
                    ? this.ecoaegtnh$virtualCPUOwner.getBaseMetaTileEntity()
                        .getWorld()
                    : null);
        }
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$injectMarkDirty(final CallbackInfo ci) {
        if (this.ecoaegtnh$core != null) {
            this.ecoaegtnh$core.markNoUpdateSync();
            ci.cancel();
        } else if (this.ecoaegtnh$virtualCPUOwner != null) {
            this.ecoaegtnh$virtualCPUOwner.markNoUpdateSync();
            ci.cancel();
        }
    }

    /** Controller behind a core-assigned cluster or a standby vCPU. */
    @Unique
    private MTEEcalArray controllerOf() {
        if (this.ecoaegtnh$core != null) {
            return this.ecoaegtnh$core.getController();
        }
        return this.ecoaegtnh$virtualCPUOwner;
    }

    // ------------------------------------------------------------------
    // ECPUCluster implementation (reference S:MixinCraftingCPUCluster.java:228-323)
    // ------------------------------------------------------------------

    @Unique
    @Override
    public void ecoaegtnh$setAvailableStorage(final long availableStorage) {
        this.availableStorage = availableStorage;
    }

    @Unique
    @Override
    public void ecoaegtnh$setAccelerators(final int accelerators) {
        this.accelerator = accelerators;
    }

    @Unique
    @Override
    public TileEcalThreadDrive ecoaegtnh$getController() {
        return ecoaegtnh$core;
    }

    @Unique
    @Override
    public void ecoaegtnh$setThreadCore(final TileEcalThreadDrive threadCore) {
        this.ecoaegtnh$core = threadCore;

        final MTEEcalArray controller = threadCore.getController();
        if (controller == null) {
            return;
        }
        final TileEcalMEChannel channel = controller.getChannel();
        if (channel != null) {
            this.machineSrc = new MachineSource(channel);
        }
    }

    @Unique
    @Override
    public void ecoaegtnh$setVirtualCPUOwner(@Nullable final MTEEcalArray virtualCPUOwner) {
        this.ecoaegtnh$virtualCPUOwner = virtualCPUOwner;
        if (virtualCPUOwner == null) {
            return;
        }
        final TileEcalMEChannel channel = virtualCPUOwner.getChannel();
        if (channel != null) {
            this.machineSrc = new MachineSource(channel);
        }
    }

    @Unique
    @Override
    public MTEEcalArray ecoaegtnh$getVirtualCPUOwner() {
        return this.ecoaegtnh$virtualCPUOwner;
    }

    @Unique
    @Override
    public boolean ecoaegtnh$isInventoryEmpty() {
        // 284: 695 的 MECraftingInventory 没有 isEmpty()——getItemList() 为内部列表（同 injectCancel）。
        return this.inventory == null || this.inventory.getItemList()
            .size() == 0;
    }

    @Unique
    @Override
    public int ecoaegtnh$getVCPUId() {
        return this.ecoaegtnh$vcpuId;
    }

    @Unique
    @Override
    public void ecoaegtnh$setVCPUId(final int vcpuId) {
        this.ecoaegtnh$vcpuId = vcpuId;
    }

    /**
     * t114h (user): vCPU display name — "ECO vCPU" for the standby row, "ECO vCPU #id" once the
     * job is running. Used by the submit screen (ContainerCraftingCPU reads getName at open time).
     * t114i: the number is allocated from the smallest-available pool only while running; any
     * unnumbered cluster (standby or still-unassigned) shows the bare "ECO vCPU".
     */
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void ecoaegtnh$injectGetName(final CallbackInfoReturnable<String> cir) {
        if (this.ecoaegtnh$virtualCPUOwner == null) {
            return;
        }
        boolean standby = this.ecoaegtnh$virtualCPUOwner.isStandbyVCPU((CraftingCPUCluster) (Object) this);
        final int id = this.ecoaegtnh$vcpuId;
        cir.setReturnValue(standby || id <= 0 ? "ECO vCPU" : "ECO vCPU #" + id);
    }

    @Unique
    @Override
    public int ecoaegtnh$getControllerTier() {
        MTEEcalArray controller = controllerOf();
        return controller == null ? -1 : controller.getTier();
    }

    /**
     * t33/t35/t114g: host REMAINING thread slots (dynamic) — Σ over all thread drives of (drive
     * thread capacity − normal-slot occupancy) PLUS the built-in thread slots (built-in total −
     * built-in occupancy). Occupancy counts assigned clusters that are NOT hyper-assigned
     * ({@link #ecoaegtnh$isHyperAssigned()}). 0 when unattached.
     */
    @Unique
    @Override
    public int ecoaegtnh$getHostThreads() {
        final MTEEcalArray owner = this.ecoaegtnh$virtualCPUOwner;
        if (owner == null) {
            return 0;
        }
        final List<TileEcalThreadDrive> cores = owner.getThreadCores();
        int remaining = Math.max(0, owner.getBuiltinThreads() - owner.getBuiltinThreadsUsed());
        if (cores != null) {
            for (TileEcalThreadDrive core : cores) {
                int normalUsed = 0;
                for (CraftingCPUCluster cpu : core.getCPUs()) {
                    if (!ECPUCluster.from(cpu)
                        .ecoaegtnh$isHyperAssigned()) {
                        normalUsed++;
                    }
                }
                remaining += Math.max(0, core.getThreads() - normalUsed);
            }
        }
        return remaining;
    }

    /**
     * t33/t35/t114g: host REMAINING hyper-thread slots — Σ over all thread drives of (hyper
     * capacity − hyper-slot occupancy) PLUS the built-in hyper slots (built-in total − built-in
     * occupancy; assigned clusters with {@link #ecoaegtnh$isHyperAssigned()}).
     */
    @Unique
    @Override
    public int ecoaegtnh$getHostHyperThreads() {
        final MTEEcalArray owner = this.ecoaegtnh$virtualCPUOwner;
        if (owner == null) {
            return 0;
        }
        final List<TileEcalThreadDrive> cores = owner.getThreadCores();
        int remaining = Math.max(0, owner.getBuiltinHyperThreads() - owner.getBuiltinHyperThreadsUsed());
        if (cores != null) {
            for (TileEcalThreadDrive core : cores) {
                int hyperUsed = 0;
                for (CraftingCPUCluster cpu : core.getCPUs()) {
                    if (ECPUCluster.from(cpu)
                        .ecoaegtnh$isHyperAssigned()) {
                        hyperUsed++;
                    }
                }
                remaining += Math.max(0, core.getHyperThreads() - hyperUsed);
            }
        }
        return remaining;
    }

    @Unique
    @Override
    public boolean ecoaegtnh$isHyperAssigned() {
        return ecoaegtnh$hyperAssigned;
    }

    @Unique
    @Override
    public void ecoaegtnh$setHyperAssigned(final boolean hyperAssigned) {
        this.ecoaegtnh$hyperAssigned = hyperAssigned;
    }

    @Unique
    @Override
    public long ecoaegtnh$getUsedExtraStorage() {
        return ecoaegtnh$usedExtraStorage;
    }

    @Unique
    @Override
    public void ecoaegtnh$setUsedExtraStorage(final long usedExtraStorage) {
        this.ecoaegtnh$usedExtraStorage = usedExtraStorage;
    }

    /** M2 (audit): real task bytes for pool accounting (excludes the virtual hyper reserve). */
    @Unique
    @Override
    public long ecoaegtnh$getUsedStorage() {
        return this.usedStorage;
    }

    @Unique
    @Override
    public void ecoaegtnh$markDestroyed() {
        this.isDestroyed = true;
        this.isComplete = true;
    }

    /**
     * t116b: vCPU effective available = pool remaining + current task bytes (used by the AE2U
     * job-merge checks in ContainerCraftConfirm/CraftingGridCache). Non-vCPU → -1.
     */
    @Unique
    @Override
    public long ecoaegtnh$effectiveAvailableStorage() {
        if (this.ecoaegtnh$virtualCPUOwner == null) {
            return -1L;
        }
        return this.ecoaegtnh$virtualCPUOwner.getAvailableBytes() + this.usedStorage;
    }

    @Unique
    @Override
    public EcoTimeRecorder ecoaegtnh$getTimeRecorder() {
        return ecoaegtnh$timeRecorder;
    }

    @Unique
    @Override
    public EcoTimeRecorder ecoaegtnh$getParallelismRecorder() {
        return ecoaegtnh$parallelismRecorder;
    }
}
