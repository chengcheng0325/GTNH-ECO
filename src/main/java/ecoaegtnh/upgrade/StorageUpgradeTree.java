package ecoaegtnh.upgrade;

import java.util.Map;

import net.minecraft.item.ItemStack;

import ecoaegtnh.item.estorage.CellSize;
import ecoaegtnh.item.estorage.StorageType;

/**
 * t112/t114: the storage array's 32-node upgrade tree — ONE NODE PER CELL
 * (docs/ECO_UPGRADE_TREE_DESIGN.md §3), three independent chains (one per storage family):
 * 
 * <pre>
 *   item chain:     I1 256k★ → I2 1024k → … → I9 16384M → I10 人造宇宙
 *   fluid chain:    F1 256k★ → F2 1024k → … → F9 16384M → F10 人造宇宙 → F11 无限水
 *   essentia chain: E1 256k★ → E2 1024k → … → E9 16384M → E10 人造宇宙 → E11 魔导源质
 * </pre>
 * 
 * Each node unlocks EXACTLY ONE cell item (the cell's {@link
 * ecoaegtnh.item.estorage.ItemEcoStorageCell#getRequiredUpgradeNode()} is prefix + size index).
 * t114: the family-exclusive sizes (INF_WATER → fluid chain only, ARCANE → essentia chain only)
 * are gated through {@link CellSize#allowed(StorageType)} — the item chain has 10 nodes, the
 * fluid/essentia chains 11 each. Chains are independent (no cross-chain prerequisites); the
 * essentia chain needs TE4 loaded (its lang/effect keys only resolve when Thaumcraft is present —
 * the tree itself is load-safe). ★ = free base nodes (activated on construction).
 */
public final class StorageUpgradeTree extends UpgradeTree {

    // Item chain (one node per cell size, 256k .. universe).
    public static final String I1 = "I1";
    public static final String I2 = "I2";
    public static final String I3 = "I3";
    public static final String I4 = "I4";
    public static final String I5 = "I5";
    public static final String I6 = "I6";
    public static final String I7 = "I7";
    public static final String I8 = "I8";
    public static final String I9 = "I9";
    public static final String I10 = "I10";
    // Fluid chain (t114: F11 = infinite water).
    public static final String F1 = "F1";
    public static final String F2 = "F2";
    public static final String F3 = "F3";
    public static final String F4 = "F4";
    public static final String F5 = "F5";
    public static final String F6 = "F6";
    public static final String F7 = "F7";
    public static final String F8 = "F8";
    public static final String F9 = "F9";
    public static final String F10 = "F10";
    public static final String F11 = "F11";
    // Essentia chain (t114: E11 = arcane).
    public static final String E1 = "E1";
    public static final String E2 = "E2";
    public static final String E3 = "E3";
    public static final String E4 = "E4";
    public static final String E5 = "E5";
    public static final String E6 = "E6";
    public static final String E7 = "E7";
    public static final String E8 = "E8";
    public static final String E9 = "E9";
    public static final String E10 = "E10";
    public static final String E11 = "E11";

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
        // GT ingot ladder (cost key sources) + AE line materials (docs §4 混合线).
        ItemStack iron = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iron);
        ItemStack alu = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Aluminium);
        ItemStack ti = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Titanium);
        ItemStack ir = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Iridium);
        ItemStack neutronium = UpgradeCosts.gtIngot(gregtech.api.enums.Materials.Neutronium);
        appeng.api.definitions.IMaterials m = appeng.api.AEApi.instance()
            .definitions()
            .materials();
        ItemStack proc = UpgradeCosts.ae(m.calcProcessor()); // 处理器
        ItemStack logic = UpgradeCosts.ae(m.logicProcessor()); // 逻辑处理器
        ItemStack board = gregtech.api.enums.ItemList.Circuit_Board_Basic.get(1); // 电路板 (AE 线点缀)

        // t113c (TEST ONLY): every non-free node costs ONE IRON INGOT so the whole tree can be
        // walked quickly in a test world; the user will replace the ladder with real materials
        // later (the original depth ladder is below, commented out).
        java.util.List<Map<String, Integer>> ladder = java.util.Arrays.asList(
            null, // I1/F1/E1 256k — free
            cost(iron, 1), // I2/F2/E2 1024k
            cost(iron, 1), // I3/F3/E3 4096k
            cost(iron, 1), // I4/F4/E4 16M
            cost(iron, 1), // I5/F5/E5 64M
            cost(iron, 1), // I6/F6/E6 256M
            cost(iron, 1), // I7/F7/E7 1024M
            cost(iron, 1), // I8/F8/E8 4096M
            cost(iron, 1), // I9/F9/E9 16384M
            cost(iron, 1), // I10/F10/E10 人造宇宙
            cost(iron, 1), // F11 无限水 (t114)
            cost(iron, 1)); // E11 魔导源质 (t114)
        // Original ladder (t112/t113 — iron → aluminium → titanium → iridium → neutronium + AE
        // line, 装机后调):
        // cost(iron, 16, alu, 8, proc, 4) // I2 1024k
        // cost(alu, 24, ti, 8, proc, 6) // I3 4096k
        // cost(alu, 32, ti, 16, board, 8) // I4 16M
        // cost(ti, 32, ir, 8, board, 10) // I5 64M
        // cost(ti, 48, ir, 12, logic, 4) // I6 256M
        // cost(ti, 64, ir, 16, logic, 8) // I7 1024M
        // cost(ir, 24, neutronium, 4, logic, 12) // I8 4096M
        // cost(ir, 32, neutronium, 8, logic, 16) // I9 16384M
        // cost(ir, 48, neutronium, 16, logic, 24) // I10 人造宇宙

        CellSize[] sizes = CellSize.values(); // ascending: 256k .. universe .. infwater .. arcane
        for (StorageType type : StorageType.values()) {
            String prefix = type == StorageType.FLUID ? "F" : type == StorageType.ESSENTIA ? "E" : "I";
            // t114d: node number = position WITHIN the family chain, not the enum ordinal — the
            // essentia chain numbers E1..E11 (ARCANE is the 11th size), so the node ids always
            // match ItemEcoStorageCell#getRequiredUpgradeNode (E11, not E12).
            int chain = 0;
            for (int i = 0; i < sizes.length; i++) {
                // t114: family-exclusive sizes only build their own chain's node (INF_WATER → F11,
                // ARCANE → E11).
                if (!sizes[i].allowed(type)) continue;
                chain++;
                String id = prefix + chain;
                // t113b: free base nodes use the 4-arg constructor — passing null through the
                // varargs would create a prerequisites array CONTAINING null (new String[]{null}),
                // which made stateColor() NPE on pack.contains(null) (client crash 2026-08-31).
                if (chain == 1) {
                    addNode(new UpgradeNode(id, k + id + ".name", k + id + ".effect", true));
                } else {
                    addNode(
                        new UpgradeNode(
                            id,
                            k + id + ".name",
                            k + id + ".effect",
                            false,
                            ladder.get(i),
                            prefix + (chain - 1)));
                }
            }
        }
    }

    /** Cost map from alternating (ItemStack, Integer) pairs (t63; see {@link UpgradeCosts}). */
    private static Map<String, Integer> cost(Object... pairs) {
        return UpgradeCosts.of(pairs);
    }
}
