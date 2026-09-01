package ecoaegtnh.upgrade;

import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * t65: the calculator host's 26-node upgrade tree (docs/ECO_UPGRADE_TREE_DESIGN.md §2 revision,
 * user decision: single main chain + branches). Structure:
 * 
 * <pre>
 *   N1 机器基础★ (free) → N2 256k → N3 1024k → N4 4096k → N5 16M → N6 64M → N7 256M
 *     → N8 1024M → N9 4096M → N10 16384M                       (cell main chain, linear)
 *   T1 1线程 ← N4 → T2 4 → T3 16                                (thread branch)
 *   H1 2超线程 ← T2 → H2 4 ← T3 → H3 8 ← T3                     (hyper-thread branch)
 *   P1 1并行 ← N4 → P2 4 → P3 16 → P4 64 → P5 256 → P6 1024 → P7 4096 → P8 16384 → P9 65536
 *   OC 超频 (需 N10 + T3 + H3 + P9) → 红线 5% + 超线程 +10% 免额
 * </pre>
 * 
 * ★ = free base node (activated on construction). Non-free nodes carry the mixed GT+AE material
 * costs (docs §4; {@link UpgradeCosts}) — amounts are BASE PLACEHOLDER CONSTANTS scaling with
 * the node depth (装机后调); keys are the canonical items' unlocalized names.
 */
public final class CalculatorUpgradeTree extends UpgradeTree {

    // Cell main chain.
    public static final String N1 = "N1";
    public static final String N2 = "N2";
    public static final String N3 = "N3";
    public static final String N4 = "N4";
    public static final String N5 = "N5";
    public static final String N6 = "N6";
    public static final String N7 = "N7";
    public static final String N8 = "N8";
    public static final String N9 = "N9";
    public static final String N10 = "N10";
    // t114: Singularity flash cell (奇点闪存晶阵) — after 16384M.
    public static final String N11 = "N11";
    // Thread branch (t114f: T4/T5 = 32/64-thread cores join after T3).
    public static final String T1 = "T1";
    public static final String T2 = "T2";
    public static final String T3 = "T3";
    public static final String T4 = "T4";
    public static final String T5 = "T5";
    // Hyper-thread branch.
    public static final String H1 = "H1";
    public static final String H2 = "H2";
    public static final String H3 = "H3";
    // Parallel branch.
    public static final String P1 = "P1";
    public static final String P2 = "P2";
    public static final String P3 = "P3";
    public static final String P4 = "P4";
    public static final String P5 = "P5";
    public static final String P6 = "P6";
    public static final String P7 = "P7";
    public static final String P8 = "P8";
    public static final String P9 = "P9";
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

        // Root (free).
        addNode(new UpgradeNode(N1, k + N1 + ".name", k + N1 + ".effect", true));

        // Cell main chain N2..N11 (depth 1..10, each depends on the previous). t114: N11 is the
        // Singularity flash cell (奇点闪存晶阵, Long.MAX_VALUE bytes).
        for (int i = 2; i <= 11; i++) {
            String id = "N" + i;
            addNode(
                new UpgradeNode(
                    id,
                    k + id + ".name",
                    k + id + ".effect",
                    false,
                    costs(i, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                    "N" + (i - 1)));
        }

        // Thread branch (T1 branches off N4, depth 4..8; t114f adds T4 32-thread / T5 64-thread).
        addNode(
            new UpgradeNode(
                T1,
                k + T1 + ".name",
                k + T1 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N4));
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
        addNode(
            new UpgradeNode(
                T4,
                k + T4 + ".name",
                k + T4 + ".effect",
                false,
                costs(7, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                T3));
        addNode(
            new UpgradeNode(
                T5,
                k + T5 + ".name",
                k + T5 + ".effect",
                false,
                costs(8, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                T4));

        // Hyper-thread branch (H1 ← T2, H2/H3 ← T3, depth 6..7).
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
                T3));

        // Parallel branch (P1 branches off N4, depth 4..12).
        addNode(
            new UpgradeNode(
                P1,
                k + P1 + ".name",
                k + P1 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N4));
        for (int i = 2; i <= 9; i++) {
            String id = "P" + i;
            addNode(
                new UpgradeNode(
                    id,
                    k + id + ".name",
                    k + id + ".effect",
                    false,
                    costs(i + 3, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                    "P" + (i - 1)));
        }

        // Overclock terminal (反转彩蛋: 红线 5% + 超线程免额) — 数千材料价值.
        addNode(
            new UpgradeNode(
                OC,
                k + OC + ".name",
                k + OC + ".effect",
                false,
                costs(13, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N10,
                T3,
                H3,
                P9));

        // t114g (user): built-in thread slots — B1 adds +3 built-in threads (base 1 → 4), B2
        // adds +2 built-in hyper threads. Branch off N4 like the thread/parallel branches.
        addNode(
            new UpgradeNode(
                B1,
                k + B1 + ".name",
                k + B1 + ".effect",
                false,
                costs(4, iron, alu, ti, ir, neu, stellar, proc, logic, cell1k, board),
                N4));
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
