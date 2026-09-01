package ecoaegtnh.ecalculator;

import appeng.container.implementations.CraftingCPUStatus;

/**
 * ECO vCPU marker injected into AE2U's {@link CraftingCPUStatus} by
 * {@code ecoaegtnh.mixin.MixinCraftingCPUStatus}. Extends the 1.12.2 reference's ECPUStatus
 * (S:ECPUStatus.java — getLevel only) with the thread counts the user asked to show in the
 * crafting-status tooltip (plan §6.5, t25). {@code level} is the controller tier (0=C4/1=C6/2=C9);
 * -1 marks a vanilla (non-ECO) CPU row.
 */
public interface ECPUStatus {

    static ECPUStatus from(final CraftingCPUStatus status) {
        return (ECPUStatus) (Object) status;
    }

    /** Controller tier (0=C4 / 1=C6 / 2=C9), or -1 for vanilla CPU rows. */
    int ecoaegtnh$getLevel();

    /** Thread slots of the thread core this vCPU is assigned to (0 while unassigned). */
    int ecoaegtnh$getThreads();

    /** Hyper-thread slots of the thread core (0 for normal cores). */
    int ecoaegtnh$getHyperThreads();

    /**
     * t33: whether this vCPU row is running (assigned to a thread core) rather than a standby
     * vCPU. Running rows hide the "available threads" tooltip line (occupied threads are not
     * available); standby rows show the host's dynamic remaining values.
     */
    boolean ecoaegtnh$isAssigned();

    /** t114h: per-machine vCPU sequence id (running rows display "ECO vCPU #id"). */
    int ecoaegtnh$getVCPUId();

    /** t116d: true when this row is an ECO vCPU (pool-accounted effective storage available). */
    boolean ecoaegtnh$isVCPU();

    /**
     * t116d: effective available storage for vCPU rows — controller pool remaining + current task
     * bytes (computed server-side at row creation, synced through NBT so the client-side
     * {@code ContainerCraftConfirm.cpuMatches} can evaluate the AE2U job-merge condition).
     */
    long ecoaegtnh$getEffectiveStorage();
}
