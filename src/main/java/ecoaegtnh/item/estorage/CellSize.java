package ecoaegtnh.item.estorage;

/**
 * t76: the twelve E-Storage cell sizes, grouped into the three capacity bands (t122 naming:
 * the old L4/L6/L9 controller-tier names are gone; the bands are k / M / big-M and map 1:1 to
 * the Mk.I / Mk.II / Mk.III storage housings).
 * <p>
 * Band mapping (user-confirmed): k-level = 256k / 1024k / 4096k, M-level = 16M / 64M / 256M
 * (re-tiered — these were L4/L6/L9 before t76), big-M = 1024M / 4096M / 16384M (+ the t113
 * Artificial-Universe tier, also big-M; t114 adds the family-exclusive INF_WATER fluid cell and
 * ARCANE essentia cell, both big-M).
 * <p>
 * Capacity: k-level cells use {@code totalBytes = value x 1024} (256k = 262,144 B), M-level cells
 * keep the old ECO {@code value x 1000 x 1024} (16M = 16,384,000 B) — the size order is strictly
 * increasing (256k &lt; 1024k &lt; 4096k &lt; 16M &lt; 64M &lt; 256M &lt; 1024M &lt; 4096M &lt;
 * 16384M &lt; 576460752303423487B &lt; 9223372036854775807B). perType = byteMultiplier x 1024 with
 * the multiplier strictly doubling per size (t91: 1/2/4/8/16/32/64/128/256; t113: universe = 512).
 * <p>
 * t114: {@link #INF_WATER} and {@link #ARCANE} are FAMILY-EXCLUSIVE — {@link #allowed(StorageType)}
 * gates registration / creative tab / upgrade-tree nodes so only the fluid chain sees the infinite
 * water cell and only the essentia chain sees the arcane cell.
 */
public enum CellSize {

    // k-level band (band 0): totalBytes = value x 1024
    K_256("256k", 256, true, 1, 0, 256L * 1024),
    K_1024("1024k", 1024, true, 2, 0, 1024L * 1024),
    K_4096("4096k", 4096, true, 4, 0, 4096L * 1024),
    // M-level band (band 1, re-tiered t76): totalBytes = value x 1000 x 1024
    M_16("16m", 16, false, 8, 1, 16L * 1000 * 1024),
    M_64("64m", 64, false, 16, 1, 64L * 1000 * 1024),
    M_256("256m", 256, false, 32, 1, 256L * 1000 * 1024),
    // big-M level band (band 2)
    M_1024("1024m", 1024, false, 64, 2, 1024L * 1000 * 1024),
    M_4096("4096m", 4096, false, 128, 2, 4096L * 1000 * 1024),
    M_16384("16384m", 16384, false, 256, 2, 16384L * 1000 * 1024),
    // t113 (user): Artificial-Universe tier (big-M) — 2^59 - 1 bytes = 576,460,752,303,423,487 B
    // (512 PiB). value stays 16384 so idleDrain (= value/4 = 4096 AE/t) matches the 16384m cell;
    // the capacity itself is the explicit totalBytes constant.
    UNIVERSE("universe", 16384, false, 512, 2, 576_460_752_303_423_487L),
    // t114 (user): Infinite-Water fluid cell (AE2FC ItemInfinityWaterStorageCell 复刻) — FLUID
    // chain only, Long.MAX_VALUE bytes (= AE2U 奇点合成存储器 capacity, practically infinite),
    // only water is accepted (fixed config, see ItemEcoStorageCell), 1 type, no idle drain.
    INF_WATER("infwater", 16384, false, 512, 2, Long.MAX_VALUE) {

        @Override
        public boolean allowed(StorageType type) {
            return type == StorageType.FLUID;
        }
    },
    // t114 (user): Arcane essentia cell (TE4 创造源质元件复刻) — ESSENTIA chain only,
    // Long.MAX_VALUE bytes (practically infinite), every essentia aspect accepted, no idle drain.
    ARCANE("arcane", 16384, false, 512, 2, Long.MAX_VALUE) {

        @Override
        public boolean allowed(StorageType type) {
            return type == StorageType.ESSENTIA;
        }
    };

    /** Registry/lang/texture suffix ("256k", "16m", ...). */
    public final String label;
    /** Size value in its own unit (k for k-level, M for M-level). */
    public final int value;
    /** True for the k-level sizes (kilo-based totalBytes). */
    public final boolean kilo;
    /** t68-style byte multiplier; perType = multiplier x 1024. */
    public final int byteMultiplier;
    /** Required controller tier (MTEEcoStorageArray.TIER_A/B/C). */
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

    /**
     * t114: whether this size exists for the given storage family. Default: all families; the
     * family-exclusive sizes (INF_WATER → fluid, ARCANE → essentia) override this. Gates the
     * item registration, the creative tab and the upgrade-tree nodes.
     */
    public boolean allowed(StorageType type) {
        return true;
    }

    /**
     * t114d: 1-based index of this size WITHIN the given family's chain (sizes that are not
     * allowed for the family are skipped). E.g. ARCANE (enum ordinal 11) is the 11th size on
     * the essentia chain → 11 → upgrade node E11; INF_WATER is the 11th on the fluid chain → F11.
     * The upgrade-tree node id and the cell's required-node both derive from this, so they always
     * agree even though the enum ordinals are shared across families.
     */
    public int chainIndex(StorageType type) {
        int idx = 0;
        for (CellSize s : values()) {
            if (!s.allowed(type)) continue;
            idx++;
            if (s == this) return idx;
        }
        return 0;
    }

    /**
     * t76: idle drain — M-level keeps the t63/t68 MB/4 value (16M→4.0, 64M→16.0, ...); k-level
     * scales the same way (value/4000) with a 0.5 floor so the smallest cells still cost a token
     * amount (256k→0.5, 1024k→0.5, 4096k≈1.02). t114: the infinite family-exclusive cells
     * (INF_WATER/ARCANE) draw nothing, matching the AE2FC infinite water / TE4 creative cells.
     */
    public double idleDrain() {
        if (this == INF_WATER || this == ARCANE) {
            return 0;
        }
        if (kilo) {
            return Math.max(0.5, value / 4000.0);
        }
        return value / 4.0;
    }

    /** Capacity-band display label ("Mk.I"/"Mk.II"/"Mk.III") for messages. */
    public String tierLabel() {
        return tier == 2 ? "Mk.III" : tier == 1 ? "Mk.II" : "Mk.I";
    }

    /** M-equivalent size for display purposes (k-levels are sub-MB: 256k → 0, 1024k → 1). */
    public int capacityMB() {
        return kilo ? value / 1000 : value;
    }
}
