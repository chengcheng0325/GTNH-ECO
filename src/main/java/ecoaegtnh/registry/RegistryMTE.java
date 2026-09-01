package ecoaegtnh.registry;

import ecoaegtnh.metatileentity.MTEEcoStorageArray;

/**
 * Registers the E-Storage Array controller as a GT MetaTileEntity. t51 (milestone, docs
 * §4.2): the L4/L6/L9 tier machines are merged into ONE array (MTE 32030) — the milestone
 * system (item/fluid/essentia lines) provides the progression. MTE_ID_L6/MTE_ID_L9 are
 * deprecated (no longer registered; old world blocks migrate via the FML missing-ID flow).
 * Must be called during the FML load phase (GT's preload..postload window).
 */
public final class RegistryMTE {

    // MTE IDs must be < 32766 (server GT5U 5.09.54.20 array size).
    // 32030-32049 verified free in GT5U MetaTileEntityIDs enum (TecTech ends at 32029, GT_Framer starts at 32050).
    public static final int MTE_ID_ARRAY = 32030;
    /** @deprecated t51: no longer registered (single array controller). */
    @Deprecated
    public static final int MTE_ID_L6 = 32031;
    /** @deprecated t51: no longer registered (single array controller). */
    @Deprecated
    public static final int MTE_ID_L9 = 32032;

    /** t51: the single unified E-Storage Array controller. */
    public static MTEEcoStorageArray ARRAY;
    /** @deprecated t51: legacy alias of {@link #ARRAY} (kept for old references). */
    @Deprecated
    public static MTEEcoStorageArray L4;
    /** @deprecated t51: legacy alias of {@link #ARRAY} (kept for old references). */
    @Deprecated
    public static MTEEcoStorageArray L6;
    /** @deprecated t51: legacy alias of {@link #ARRAY} (kept for old references). */
    @Deprecated
    public static MTEEcoStorageArray L9;

    private RegistryMTE() {}

    public static void register() {
        ARRAY = new MTEEcoStorageArray(
            MTE_ID_ARRAY,
            "estorage.array",
            "ECO E-Storage Array",
            MTEEcoStorageArray.TIER_A);
        // Legacy tier aliases (Recipes / TAB_STORAGE still reference L4; L6/L9 keep non-null so
        // old references never NPE — the tier recipes themselves are reworked in the T51 pass).
        L4 = ARRAY;
        L6 = ARRAY;
        L9 = ARRAY;
        // t41: the TAB_STORAGE creative page lists the controllers directly from these fields;
        // the old controllerStacks array (machines tab) is gone.
    }
}
