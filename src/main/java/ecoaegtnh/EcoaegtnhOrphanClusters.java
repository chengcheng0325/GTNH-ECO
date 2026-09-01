package ecoaegtnh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * t122 (user): registry of ORPHANED vCPU clusters — in-flight jobs whose controller was removed
 * (block broken) while the ME grid was unreachable, so the refunding cancel() could not land.
 * Destroying such a cluster would swallow the job's materials, so instead the cluster is adopted
 * here: the STRONG static reference keeps the cluster (and its materials) alive across controller
 * removal and grid rebuilds, and every live {@code CraftingGridCache} re-drives adopted clusters
 * (MixinCraftingGridCache.injectUpdateCPUClusters), so the job resumes, completes and refunds its
 * materials into the grid on reconnect. destroy() releases the entry (MixinCraftingCPUCluster
 * injectDestroy), so the registry self-cleans when a job finishes.
 */
public final class EcoaegtnhOrphanClusters {

    private static final Set<CraftingCPUCluster> ORPHANS = new HashSet<>();

    private EcoaegtnhOrphanClusters() {}

    /** Adopt a cluster whose cancel/refund could not complete (idempotent). */
    public static void adopt(CraftingCPUCluster cluster) {
        if (cluster != null) {
            ORPHANS.add(cluster);
        }
    }

    /** Release a destroyed orphan (called by the M1 destroy mixin; idempotent). */
    public static void release(CraftingCPUCluster cluster) {
        if (cluster != null) {
            ORPHANS.remove(cluster);
        }
    }

    /** Snapshot of all adopted clusters (safe to iterate while modifying). */
    public static Collection<CraftingCPUCluster> all() {
        return new ArrayList<>(ORPHANS);
    }
}
