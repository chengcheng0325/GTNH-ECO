package ecoaegtnh.upgrade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * <p>
 * <b>t131 — versioned NBT + double keys + one-time v0→v2 migration</b>
 * (docs/t131-t127t128-sync-plan.md §3.5/§3.6). The t128 tree merge reused node ids that now mean
 * something DIFFERENT (old {@code N2} = 256k cell vs new {@code N2} = the {4096k,16M,64M} group),
 * so a legacy save may NEVER be read by id passthrough ({@code N2}/{@code N4}/{@code I2}/{@code I4}
 * would silently hand out progress the player never paid for). Instead:
 * <ul>
 * <li>{@code treeVersion} = {@link #TREE_VERSION} (2) marks the new schema;</li>
 * <li>{@code activatedV2} / {@code paidV2} are the ONLY authoritative state from v2 on;</li>
 * <li>a save without {@code treeVersion} (v0) or with {@code treeVersion < 2} is migrated ONCE on
 * load through the per-tree legacy id table ({@link #legacyIdMap}): every activated id is remapped
 * semantically, prerequisite closures are filled in (只增不减), {@code paid} amounts are summed per
 * target node and truncated to that node's cost;</li>
 * <li>unknown ids are logged and dropped into the {@code unmapped} list — never guessed (they do
 * not affect the nodes that DID map);</li>
 * <li>the pre-migration v0 payload is FROZEN and re-emitted verbatim on every save, so an old jar
 * (which ignores {@code treeVersion}/{@code *V2}) still reads the pre-migration state — see
 * {@link #writeToNBT}. Rolling back therefore loses post-migration progress but never breaks the
 * world (非双向无损).</li>
 * </ul>
 */
public class UpgradeTree {

    /** t131: NBT schema version — 2 = the merged 3-in-1 trees (see class javadoc). */
    public static final int TREE_VERSION = 2;
    /** t131: schema version key. */
    private static final String KEY_VERSION = "treeVersion";
    /** t131: v2 authoritative keys. */
    private static final String KEY_ACTIVATED_V2 = "activatedV2";
    private static final String KEY_PAID_V2 = "paidV2";
    /** t131: legacy v0 keys — read-only (never updated with v2 state), re-emitted frozen. */
    private static final String KEY_ACTIVATED_V0 = "activated";
    private static final String KEY_PAID_V0 = "paid";

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager
        .getLogger("ECOAEGTNH");

    private final Map<String, UpgradeNode> nodes = new LinkedHashMap<>();
    private final Set<String> activated = new LinkedHashSet<>();
    /**
     * t61: paid material amounts per node — nodeId → (material key → paid count). The GUI
     * material-submit flow adds staging items here (分步支付); when every cost entry is
     * fulfilled the node activates and its paid record is cleared.
     */
    private final Map<String, Map<String, Integer>> paid = new LinkedHashMap<>();

    /**
     * t131: legacy node-id → merged node-id table used by the one-time v0→v2 migration. {@code null}
     * means "no migration" (the empty placeholder tree). A legacy id that is absent from the table
     * is NOT passed through — it is reported as unmapped, because the merged tree reused ids with
     * new meanings (docs §3.2 blocker).
     */
    private final Map<String, String> legacyIdMap;

    /** t131: frozen pre-migration v0 payload, re-emitted verbatim by {@link #writeToNBT}. */
    private Set<String> legacyActivated;
    private Map<String, Map<String, Integer>> legacyPaid;
    /** t131: one-shot guard so the migration log line is printed once per tree instance. */
    private boolean legacyMigrationLogged;

    /** t61: an empty tree (the storage array holds a placeholder tree until its nodes land). */
    public UpgradeTree() {
        this.legacyIdMap = null;
    }

    /**
     * t79: a per-machine tree instance built from a static node DEFINITION (see
     * CalculatorUpgradeTree/StorageUpgradeTree). Every machine gets its OWN activated/paid
     * state — the definition map itself is shared read-only (nodes are immutable after
     * construction). Free base nodes activate on construction.
     */
    public UpgradeTree(java.util.Map<String, UpgradeNode> definition) {
        this(definition, null);
    }

    /**
     * t131: per-machine tree with the legacy id table used by the one-time v0→v2 save migration
     * (docs/t131-t127t128-sync-plan.md §3.3 calculator tree / §3.4 storage tree). {@code null}
     * keeps the pre-t131 behaviour of loading node ids as-is.
     */
    public UpgradeTree(java.util.Map<String, UpgradeNode> definition, java.util.Map<String, String> legacyIdMap) {
        this.legacyIdMap = legacyIdMap;
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

    // ------------------------------------------------------------------
    // t131: persistence — treeVersion + double keys
    // ------------------------------------------------------------------

    /**
     * t131: writes the v2 schema ({@code treeVersion} = 2 + {@code activatedV2} / {@code paidV2},
     * the sole authority from v2 on) and re-emits the FROZEN pre-migration v0 payload
     * ({@code activated} / {@code paid}) verbatim when this machine had one.
     * <p>
     * ⚠ The legacy keys are never updated with v2 state — that is what makes the documented
     * rollback work: an old jar ignores {@code treeVersion}/{@code *V2} and reads the
     * pre-migration state, so the world survives the downgrade (post-migration progress is lost —
     * 非双向无损, docs §5.3). Machines created after t131 simply carry no legacy keys.
     */
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(KEY_VERSION, TREE_VERSION);
        // t131: v2 is the single authoritative state.
        NBTTagList list = new NBTTagList();
        for (String id : activated) {
            list.appendTag(new NBTTagString(id));
        }
        tag.setTag(KEY_ACTIVATED_V2, list);
        tag.setTag(KEY_PAID_V2, writePaidMap(paid));
        // t131: frozen legacy payload (rollback support); t61 keys used to live here verbatim.
        if (legacyActivated != null) {
            NBTTagList legacyList = new NBTTagList();
            for (String id : legacyActivated) {
                legacyList.appendTag(new NBTTagString(id));
            }
            tag.setTag(KEY_ACTIVATED_V0, legacyList);
        }
        if (legacyPaid != null) {
            tag.setTag(KEY_PAID_V0, writePaidMap(legacyPaid));
        }
    }

    /**
     * Loads the activation + paid state (unknown ids are ignored; free nodes stay active).
     * <p>
     * t131: {@code treeVersion >= 2} loads {@code activatedV2}/{@code paidV2} (the sole authority)
     * and keeps any frozen legacy payload present in the file byte-identical; anything older (or
     * a save written before t131, where the key is absent → v0) runs the one-time semantic
     * migration {@link #migrateLegacyState(NBTTagCompound)}.
     */
    @SuppressWarnings("unchecked")
    public void readFromNBT(NBTTagCompound tag) {
        activated.clear();
        paid.clear();
        int version = tag.hasKey(KEY_VERSION) ? tag.getInteger(KEY_VERSION) : 0;
        if (version >= TREE_VERSION) {
            // t131: v2 save — read the authoritative keys, filter unknown ids.
            if (tag.hasKey(KEY_ACTIVATED_V2)) {
                NBTTagList list = tag.getTagList(KEY_ACTIVATED_V2, 8); // NBTTagString
                for (int i = 0; i < list.tagCount(); i++) {
                    String id = list.getStringTagAt(i);
                    if (id != null && nodes.containsKey(id)) {
                        activated.add(id);
                    }
                }
            }
            if (tag.hasKey(KEY_PAID_V2)) {
                NBTTagCompound paidTag = tag.getCompoundTag(KEY_PAID_V2);
                for (String nodeId : (java.util.Set<String>) paidTag.func_150296_c()) {
                    if (!nodes.containsKey(nodeId)) continue;
                    paid.put(nodeId, readPaidNode(paidTag.getCompoundTag(nodeId)));
                }
            }
            // t131: keep a previously frozen legacy snapshot (rollback support) — verbatim, no
            // interpretation: it is dead weight for v2 and only an old jar ever reads it.
            if (tag.hasKey(KEY_ACTIVATED_V0) || tag.hasKey(KEY_PAID_V0)) {
                legacyActivated = readIdList(tag, KEY_ACTIVATED_V0);
                legacyPaid = readPaidMap(tag, KEY_PAID_V0);
            }
        } else {
            migrateLegacyState(tag);
        }
        // Free base nodes are always active.
        for (UpgradeNode node : nodes.values()) {
            if (node.isFree()) {
                activated.add(node.getId());
            }
        }
        reorderState();
    }

    /**
     * t131: one-time v0→v2 migration (docs/t131-t127t128-sync-plan.md §3.6). Pure function of the
     * legacy keys, so a re-read of an unsaved legacy payload migrates identically (no accumulation
     * across loads). Never resets progress: 只增不减.
     */
    private void migrateLegacyState(NBTTagCompound tag) {
        Set<String> oldActivated = readIdList(tag, KEY_ACTIVATED_V0);
        Map<String, Map<String, Integer>> oldPaid = readPaidMap(tag, KEY_PAID_V0);

        Set<String> mapped = new LinkedHashSet<>();
        List<String> unmapped = new ArrayList<>();
        for (String oldId : oldActivated) {
            String newId = mapLegacyId(oldId);
            if (newId == null) {
                if (!unmapped.contains(oldId)) unmapped.add(oldId);
                continue;
            }
            mapped.add(newId);
        }
        Map<String, Map<String, Integer>> mappedPaid = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : oldPaid.entrySet()) {
            String newId = mapLegacyId(e.getKey());
            if (newId == null) {
                if (!unmapped.contains(e.getKey())) unmapped.add(e.getKey());
                continue;
            }
            // §3.8: several legacy nodes may merge into ONE node — amounts are SUMMED, never
            // overwritten, refunded or silently dropped.
            Map<String, Integer> target = mappedPaid.computeIfAbsent(newId, key -> new LinkedHashMap<>());
            for (Map.Entry<String, Integer> m : e.getValue()
                .entrySet()) {
                if (m.getValue() == null || m.getValue() <= 0) continue;
                target.merge(m.getKey(), m.getValue(), Integer::sum);
            }
        }
        // §3.6: prerequisite closure — a node whose new prerequisites are stricter than the legacy
        // ones (H3 ← H2, OC ← {N4,T3,H3,P3}) has the missing prerequisite granted free of charge,
        // so 已解锁的节点不会被锁回去.
        closePrerequisites(mapped);
        capPaidToCost(mappedPaid);

        activated.addAll(mapped);
        paid.putAll(mappedPaid);

        // t131: freeze the pre-migration payload verbatim for old-jar rollback (docs §5.3).
        legacyActivated = oldActivated;
        legacyPaid = oldPaid;

        if (!legacyMigrationLogged) {
            legacyMigrationLogged = true;
            // Server-side log file only (docs §8.1 #6) — deliberately NO chat message.
            LOG.info(
                "[t131] upgrade tree v0→v2: activated {}→{}, paid nodes {}→{}, unmapped={}",
                oldActivated.size(),
                activated.size(),
                oldPaid.size(),
                mappedPaid.size(),
                unmapped);
        }
    }

    /** t131: legacy id → merged node id; {@code null} = unknown/not mappable (never guessed). */
    private String mapLegacyId(String oldId) {
        if (oldId == null) return null;
        String mapped = legacyIdMap == null ? oldId : legacyIdMap.get(oldId);
        if (mapped == null || !nodes.containsKey(mapped)) return null;
        return mapped;
    }

    /** t131: adds every missing prerequisite of an already-mapped node (transitively, free). */
    private void closePrerequisites(Set<String> mapped) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (UpgradeNode node : nodes.values()) {
                if (!mapped.contains(node.getId())) continue;
                for (String prereq : node.getPrerequisites()) {
                    if (prereq != null && !mapped.contains(prereq)) {
                        mapped.add(prereq);
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * t131 §3.8: truncates a migrated {@code paid} amount to the target node's requirement — two
     * legacy nodes that each held one iron now pay one merged node, and the tree costs one iron, so
     * the surplus is unreachable (it can never be spent anyway: the node activates as soon as the
     * cost is fulfilled). The amounts that cannot be represented are therefore not "swallowed
     * progress" but an artefact of the merge.
     */
    private void capPaidToCost(Map<String, Map<String, Integer>> mappedPaid) {
        for (Map.Entry<String, Map<String, Integer>> e : mappedPaid.entrySet()) {
            UpgradeNode node = nodes.get(e.getKey());
            if (node == null) continue;
            Map<String, Integer> cost = node.getMaterialCost();
            for (Map.Entry<String, Integer> m : e.getValue()
                .entrySet()) {
                Integer need = cost.get(m.getKey());
                if (need != null && m.getValue() > need) {
                    m.setValue(need);
                }
            }
        }
    }

    /** t131: rebuilds activated/paid in DEFINITION order (stable NBT output, no state change). */
    private void reorderState() {
        Set<String> orderedActivated = new LinkedHashSet<>();
        for (String id : nodes.keySet()) {
            if (activated.contains(id)) orderedActivated.add(id);
        }
        activated.clear();
        activated.addAll(orderedActivated);
        Map<String, Map<String, Integer>> orderedPaid = new LinkedHashMap<>();
        for (String id : nodes.keySet()) {
            Map<String, Integer> m = paid.get(id);
            if (m != null) orderedPaid.put(id, m);
        }
        paid.clear();
        paid.putAll(orderedPaid);
    }

    /** t131: reads an {@code activated}-style string list verbatim (no node filtering). */
    private static Set<String> readIdList(NBTTagCompound tag, String key) {
        Set<String> ids = new LinkedHashSet<>();
        if (!tag.hasKey(key)) return ids;
        NBTTagList list = tag.getTagList(key, 8); // NBTTagString
        for (int i = 0; i < list.tagCount(); i++) {
            String id = list.getStringTagAt(i);
            if (id != null && !id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    /** t131: reads a {@code paid}-style compound verbatim (no node filtering). */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Integer>> readPaidMap(NBTTagCompound tag, String key) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        if (!tag.hasKey(key)) return out;
        NBTTagCompound paidTag = tag.getCompoundTag(key);
        for (String nodeId : (java.util.Set<String>) paidTag.func_150296_c()) {
            out.put(nodeId, readPaidNode(paidTag.getCompoundTag(nodeId)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> readPaidNode(NBTTagCompound nodeTag) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String key : (java.util.Set<String>) nodeTag.func_150296_c()) {
            m.put(key, nodeTag.getInteger(key));
        }
        return m;
    }

    private static NBTTagCompound writePaidMap(Map<String, Map<String, Integer>> paid) {
        NBTTagCompound paidTag = new NBTTagCompound();
        for (Map.Entry<String, Map<String, Integer>> e : paid.entrySet()) {
            NBTTagCompound nodeTag = new NBTTagCompound();
            for (Map.Entry<String, Integer> m : e.getValue()
                .entrySet()) {
                nodeTag.setInteger(m.getKey(), m.getValue());
            }
            paidTag.setTag(e.getKey(), nodeTag);
        }
        return paidTag;
    }
}
