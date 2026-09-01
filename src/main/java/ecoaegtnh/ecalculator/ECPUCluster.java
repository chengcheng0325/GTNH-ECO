package ecoaegtnh.ecalculator;

import javax.annotation.Nullable;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import ecoaegtnh.metatileentity.MTEEcalArray;
import ecoaegtnh.tile.ecalculator.TileEcalThreadDrive;

/**
 * vCPU enhancement interface injected into AE2U's {@link CraftingCPUCluster} by
 * {@code ecoaegtnh.mixin.MixinCraftingCPUCluster}. Mirrors the 1.12.2 reference's ECPUCluster
 * (S:ECPUCluster.java), adapted to rv3 (method prefix {@code ecoaegtnh$}, tier as int, own
 * {@link EcoTimeRecorder} instead of MMCE TimeRecorder). Plan §6.2 / R1 §2.1.
 */
public interface ECPUCluster {

    static ECPUCluster from(final CraftingCPUCluster cluster) {
        return (ECPUCluster) (Object) cluster;
    }

    /** Overrides the AE2 private {@code availableStorage} (vCPU capacity = task bytes). */
    void ecoaegtnh$setAvailableStorage(long availableStorage);

    /** Overrides the AE2 {@code accelerator} (parallelism → remainingOperations per tick). */
    void ecoaegtnh$setAccelerators(int accelerators);

    /** The thread drive this cluster is assigned to, or null (t35: drives replaced thread cores). */
    @Nullable
    TileEcalThreadDrive ecoaegtnh$getController();

    /**
     * t33: host REMAINING thread slots (dynamic) — Σ over all thread cores of
     * ({@code getThreads()} − normal-slot occupancy), where occupancy counts the core's assigned
     * clusters that are NOT hyper-assigned; 0 when unattached. A 2-core host with 1 running task
     * reports 1, fully occupied reports 0. Shown on standby-vCPU rows (running rows hide the
     * threads line — occupied threads are not "available").
     */
    int ecoaegtnh$getHostThreads();

    /**
     * t33: host REMAINING hyper-thread slots — Σ over all thread cores of ({@code getHyperThreads()}
     * − hyper-slot occupancy, hyper-assigned clusters per {@link #ecoaegtnh$isHyperAssigned()}).
     */
    int ecoaegtnh$getHostHyperThreads();

    /** t33: whether this cluster occupies a hyper-thread slot (TileEcalThreadDrive.addCPU(hyper)). */
    boolean ecoaegtnh$isHyperAssigned();

    /** t33: marks the cluster's slot kind (set on hyper assignment, cleared on destroy). */
    void ecoaegtnh$setHyperAssigned(boolean hyperAssigned);

    /** Assigns the cluster to a thread drive (also rewrites machineSrc to the channel source). */
    void ecoaegtnh$setThreadCore(TileEcalThreadDrive threadCore);

    /** Owner controller while the cluster is an unassigned standby vCPU. */
    void ecoaegtnh$setVirtualCPUOwner(@Nullable MTEEcalArray virtualCPUOwner);

    /** t114i: vCPU number from the controller's smallest-available pool (0 while standby). */
    int ecoaegtnh$getVCPUId();

    void ecoaegtnh$setVCPUId(int vcpuId);

    /** Controller tier (0=C4 / 1=C6 / 2=C9), or -1 when unattached. */
    int ecoaegtnh$getControllerTier();

    long ecoaegtnh$getUsedExtraStorage();

    void ecoaegtnh$setUsedExtraStorage(long usedExtraStorage);

    void ecoaegtnh$markDestroyed();

    EcoTimeRecorder ecoaegtnh$getTimeRecorder();

    EcoTimeRecorder ecoaegtnh$getParallelismRecorder();
}
