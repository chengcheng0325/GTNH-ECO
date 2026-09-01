package ecoaegtnh.milestone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * t49: the milestone system held by a machine — an ordered set of {@link MilestoneLine}s plus
 * the currently selected feed line (the GUI feed slots add material value to this line).
 * <p>
 * Calculator host (docs/ECO_MILESTONE_DESIGN.md §2): {@link #calculator()} = main cell line
 * (auto-upgrade) + parallel branch + thread branch. Storage array: {@link #storage()} =
 * item / fluid / essentia main lines (the array side is wired in T52; the framework supports
 * any line set). No reset / refund (不可重设) — there is no API to lower progress or levels.
 *
 * @deprecated t60: superseded by the upgrade-tree system (docs/ECO_UPGRADE_TREE_DESIGN.md) —
 *             the storage array still drives its cell gates from the milestone lines (storage tree lands
 *             in a later task); the calculator host keeps it only as the legacy GUI data source until the
 *             t61+ GUI migration. New code must use {@code ecoaegtnh.upgrade.UpgradeTree}.
 */
@Deprecated
public class MilestoneSystem {

    // Calculator line ids.
    public static final String LINE_MAIN_CELL = "main_cell";
    public static final String LINE_PARALLEL = "branch_parallel";
    public static final String LINE_THREAD = "branch_thread";
    // Storage line ids (T52).
    public static final String LINE_ITEM = "item";
    public static final String LINE_FLUID = "fluid";
    public static final String LINE_ESSENTIA = "essentia";

    private final Map<String, MilestoneLine> lines = new LinkedHashMap<>();
    private String currentLineId;

    private MilestoneSystem() {}

    /** Calculator host lines: main cell (auto), parallel branch, thread branch. */
    public static MilestoneSystem calculator() {
        MilestoneSystem s = new MilestoneSystem();
        s.lines.put(LINE_MAIN_CELL, new MilestoneLine(LINE_MAIN_CELL, "ecoaegtnh.milestone.line.main_cell"));
        s.lines.put(LINE_PARALLEL, new MilestoneLine(LINE_PARALLEL, "ecoaegtnh.milestone.line.parallel"));
        s.lines.put(LINE_THREAD, new MilestoneLine(LINE_THREAD, "ecoaegtnh.milestone.line.thread"));
        s.currentLineId = LINE_MAIN_CELL;
        return s;
    }

    /** Storage array lines: item / fluid / essentia (used by T52; framework-ready). */
    public static MilestoneSystem storage() {
        MilestoneSystem s = new MilestoneSystem();
        s.lines.put(LINE_ITEM, new MilestoneLine(LINE_ITEM, "ecoaegtnh.milestone.line.item"));
        s.lines.put(LINE_FLUID, new MilestoneLine(LINE_FLUID, "ecoaegtnh.milestone.line.fluid"));
        s.lines.put(LINE_ESSENTIA, new MilestoneLine(LINE_ESSENTIA, "ecoaegtnh.milestone.line.essentia"));
        s.currentLineId = LINE_ITEM;
        return s;
    }

    public MilestoneLine getLine(String id) {
        return lines.get(id);
    }

    public Collection<MilestoneLine> getLines() {
        return lines.values();
    }

    public MilestoneLine getCurrentLine() {
        MilestoneLine l = lines.get(currentLineId);
        return l != null ? l
            : lines.values()
                .iterator()
                .next();
    }

    public String getCurrentLineId() {
        return currentLineId;
    }

    /** Feed-line selector button: cycle to the next line in declaration order. */
    public void cycleCurrentLine() {
        List<String> ids = new ArrayList<>(lines.keySet());
        int i = ids.indexOf(currentLineId);
        currentLineId = ids.get((i + 1) % ids.size());
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("current", currentLineId);
        NBTTagCompound linesTag = new NBTTagCompound();
        for (Map.Entry<String, MilestoneLine> e : lines.entrySet()) {
            NBTTagCompound lt = new NBTTagCompound();
            e.getValue()
                .writeToNBT(lt);
            linesTag.setTag(e.getKey(), lt);
        }
        tag.setTag("lines", linesTag);
    }

    public void loadFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("lines")) {
            NBTTagCompound linesTag = tag.getCompoundTag("lines");
            for (Map.Entry<String, MilestoneLine> e : lines.entrySet()) {
                if (linesTag.hasKey(e.getKey())) {
                    e.getValue()
                        .readFromNBT(linesTag.getCompoundTag(e.getKey()));
                }
            }
        }
        if (tag.hasKey("current") && lines.containsKey(tag.getString("current"))) {
            currentLineId = tag.getString("current");
        }
    }
}
