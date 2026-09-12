package ecoaegtnh.upgrade;

import net.minecraft.item.ItemStack;

/**
 * t112/t114→t131: the storage array's upgrade tree — ONE MERGED NODE PER CAPACITY GROUP
 * (docs/ECO_UPGRADE_TREE_DESIGN.md §3 + user t128 decision: 3-in-1), three independent chains
 * (one per storage family):
 * 
 * <pre>
 *   item chain:     I1 k级★ → I2 M级 → I3 大M级 → I4 人造宇宙
 *   fluid chain:    F1 k级★ → F2 M级 → F3 大M级 → F4 人造宇宙 → F5 无限水
 *   essentia chain: E1 k级★ → E2 M级 → E3 大M级 → E4 人造宇宙 → E5 魔导源质
 * </pre>
 * 
 * Each merged node unlocks a WHOLE GROUP of three cell tiers at once (t131: old nodes 1-3 → new
 * node 1, old 4-6 → new node 2, old 7-9 → new node 3; the family tails 人造宇宙 / 无限水 / 魔导源质
 * keep their own nodes and are renumbered last). The cell's {@link
 * ecoaegtnh.item.estorage.ItemEcoStorageCell#getRequiredUpgradeNode()} is prefix + the size's
 * group number ({@link ecoaegtnh.item.estorage.CellSize#upgradeGroupIndex()}: 256k..4096k → 1,
 * 16M..256M → 2, 1024M..16384M → 3, 人造宇宙 → 4, 无限水 → F5, 魔导源质 → E5). Chains are
 * independent (no cross-chain prerequisites); the essentia chain needs TE4 loaded (its lang/effect
 * keys only resolve when Thaumcraft is present — the tree itself is load-safe). ★ = free base nodes
 * (activated on construction).
 * <p>
 * t131 (284, docs/t131-t127t128-sync-plan.md §3.4): legacy 32-node saves are remapped by
 * {@link #LEGACY_ID_MAP}. Both trees are strictly linear with a free head node, so the storage
 * remap can never break a prerequisite (the calculator tree is the one that needs the closure —
 * §3.6).
 */
public final class StorageUpgradeTree extends UpgradeTree {

    // Item chain (k级 → M级 → 大M级 → 人造宇宙).
    public static final String I1 = "I1";
    public static final String I2 = "I2";
    public static final String I3 = "I3";
    public static final String I4 = "I4";
    // Fluid chain (t131: F5 = 无限水 tail).
    public static final String F1 = "F1";
    public static final String F2 = "F2";
    public static final String F3 = "F3";
    public static final String F4 = "F4";
    public static final String F5 = "F5";
    // Essentia chain (t131: E5 = 魔导源质 tail).
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

    /**
     * t131: legacy (pre-merge, 32-node) id → merged id table for the one-time v0→v2 save migration
     * (docs/t131-t127t128-sync-plan.md §3.4). Listed EXPLICITLY per family: ids the merge reused
     * with a new meaning (I2 = 1024k before, M-level group now; I4 = 16M before, 人造宇宙 now)
     * must NEVER pass through (§3.2 blocker).
     */
    public static final java.util.Map<String, String> LEGACY_ID_MAP = buildLegacyIdMap();

    private static java.util.Map<String, UpgradeNode> buildDefinition() {
        java.util.Map<String, UpgradeNode> m = new java.util.LinkedHashMap<>();
        for (UpgradeNode n : new StorageUpgradeTree().getNodes()) {
            m.put(n.getId(), n);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** t131: legacy → merged id mapping (docs §3.4 storage-tree table, three isomorphic chains). */
    private static java.util.Map<String, String> buildLegacyIdMap() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        // Groups: old 1-3 → 1 (k级), 4-6 → 2 (M级), 7-9 → 3 (大M级); tails: 10 → 4 (人造宇宙),
        // and the family-exclusive infinite cells renumber last (F11 → F5, E11 → E5).
        for (int i = 1; i <= 3; i++) m.put("I" + i, I1);
        for (int i = 4; i <= 6; i++) m.put("I" + i, I2);
        for (int i = 7; i <= 9; i++) m.put("I" + i, I3);
        m.put("I10", I4);
        for (int i = 1; i <= 3; i++) m.put("F" + i, F1);
        for (int i = 4; i <= 6; i++) m.put("F" + i, F2);
        for (int i = 7; i <= 9; i++) m.put("F" + i, F3);
        m.put("F10", F4);
        m.put("F11", F5);
        for (int i = 1; i <= 3; i++) m.put("E" + i, E1);
        for (int i = 4; i <= 6; i++) m.put("E" + i, E2);
        for (int i = 7; i <= 9; i++) m.put("E" + i, E3);
        m.put("E10", E4);
        m.put("E11", E5);
        return java.util.Collections.unmodifiableMap(m);
    }

    /** t79: a fresh per-machine tree instance (free base nodes pre-activated, nothing else). */
    public static UpgradeTree newInstance() {
        return new UpgradeTree(DEFINITION, LEGACY_ID_MAP);
    }

    private StorageUpgradeTree() {
        String k = "ecoaegtnh.upgrade.node.";
        // t113c (TEST ONLY): every non-free node costs ONE IRON INGOT so the whole tree can be
        // walked quickly in a test world — here one iron per MERGED GROUP (one node unlocks three
        // cell tiers). The user will replace the ladder with real materials later (the original
        // per-size ladder lives in docs/ECO_UPGRADE_TREE_DESIGN.md §4).
        ItemStack iron = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iron);
        // t131: the three chains are strictly linear with a free head node (I1/F1/E1★) — the
        // merged tree has 4/5/5 nodes instead of 10/11/11 (§3.4).
        addChain(iron, k, "I", 4);
        addChain(iron, k, "F", 5);
        addChain(iron, k, "E", 5);
    }

    /**
     * t131: one independent chain of merged group nodes — the head node is free (auto-activated
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
