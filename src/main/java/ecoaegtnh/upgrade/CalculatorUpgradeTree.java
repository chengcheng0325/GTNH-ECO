package ecoaegtnh.upgrade;

import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * t65→t131: the calculator host's upgrade tree (docs/ECO_UPGRADE_TREE_DESIGN.md §2 revision,
 * user decision: single main chain + branches). Structure:
 * 
 * <pre>
 *   N1 机器基础★ (free) → N2 4096k/16M/64M → N3 256M/1024M/4096M → N4 16384M → N5 奇点晶阵
 *                                                              (cell main chain, linear)
 *   T1 1线程 ← N2 → T2 4 → T3 16/32/64                          (thread branch)
 *   H1 2超线程 ← T2 → H2 4 ← T3 → H3 8 ← H2                     (hyper-thread branch)
 *   P1 ≤16 ← N2 → P2 ≤1024 → P3 ≤16777216                       (parallel branch)
 *   OC 超频 (需 N4 + T3 + H3 + P3) → 红线 5% + 超线程 +10% 免额
 *   B1 内置线程+3 ← N2 → B2 内置超线程+2
 * </pre>
 * 
 * ★ = free base node (activated on construction). Non-free nodes carry the mixed GT+AE material
 * costs (docs §4; {@link UpgradeCosts}) — amounts are BASE PLACEHOLDER CONSTANTS (装机后调);
 * keys are the canonical items' unlocalized names.
 * <p>
 * t131 (284, docs/t131-t127t128-sync-plan.md §1.3#1/§3.3): the t128 tree merge collapses every
 * three consecutive cell sizes into ONE node (a node unlocks a whole group of three tiers) and
 * renumbers sequentially — 31 → 17 nodes, T4/T5 gone, H3 ← H2, OC ← {N4,T3,H3,P3}, B1 ← N2.
 * Legacy saves are remapped by {@link #LEGACY_ID_MAP} (never by id passthrough — the merged tree
 * REUSES ids with new meanings, docs §3.2).
 * <p>
 * ⚠ 284 divergence (docs §6): the 32/64-thread cores are KEPT on this version (they may already be
 * inserted in a live world), so the thread branch ends at T3 and both legacy T4/T5 map onto T3.
 */
public final class CalculatorUpgradeTree extends UpgradeTree {

    // Cell main chain (t131: 5 nodes — 3 merged groups + 16384M + singularity tails).
    public static final String N1 = "N1";
    public static final String N2 = "N2";
    public static final String N3 = "N3";
    public static final String N4 = "N4";
    public static final String N5 = "N5";
    // Thread branch (t131: T1..T3; the legacy T4/T5 nodes are gone — 32/64-thread cores map to T3).
    public static final String T1 = "T1";
    public static final String T2 = "T2";
    public static final String T3 = "T3";
    // Hyper-thread branch.
    public static final String H1 = "H1";
    public static final String H2 = "H2";
    public static final String H3 = "H3";
    // Parallel branch (t131: 3 merged nodes).
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

    /**
     * t131: legacy (pre-merge, 31-node) id → merged id table for the one-time v0→v2 save migration
     * (docs/t131-t127t128-sync-plan.md §3.3). Every legacy id is listed EXPLICITLY: ids the merge
     * reused with a new meaning (N2/N4, the old 256k/4096k nodes) must NOT pass through, or the
     * migration would hand out progress the player never paid for (§3.2 blocker).
     */
    public static final java.util.Map<String, String> LEGACY_ID_MAP = buildLegacyIdMap();

    private static java.util.Map<String, UpgradeNode> buildDefinition() {
        java.util.Map<String, UpgradeNode> m = new java.util.LinkedHashMap<>();
        for (UpgradeNode n : new CalculatorUpgradeTree().getNodes()) {
            m.put(n.getId(), n);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** t131: legacy → merged id mapping (docs §3.3 calculator-tree table). */
    private static java.util.Map<String, String> buildLegacyIdMap() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        // Cell main chain: 3-in-1 groups; k-level collapses into the free head node.
        m.put("N1", N1); // 机器基础 ★
        m.put("N2", N1); // 256k → free head
        m.put("N3", N1); // 1024k → free head
        m.put("N4", N2); // 4096k → {4096k,16M,64M}
        m.put("N5", N2); // 16M
        m.put("N6", N2); // 64M
        m.put("N7", N3); // 256M → {256M,1024M,4096M}
        m.put("N8", N3); // 1024M
        m.put("N9", N3); // 4096M
        m.put("N10", N4); // 16384M
        m.put("N11", N5); // 奇点闪存晶阵
        // Thread branch: the legacy 32/64-thread nodes are gone → merged into T3 (§3.7).
        m.put("T1", T1);
        m.put("T2", T2);
        m.put("T3", T3);
        m.put("T4", T3);
        m.put("T5", T3);
        // Hyper-thread branch (unchanged ids).
        m.put("H1", H1);
        m.put("H2", H2);
        m.put("H3", H3);
        // Parallel branch: P1..P3 → P1 (≤16), P4..P6 → P2 (≤1024), P7..P9 → P3 (≤16777216).
        for (int i = 1; i <= 3; i++) {
            m.put("P" + i, P1);
        }
        for (int i = 4; i <= 6; i++) {
            m.put("P" + i, P2);
        }
        for (int i = 7; i <= 9; i++) {
            m.put("P" + i, P3);
        }
        // Overclock + built-in thread slots.
        m.put("OC", OC);
        m.put("B1", B1);
        m.put("B2", B2);
        return java.util.Collections.unmodifiableMap(m);
    }

    /** t79: a fresh per-machine tree instance (free base nodes pre-activated, nothing else). */
    public static UpgradeTree newInstance() {
        return new UpgradeTree(DEFINITION, LEGACY_ID_MAP);
    }

    private CalculatorUpgradeTree() {
        String k = "ecoaegtnh.upgrade.node.";
        // GT line ingots (cost key sources) + AE line materials (docs §4 混合线).
        ItemStack iron = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iron);
        ItemStack alu = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Aluminium);
        ItemStack ti = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Titanium);
        ItemStack ir = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iridium);
        ItemStack neu = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Neutronium);
        ItemStack stellar = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.StellarAlloy);
        appeng.api.definitions.IMaterials m = appeng.api.AEApi.instance()
            .definitions()
            .materials();
        ItemStack proc = UpgradeCosts.ae(m.calcProcessor()); // 处理器
        ItemStack logic = UpgradeCosts.ae(m.logicProcessor()); // 逻辑处理器
        ItemStack cell1k = UpgradeCosts.ae(m.cell1kPart()); // 存储元件 1k
        ItemStack board = gregtech.api.enums.ItemList.Circuit_Board_Basic.get(1); // 电路板 (AE 线点缀)

        // Root (free) — t131 merged head: 机器基础 + 256k/1024k cells, auto-activated.
        addNode(new UpgradeNode(N1, k + N1 + ".name", k + N1 + ".effect", true));

        // Cell main chain N2..N5 (each depends on the previous). t131: N2 = old {4096k,16M,64M},
        // N3 = old {256M,1024M,4096M}, N4 = old N10 (16384M), N5 = old N11 (奇点闪存晶阵).
        // The depth argument is the merged group's OLD ladder position (cost is iron x1 either way).
        addNode(
            new UpgradeNode(
                N2,
                k + N2 + ".name",
                k + N2 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N1));
        addNode(
            new UpgradeNode(
                N3,
                k + N3 + ".name",
                k + N3 + ".effect",
                false,
                costs(7, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N2));
        addNode(
            new UpgradeNode(
                N4,
                k + N4 + ".name",
                k + N4 + ".effect",
                false,
                costs(10, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N3));
        addNode(
            new UpgradeNode(
                N5,
                k + N5 + ".name",
                k + N5 + ".effect",
                false,
                costs(11, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N4));

        // Thread branch (t131: T1..T3 head off the NEW N2 — the old branch base was the 4096k node
        // N4, which is merged into new N2). 284 keeps the 32/64-thread cores: they gate on T3.
        addNode(
            new UpgradeNode(
                T1,
                k + T1 + ".name",
                k + T1 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N2));
        addNode(
            new UpgradeNode(
                T2,
                k + T2 + ".name",
                k + T2 + ".effect",
                false,
                costs(5, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                T1));
        addNode(
            new UpgradeNode(
                T3,
                k + T3 + ".name",
                k + T3 + ".effect",
                false,
                costs(6, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                T2));

        // Hyper-thread branch (H1 ← T2, H2 ← T3 unchanged; t131: H3 prerequisite T3 → H2).
        addNode(
            new UpgradeNode(
                H1,
                k + H1 + ".name",
                k + H1 + ".effect",
                false,
                costs(6, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                T2));
        addNode(
            new UpgradeNode(
                H2,
                k + H2 + ".name",
                k + H2 + ".effect",
                false,
                costs(7, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                T3));
        addNode(
            new UpgradeNode(
                H3,
                k + H3 + ".name",
                k + H3 + ".effect",
                false,
                costs(7, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                H2));

        // Parallel branch (t131: P1 ≤16 / P2 ≤1024 / P3 ≤16777216 — the 11 parallel tiers of t130
        // fold onto these three; P1 heads off the new N2 like the thread branch).
        addNode(
            new UpgradeNode(
                P1,
                k + P1 + ".name",
                k + P1 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N2));
        addNode(
            new UpgradeNode(
                P2,
                k + P2 + ".name",
                k + P2 + ".effect",
                false,
                costs(5, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                P1));
        addNode(
            new UpgradeNode(
                P3,
                k + P3 + ".name",
                k + P3 + ".effect",
                false,
                costs(6, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                P2));

        // Overclock terminal (反转彩蛋: 红线 5% + 超线程免额) — t131 prerequisites renamed:
        // old {N10,T3,H3,P9} → {N4,T3,H3,P3}.
        addNode(
            new UpgradeNode(
                OC,
                k + OC + ".name",
                k + OC + ".effect",
                false,
                costs(13, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N4,
                T3,
                H3,
                P3));

        // t114g (user): built-in thread slots — B1 adds +3 built-in threads (base 1 → 4), B2
        // adds +2 built-in hyper threads. t131: branch off the new N2 like the thread branch.
        addNode(
            new UpgradeNode(
                B1,
                k + B1 + ".name",
                k + B1 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N2));
        addNode(
            new UpgradeNode(
                B2,
                k + B2 + ".name",
                k + B2 + ".effect",
                false,
                costs(5, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                B1));
    }

    /**
     * t113c (TEST ONLY): every non-free upgrade node costs ONE IRON INGOT so the whole tree can
     * be walked quickly in a test world. The user will replace this with the real per-node cost
     * ladder later (the original depth-based ladder is below, commented out).
     * <p>
     * t131: kept verbatim by user decision (docs §8.1 #9 — 每节点 1 铁锭, {@link UpgradeCosts}
     * unchanged); the merged groups reuse their first member's old depth so the commented ladder
     * below still reads correctly.
     */
    private static Map<String, Integer> costs(int depth, ItemStack iron, ItemStack alu, ItemStack ti, ItemStack ir,
        ItemStack neu, ItemStack stellar, ItemStack proc, ItemStack logic, ItemStack cell1k, ItemStack board) {
        return UpgradeCosts.of(iron, 1);
        // Original depth ladder (t63/t65 — GT line + AE line, 装机后调, docs §4):
        // if (depth <= 3) return UpgradeCosts.of(iron, 16 * depth, alu, 8 * depth, proc, 2 * depth);
        // if (depth <= 6) return UpgradeCosts.of(alu, 16 * (depth - 2), ti, 8 * (depth - 2), board, 2 * (depth - 2));
        // if (depth <= 9) return UpgradeCosts.of(ti, 16 * (depth - 5), ir, 8 * (depth - 5), logic, 2 * (depth - 5));
        // if (depth <= 12) return UpgradeCosts.of(ir, 16 * (depth - 8), neu, 8 * (depth - 8), cell1k, 2 * (depth - 8));
        // return UpgradeCosts.of(neu, 8, stellar, 2, logic, 8, cell1k, 8); // OC terminal
    }
}
