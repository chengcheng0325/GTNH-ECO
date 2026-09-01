package ecoaegtnh.item.ecalculator;

/**
 * t28: the nine E-Calculator flash-cell sizes (闪存晶阵), grouped into the three controller
 * tiers exactly like the E-Storage {@code CellSize} (t76, 9 尺寸对齐存储阵列): k-level
 * 256k / 1024k / 4096k → C4 hosts, M-level 16M / 64M / 256M → C6 hosts, big-M
 * 1024M / 4096M / 16384M → C9 hosts only.
 * <p>
 * Capacity follows the storage-disk system (存储盘 t76): k-level cells use
 * {@code totalBytes = value x 1024} (256k = 262,144 B), M / big-M cells keep the old ECO
 * {@code value x 1000 x 1024} (16M = 16,384,000 B ... 16384M = 16,777,216,000 B) — the size
 * order is strictly increasing across all nine sizes (256k &lt; 1024k &lt; 4096k &lt; 16M &lt;
 * 64M &lt; 256M &lt; 1024M &lt; 4096M &lt; 16384M).
 */
public enum CellSize {

    // k-level (TIER_C4): totalBytes = value x 1024
    K_256("256k", 256, true, 1, 0, 256L * 1024),
    K_1024("1024k", 1024, true, 2, 0, 1024L * 1024),
    K_4096("4096k", 4096, true, 4, 0, 4096L * 1024),
    // M-level (TIER_C6): totalBytes = value x 1000 x 1024
    M_16("16m", 16, false, 8, 1, 16L * 1000 * 1024),
    M_64("64m", 64, false, 16, 1, 64L * 1000 * 1024),
    M_256("256m", 256, false, 32, 1, 256L * 1000 * 1024),
    // big-M level (TIER_C9, C9 hosts only)
    M_1024("1024m", 1024, false, 64, 2, 1024L * 1000 * 1024),
    M_4096("4096m", 4096, false, 128, 2, 4096L * 1000 * 1024),
    M_16384("16384m", 16384, false, 256, 2, 16384L * 1000 * 1024),
    // t114 (user): Singularity flash cell (奇点闪存晶阵) — capacity = Long.MAX_VALUE, matching
    // the AE2U 奇点合成存储器 (BlockSingularityCraftingStorage.getStorageBytes). C9 hosts only,
    // value stays 16384 (same tier band as 16384m).
    SINGULARITY("singularity", 16384, false, 512, 2, Long.MAX_VALUE);

    /** Registry/lang/texture suffix ("256k", "16m", ...). */
    public final String label;
    /** Size value in its own unit (k for k-level, M for M-level). */
    public final int value;
    /** True for the k-level sizes (kilo-based totalBytes). */
    public final boolean kilo;
    /**
     * E-Storage parity (t76): perType = byteMultiplier x 1024 — reserved, the flash cell is a
     * pure byte pool (no AE perType math).
     */
    public final int byteMultiplier;
    /** Required host tier (ItemEcalCell.TIER_C4/C6/C9). */
    public final int tier;
    /** Total byte capacity. */
    public final long totalBytes;

    CellSize(String label, int value, boolean kilo, int byteMultiplier, int tier, long totalBytes) {
        this.label = label;
        this.value = value;
        this.kilo = kilo;
        this.byteMultiplier = byteMultiplier;
        this.tier = tier;
        this.totalBytes = totalBytes;
    }
}
