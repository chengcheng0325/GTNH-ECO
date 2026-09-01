package ecoaegtnh.upgrade;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * t60: the upgrade tree held by a machine (docs/ECO_UPGRADE_TREE_DESIGN.md) — an ordered set of
 * {@link UpgradeNode}s forming a prerequisite DAG, plus the machine's activation state.
 * <p>
 * Rules: a node activates only when ALL its prerequisites are active (free base nodes activate
 * on construction); activation is permanent (no reset — 不可重设). The material payment happens
 * in the GUI submit flow (t61+) — the framework exposes {@link #canActivate(String)} (prereq
 * check) and {@link #activate(String)} (prereq check + free-node allowance; the t61 GUI adds
 * the cost check before calling activate). Activation state persists in machine NBT.
 */
public class UpgradeTree {

    private final Map<String, UpgradeNode> nodes = new LinkedHashMap<>();
    private final Set<String> activated = new LinkedHashSet<>();
    /**
     * t61: paid material amounts per node — nodeId → (material key → paid count). The GUI
     * material-submit flow adds staging items here (分步支付); when every cost entry is
     * fulfilled the node activates and its paid record is cleared.
     */
    private final Map<String, Map<String, Integer>> paid = new LinkedHashMap<>();

    /** t61: an empty tree (the storage array holds a placeholder tree until its nodes land). */
    public UpgradeTree() {}

    /**
     * t79: a per-machine tree instance built from a static node DEFINITION (see
     * CalculatorUpgradeTree/StorageUpgradeTree). Every machine gets its OWN activated/paid
     * state — the definition map itself is shared read-only (nodes are immutable after
     * construction). Free base nodes activate on construction.
     */
    public UpgradeTree(java.util.Map<String, UpgradeNode> definition) {
        if (definition != null) {
            for (UpgradeNode node : definition.values()) {
                addNode(node);
            }
        }
    }

    /** Registers a node (no duplicates) and auto-activates free base nodes. */
    protected final void addNode(UpgradeNode node) {
        if (node == null || nodes.containsKey(node.getId())) return;
        nodes.put(node.getId(), node);
        if (node.isFree()) {
            activated.add(node.getId());
        }
    }

    public UpgradeNode getNode(String id) {
        return nodes.get(id);
    }

    public Collection<UpgradeNode> getNodes() {
        return nodes.values();
    }

    public boolean isActivated(String id) {
        return activated.contains(id);
    }

    /** True when the node exists, is not yet active and all prerequisites are active. */
    public boolean canActivate(String id) {
        UpgradeNode node = nodes.get(id);
        if (node == null || activated.contains(id)) return false;
        for (String prereq : node.getPrerequisites()) {
            if (!activated.contains(prereq)) return false;
        }
        return true;
    }

    /**
     * Activates a node (permanent). Checks prerequisites and the free-node allowance; the
     * t61 GUI material-submit flow verifies/consumes the material cost BEFORE calling this.
     * Returns false when the node is unknown, already active, or prerequisites are missing.
     */
    public boolean activate(String id) {
        UpgradeNode node = nodes.get(id);
        if (node == null || activated.contains(id)) return false;
        for (String prereq : node.getPrerequisites()) {
            if (!activated.contains(prereq)) return false;
        }
        activated.add(id);
        return true;
    }

    /** Number of activated nodes (diagnostics / GUI). */
    public int getActivatedCount() {
        return activated.size();
    }

    // ------------------------------------------------------------------
    // t61: material payments (分步支付 — the GUI staging window adds payments; a node
    // activates once every cost entry is paid)
    // ------------------------------------------------------------------

    /** Paid amount of one material for a node (0 when nothing paid yet). */
    public int getPaid(String nodeId, String materialKey) {
        Map<String, Integer> m = paid.get(nodeId);
        return m == null ? 0 : m.getOrDefault(materialKey, 0);
    }

    /** Adds a payment for a node material (staging consumption). */
    public void addPayment(String nodeId, String materialKey, int amount) {
        if (amount <= 0) return;
        paid.computeIfAbsent(nodeId, k -> new LinkedHashMap<>())
            .merge(materialKey, amount, Integer::sum);
    }

    /** True when the node has no material cost or every cost entry is fully paid. */
    public boolean isCostFulfilled(String nodeId) {
        UpgradeNode node = nodes.get(nodeId);
        if (node == null) return false;
        Map<String, Integer> cost = node.getMaterialCost();
        if (cost.isEmpty()) return true;
        Map<String, Integer> p = paid.get(nodeId);
        for (Map.Entry<String, Integer> e : cost.entrySet()) {
            int have = p == null ? 0 : p.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) return false;
        }
        return true;
    }

    /** Clears the paid record of a node (after activation). */
    public void clearPaid(String nodeId) {
        paid.remove(nodeId);
    }

    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (String id : activated) {
            list.appendTag(new NBTTagString(id));
        }
        tag.setTag("activated", list);
        // t61: paid materials (node → material → amount).
        NBTTagCompound paidTag = new NBTTagCompound();
        for (Map.Entry<String, Map<String, Integer>> e : paid.entrySet()) {
            NBTTagCompound nodeTag = new NBTTagCompound();
            for (Map.Entry<String, Integer> m : e.getValue()
                .entrySet()) {
                nodeTag.setInteger(m.getKey(), m.getValue());
            }
            paidTag.setTag(e.getKey(), nodeTag);
        }
        tag.setTag("paid", paidTag);
    }

    /** Loads the activation + paid state (unknown ids are ignored; free nodes stay active). */
    @SuppressWarnings("unchecked")
    public void readFromNBT(NBTTagCompound tag) {
        activated.clear();
        if (tag.hasKey("activated")) {
            NBTTagList list = tag.getTagList("activated", 8); // NBTTagString
            for (int i = 0; i < list.tagCount(); i++) {
                String id = list.getStringTagAt(i);
                if (nodes.containsKey(id)) {
                    activated.add(id);
                }
            }
        }
        paid.clear();
        if (tag.hasKey("paid")) {
            NBTTagCompound paidTag = tag.getCompoundTag("paid");
            for (String nodeId : (java.util.Set<String>) paidTag.func_150296_c()) {
                if (!nodes.containsKey(nodeId)) continue;
                NBTTagCompound nodeTag = paidTag.getCompoundTag(nodeId);
                Map<String, Integer> m = new LinkedHashMap<>();
                for (String key : (java.util.Set<String>) nodeTag.func_150296_c()) {
                    m.put(key, nodeTag.getInteger(key));
                }
                paid.put(nodeId, m);
            }
        }
        // Free base nodes are always active.
        for (UpgradeNode node : nodes.values()) {
            if (node.isFree()) {
                activated.add(node.getId());
            }
        }
    }
}
