package ecoaegtnh.upgrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * t60: one upgrade-tree node (docs/ECO_UPGRADE_TREE_DESIGN.md §1) — an ability unlocked by
 * activating its prerequisites and (from the GUI material-submit flow, t61+) paying its
 * material cost. No currency, no levels: a node is either activated or not; the activation
 * state persists in the machine NBT.
 * <p>
 * Each node carries: a stable id, a lang key for the display name, the prerequisite node ids
 * (a node can only be activated when ALL of them are active — the DAG), the material cost
 * (placeholder map until the t61 material table; {@code free} nodes skip the payment) and a
 * lang key describing the unlock effect.
 */
public final class UpgradeNode {

    private final String id;
    private final String nameKey;
    private final String[] prerequisites;
    private final Map<String, Integer> materialCost;
    private final boolean free;
    private final String effectKey;

    public UpgradeNode(String id, String nameKey, String effectKey, boolean free, String... prerequisites) {
        this(id, nameKey, effectKey, free, Collections.emptyMap(), prerequisites);
    }

    public UpgradeNode(String id, String nameKey, String effectKey, boolean free, Map<String, Integer> materialCost,
        String... prerequisites) {
        this.id = id;
        this.nameKey = nameKey;
        this.effectKey = effectKey;
        this.free = free;
        this.materialCost = materialCost == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(materialCost));
        this.prerequisites = prerequisites == null ? new String[0] : prerequisites.clone();
    }

    public String getId() {
        return id;
    }

    /** Lang key of the display name (e.g. {@code ecoaegtnh.upgrade.node.N2.name}). */
    public String getNameKey() {
        return nameKey;
    }

    /** Lang key describing what activating this node unlocks. */
    public String getEffectKey() {
        return effectKey;
    }

    /** Prerequisite node ids (ALL must be active to activate this node). */
    public String[] getPrerequisites() {
        return prerequisites.clone();
    }

    /**
     * Material cost (unlocalized item names → amount). Placeholder until the t61 material
     * table; the GUI material-submit flow consumes this and deducts the items.
     */
    public Map<String, Integer> getMaterialCost() {
        return materialCost;
    }

    /** Free base nodes are activated on construction (no payment, no prerequisites check). */
    public boolean isFree() {
        return free;
    }

    @Override
    public String toString() {
        return "UpgradeNode(" + id + ")";
    }
}
