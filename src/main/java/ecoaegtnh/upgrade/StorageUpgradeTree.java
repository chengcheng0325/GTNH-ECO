package ecoaegtnh.upgrade;

import net.minecraft.item.ItemStack;

/**
 * t112/t114→t128: the storage array's upgrade tree — ONE MERGED NODE PER CAPACITY GROUP
 * (docs/ECO_UPGRADE_TREE_DESIGN.md §3 + user t128 decision: 3-in-1), three independent chains
 * (one per storage family):
 * 
 * <pre>
 *   item chain:     I1 k级★ → I2 M级 → I3 大M级 → I4 人造宇宙
 *   fluid chain:    F1 k级★ → F2 M级 → F3 大M级 → F4 人造宇宙 → F5 无限水
 *   essentia chain: E1 k级★ → E2 M级 → E3 大M级 → E4 人造宇宙 → E5 魔导源质
 * </pre>
 * 
 * Each merged node unlocks a WHOLE GROUP of three cell tiers at once (t128: old nodes 1-3 → new
 * node 1, old 4-6 → new node 2, old 7-9 → new node 3; the family tails 人造宇宙 / 无限水 / 魔导源质
 * keep their own nodes and are renumbered last). The cell's {@link
 * ecoaegtnh.item.estorage.ItemEcoStorageCell#getRequiredUpgradeNode()} is prefix + the size's
 * group number (256k..4096k → 1, 16M..256M → 2, 1024M..16384M → 3, 人造宇宙 → 4, 无限水 → F5,
 * 魔导源质 → E5). Chains are independent (no cross-chain prerequisites); the essentia chain needs
 * TE4 loaded (its lang/effect keys only resolve when Thaumcraft is present — the tree itself is
 * load-safe). ★ = free base nodes (activated on construction).
 */
public final class StorageUpgradeTree extends UpgradeTree {

    // Item chain (k级 → M级 → 大M级 → 人造宇宙).
    public static final String I1 = "I1";
    public static final String I2 = "I2";
    public static final String I3 = "I3";
    public static final String I4 = "I4";
    // Fluid chain (t128: F5 = 无限水 tail).
    public static final String F1 = "F1";
    public static final String F2 = "F2";
    public static final String F3 = "F3";
    public static final String F4 = "F4";
    public static final String F5 = "F5";
    // Essentia chain (t128: E5 = 魔导源质 tail).
    public static final String E1 = "E1";
    public static final String E2 = "E2";
    public static final String E3 = "E3";
    public static final String E4 = "E4";
    public static final String E5 = "E5";

    /**
     * t79: static read-only node definitions. Activation/paid state lives on EACH machine's own
     * {@link UpgradeTree} instance ({@link #newInstance()}) — sharing the singleton used to make
     * every array inherit the same unlocks.
     */
    public static final java.util.Map<String, UpgradeNode> DEFINITION = buildDefinition();

    private static java.util.Map<String, UpgradeNode> buildDefinition() {
        java.util.Map<String, UpgradeNode> m = new java.util.LinkedHashMap<>();
        for (UpgradeNode n : new StorageUpgradeTree().getNodes()) {
            m.put(n.getId(), n);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** t79: a fresh per-machine tree instance (free base nodes pre-activated, nothing else). */
    public static UpgradeTree newInstance() {
        return new UpgradeTree(DEFINITION);
    }

    private StorageUpgradeTree() {
        String k = "ecoaegtnh.upgrade.node.";
        // t128 (TEST ONLY, carried over from t113c): every non-free node costs ONE IRON INGOT so
        // the whole tree can be walked quickly in a test world — here one iron per MERGED GROUP
        // (one node unlocks three cell tiers). The user will replace the ladder with real
        // materials later.
        ItemStack iron = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iron);
        addChain(iron, k, "I", 4);
        addChain(iron, k, "F", 5);
        addChain(iron, k, "E", 5);
    }

    /**
     * t128: one independent chain of merged group nodes — the head node is free (auto-activated
     * on construction, ★), every other node costs one iron and depends on the previous node
     * (sequential chain). Node ids are prefix + 1..count (I1..I4 / F1..F5 / E1..E5).
     */
    private void addChain(ItemStack iron, String k, String prefix, int count) {
        for (int i = 1; i <= count; i++) {
            String id = prefix + i;
            // t113b: free base nodes use the 4-arg constructor — passing null through the
            // varargs would create a prerequisites array CONTAINING null (new String[]{null}),
            // which made stateColor() NPE on pack.contains(null) (client crash 2026-08-31).
            if (i == 1) {
                addNode(new UpgradeNode(id, k + id + ".name", k + id + ".effect", true));
            } else {
                addNode(
                    new UpgradeNode(
                        id,
                        k + id + ".name",
                        k + id + ".effect",
                        false,
                        UpgradeCosts.of(iron, 1),
                        prefix + (i - 1)));
            }
        }
    }
}
