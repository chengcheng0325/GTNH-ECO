package ecoaegtnh.upgrade;

import net.minecraft.item.ItemStack;

/**
 * t65→t128: the calculator host's upgrade tree (docs/ECO_UPGRADE_TREE_DESIGN.md §2 revision,
 * user decision: single main chain + branches). t128 merges every three consecutive cell nodes
 * into ONE node (a node unlocks a whole group of three tiers) and renumbers sequentially:
 * 
 * <pre>
 *   N1 基础★ (free) → N2 4096k/16M/64M → N3 256M/1024M/4096M → N4 16384M → N5 奇点晶阵
 *                                                              (cell main chain, linear)
 *   T1 1线程 ← N2 → T2 4 → T3 16                                 (thread branch; T4/T5 removed)
 *   H1 2超线程 ← T2 → H2 4 ← T3 → H3 8 ← H2                     (hyper-thread branch)
 *   P1 ≤16 ← N2 → P2 ≤1024 → P3 ≤65536                           (parallel branch)
 *   OC 超频 (需 N4 + T3 + H3 + P3) → 红线 5% + 超线程 +10% 免额
 * </pre>
 * 
 * ★ = free base node (activated on construction). Non-free nodes carry the mixed GT+AE material
 * costs (docs §4; {@link UpgradeCosts}) — amounts are BASE PLACEHOLDER CONSTANTS (装机后调);
 * t128 (TEST ONLY, carried over from t113c): every non-free node costs ONE IRON INGOT so the
 * whole tree can be walked quickly in a test world.
 */
public final class CalculatorUpgradeTree extends UpgradeTree {

    // Cell main chain (t128: 5 nodes — 3 merged groups + 16384M + singularity tails).
    public static final String N1 = "N1";
    public static final String N2 = "N2";
    public static final String N3 = "N3";
    public static final String N4 = "N4";
    public static final String N5 = "N5";
    // Thread branch (t128: T4/T5 = 32/64-thread cores removed — no such cores exist).
    public static final String T1 = "T1";
    public static final String T2 = "T2";
    public static final String T3 = "T3";
    // Hyper-thread branch.
    public static final String H1 = "H1";
    public static final String H2 = "H2";
    public static final String H3 = "H3";
    // Parallel branch (t128: 3 merged nodes).
    public static final String P1 = "P1";
    public static final String P2 = "P2";
    public static final String P3 = "P3";
    // Overclock (反转彩蛋).
    public static final String OC = "OC";
    // t114g: built-in thread slots (machine provides 1 thread by itself; B1 +3 → 4, B2 +2 hyper).
    public static final String B1 = "B1";
    public static final String B2 = "B2";

    /**
     * t79: static read-only node definitions. Activation/paid state lives on EACH machine's own
     * {@link UpgradeTree} instance ({@link #newInstance()}) — sharing the singleton used to make
     * every host inherit the same unlocks. Nodes are immutable after construction, so the
     * definition map is safe to share.
     */
    public static final java.util.Map<String, UpgradeNode> DEFINITION = buildDefinition();

    private static java.util.Map<String, UpgradeNode> buildDefinition() {
        java.util.Map<String, UpgradeNode> m = new java.util.LinkedHashMap<>();
        for (UpgradeNode n : new CalculatorUpgradeTree().getNodes()) {
            m.put(n.getId(), n);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** t79: a fresh per-machine tree instance (free base nodes pre-activated, nothing else). */
    public static UpgradeTree newInstance() {
        return new UpgradeTree(DEFINITION);
    }

    private CalculatorUpgradeTree() {
        String k = "ecoaegtnh.upgrade.node.";
        // t128 (TEST ONLY): one iron per non-free node (the merged groups replace the old
        // per-size ladder of t113c; the user will replace the test price later).
        ItemStack iron = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iron);

        // Root (free) — t128 merged head: 机器基础 + 256k/1024k cells, auto-activated.
        addNode(new UpgradeNode(N1, k + N1 + ".name", k + N1 + ".effect", true));

        // Cell main chain N2..N5 (each depends on the previous). t128: N2 = old {4096k,16M,64M},
        // N3 = old {256M,1024M,4096M}, N4 = old N10 (16384M), N5 = old N11 (奇点闪存晶阵).
        paid(iron, k, N2, N1);
        paid(iron, k, N3, N2);
        paid(iron, k, N4, N3);
        paid(iron, k, N5, N4);

        // Thread branch (T1..T3 head off the NEW N2 — the old branch base was the 4096k node
        // N4, which is merged into new N2). t128: T4/T5 (32/64-thread cores) removed.
        paid(iron, k, T1, N2);
        paid(iron, k, T2, T1);
        paid(iron, k, T3, T2);

        // Hyper-thread branch (H1 ← T2, H2 ← T3 unchanged; t128: H3 prerequisite T3 → H2).
        paid(iron, k, H1, T2);
        paid(iron, k, H2, T3);
        paid(iron, k, H3, H2);

        // Parallel branch (t128: P1 covers 1/4/16 (≤16), P2 64/256/1024 (≤1024), P3
        // 4096/16384/65536 (≤65536); head P1 branches off the new N2 like the thread branch).
        paid(iron, k, P1, N2);
        paid(iron, k, P2, P1);
        paid(iron, k, P3, P2);

        // Overclock terminal (反转彩蛋: 红线 5% + 超线程免额) — t128 prerequisites renamed:
        // old {N10,T3,H3,P9} → {N4,T3,H3,P3}.
        paid(iron, k, OC, N4, T3, H3, P3);

        // t114g (user): built-in thread slots — B1 adds +3 built-in threads (base 1 → 4), B2
        // adds +2 built-in hyper threads. Branch off the new N2 like the thread/parallel branches.
        paid(iron, k, B1, N2);
        paid(iron, k, B2, B1);
    }

    /** t128 helper: one paid (1 iron) node with the given prerequisites. */
    private void paid(ItemStack iron, String k, String id, String... prereqs) {
        addNode(new UpgradeNode(id, k + id + ".name", k + id + ".effect", false, UpgradeCosts.of(iron, 1), prereqs));
    }
}
