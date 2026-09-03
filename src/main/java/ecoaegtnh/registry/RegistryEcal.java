package ecoaegtnh.registry;

import java.util.Arrays;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.block.ecalculator.BlockEcalCasing;
import ecoaegtnh.block.ecalculator.BlockEcalCellDrive;
import ecoaegtnh.block.ecalculator.BlockEcalMEChannel;
import ecoaegtnh.block.ecalculator.BlockEcalParallelDrive;
import ecoaegtnh.block.ecalculator.BlockEcalThreadDrive;
import ecoaegtnh.block.ecalculator.BlockEcalTransmitterBus;
import ecoaegtnh.item.ecalculator.CellSize;
import ecoaegtnh.item.ecalculator.ItemBlockEcal;
import ecoaegtnh.item.ecalculator.ItemEcalCell;
import ecoaegtnh.item.ecalculator.ItemEcalParallelCore;
import ecoaegtnh.item.ecalculator.ItemEcalThreadCore;
import ecoaegtnh.metatileentity.MTEEcalArray;
import ecoaegtnh.tile.ecalculator.TileEcalCellDrive;
import ecoaegtnh.tile.ecalculator.TileEcalMEChannel;
import ecoaegtnh.tile.ecalculator.TileEcalParallelDrive;
import ecoaegtnh.tile.ecalculator.TileEcalThreadDrive;

/**
 * Registers the E-Calculator part blocks, tile entities and the C4/C6/C9 controller MTEs. Naming
 * per plan §4.2 (user-confirmed): registration-name tier suffixes are {@code _c4/_c6/_c9}
 * (distinct from E-Storage's {@code _l4/_l6/_l9}). t28 reworks the flash cells from the 3-tier
 * {@code ecalculator_cell_c4/c6/c9} to nine size-style registrations
 * ({@code ecalculator_cell_256k ... ecalculator_cell_16384m}, k/M/big-M groups aligned with
 * E-Storage t76).
 * <p>
 * t35 (user decision): the parallel/thread/hyper CORE BLOCKS are removed entirely and replaced by
 * drive blocks + insertable core ITEMS — {@code ecalculator_parallel_drive} /
 * {@code ecalculator_thread_drive} (1 slot each) and 15 core items
 * ({@code ecal_parallel_core_1..65536} ×9, {@code ecal_thread_core_1/4/16} + hyper ×3), usable on
 * ANY controller tier (全档自由).
 * <p>
 * t41: all E-Calculator blocks/items register to {@link EcoAEGTNHCore#TAB_CALC} (the separate
 * TAB_ECAL_CORES tab is removed; TAB_CALC lists everything explicitly).
 * <p>
 * Legacy compat: {@code PARALLEL_PROCS/THREAD_CORES/HYPER_THREAD_CORES} and the singular
 * {@code parallelProc/threadCore} fields are kept (Recipes references them) and alias the new
 * drive instances.
 * <p>
 * Hook points (additive lines in {@link EcoAERegistry}): blocks in preInit (before the AE2 grid
 * is used), the MTE in init (GT preload..postload window, after {@link RegistryMTE}).
 */
public final class RegistryEcal {

    // MTE IDs must be < 32766 (server GT5U 5.09.54.20 array size). 32033-32049 verified free
    // (E-Storage uses 32030-32032; TecTech ends at 32029, GT_Framer starts at 32050).
    // t49 (milestone, docs/ECO_MILESTONE_DESIGN.md §2): the C4/C6/C9 tier controllers are merged
    // into ONE machine — MTE 32033 is the only E-Calculator controller. MTE_ID_C6/MTE_ID_C9 are
    // deprecated (no longer registered; old world blocks migrate via the FML missing-ID flow).
    public static final int MTE_ID_ARRAY = 32033;
    /** @deprecated t49: no longer registered (single controller). */
    @Deprecated
    public static final int MTE_ID_C6 = 32034;
    /** @deprecated t49: no longer registered (single controller). */
    @Deprecated
    public static final int MTE_ID_C9 = 32035;

    /**
     * t35: canonical drive instances. The legacy tier-indexed arrays
     * ({@code PARALLEL_PROCS/THREAD_CORES/HYPER_THREAD_CORES} — referenced by Recipes) alias
     * these (all three tier slots point at the single drive instance).
     */
    public static final BlockEcalParallelDrive[] PARALLEL_PROCS = new BlockEcalParallelDrive[3];
    public static final BlockEcalThreadDrive[] THREAD_CORES = new BlockEcalThreadDrive[3];
    public static final BlockEcalThreadDrive[] HYPER_THREAD_CORES = new BlockEcalThreadDrive[3];
    /** t28: canonical nine-size flash-cell registry (key = CellSize, registry name uses label). */
    public static final java.util.EnumMap<CellSize, ItemEcalCell> CELLS_BY_SIZE = new java.util.EnumMap<>(
        CellSize.class);
    /** t35: parallel core items by parallelism value. */
    public static final java.util.Map<Integer, ItemEcalParallelCore> PARALLEL_CORES = new java.util.HashMap<>();
    /** t35: thread core items by registry suffix ("1","4","16","hyper_2","hyper_4","hyper_8"). */
    public static final java.util.Map<String, ItemEcalThreadCore> THREAD_CORES_BY_SUFFIX = new java.util.HashMap<>();
    /**
     * Legacy tier-indexed aliases (TAB_CALC in EcoAEGTNHCore, Recipes): t28 keeps them pointing at
     * the direct size successors of the removed cell_c4/c6/c9 (64M→64m, 1024M→1024m,
     * 16384M→16384m) so existing references stay non-null and byte-identical until T29 syncs the
     * recipes. Note: a 64m cell is M-level (C6 gate) — the aliases are size successors, not
     * tier-group representatives.
     */
    public static final ItemEcalCell[] CELLS = new ItemEcalCell[3];
    public static final MTEEcalArray[] CONTROLLERS = new MTEEcalArray[3];

    /** Legacy singular fields (C4-era references: recipes, calc tab). */
    public static BlockEcalCasing casing;
    /** t35: the parallel-core drive (legacy name kept for Recipes R8; see {@link #parallelDrive}). */
    public static BlockEcalParallelDrive parallelProc;
    /** t35: the thread-core drive (legacy name kept for Recipes R8; see {@link #threadDrive}). */
    public static BlockEcalThreadDrive threadCore;
    public static BlockEcalCellDrive cellDrive;
    public static BlockEcalMEChannel meChannel;
    public static BlockEcalTransmitterBus transmitterBus;
    /** t35: canonical drive fields (the legacy parallelProc/threadCore alias these instances). */
    public static BlockEcalParallelDrive parallelDrive;
    public static BlockEcalThreadDrive threadDrive;
    /** t28: alias of CELLS[TIER_C4] (64m cell, successor of the removed 64M cell_c4). */
    public static ItemEcalCell cellC4;
    /** t49: the single unified E-Calculator controller (C4/C6/C9 merged). */
    public static MTEEcalArray ARRAY;
    /** @deprecated t49: legacy alias of {@link #ARRAY} (kept for old references). */
    @Deprecated
    public static MTEEcalArray C4;
    /** @deprecated t49: no longer registered (single controller). */
    @Deprecated
    public static MTEEcalArray C6;
    /** @deprecated t49: no longer registered (single controller). */
    @Deprecated
    public static MTEEcalArray C9;

    /** t35 legacy accessor: the parallel-core drive (tier index ignored — drives are tier-free). */
    public static BlockEcalParallelDrive parallelProc(int tier) {
        return PARALLEL_PROCS[0];
    }

    /** t35 legacy accessor: the thread-core drive (tier index ignored). */
    public static BlockEcalThreadDrive threadCore(int tier) {
        return THREAD_CORES[0];
    }

    /** t35 legacy accessor: the thread-core drive (hyper cores are now items, tier index ignored). */
    public static BlockEcalThreadDrive hyperThreadCore(int tier) {
        return HYPER_THREAD_CORES[0];
    }

    public static ItemEcalCell cell(int tier) {
        return CELLS[tier];
    }

    public static ItemEcalCell cell(CellSize size) {
        return CELLS_BY_SIZE.get(size);
    }

    private RegistryEcal() {}

    public static void registerBlocks() {
        // t14 (user feedback point 5): every functional block needs a hover tooltip, so the
        // blocks are registered here with the custom ItemBlockEcal (addInformation reads the
        // localized role+value keys). The block classes' static register() helpers use the
        // default ItemBlock — they are bypassed here (INSTANCE is assigned inline) so the
        // ItemBlock class can be specified; the helpers stay untouched.
        casing = new BlockEcalCasing();
        BlockEcalCasing.INSTANCE = casing;
        GameRegistry.registerBlock(casing, ItemBlockEcal.class, "ecalculator_casing");

        // t35: parallel-core drive + thread-core drive (replace the removed tiered core blocks).
        parallelDrive = new BlockEcalParallelDrive();
        BlockEcalParallelDrive.INSTANCE = parallelDrive;
        GameRegistry.registerBlock(parallelDrive, ItemBlockEcal.class, "ecalculator_parallel_drive");
        parallelProc = parallelDrive; // legacy alias (Recipes R8 / TAB_CALC)
        Arrays.fill(PARALLEL_PROCS, parallelDrive);

        threadDrive = new BlockEcalThreadDrive();
        BlockEcalThreadDrive.INSTANCE = threadDrive;
        GameRegistry.registerBlock(threadDrive, ItemBlockEcal.class, "ecalculator_thread_drive");
        threadCore = threadDrive; // legacy alias (Recipes R8 / TAB_CALC)
        Arrays.fill(THREAD_CORES, threadDrive);
        Arrays.fill(HYPER_THREAD_CORES, threadDrive);

        cellDrive = new BlockEcalCellDrive();
        BlockEcalCellDrive.INSTANCE = cellDrive;
        GameRegistry.registerBlock(cellDrive, ItemBlockEcal.class, "ecalculator_cell_drive");

        meChannel = new BlockEcalMEChannel();
        BlockEcalMEChannel.INSTANCE = meChannel;
        GameRegistry.registerBlock(meChannel, ItemBlockEcal.class, "ecalculator_me_channel");

        transmitterBus = new BlockEcalTransmitterBus();
        BlockEcalTransmitterBus.INSTANCE = transmitterBus;
        GameRegistry.registerBlock(transmitterBus, ItemBlockEcal.class, "ecalculator_transmitter_bus");

        // t28: flash cells — nine sizes in three tier groups (see the t28 javadoc above).
        for (CellSize size : CellSize.values()) {
            ItemEcalCell c = new ItemEcalCell(size);
            CELLS_BY_SIZE.put(size, c);
            GameRegistry.registerItem(c, "ecalculator_cell_" + size.label);
        }
        // Legacy aliases (Recipes / TAB_CALC): direct size successors of the removed cells.
        CELLS[ItemEcalCell.TIER_C4] = CELLS_BY_SIZE.get(CellSize.M_64);
        CELLS[ItemEcalCell.TIER_C6] = CELLS_BY_SIZE.get(CellSize.M_1024);
        CELLS[ItemEcalCell.TIER_C9] = CELLS_BY_SIZE.get(CellSize.M_16384);
        cellC4 = CELLS[ItemEcalCell.TIER_C4];

        // t35: insertable core items — 9 parallel (1/4/16/64/256/1024/4096/16384/65536, ×4) and
        // 6 thread (normal 1/4/16 + hyper 0+4/4+8/8+16, t114s doubling), all tiers free. They
        // register to EcoAEGTNHCore.TAB_CALC (t41; TAB_CALC lists them explicitly).
        // t128: the t114f 32/64-thread cores are REMOVED (no such cores exist; upgrade tree T4/T5
        // nodes are gone too — any ≥16-thread core maps onto the T3 node).
        for (int parallelism : ItemEcalParallelCore.SIZES) {
            ItemEcalParallelCore core = new ItemEcalParallelCore(parallelism);
            PARALLEL_CORES.put(parallelism, core);
            GameRegistry.registerItem(core, "ecal_parallel_core_" + parallelism);
        }
        registerThreadCore("1", 1, 0);
        registerThreadCore("4", 4, 0);
        registerThreadCore("16", 16, 0);
        // t114s (user): hyper core SUPPLIED thread counts doubled — hyper_2 = 0+4,
        // hyper_4 = 4+8, hyper_8 = 8+16 (registry suffix stays hyper_2/4/8).
        registerThreadCore("hyper_2", 0, 4);
        registerThreadCore("hyper_4", 4, 8);
        registerThreadCore("hyper_8", 8, 16);

        GameRegistry.registerTileEntity(TileEcalCellDrive.class, "ecoaegtnh.ecal_cell_drive");
        GameRegistry.registerTileEntity(TileEcalParallelDrive.class, "ecoaegtnh.ecal_parallel_drive");
        GameRegistry.registerTileEntity(TileEcalThreadDrive.class, "ecoaegtnh.ecal_thread_drive");
        GameRegistry.registerTileEntity(TileEcalMEChannel.class, "ecoaegtnh.ecal_me_channel");
    }

    private static void registerThreadCore(String suffix, int threads, int hyperThreads) {
        ItemEcalThreadCore core = new ItemEcalThreadCore(threads, hyperThreads, suffix);
        THREAD_CORES_BY_SUFFIX.put(suffix, core);
        GameRegistry.registerItem(core, "ecal_thread_core_" + suffix);
    }

    public static void registerMTE() {
        // Must run during the FML init phase (GT preload..postload window), AFTER RegistryMTE.
        // t49 (milestone): ONE unified controller — the C4/C6/C9 tier machines are merged into a
        // single "ECO 可扩展计算子系统主机" (display name without a tier suffix; the milestone
        // system provides the progression). MTE_ID_C6/MTE_ID_C9 are deprecated/unregistered.
        ARRAY = new MTEEcalArray(
            MTE_ID_ARRAY,
            "ecalculator.array",
            "ECO Extensible Calculator Subsystem Host",
            MTEEcalArray.TIER_C4);
        CONTROLLERS[MTEEcalArray.TIER_C4] = ARRAY;
        C4 = ARRAY; // legacy alias (tier index 0 = the unified machine)
        // Legacy tier aliases (Recipes t23 entries still reference C6/C9): they point at the
        // unified machine so recipe registration stays non-null; the tier recipes themselves
        // are reworked in the T51 milestone-gating pass.
        C6 = ARRAY;
        C9 = ARRAY;
        // t41: the TAB_CALC creative page lists the controllers directly from CONTROLLERS; the
        // old controllerStacks array (machines tab) is gone.
    }
}
