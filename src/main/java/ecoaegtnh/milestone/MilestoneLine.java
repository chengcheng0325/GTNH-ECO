package ecoaegtnh.milestone;

import net.minecraft.nbt.NBTTagCompound;

/**
 * t49: one milestone line — cumulative feed progress toward a level (5-level power law).
 * <p>
 * Model (docs/ECO_MILESTONE_DESIGN.md §3): the machine's GUI feed slots accept materials;
 * every insert adds the material's value to the SELECTED line's progress; when the cumulative
 * progress reaches the next level's threshold the line levels up (Lv1..Lv5). Levels unlock
 * content (cells / cores — the gates are wired in T51). There is NO reset / refund path
 * (不可重设, user decision) — this class deliberately exposes no way to decrease progress.
 *
 * @deprecated t60: superseded by the upgrade-tree system (docs/ECO_UPGRADE_TREE_DESIGN.md) —
 *             kept only as the legacy data source for the pre-migration milestone GUI windows until the
 *             t61+ GUI migration removes them. No gating code uses milestone levels anymore.
 */
@Deprecated
public class MilestoneLine {

    public static final int MAX_LEVEL = 5;

    /**
     * Lv1→Lv2 baseline (relative power law: Lv2=1×, Lv3=4×, Lv4=16×, Lv5=64× of this value —
     * {@link #requiredForLevel(int)}). Placeholder constant, to be tuned with the T50 material
     * value table during balancing.
     */
    public static final long BASE_COST = 10_000L;

    private final String id;
    private final String nameKey;
    private long progress = 0;
    private int level = 1;

    public MilestoneLine(String id, String nameKey) {
        this.id = id;
        this.nameKey = nameKey;
    }

    public String getId() {
        return id;
    }

    /** Lang key for the display name (e.g. {@code ecoaegtnh.milestone.line.main_cell}). */
    public String getNameKey() {
        return nameKey;
    }

    public int getLevel() {
        return level;
    }

    public long getProgress() {
        return progress;
    }

    public boolean isMaxLevel() {
        return level >= MAX_LEVEL;
    }

    /**
     * Cumulative threshold for reaching {@code lv}: power law relative to the Lv1→2 baseline —
     * Lv2 = 1×, Lv3 = 4×, Lv4 = 16×, Lv5 = 64× {@link #BASE_COST}. {@code lv<=1} → 0.
     */
    public static long requiredForLevel(int lv) {
        if (lv <= 1) return 0;
        return BASE_COST * (1L << (2 * (lv - 2)));
    }

    /** Threshold for the NEXT level (0 when already maxed). */
    public long getRequired() {
        return isMaxLevel() ? 0 : requiredForLevel(level + 1);
    }

    /**
     * Adds feed value to the cumulative progress; returns whether the line leveled up (possibly
     * multiple levels in one feed). No-op when maxed or value <= 0. Never decreases progress.
     */
    public boolean addProgress(long value) {
        if (value <= 0 || isMaxLevel()) return false;
        progress += value;
        boolean leveled = false;
        while (level < MAX_LEVEL && progress >= requiredForLevel(level + 1)) {
            level++;
            leveled = true;
        }
        return leveled;
    }

    /** Progress toward the NEXT level in percent (0..100; 100 when maxed). */
    public int getPercent() {
        if (isMaxLevel()) return 100;
        long req = requiredForLevel(level + 1);
        long base = level <= 1 ? 0 : requiredForLevel(level);
        if (req <= base) return 100;
        return (int) Math.min(100, (progress - base) * 100 / (req - base));
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong("progress", progress);
        tag.setInteger("level", level);
    }

    public void readFromNBT(NBTTagCompound tag) {
        progress = tag.getLong("progress");
        level = Math.max(1, Math.min(MAX_LEVEL, tag.getInteger("level")));
    }
}
