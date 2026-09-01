package ecoaegtnh.ecalculator;

/**
 * Rolling-window usage recorder replacing MMCE's TimeRecorder (plan §7.5). Tracks per-tick values
 * (updateCraftingLogic µs, or started operations per tick) over a fixed window with average/peak.
 */
public class EcoTimeRecorder {

    private static final int WINDOW = 100;

    private long total = 0;
    private int count = 0;
    private int peak = 0;
    private int last = 0;

    public void addUsedTime(final int used) {
        if (used < 0) return;
        last = used;
        total += used;
        if (count < WINDOW) count++;
        if (used > peak) peak = used;
    }

    /** Rolling average over the window (0 when nothing recorded). */
    public int getAverage() {
        return count == 0 ? 0 : (int) (total / count);
    }

    public int getPeak() {
        return peak;
    }

    public int getLast() {
        return last;
    }

    public void reset() {
        total = 0;
        count = 0;
        peak = 0;
        last = 0;
    }
}
