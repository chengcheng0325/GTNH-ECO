package ecoaegtnh.metatileentity;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import appeng.api.config.CraftingAllow;
import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ecoaegtnh.block.ecalculator.BlockEcalCasing;
import ecoaegtnh.block.ecalculator.BlockEcalCellDrive;
import ecoaegtnh.block.ecalculator.BlockEcalMEChannel;
import ecoaegtnh.block.ecalculator.BlockEcalParallelDrive;
import ecoaegtnh.block.ecalculator.BlockEcalThreadDrive;
import ecoaegtnh.block.ecalculator.BlockEcalTransmitterBus;
import ecoaegtnh.ecalculator.ECPUCluster;
import ecoaegtnh.ecalculator.EcoTimeRecorder;
import ecoaegtnh.tile.ecalculator.TileEcalCellDrive;
import ecoaegtnh.tile.ecalculator.TileEcalMEChannel;
import ecoaegtnh.tile.ecalculator.TileEcalParallelDrive;
import ecoaegtnh.tile.ecalculator.TileEcalThreadDrive;
import ecoaegtnh.upgrade.UpgradeNode;
import ecoaegtnh.upgrade.UpgradeTree;
import ecoaegtnh.upgrade.UpgradeTreeGui;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import tectech.thing.gui.TecTechUITextures;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

/**
 * ECO E-Calculator controller (GT multiblock), tiers C4/C6/C9 (C = Calculator; phase A registers
 * only C4, the class is tier-parameterized). A fixed 3脳3脳2 head (controller + ME channel + 16
 * casing) plus 1..12 extension segments (each 2-deep 脳 3-tall: 2 cell drives + 1 transmitter bus
 * on the front plane, 2 parallel cores + 1 thread core on the back plane), implemented with the
 * exact E-Storage machinery (StructureLib shapes, 12-shape checkMachine loop, GTStructureChannels
 * length, no-maintenance, pure AE power).
 * <p>
 * t17 (user decision): the extension direction is UNIFIED with E-Storage 鈥?with the controller's
 * front facing the player, the segments extend on the RIGHT-hand side (E-Storage t30 "鍒楀線鍙虫墿").
 * Shape layout: A=0..n-1 = the n segments, A=n..n+2 = head (A=n+1,B=1,C=0 is the controller anchor
 * '~', A=n,B=1,C=1 is the ME channel 'M' 鈥?back-plane right corner, same relative position as
 * E-Storage's ME bus, t30 semantics); C=0 = front plane, C=1 = back plane; B=0 top .. B=2 bottom.
 * The structure is checked with base offsets (n+1, 1, 0).
 */
public class MTEEcalArray extends TTMultiblockBase implements ISurvivalConstructable {

    public static final int TIER_C4 = 0;
    public static final int TIER_C6 = 1;
    public static final int TIER_C9 = 2;

    /** Structure piece name prefix; shapes "size1".."size12". */
    private static final String PIECE_PREFIX = "size";
    private static final int MAX_SEGMENTS = 12;

    private static final IStructureDefinition<MTEEcalArray> STRUCTURE_DEFINITION = buildDefinitions();

    /**
     * Cell-drive structure element (t15, E-Storage t25/t32 DriveElement pattern): accepts a drive
     * of ANY metadata (placed bays hold their horizontal facing 2-5), but PLACES it facing the
     * controller's front so the per-face front texture is visible on autoplaced machines.
     */
    private static final class DriveElement implements IStructureElement<MTEEcalArray> {

        @Override
        public boolean check(MTEEcalArray t, World world, int x, int y, int z) {
            return world.getBlock(x, y, z) == BlockEcalCellDrive.INSTANCE;
        }

        @Override
        public boolean spawnHint(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger) {
            com.gtnewhorizon.structurelib.StructureLibAPI.hintParticle(world, x, y, z, BlockEcalCellDrive.INSTANCE, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger) {
            world.setBlock(x, y, z, BlockEcalCellDrive.INSTANCE, facingToDriveMeta(t), 2);
            return true;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger,
            com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            // The item placement path triggers BlockEcalCellDrive.onBlockPlacedBy, which derives
            // the facing from the player's look direction (natural when building by hand).
            return StructureUtility.survivalPlaceBlock(
                BlockEcalCellDrive.INSTANCE,
                facingToDriveMeta(t),
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger,
            com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment env) {
            return BlocksToPlace.create(BlockEcalCellDrive.INSTANCE, facingToDriveMeta(t));
        }

        /** Drive metadata facing matching the controller's front (2=N, 3=S, 4=W, 5=E). */
        private static int facingToDriveMeta(MTEEcalArray t) {
            ForgeDirection dir = t.getExtendedFacing()
                .getDirection();
            if (dir == ForgeDirection.SOUTH) return BlockEcalCellDrive.META_SOUTH;
            if (dir == ForgeDirection.WEST) return BlockEcalCellDrive.META_WEST;
            if (dir == ForgeDirection.EAST) return BlockEcalCellDrive.META_EAST;
            return BlockEcalCellDrive.META_NORTH;
        }

        /**
         * t39: reversed drive metadata facing 鈥?180掳 from the controller's front (NORTH鈫擲OUTH,
         * EAST鈫擶EST swap). t44 鑼冨洿淇锛氫粎鑷姩鏀剧疆锛堢粨鏋勫伐鍏凤級淇濇寔姝ゅ弽杞紙缃戞牸/鑺墖闈㈡湞缁撴瀯
         * 鑳岄潰锛屼笉琚墠鎺掓櫠闃甸┍鍔ㄥ櫒閬尅锛夛紱鎵嬪姩鏀剧疆宸叉仮澶?vanilla 姝ｅ悜锛堣 Block*Drive.onBlockPlacedBy锛夈€?
         */
        private static int facingToDriveMetaReversed(MTEEcalArray t) {
            return reverseDriveMeta(facingToDriveMeta(t));
        }

        /** t39: swap a horizontal facing meta by 180掳 (2鈫?, 4鈫?). */
        private static int reverseDriveMeta(int meta) {
            if (meta == BlockEcalCellDrive.META_NORTH) return BlockEcalCellDrive.META_SOUTH;
            if (meta == BlockEcalCellDrive.META_SOUTH) return BlockEcalCellDrive.META_NORTH;
            if (meta == BlockEcalCellDrive.META_WEST) return BlockEcalCellDrive.META_EAST;
            if (meta == BlockEcalCellDrive.META_EAST) return BlockEcalCellDrive.META_WEST;
            return meta;
        }
    }

    /**
     * t35: parallel-core drive element 鈥?accepts a {@link BlockEcalParallelDrive} of ANY metadata
     * (drives hold their horizontal facing 2-5) and places it facing away from the controller's
     * front (t39/t44 鑼冨洿淇: auto-placement keeps the 180掳 reversal 鈥?grid/chip face points to
     * the structure's back; only manual placement uses the vanilla forward facing). The
     * inserted core item (not the block) carries the parallelism value; no tier gate (鍏ㄦ。鑷敱).
     */
    private static final class ParallelDriveElement implements IStructureElement<MTEEcalArray> {

        @Override
        public boolean check(MTEEcalArray t, World world, int x, int y, int z) {
            return world.getBlock(x, y, z) instanceof BlockEcalParallelDrive;
        }

        @Override
        public boolean spawnHint(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger) {
            com.gtnewhorizon.structurelib.StructureLibAPI
                .hintParticle(world, x, y, z, BlockEcalParallelDrive.INSTANCE, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger) {
            world.setBlock(x, y, z, BlockEcalParallelDrive.INSTANCE, DriveElement.facingToDriveMetaReversed(t), 2);
            return true;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger,
            com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            return StructureUtility.survivalPlaceBlock(
                BlockEcalParallelDrive.INSTANCE,
                DriveElement.facingToDriveMetaReversed(t),
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger,
            com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment env) {
            return BlocksToPlace.create(BlockEcalParallelDrive.INSTANCE, DriveElement.facingToDriveMetaReversed(t));
        }
    }

    /**
     * t35: thread-core drive element 鈥?accepts a {@link BlockEcalThreadDrive} of ANY metadata and
     * places it facing away from the controller's front (t39/t44 鑼冨洿淇: auto-placement keeps
     * the 180掳 reversal 鈥?chip face points to the structure's back; only manual placement uses the
     * vanilla forward facing). The inserted core item (not the block) carries the thread slots;
     * no tier gate (鍏ㄦ。鑷敱).
     */
    private static final class ThreadDriveElement implements IStructureElement<MTEEcalArray> {

        @Override
        public boolean check(MTEEcalArray t, World world, int x, int y, int z) {
            return world.getBlock(x, y, z) instanceof BlockEcalThreadDrive;
        }

        @Override
        public boolean spawnHint(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger) {
            com.gtnewhorizon.structurelib.StructureLibAPI
                .hintParticle(world, x, y, z, BlockEcalThreadDrive.INSTANCE, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger) {
            world.setBlock(x, y, z, BlockEcalThreadDrive.INSTANCE, DriveElement.facingToDriveMetaReversed(t), 2);
            return true;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger,
            com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            return StructureUtility.survivalPlaceBlock(
                BlockEcalThreadDrive.INSTANCE,
                DriveElement.facingToDriveMetaReversed(t),
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTEEcalArray t, World world, int x, int y, int z, ItemStack trigger,
            com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment env) {
            return BlocksToPlace.create(BlockEcalThreadDrive.INSTANCE, DriveElement.facingToDriveMetaReversed(t));
        }
    }

    private static IStructureDefinition<MTEEcalArray> buildDefinitions() {
        StructureDefinition.Builder<MTEEcalArray> builder = StructureDefinition.<MTEEcalArray>builder()
            // Pure AE power: the structure contains no GT energy hatches (plan 搂5.2 / 搂13 d6).
            .addElement('C', ofBlock(BlockEcalCasing.INSTANCE, 0))
            // t15: cell drives carry a horizontal facing in metadata 2-5 (per-face rendering) 鈥?
            // the element accepts ANY drive metadata and places bays facing the controller's
            // front (E-Storage t25/t32 DriveElement pattern).
            .addElement('D', new DriveElement())
            .addElement('B', ofBlock(BlockEcalTransmitterBus.INSTANCE, 0))
            // t35: drive elements 鈥?'P' = parallel-core drive, 'T' = thread-core drive (the old
            // tier-matched core blocks are gone; any drive/inserted item works on any tier).
            .addElement('P', new ParallelDriveElement())
            .addElement('T', new ThreadDriveElement())
            .addElement('M', ofBlock(BlockEcalMEChannel.INSTANCE, 0));

        // Shape axes (StructureLib): outer String[] = C slices (front-back), inner string = B lines
        // (top-bottom), chars = A (left-right). Controller anchor '~' at (A=n+1, B=1, C=0); the
        // structure is checked with base offsets (n+1, 1, 0).
        // Layout (t17, unified with E-Storage t30: with the controller's front facing the player,
        // the segments extend on the RIGHT-hand side 鈥?A- direction, same as E-Storage's columns):
        // A=0..n-1 (segments 1..n, right of the head): C=0 = D/B/D (cell drives + transmitter bus),
        // C=1 = P/T/P (t35: parallel-core drives + thread-core drive 鈥?same positions as the old
        // tiered core blocks, now drive blocks holding insertable core items)
        // A=n (head column adjacent to the segments, column side): C=1 plane has the ME channel
        // at B=1 (back-plane right corner, E-Storage ME-bus relative position)
        // A=n+1 (controller column): C=0 plane has the controller at B=1, rest casing
        // A=n+2 (head outer column): all casing
        for (int n = 1; n <= MAX_SEGMENTS; n++) {
            // Per B line, chars A=0..n+2: A=0..n-1 = segments, A=n..n+2 = head.
            // (Java 8 runtime: no String.repeat, so use the local fill helper.)
            String headRow = "CCC"; // all-casing B row over the head columns
            String frontTopRow = repeat('D', n) + headRow; // C=0, B=0 (drive top)
            String frontMidRow = repeat('B', n) + "C~C"; // C=0, B=1 (transmitter buses + controller)
            String frontBotRow = repeat('D', n) + headRow; // C=0, B=2 (drive bottom)
            String backTopRow = repeat('P', n) + headRow; // C=1, B=0 (parallel top)
            String backMidRow = repeat('T', n) + "MCC"; // C=1, B=1 (thread cores + ME channel)
            String backBotRow = repeat('P', n) + headRow; // C=1, B=2 (parallel bottom)
            String[][] slices = { { frontTopRow, frontMidRow, frontBotRow }, { backTopRow, backMidRow, backBotRow } };
            builder.addShape(PIECE_PREFIX + n, slices);
        }
        return builder.build();
    }

    /** Fills a char n times (Java 8-safe replacement for {@code String.repeat}). */
    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

    protected final int tier;

    // Parts collected during structure check (t35: cell drives + parallel drives + thread drives
    // + ME channel 鈥?the parallel/thread CORE blocks became insertable items in the drives).
    protected final List<TileEcalCellDrive> cellDrives = new ArrayList<>();
    protected final List<TileEcalParallelDrive> parallelDrives = new ArrayList<>();
    protected final List<TileEcalThreadDrive> threadCores = new ArrayList<>();
    protected TileEcalMEChannel channel = null;
    protected int segmentLength = 0;
    /** Previously assembled parts, to disassemble the ones that disappear. */
    protected final List<TileEcalCellDrive> prevCellDrives = new ArrayList<>();
    protected final List<TileEcalParallelDrive> prevParallelDrives = new ArrayList<>();
    protected final List<TileEcalThreadDrive> prevThreadCores = new ArrayList<>();
    protected TileEcalMEChannel prevChannel = null;

    /** Computed AE idle power usage (AE/t); pushed to the ME channel proxy (plan 搂5.2). */
    private double idlePowerUsage = 0;

    // ------------------------------------------------------------------
    // Phase C1: GUI sync targets (E-Storage t58 pattern). The MUI1 text
    // suppliers run CLIENT-side, where the part lists are empty 鈥?the
    // FakeSyncWidget setters write the server-synced values into these
    // fields and the suppliers read them (direct reads showed 0).
    // ------------------------------------------------------------------
    private boolean syncStructureValid = false;
    private boolean syncChannelActive = false;
    private int syncThreadCoreCount = 0;
    private int syncParallelism = 0;
    private long syncTotalBytes = 0;
    private long syncAvailableBytes = 0;
    private boolean syncRedLineTriggered = false;
    private boolean syncStandbyVCPU = false;
    private int syncActiveTaskCount = 0;
    // t45: thread-row sync targets 鈥?normal used/total + hyper used/total.
    private int syncThreadsUsed = 0;
    private int syncThreadsTotal = 0;
    private int syncHyperUsed = 0;
    private int syncHyperTotal = 0;
    // t114g: built-in thread-row sync targets (separate display).
    private int syncBuiltinThreadsUsed = 0;
    private int syncBuiltinThreadsTotal = 0;
    private int syncBuiltinHyperUsed = 0;
    private int syncBuiltinHyperTotal = 0;
    private double syncIdlePowerUsage = 0;

    /**
     * t65: byte-pool cap by the activated cell main-chain node (docs §2 revision) — N2 (256k) =
     * 12M pool, N3 (1024k) = 64M, N4 (4096k) = 256M, N5 (16M) = 1G, N6 (64M) = 4G, N7 (256M) =
     * 16G, N8 (1024M) = 64G, N9 (4096M) = 256G, N10 (16384M) = unlimited (Long.MAX_VALUE).
     */
    private static final long[] BYTE_POOL_CAP = { 12_000_000L, 64_000_000L, 256_000_000L, 1_000_000_000L,
        4_000_000_000L, 16_000_000_000L, 64_000_000_000L, 256_000_000_000L, Long.MAX_VALUE };

    /**
     * t65: the upgrade tree (docs/ECO_UPGRADE_TREE_DESIGN.md) — 26 nodes (single cell main chain
     * N1-N10 + thread/parallel branches + hyper branch + overclock OC), activation state
     * persisted in NBT ("upgradeTree" key). Drives gate insertions by node activation; OC drives
     * the overclock mode (red line 5% + free hyper threads) and the byte-pool cap follows the
     * cell main chain.
     */
    protected final ecoaegtnh.upgrade.UpgradeTree upgradeTree = ecoaegtnh.upgrade.CalculatorUpgradeTree.newInstance();

    /** t61: node opened in the detail/material windows (server authority). */
    protected String selectedUpgradeNode = null;

    /** t61: 16-slot material staging area (consumed by the submit action; not persisted). */
    protected final ItemStack[] upgradeStaging = new ItemStack[16];

    /** t61: MUI1-visible handler over the staging slots. */
    protected final ItemStackHandler upgradeStagingHandler = new ItemStackHandler(upgradeStaging);

    // t61 GUI sync targets (client-side fields; server suppliers pack the tree state).
    private String syncUpgradeActivated = "";
    private String syncUpgradeSelected = "";
    private String syncUpgradePaid = "";

    // ------------------------------------------------------------------
    // Phase B: computation core state (plan 搂7)
    // ------------------------------------------------------------------

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager
        .getLogger("ECOAEGTNH");

    /** Sum of installed parallel-core parallelism (C4=256/core), counted at structure scan. */
    private int parallelismTotal = 0;
    /**
     * t114i: released vCPU numbers, re-issued smallest-first — the minimum-available number
     * pool. Every RUNNING vCPU holds exactly one number ("ECO vCPU #id", ids consecutive from
     * 1 while several run at once); cluster destroy returns the number here. The standby vCPU
     * never takes a number (id 0), so standby refill churn can not inflate the pool.
     */
    private final PriorityQueue<Integer> freeVCPUIds = new PriorityQueue<>();
    /** t114i: highest vCPU number issued so far — grows only while no released number is free. */
    private int vcpuIdCounter = 0;
    /** Sum of installed cell-drive bytes (C4 cell = 65,536,000 AE bytes). */
    private long totalBytes = 0;
    /** Standby vCPU awaiting a job (null while none, or while destroyed). */
    private CraftingCPUCluster virtualCPU = null;

    /**
     * t114g (user): BUILT-IN thread slots — the machine provides 1 normal thread by itself
     * (upgrade node B1 adds +3 → 4, B2 adds +2 built-in hyper threads). Clusters assigned to a
     * built-in slot live in these lists (no thread drive owns them); they are released by the
     * cluster destroy hook ({@link #onClusterReleased}).
     */
    private final java.util.List<CraftingCPUCluster> builtinThreadClusters = new ArrayList<>();
    private final java.util.List<CraftingCPUCluster> builtinHyperClusters = new ArrayList<>();

    public MTEEcalArray(int aID, String aName, String aNameRegional, int tier) {
        super(aID, aName, aNameRegional);
        this.tier = tier;
    }

    public MTEEcalArray(String aName, int tier) {
        super(aName);
        this.tier = tier;
    }

    // ------------------------------------------------------------------
    // No maintenance (E-Storage t44/t37 pattern)
    // ------------------------------------------------------------------

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * t37 注记（284 移植版）：原 supportsMaintenanceIssueHoverable()/showMachineStatusInGUI()
     * 覆写在 GT5U 5.09.51.482 中不存在（5.09.54 新增）——2.8.4 下终端维护悬停与 GUI 状态行
     * 回到默认行为，见 移植报告.md 已知差异。
     */

    public int getTier() {
        return tier;
    }

    public List<TileEcalCellDrive> getCellDrives() {
        return cellDrives;
    }

    public List<TileEcalThreadDrive> getThreadCores() {
        return threadCores;
    }

    public TileEcalMEChannel getChannel() {
        return channel;
    }

    public int getSegmentLength() {
        return segmentLength;
    }

    public boolean isStructureValid() {
        return mMachine;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        MTEEcalArray inst = new MTEEcalArray(this.mName, this.tier);
        ecoaegtnh.EcoaegtnhLifecycleHooks.registerController(inst);
        return inst;
    }

    // ------------------------------------------------------------------
    // IAlignment: horizontal only, no rotation/flip (E-Storage t35 pattern 鈥?do NOT override
    // getExtendedFacing/setExtendedFacing, the base manages them).
    // ------------------------------------------------------------------

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && f.isNotFlipped();
    }

    // ------------------------------------------------------------------
    // Structure check
    // ------------------------------------------------------------------

    /**
     * t5 F3 (new implementation, reference S:EPartController.java:39-51/102-113): refuses to form
     * when another E-Calculator controller exists directly above or below (1..2 blocks).
     */
    private boolean checkControllerShared(IGregTechTileEntity base) {
        World world = base.getWorld();
        for (int dy = 1; dy <= 2; dy++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                TileEntity te = world.getTileEntity(base.getXCoord(), base.getYCoord() + sign * dy, base.getZCoord());
                if (te instanceof IGregTechTileEntity igte && igte.getMetaTileEntity() instanceof MTEEcalArray) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 284 移植版：5.09.51.482 无 structure.error API——checkMachine 唯一钩子
     * checkMachine_EM（布尔返回），具体错误文案丢失（见 移植报告.md 已知差异）。
     */
    @Override
    protected boolean checkMachine_EM(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        if (!checkControllerShared(aBaseMetaTileEntity)) {
            disassembleAll();
            return false;
        }
        cellDrives.clear();
        parallelDrives.clear();
        threadCores.clear();
        channel = null;
        segmentLength = 0;
        parallelismTotal = 0;

        boolean ok = false;
        for (int n = MAX_SEGMENTS; n >= 1; n--) {
            // Controller anchor '~' sits at shape (A=n+1, B=1, C=0), so the structure is checked
            // with base offsets (n+1, 1, 0); the n segments extend from A=0 to A=n-1 (right-hand
            // side, unified with E-Storage t30).
            if (STRUCTURE_DEFINITION.check(
                this,
                PIECE_PREFIX + n,
                aBaseMetaTileEntity.getWorld(),
                getExtendedFacing(),
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord(),
                n + 1,
                1,
                0,
                true)) {
                segmentLength = n;
                ok = true;
                break;
            }
        }
        if (!ok) {
            disassembleAll();
            return false;
        }
        return scanStructureVolume(aBaseMetaTileEntity);
    }

    /**
     * Iterates every cell of the matched shape using the same facing-relative conversion as the
     * structure check, and collects the part tiles (cell drives, thread cores, ME channel).
     * 284：布尔返回（5.09.51.482 无错误列表 API）。
     */
    private boolean scanStructureVolume(IGregTechTileEntity base) {
        int aMax = segmentLength + 2;
        int offsetA = segmentLength + 1;
        int[] abc = new int[3];
        int[] xyz = new int[3];
        int baseX = base.getXCoord();
        int baseY = base.getYCoord();
        int baseZ = base.getZCoord();
        com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing facing = getExtendedFacing();
        for (int a = 0; a <= aMax; a++) {
            for (int b = 0; b <= 2; b++) {
                for (int c = 0; c <= 1; c++) {
                    if (a == offsetA && b == 1 && c == 0) continue; // controller itself
                    abc[0] = a - offsetA;
                    abc[1] = b - 1;
                    abc[2] = c;
                    facing.getWorldOffset(abc, xyz);
                    int wx = baseX + xyz[0];
                    int wy = baseY + xyz[1];
                    int wz = baseZ + xyz[2];
                    TileEntity te = base.getWorld()
                        .getTileEntity(wx, wy, wz);
                    if (te instanceof TileEcalCellDrive drive) {
                        // No sharing: a drive already claimed by another controller is excluded.
                        if (drive.onAssembled(this)) cellDrives.add(drive);
                    } else if (te instanceof TileEcalParallelDrive parallelDrive) {
                        if (parallelDrive.onAssembled(this)) parallelDrives.add(parallelDrive);
                    } else if (te instanceof TileEcalThreadDrive core) {
                        if (core.onAssembled(this)) threadCores.add(core);
                    } else if (te instanceof TileEcalMEChannel ch) {
                        if (!ch.onAssembled(this)) {
                            disassembleAll();
                            return false;
                        }
                        if (channel == null) channel = ch;
                        else {
                            disassembleAll();
                            return false;
                        }
                    }
                    // t35: parallelism comes from the core ITEMS inserted into the parallel drives
                    // (see below); the old tiered parallel-proc block counting is gone.
                    // Pure AE power — no GT energy hatches in the structure (plan §5.2).
                }
            }
        }
        // t35: parallelism total = Σ inserted parallel cores across all parallel drives.
        parallelismTotal = 0;
        for (TileEcalParallelDrive drive : parallelDrives) {
            parallelismTotal += drive.getSuppliedParallelism();
        }
        if (cellDrives.isEmpty() || channel == null) {
            disassembleAll();
            return false;
        }

        // Disassemble parts that are no longer present; assemble new ones.
        for (TileEcalCellDrive old : prevCellDrives) {
            if (!cellDrives.contains(old)) old.onDisassembled();
        }
        for (TileEcalParallelDrive old : prevParallelDrives) {
            if (!parallelDrives.contains(old)) old.onDisassembled();
        }
        for (TileEcalThreadDrive old : prevThreadCores) {
            if (!threadCores.contains(old)) old.onDisassembled();
        }
        if (prevChannel != null && prevChannel != channel) prevChannel.onDisassembled();
        for (TileEcalCellDrive drive : cellDrives) {
            if (!prevCellDrives.contains(drive)) drive.onAssembled(this);
        }
        for (TileEcalParallelDrive drive : parallelDrives) {
            if (!prevParallelDrives.contains(drive)) drive.onAssembled(this);
        }
        for (TileEcalThreadDrive core : threadCores) {
            if (!prevThreadCores.contains(core)) core.onAssembled(this);
        }
        if (prevChannel != channel) channel.onAssembled(this);

        prevCellDrives.clear();
        prevCellDrives.addAll(cellDrives);
        prevParallelDrives.clear();
        prevParallelDrives.addAll(parallelDrives);
        prevThreadCores.clear();
        prevThreadCores.addAll(threadCores);
        prevChannel = channel;
        recalculateIdlePower();
        // t9: computation-core state — parallelism, byte pool, standby vCPU.
        recalculateParallelism();
        recalculateTotalBytes();
        createVirtualCPU();
        return true;
    }

    /**
     * KEEP-mode teardown (t122, user): the controller was unformed/disassembled but the machine
     * block stays — in-flight vCPU jobs are NOT cancelled/destroyed. The channel proxy is NOT
     * invalidated: the grid node must stay alive so the jobs keep their data (they freeze and
     * resume when the machine forms again). The vCPU number pool is NOT reset (running clusters
     * still hold their numbers). Full refund teardown lives in {@link #disassembleAllRefund()}.
     */
    protected void disassembleAll() {
        destroyStandbyVCPU();
        // External thread drives: release the controller reference but keep their clusters/jobs.
        for (TileEcalThreadDrive core : threadCores) {
            core.onControllerDisassembledKeepJobs();
        }
        for (TileEcalThreadDrive old : prevThreadCores) {
            if (!threadCores.contains(old)) old.onControllerDisassembledKeepJobs();
        }
        // Channel: release the controller reference but DO NOT invalidate the proxy (grid node
        // stays so in-flight vCPU jobs keep their data; re-form re-claims it via onAssembled).
        if (channel != null) {
            channel.onControllerDisassembledKeepProxy();
        }
        if (prevChannel != null && prevChannel != channel) prevChannel.onControllerDisassembledKeepProxy();
        parallelismTotal = 0;
        totalBytes = 0;
        // t122: the vCPU number pool is NOT reset — running clusters still hold their numbers.
        for (TileEcalCellDrive drive : cellDrives) {
            drive.onDisassembled();
        }
        for (TileEcalCellDrive old : prevCellDrives) {
            if (!cellDrives.contains(old)) old.onDisassembled();
        }
        for (TileEcalParallelDrive drive : parallelDrives) {
            drive.onDisassembled();
        }
        for (TileEcalParallelDrive old : prevParallelDrives) {
            if (!parallelDrives.contains(old)) old.onDisassembled();
        }
        for (TileEcalThreadDrive core : threadCores) {
            core.onDisassembled();
        }
        for (TileEcalThreadDrive old : prevThreadCores) {
            if (!threadCores.contains(old)) old.onDisassembled();
        }
        prevCellDrives.clear();
        prevParallelDrives.clear();
        prevThreadCores.clear();
        prevChannel = null;
    }

    /**
     * REFUND teardown for machine removal (block broken) / server stopping: cancel every
     * in-flight cluster (AE2U cancel() refunds the job's materials into the grid via postChange)
     * then destroy. Clusters whose cancel fails or whose grid is unreachable (disconnected
     * network) are NOT destroyed — they are adopted as orphans (kept alive with their materials)
     * and resume/refund automatically once any live grid drives them again.
     */
    protected void disassembleAllRefund() {
        // t5 F2 (phase B): controller teardown — destroy the standby vCPU, cancel/destroy every
        // in-flight cluster per thread core, then notify the grid (no NPE/leak: the M1 destroy
        // mixin routes each destroy to its core's onCPUDestroyed, which unregisters and notifies).
        destroyStandbyVCPU();
        for (CraftingCPUCluster cluster : new java.util.ArrayList<>(builtinThreadClusters)) {
            cancelAndDestroyBuiltin(cluster);
        }
        for (CraftingCPUCluster cluster : new java.util.ArrayList<>(builtinHyperClusters)) {
            cancelAndDestroyBuiltin(cluster);
        }
        builtinThreadClusters.clear();
        builtinHyperClusters.clear();
        for (TileEcalThreadDrive core : threadCores) {
            core.onControllerDisassembled();
        }
        for (TileEcalThreadDrive old : prevThreadCores) {
            if (!threadCores.contains(old)) old.onControllerDisassembled();
        }
        if (channel != null) {
            channel.postCPUClusterChangeEvent();
        }
        parallelismTotal = 0;
        totalBytes = 0;
        // t114i: teardown destroyed every cluster — reset the vCPU number pool (the per-cluster
        // release hooks above are individually idempotent; a full reset is simpler and exact).
        freeVCPUIds.clear();
        vcpuIdCounter = 0;
        for (TileEcalCellDrive drive : cellDrives) {
            drive.onDisassembled();
        }
        for (TileEcalCellDrive old : prevCellDrives) {
            if (!cellDrives.contains(old)) old.onDisassembled();
        }
        for (TileEcalParallelDrive drive : parallelDrives) {
            drive.onDisassembled();
        }
        for (TileEcalParallelDrive old : prevParallelDrives) {
            if (!parallelDrives.contains(old)) old.onDisassembled();
        }
        for (TileEcalThreadDrive core : threadCores) {
            core.onDisassembled();
        }
        for (TileEcalThreadDrive old : prevThreadCores) {
            if (!threadCores.contains(old)) old.onDisassembled();
        }
        // t122c (user): KEEP the channel proxy alive — an orphan (adopted because the refund
        // could not land on a disconnected grid) is only re-driven when its owner channel's grid
        // node is alive; invalidating it here (channel.onDisassembled) would leave the orphan
        // locked forever with its materials. The node re-activates on reconnect and the orphan's
        // isComplete branch retries the storeItems() refund; players who want a full disconnect
        // can break the channel block itself (then a rebuilt machine re-homes the orphan).
        if (channel != null) channel.onControllerDisassembledKeepProxy();
        if (prevChannel != null && prevChannel != channel) prevChannel.onControllerDisassembledKeepProxy();
        prevCellDrives.clear();
        prevParallelDrives.clear();
        prevThreadCores.clear();
        prevChannel = null;
    }

    /**
     * t118: cancel (refund job materials into the grid) then destroy a built-in-slot cluster during
     * controller teardown. destroy() routes through the M1 injectDestroy hook → onClusterReleased
     * (releases the thread slot + vCPU number, idempotent). Mirrors TileEcalThreadDrive.
     * onControllerDisassembled's cancel-then-destroy order.
     * t122 (user): AE2U cancel() refunds via storeItems() at its tail — when the grid is
     * unreachable that refund is partial and the cluster's inventory still holds materials.
     * Destroying such a cluster would swallow them, so a non-empty inventory after cancel() means
     * the cluster is adopted as an orphan (EcoaegtnhOrphanClusters): any live grid re-drives it,
     * updateCraftingLogic's isComplete branch retries storeItems() and destroys it once the
     * inventory is empty — the job refunds automatically on reconnect instead of being swallowed.
     */
    private void cancelAndDestroyBuiltin(CraftingCPUCluster cluster) {
        try {
            cluster.cancel();
        } catch (Exception e) {
            LOG.warn(
                "Ecal: cancel failed during teardown — adopting cluster as orphan; it refunds when a grid drives it again",
                e);
            ecoaegtnh.EcoaegtnhOrphanClusters.adopt(cluster);
            return;
        }
        if (!ECPUCluster.from(cluster)
            .ecoaegtnh$isInventoryEmpty()) {
            // t122: cancel()'s storeItems() refund did not complete (grid unreachable) — the
            // inventory still holds materials. Keep the cluster alive; the isComplete branch of
            // updateCraftingLogic retries the refund once a grid drives it again.
            LOG.warn(
                "Ecal: refund incomplete (grid unreachable) — adopting cluster as orphan; it refunds when the network is back");
            ecoaegtnh.EcoaegtnhOrphanClusters.adopt(cluster);
            return;
        }
        // Refund complete (the M1 injectCancel hook may already have destroyed an empty cluster;
        // an explicit destroy is an idempotent fallback).
        ECPUCluster.from(cluster)
            .ecoaegtnh$markDestroyed();
        cluster.destroy();
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        disassembleAllRefund();
    }

    // ------------------------------------------------------------------
    // StructureLib constructable / preview
    // ------------------------------------------------------------------

    @Override
    public IStructureDefinition<MTEEcalArray> getStructure_EM() {
        return STRUCTURE_DEFINITION;
    }

    /**
     * Structure length for build/preview: formed length if assembled, else the GTNH structure
     * channel (controller stack size) clamped to 1..12.
     */
    private int structureLengthFor(ItemStack stack) {
        return segmentLength > 0 ? segmentLength
            : gregtech.common.misc.GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stack, 1, MAX_SEGMENTS);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        int length = structureLengthFor(stackSize);
        STRUCTURE_DEFINITION.buildOrHints(
            this,
            stackSize,
            PIECE_PREFIX + length,
            base.getWorld(),
            getExtendedFacing(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            length + 1,
            1,
            0,
            hintsOnly);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        int length = structureLengthFor(stackSize);
        return STRUCTURE_DEFINITION.survivalBuild(
            this,
            stackSize,
            PIECE_PREFIX + length,
            base.getWorld(),
            getExtendedFacing(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            length + 1,
            1,
            0,
            elementBudget,
            env,
            false);
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] {
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.ecal_segments"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.ecal_segment_detail"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.ecal_head"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.ecal_power"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.ecal_length"),
            net.minecraft.util.StatCollector
                .translateToLocalFormatted("ecoaegtnh.structure.desc.ecal_current_length", segmentLength) };
    }

    // ------------------------------------------------------------------
    // Power (pure AE, plan 搂5.2 / 搂13 d6): idle usage on the ME channel proxy.
    // ------------------------------------------------------------------

    /** A cell drive's inventory changed (phase B: recompute the byte pool + replenish vCPU). */
    public void onCellDriveChanged() {
        if (!mMachine) return;
        recalculateIdlePower();
        recalculateTotalBytes();
        createVirtualCPU();
        onClusterChanged();
    }

    /**
     * t35: a parallel drive's inserted core changed 鈥?recompute the parallelism total and push it
     * into every cluster + the standby vCPU.
     */
    public void onParallelDriveChanged() {
        if (!mMachine) return;
        parallelismTotal = 0;
        for (TileEcalParallelDrive drive : parallelDrives) {
            parallelismTotal += drive.getSuppliedParallelism();
        }
        recalculateParallelism();
        onClusterChanged();
    }

    /**
     * t35: a thread drive's inserted core changed 鈥?thread capacity / idle power changed (the
     * remaining-slot readouts and slot gates derive from the inserted items live).
     */
    public void onThreadDriveChanged() {
        if (!mMachine) return;
        recalculateIdlePower();
        createVirtualCPU();
        onClusterChanged();
    }

    /** Recompute AE idle power usage and push it to the ME channel proxy: tierBase + threads. */
    public void recalculateIdlePower() {
        double usage = tierBaseForPower();
        int threads = 0;
        for (TileEcalThreadDrive core : threadCores) {
            threads += core.getThreads();
        }
        usage += threads;
        this.idlePowerUsage = usage;
        if (channel != null) {
            channel.getProxy()
                .setIdlePowerUsage(usage);
        }
    }

    // ------------------------------------------------------------------
    // Computation core (phase B, plan 搂7.4): parallelism / byte pool / vCPU lifecycle
    // ------------------------------------------------------------------

    /** Pushes the scanned parallelism total into every assigned cluster + the standby vCPU. */
    public void recalculateParallelism() {
        for (TileEcalThreadDrive core : threadCores) {
            for (CraftingCPUCluster cpu : core.getCPUs()) {
                ECPUCluster.from(cpu)
                    .ecoaegtnh$setAccelerators(parallelismTotal);
            }
        }
        if (virtualCPU != null) {
            ECPUCluster.from(virtualCPU)
                .ecoaegtnh$setAccelerators(parallelismTotal);
        }
    }

    /**
     * Recomputes the byte pool from the installed cell drives (tier-gated). t114b: SATURATING
     * sum — the Singularity flash cell (奇点闪存晶阵) carries Long.MAX_VALUE bytes, so two of
     * them would overflow the long sum into a negative pool (user: "放两个就超了"). Once the
     * total reaches Long.MAX_VALUE it stays there: one singularity cell already means an
     * unlimited pool, more of them change nothing.
     */
    public void recalculateTotalBytes() {
        long total = 0;
        for (TileEcalCellDrive drive : cellDrives) {
            long b = drive.getSuppliedBytes();
            if (b <= 0) continue;
            if (Long.MAX_VALUE - total < b) {
                total = Long.MAX_VALUE; // saturate — a singularity cell already means unlimited
                break;
            }
            total += b;
        }
        this.totalBytes = total;
    }

    /**
     * Pool bytes not yet committed to tasks: totalBytes − Σ thread-drive used storage −
     * Σ built-in slot task bytes. t116c: the built-in thread/hyper clusters live in
     * builtinThreadClusters/builtinHyperClusters (not in any TileEcalThreadDrive), so their
     * task bytes must be counted here too — otherwise the pool never shrinks while a built-in slot
     * runs a job. M2 (audit): only REAL task bytes (ecoaegtnh$getUsedStorage) are charged — the
     * hyper +10% virtual reserve must not overdraw the shared pool.
     */
    public long getAvailableBytes() {
        long used = 0;
        for (TileEcalThreadDrive core : threadCores) {
            used += core.getUsedStorage();
        }
        for (CraftingCPUCluster cluster : builtinThreadClusters) {
            used += ECPUCluster.from(cluster)
                .ecoaegtnh$getUsedStorage();
        }
        for (CraftingCPUCluster cluster : builtinHyperClusters) {
            used += ECPUCluster.from(cluster)
                .ecoaegtnh$getUsedStorage();
        }
        return totalBytes - used;
    }

    /**
     * Creates/refreshes the standby vCPU (plan 搂7.4). Long-integer 10% red line: no vCPU while the
     * available pool is below totalBytes/10 (R1 搂12.3 float fix; t50: 5% in overclock mode).
     * t46: no free thread slot 鈫?no standby vCPU at all 鈥?the slot check now runs BEFORE the
     * refresh branch, so removing the last thread core destroys a stale standby vCPU instead of
     * refreshing it (otherwise AE2 can still select it for one more job with no slot to assign).
     * t50: the available pool is capped by the activated cell-chain upgrade node (byte-pool cap).
     * Refreshes the existing vCPU's capacity/parallelism; otherwise builds a new cluster if any
     * thread core has a free slot. In-flight vCPUs (already in the thread drives' cpus lists) are
     * never touched here.
     */
    public void createVirtualCPU() {
        if (channel == null) return;
        // t122 (user): re-home built-in orphans whose original channel block is gone (its grid
        // node is dead, so nothing can drive their refund). This rebuilt/formed machine adopts
        // them: the live channel then exposes them to the grid (MixinCraftingGridCache) and
        // updateCraftingLogic's isComplete branch retries the storeItems() refund, so the
        // materials come back instead of staying locked forever.
        for (CraftingCPUCluster orphan : ecoaegtnh.EcoaegtnhOrphanClusters.all()) {
            MTEEcalArray old = ECPUCluster.from(orphan)
                .ecoaegtnh$getVirtualCPUOwner();
            if (old == null || old == this) {
                continue;
            }
            TileEcalMEChannel oldChannel = old.getChannel();
            if (oldChannel != null && oldChannel.getProxy() != null
                && oldChannel.getProxy()
                    .getNode() != null) {
                continue; // the original channel is still alive — it drives the refund itself
            }
            ECPUCluster.from(orphan)
                .ecoaegtnh$setVirtualCPUOwner(this);
            // T-M2 (t122 audit): drop the old controller's vCPU number so it cannot collide with
            // this controller's renumbered pool (display/registry level only).
            ECPUCluster.from(orphan)
                .ecoaegtnh$setVCPUId(0);
            LOG.warn(
                "Ecal: orphan vCPU re-homed to rebuilt controller ({},{},{}) — its materials refund through this grid",
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        if (totalBytes <= 0) {
            destroyStandbyVCPU();
            return;
        }
        long availableBytes = Math.min(getAvailableBytes(), getBytePoolCap());
        if (availableBytes < redLineThreshold()) {
            destroyStandbyVCPU();
            return;
        }
        boolean slotFree = false;
        for (TileEcalThreadDrive core : threadCores) {
            if (core.canAddCPU() || core.canAddHyperThread()) {
                slotFree = true;
                break;
            }
        }
        // t114g: built-in thread slots also count as free slots (1 base / +3 B1 / +2 hyper B2).
        if (!slotFree && (builtinThreadClusters.size() < getBuiltinThreads()
            || builtinHyperClusters.size() < getBuiltinHyperThreads())) {
            slotFree = true;
        }
        if (!slotFree) {
            destroyStandbyVCPU();
            return;
        }
        if (virtualCPU == null) {
            virtualCPU = newStandbyCluster(availableBytes);
            LOG.info(
                "Ecal vCPU created: bytes={}, parallelism={}, segments={}",
                availableBytes,
                parallelismTotal,
                segmentLength);
            channel.postCPUClusterChangeEvent();
        } else {
            ECPUCluster.from(virtualCPU)
                .ecoaegtnh$setAvailableStorage(availableBytes);
            ECPUCluster.from(virtualCPU)
                .ecoaegtnh$setAccelerators(parallelismTotal);
        }
    }

    /** Builds one standby cluster with the pool's current bytes/parallelism + the persisted mode. */
    private CraftingCPUCluster newStandbyCluster(long availableBytes) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        WorldCoord pos = new WorldCoord(base.getXCoord(), base.getYCoord(), base.getZCoord());
        CraftingCPUCluster cluster = new CraftingCPUCluster(pos, pos);
        ECPUCluster.from(cluster)
            .ecoaegtnh$setVirtualCPUOwner(this);
        // t114i: the standby vCPU carries NO number (id 0) — a number is allocated from the
        // smallest-available pool only when a job assigns the cluster to a thread slot, so
        // standby refills (createVirtualCPU) never consume or leak numbers.
        ECPUCluster.from(cluster)
            .ecoaegtnh$setVCPUId(0);
        ECPUCluster.from(cluster)
            .ecoaegtnh$setAvailableStorage(availableBytes);
        ECPUCluster.from(cluster)
            .ecoaegtnh$setAccelerators(parallelismTotal);
        // t34: inherit the controller's persisted CraftingAllow mode — a fresh cluster defaults
        // to ALLOW_ALL (vanilla AE2 CPUs persist the mode via their block's NBT; virtual vCPUs
        // are recreated on every refill, so the controller is the persistence point).
        cluster.changeCraftingAllowMode(craftingAllowMode);
        return cluster;
    }

    /** t114h: whether the cluster is the current STANDBY vCPU (vs. a running one). */
    public boolean isStandbyVCPU(CraftingCPUCluster cluster) {
        return virtualCPU == cluster;
    }

    /**
     * t114i: allocates the smallest free vCPU number — released numbers first, then the next
     * fresh one. Called only when a cluster becomes RUNNING; the standby never takes a number.
     */
    private int allocateVCPUId() {
        final Integer freed = freeVCPUIds.poll();
        return freed != null ? freed : ++vcpuIdCounter;
    }

    /**
     * t114i: returns a destroyed cluster's vCPU number to the pool (idempotent — a cluster with
     * id 0, e.g. the standby or an already-released one, releases nothing).
     */
    public void releaseVCPUId(CraftingCPUCluster cluster) {
        ECPUCluster ec = ECPUCluster.from(cluster);
        final int id = ec.ecoaegtnh$getVCPUId();
        if (id <= 0) {
            return;
        }
        ec.ecoaegtnh$setVCPUId(0);
        freeVCPUIds.add(id);
    }

    /**
     * t114i: unified cluster-release hook (called by M1 injectDestroy on EVERY destroy path) —
     * returns the vCPU number to the pool and frees any built-in thread slot the cluster held
     * (merges the former onBuiltinClusterDestroyed duty; keeps posting the grid change event).
     */
    public void onClusterReleased(CraftingCPUCluster cluster) {
        releaseVCPUId(cluster);
        boolean removed = builtinThreadClusters.remove(cluster);
        removed |= builtinHyperClusters.remove(cluster);
        if (removed && channel != null) {
            channel.postCPUClusterChangeEvent();
        }
        // t114k: observability — the built-in slot release previously had no log line, so a
        // stuck built-in job (dropped from the grid's drive set) left no trace in the log.
        LOG.info(
            "Ecal builtin cluster released: normal={}/{} hyper={}/{}, freeIds={}",
            builtinThreadClusters.size(),
            getBuiltinThreads(),
            builtinHyperClusters.size(),
            getBuiltinHyperThreads(),
            freeVCPUIds);
    }

    private void destroyStandbyVCPU() {
        if (virtualCPU == null) return;
        ECPUCluster.from(virtualCPU)
            .ecoaegtnh$markDestroyed();
        virtualCPU = null;
        if (channel != null) {
            channel.postCPUClusterChangeEvent();
        }
    }

    /**
     * M9 (audit): whether the cluster already occupies a thread slot (built-in lists or any
     * thread drive). Used to de-duplicate assignment (merge / stale-cluster paths).
     */
    public boolean isClusterAssigned(CraftingCPUCluster cluster) {
        if (builtinThreadClusters.contains(cluster) || builtinHyperClusters.contains(cluster)) {
            return true;
        }
        for (TileEcalThreadDrive core : threadCores) {
            if (core.getCPUs()
                .contains(cluster)) {
                return true;
            }
        }
        return false;
    }

    /**
     * M7 (audit): cancel + destroy every in-flight cluster (built-in slots and thread drives),
     * refunding job materials into the grid; then replenish the standby vCPU. Reuses the same
     * cancel→destroy order as disassembleAll. Public: called from EcoaegtnhLifecycleHooks (H2).
     */
    public void cancelAllInFlight(String reason) {
        LOG.info("Ecal: cancelling all in-flight vCPU jobs ({})", reason);
        for (CraftingCPUCluster cluster : new java.util.ArrayList<>(builtinThreadClusters)) {
            cancelAndDestroyBuiltin(cluster);
        }
        for (CraftingCPUCluster cluster : new java.util.ArrayList<>(builtinHyperClusters)) {
            cancelAndDestroyBuiltin(cluster);
        }
        builtinThreadClusters.clear();
        builtinHyperClusters.clear();
        for (TileEcalThreadDrive core : threadCores) {
            core.onControllerDisassembled();
        }
        createVirtualCPU();
    }

    /**
     * Assignment hook (called by the M1 submitJob RETURN inject, 搂6.2): puts the job-loaded vCPU
     * into a free slot in the USER-ORDERED priority (t114l): 1) built-in normal thread, 2)
     * external normal thread, 3) built-in hyper thread, 4) external hyper thread (hyper slots
     * carry the +10% extra storage, free in overclock mode), then replenishes the standby vCPU.
     */
    public void onVirtualCPUSubmitJob(CraftingCPUCluster cluster, long usedBytes) {
        // M9 (audit): if the cluster is ALREADY assigned (merge onto a running vCPU, or a stale
        // cluster that never left the lists after storeItems failure), do not double-add it —
        // just refresh its byte accounting and keep the existing slot.
        if (isClusterAssigned(cluster)) {
            ECPUCluster.from(cluster)
                .ecoaegtnh$setAvailableStorage(usedBytes);
            return;
        }
        // t114i: the job makes this cluster RUNNING — take the smallest free vCPU number now
        // (the standby carried none). If no thread slot is free after all, the number goes back.
        final ECPUCluster ec = ECPUCluster.from(cluster);
        final boolean numbered = ec.ecoaegtnh$getVCPUId() > 0;
        if (!numbered) {
            ec.ecoaegtnh$setVCPUId(allocateVCPUId());
        }
        boolean assigned = false;
        // t114l (user): built-in NORMAL thread slot comes FIRST (no thread drive required).
        if (builtinThreadClusters.size() < getBuiltinThreads()) {
            ECPUCluster.from(cluster)
                .ecoaegtnh$setHyperAssigned(false);
            ECPUCluster.from(cluster)
                .ecoaegtnh$setAvailableStorage(usedBytes);
            builtinThreadClusters.add(cluster);
            assigned = true;
            LOG.info("Ecal vCPU assigned to BUILT-IN thread slot: taskBytes={}", usedBytes);
        }
        if (!assigned) {
            for (TileEcalThreadDrive core : threadCores) {
                if (core.addCPU(cluster, false)) {
                    ECPUCluster.from(cluster)
                        .ecoaegtnh$setAvailableStorage(usedBytes);
                    assigned = true;
                    LOG.info(
                        "Ecal vCPU assigned: threadCore=({},{},{}), taskBytes={}",
                        core.xCoord,
                        core.yCoord,
                        core.zCoord,
                        usedBytes);
                    break;
                }
            }
        }
        // t114l (user): built-in HYPER thread slot before the external hyper slots.
        if (!assigned && builtinHyperClusters.size() < getBuiltinHyperThreads()) {
            long extra = isOverclocked() ? 0 : usedBytes / 10;
            ECPUCluster.from(cluster)
                .ecoaegtnh$setHyperAssigned(true);
            ECPUCluster.from(cluster)
                .ecoaegtnh$setUsedExtraStorage(extra);
            ECPUCluster.from(cluster)
                .ecoaegtnh$setAvailableStorage(usedBytes + extra);
            builtinHyperClusters.add(cluster);
            assigned = true;
            LOG.info("Ecal vCPU assigned to BUILT-IN hyper slot: taskBytes={}+{}", usedBytes, extra);
        }
        if (!assigned) {
            // Hyper-thread fallback (phase D): +10% extra storage on top of the task bytes.
            // t50 瓒呴妯″紡 (鍙嶈浆褰╄泲): all lines maxed 鈫?the +10% surcharge is free (extra = 0).
            for (TileEcalThreadDrive core : threadCores) {
                if (core.addCPU(cluster, true)) {
                    long extra = isOverclocked() ? 0 : usedBytes / 10;
                    ECPUCluster.from(cluster)
                        .ecoaegtnh$setUsedExtraStorage(extra);
                    ECPUCluster.from(cluster)
                        .ecoaegtnh$setAvailableStorage(usedBytes + extra);
                    assigned = true;
                    LOG.info(
                        "Ecal vCPU assigned (hyper): threadCore=({},{},{}), taskBytes={}+{}",
                        core.xCoord,
                        core.yCoord,
                        core.zCoord,
                        usedBytes,
                        extra);
                    break;
                }
            }
        }
        if (!assigned) {
            // H3 (audit): no thread slot available — the job already passed the byte precheck and
            // got a link, but an unassigned cluster drops out of the grid's CPU set on the next
            // rebuild → job + materials freeze forever. Cancel (refund materials) + destroy now.
            LOG.warn("Ecal vCPU submit: no thread slot available for {} bytes; cancelling the job", usedBytes);
            try {
                cluster.cancel();
            } catch (Exception e) {
                LOG.warn("Ecal: cancel on slot-exhausted cluster failed", e);
            }
            // T-H1 (t122 audit): same inventory guard as cancelAndDestroyBuiltin — cancel()'s
            // storeItems() refund can be partial when the grid is unreachable; destroying a
            // non-empty inventory would swallow the remaining materials. Adopt as an orphan
            // instead (it refunds once a grid drives it again).
            if (!ECPUCluster.from(cluster)
                .ecoaegtnh$isInventoryEmpty()) {
                LOG.warn("Ecal: refund incomplete (grid unreachable) — adopting slot-exhausted cluster as orphan");
                ecoaegtnh.EcoaegtnhOrphanClusters.adopt(cluster);
                if (virtualCPU == cluster) {
                    virtualCPU = null;
                }
                createVirtualCPU();
                return;
            }
            ECPUCluster.from(cluster)
                .ecoaegtnh$markDestroyed();
            cluster.destroy(); // M1 injectDestroy → onClusterReleased (slot/number release, idempotent)
            if (!numbered) {
                releaseVCPUId(cluster); // t114i: not running — hold no number
            }
        }
        if (virtualCPU == cluster) {
            virtualCPU = null;
        }
        createVirtualCPU();
    }

    /**
     * Clusters this channel exposes to the AE grid: thread-drive CPUs + BUILT-IN slot clusters +
     * standby vCPU. t114k: the built-in lists must be included — the AE2 grid only drives
     * {@code CraftingCPUCluster.updateCraftingLogic} for clusters registered in its
     * {@code CraftingGridCache.craftingCPUClusters} set (rebuilt from {@code channel.getCPUs()}
     * on every {@code MENetworkCraftingCpuChange}); without them, a built-in job's cluster is
     * dropped from the set on the next rebuild (any other task finishing triggers one), its
     * updateCraftingLogic stops being called, the job freezes forever and the built-in thread
     * slot + vCPU number are never released.
     */
    public List<CraftingCPUCluster> getClusterList() {
        List<CraftingCPUCluster> result = new ArrayList<>();
        for (TileEcalThreadDrive core : threadCores) {
            result.addAll(core.getCPUs());
        }
        result.addAll(builtinThreadClusters);
        result.addAll(builtinHyperClusters);
        if (virtualCPU != null) {
            ECPUCluster.from(virtualCPU)
                .ecoaegtnh$setVirtualCPUOwner(this);
            result.add(virtualCPU);
        }
        return result;
    }

    /** CPU count changed 鈫?tell the grid to re-scan the CPU list (plan 搂7.3). */
    public void onClusterChanged() {
        if (channel != null) {
            channel.postCPUClusterChangeEvent();
        }
    }

    /** markDirty without re-render (M1 markDirty redirect target). */
    public void markNoUpdateSync() {
        markDirty();
    }

    // ------------------------------------------------------------------
    // CraftingAllow mode persistence (t34): AE2's "accept requests" mode lives on each
    // CraftingCPUCluster instance; ECO vCPUs are virtual clusters recreated on every standby
    // refill (createVirtualCPU), so the controller stores the mode, applies it to every new vCPU
    // and persists it via the GT MTE NBT hooks (vanilla CPUs persist via their block instead).
    // User changes made through the AE terminal CPU detail GUI are written back here by the M1
    // changeCraftingAllowMode injection.
    // ------------------------------------------------------------------

    private CraftingAllow craftingAllowMode = CraftingAllow.ALLOW_ALL;

    public CraftingAllow getCraftingAllowMode() {
        return craftingAllowMode;
    }

    public void setCraftingAllowMode(CraftingAllow craftingAllowMode) {
        if (craftingAllowMode == null || this.craftingAllowMode == craftingAllowMode) {
            return;
        }
        this.craftingAllowMode = craftingAllowMode;
        markDirty();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("craftingAllowMode", craftingAllowMode.ordinal());
        // t60: upgrade-tree activation state (鏉冨▉闂ㄦ帶鏁版嵁).
        NBTTagCompound treeTag = new NBTTagCompound();
        upgradeTree.writeToNBT(treeTag);
        aNBT.setTag("upgradeTree", treeTag);
    }

    /**
     * t79 (godforge MTEForgeOfGods.setItemNBT:1001-1017 同款): GT calls this when the machine is
     * mined — the full NBT (incl. this machine's upgrade tree) goes into the dropped item, so
     * placing the drop restores the unlocks through loadNBTData. Each machine keeps its OWN
     * tree instance (CalculatorUpgradeTree.newInstance()), so two hosts never share unlocks.
     */
    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("craftingAllowMode")) {
            int ordinal = aNBT.getInteger("craftingAllowMode");
            if (ordinal >= 0 && ordinal < CraftingAllow.values().length) {
                craftingAllowMode = CraftingAllow.values()[ordinal];
            }
        }
        if (aNBT.hasKey("upgradeTree")) {
            upgradeTree.readFromNBT(aNBT.getCompoundTag("upgradeTree"));
        }
    }

    /** Controller-tier base drain 鈥?C4=2.0, C6=4.0, C9=8.0 (plan 搂5.2). */
    private double tierBaseForPower() {
        if (tier == TIER_C9) {
            return 8.0;
        }
        if (tier == TIER_C6) {
            return 4.0;
        }
        return 2.0;
    }

    // ------------------------------------------------------------------
    // Processing loop (no recipes 鈥?the computation runs through AE2 vCPUs, phase B)
    // ------------------------------------------------------------------

    @Override
    protected @org.jetbrains.annotations.NotNull CheckRecipeResult checkProcessing_EM() {
        return CheckRecipeResultRegistry.NONE;
    }

    /** t122 (user): first tick the channel was observed down (for the once-per-transition log). */
    private long channelDownTick = -1;

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (!mMachine) return;
        // t44 pattern: a no-maintenance machine can never legitimately stop with NO_REPAIR; clear
        // any persisted stale reason.
        if (aBaseMetaTileEntity.getLastShutDownReason()
            == gregtech.api.util.shutdown.ShutDownReasonRegistry.NO_REPAIR) {
            aBaseMetaTileEntity.setShutDownReason(gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE);
            aBaseMetaTileEntity.setShutdownStatus(false);
        }
        // Keep the idle power in sync with the parts every 5 ticks (cheap; structure changes also
        // trigger it via scanStructureVolume).
        long worldTime = aBaseMetaTileEntity.getWorld()
            .getTotalWorldTime();
        if (worldTime % 5 == 0) {
            recalculateIdlePower();
        }
        // t122 (user): channel down no longer cancels anything — in-flight vCPU jobs stay frozen
        // (updateCraftingLogic's isActive redirect pauses them) and RESUME automatically on
        // reconnect; their materials stay safe inside the AE grid clusters. Log the state change
        // once per transition for observability.
        if (worldTime % 20 == 0) {
            // T-L1 (t122 audit): no channel (block removed, structure not yet re-checked) — skip
            // the state machine instead of logging a spurious "channel down".
            if (channel == null) {
                channelDownTick = -1;
            } else {
                boolean active = channel.getProxy() != null && channel.getProxy()
                    .isActive();
                if (active) {
                    if (channelDownTick >= 0) {
                        LOG.info("Ecal: ME channel back — in-flight vCPU jobs resume");
                    }
                    channelDownTick = -1;
                } else if (channelDownTick < 0) {
                    channelDownTick = worldTime;
                    LOG.warn(
                        "Ecal: ME channel down — in-flight vCPU jobs stay frozen and resume on reconnect (materials are kept)");
                }
            }
        }
        // Phase B: replenish the standby vCPU on the structure-recheck cadence (plan 搂7.4 鈥?the
        // reference replenishes during its 40-tick structure recheck; drive changes also trigger
        // this via onCellDriveChanged).
        if (worldTime % 40 == 0) {
            recalculateParallelism();
            recalculateTotalBytes();
            createVirtualCPU();
        }
        // Perf summary every 600 ticks (t115: was 200 — several hosts on one server spammed INFO;
        // 600t = 30s keeps it observable without log noise).
        if (worldTime % 600 == 0) {
            int clusters = 0;
            long totalUs = 0;
            for (TileEcalThreadDrive core : threadCores) {
                clusters += core.getCPUs()
                    .size();
                for (CraftingCPUCluster cpu : core.getCPUs()) {
                    EcoTimeRecorder rec = ECPUCluster.from(cpu)
                        .ecoaegtnh$getTimeRecorder();
                    totalUs += rec.getAverage();
                }
            }
            LOG.info(
                "Ecal perf: segments={}, parallelism={}, totalBytes={}, availableBytes={}, clusters={}, standbyVCPU={}, avgUpdateUs={}",
                segmentLength,
                parallelismTotal,
                totalBytes,
                getAvailableBytes(),
                clusters,
                virtualCPU != null,
                totalUs);
        }
    }

    // ------------------------------------------------------------------
    // Controller textures (t18, E-Storage t18 pattern 鈥?MTEEcoStorageArray.java:1381-1436):
    // registerIcons wraps the atlas icons in IIconContainers; getTexture returns the front
    // texture on the facing side (formed 鈫?ecal_controller_front, unformed 鈫?front_off) and the
    // side texture elsewhere; null (server-side render calls) falls back to stable titanium.
    // ------------------------------------------------------------------

    // t26: static per-tier arrays, indexed by tier 0/1/2. GT calls registerIcons only on the
    // PROTOTYPE instances (BlockMachines.registerBlockIcons iterates METATILEENTITIES); world-placed
    // instances come from newMetaEntity and would keep instance fields null 鈫?getTexture fell back
    // to titanium (t21's instance-field fix traded the cross-tier overwrite for this). Static arrays
    // make the prototype-registered icons visible to every instance (E-Storage t18 pattern) while the
    // tier index keeps the three tiers from overwriting each other (t21 intent preserved). Null
    // (server-side render calls) still falls back to stable titanium.
    // t30 (server startup crash fix): the fields must NOT be final and must have NO inline
    // initializer. FML's SideTransformer strips @SideOnly(Side.CLIENT) members when the class loads
    // on a dedicated server; an inline `= new IIconContainer[3]` makes javac emit a putstatic in
    // <clinit> that dangles after the strip 鈫?NoSuchFieldError at MTEEcalArray.<clinit> (server
    // crash). registerIcons (also CLIENT-only, stripped on the server) lazily allocates the arrays
    // instead; getTexture is never invoked server-side 鈥?GT5U 5.09.54.20 bytecode audit: the only
    // callers of BaseMetaTileEntity.getTexture(Block, ForgeDirection) (which delegates to the MTE
    // getTexture) are the client-only renderers GTRendererBlock and gtPlusPlus MachineBlockRenderer
    // (via GTMethodHelper), so its getstatic references stay lazy-unresolved on a dedicated server.
    @SideOnly(Side.CLIENT)
    private static IIconContainer[] controllerIconFront;
    @SideOnly(Side.CLIENT)
    private static IIconContainer[] controllerIconSide;
    @SideOnly(Side.CLIENT)
    private static IIconContainer[] controllerIconFrontOff;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        super.registerIcons(aBlockIconRegister);
        // t30: lazy allocation 鈥?the CLIENT-only fields are stripped on a dedicated server, and
        // without an inline initializer <clinit> holds no putstatic for them (NoSuchFieldError fix).
        if (controllerIconFront == null) {
            controllerIconFront = new IIconContainer[3];
            controllerIconSide = new IIconContainer[3];
            controllerIconFrontOff = new IIconContainer[3];
        }
        // t21: per-tier controller textures (T22): ecal_controller[_c6|_c9]_front/side/front_off.
        String suffix = tier == 0 ? "" : tier == 1 ? "_c6" : "_c9";
        final IIcon iconFront = aBlockIconRegister
            .registerIcon(ecoaegtnh.EcoAEGTNHCore.MODID + ":ecal_controller" + suffix + "_front");
        final IIcon iconSide = aBlockIconRegister
            .registerIcon(ecoaegtnh.EcoAEGTNHCore.MODID + ":ecal_controller" + suffix + "_side");
        final IIcon iconFrontOff = aBlockIconRegister
            .registerIcon(ecoaegtnh.EcoAEGTNHCore.MODID + ":ecal_controller" + suffix + "_front_off");
        controllerIconFront[tier] = iconContainer(iconFront, "blocks/ecal_controller" + suffix + "_front");
        controllerIconSide[tier] = iconContainer(iconSide, "blocks/ecal_controller" + suffix + "_side");
        controllerIconFrontOff[tier] = iconContainer(iconFrontOff, "blocks/ecal_controller" + suffix + "_front_off");
    }

    @SideOnly(Side.CLIENT)
    private static IIconContainer iconContainer(final IIcon icon, final String texturePath) {
        return new IIconContainer() {

            @Override
            public IIcon getIcon() {
                return icon;
            }

            @Override
            public IIcon getOverlayIcon() {
                return icon;
            }

            @Override
            public ResourceLocation getTextureFile() {
                return new ResourceLocation(ecoaegtnh.EcoAEGTNHCore.MODID, texturePath);
            }
        };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstone) {
        final IIconContainer front = tier < controllerIconFront.length ? controllerIconFront[tier] : null;
        if (front != null) {
            // side == facing is the front face (the direction the controller points).
            if (side == facing) {
                // t18: formed 鈫?lit front, unformed 鈫?dark/off front (mMachine is synced to the
                // client via getDescriptionData/onDescriptionPacket below).
                return new ITexture[] { TextureFactory.of(mMachine ? front : controllerIconFrontOff[tier]) };
            }
            return new ITexture[] { TextureFactory.of(controllerIconSide[tier]) };
        }
        // Fallback (server-side render calls / icon registration not yet run): stable titanium.
        return new ITexture[] {
            TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.MACHINE_CASING_STABLE_TITANIUM) };
    }

    /**
     * t18: sync the formed flag to the client (5.09.54.20 MTE sync hook 鈥?the GT description
     * packet calls getDescriptionData/onDescriptionPacket; verified in CommonBaseMetaTileEntity
     * bytecode). Without this the client's mMachine stays false and getTexture would always show
     * the "off" front.
     */
    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound tag = super.getDescriptionData();
        if (tag == null) {
            tag = new NBTTagCompound();
        }
        tag.setBoolean("ecalFormed", mMachine);
        return tag;
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound tag) {
        super.onDescriptionPacket(tag);
        if (tag != null && tag.hasKey("ecalFormed")) {
            mMachine = tag.getBoolean("ecalFormed");
        }
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        // t53: single unified host 鈥?the machine-type line carries no tier marker anymore
        // (the upgrade tree provides the progression).
        tt.addMachineType(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.machinetype"))
            .addInfo(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.info.compute"))
            .addInfo(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.info.power"))
            .addInfo(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.info.no_maintenance"))
            .beginVariableStructureBlock(4, MAX_SEGMENTS + 3, 3, 3, 2, 2, false)
            .addController(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.controller"))
            // 284：5.09.51.482 的 MultiblockTooltipBuilder 无 addCasing(String,String,boolean)，
            // 用 addCasingInfoMin/Exactly 等价表达（"2+"→≥2，"1+"→≥1，"1"→恰好 1）。
            .addCasingInfoMin(
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.ecalculator_casing.name"),
                2,
                false)
            .addCasingInfoMin(
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.ecalculator_cell_drive.name"),
                1,
                false)
            .addCasingInfoMin(
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.ecalculator_parallel_drive.name"),
                1,
                false)
            .addCasingInfoMin(
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.ecalculator_thread_drive.name"),
                1,
                false)
            .addCasingInfoExactly(
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.ecalculator_transmitter_bus.name"),
                1,
                false)
            .addCasingInfoExactly(
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.ecalculator_me_channel.name"),
                1,
                false)
            // 284：5.09.51.482 的 MultiblockTooltipBuilder 无 addStructureFooter——用
            // addStructureInfo 输出放置说明（语义最接近的结构信息行）。
            .addStructureInfo(
                net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.ecal.footer.placement"))
            .toolTipFinisher();
        return tt;
    }

    // ------------------------------------------------------------------
    // GUI (phase C1): quantum-computer-style MUI1 panel with the IDENTICAL
    // mechanism to E-Storage (t54/t58/t65/t77): useMui2()==false 鈫?the
    // TTMultiblockBase default right-click GUI (GTUIInfos.openGTTileEntityUI
    // 鈫?addUIWidgets), deep-blue screen_blue 198脳192 window, Scrollable text
    // screen (drawTexts) + bottom parameter strip with hover LED cells +
    // FakeSyncWidget data write-back. No 1.12.2 custom panels
    // (MonitorPanel/CPUStatusPanel/StorageBar) 鈥?only their information
    // content (byte pool / thread cores / task rows) is mapped to the
    // E-Storage form (plan 搂8.1 / 搂0.1 constraint 1).
    // ------------------------------------------------------------------

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        if (doesBindPlayerInventory()) {
            builder.widget(
                new DrawableWidget().setDrawable(tectech.thing.gui.TecTechUITextures.BACKGROUND_SCREEN_BLUE)
                    .setPos(4, 4)
                    .setSize(190, 91));
        } else {
            builder.widget(
                new DrawableWidget()
                    .setDrawable(tectech.thing.gui.TecTechUITextures.BACKGROUND_SCREEN_BLUE_NO_INVENTORY)
                    .setPos(4, 4)
                    .setSize(190, 171));
        }
        final SlotWidget inventorySlot = new SlotWidget(new BaseSlot(inventoryHandler, getControllerSlotIndex()) {

            @Override
            public int getSlotStackLimit() {
                return getInventoryStackLimit();
            }
        })
            // t111 (godforge parity): keep the controller slot out of shift-click routing so a
            // shift+clicked backpack item lands in the upgrade material window's staging slots
            // instead of here (both were priority 0, this one registered first). Manual clicks
            // still work; only QUICK_MOVE transfer is disabled.
            .disableShiftInsert();
        if (doesBindPlayerInventory()) {
            builder
                .widget(
                    inventorySlot
                        .setBackground(
                            getGUITextureSet().getItemSlot(),
                            tectech.thing.gui.TecTechUITextures.OVERLAY_SLOT_MESH)
                        .setPos(173, 167))
                .widget(
                    new DrawableWidget().setDrawable(tectech.thing.gui.TecTechUITextures.PICTURE_HEAT_SINK_SMALL)
                        .setPos(173, 185)
                        .setSize(18, 6));
        }

        final DynamicPositionedColumn screenElements = new DynamicPositionedColumn();
        drawTexts(screenElements, inventorySlot);
        builder.widget(
            new Scrollable().setVerticalScroll()
                .widget(screenElements)
                .setPos(10, 7)
                .setSize(182, doesBindPlayerInventory() ? 79 : 165));

        // t58: only the power switch (work toggle) stays 鈥?the power-pass / safe-void buttons
        // are useless for a pure-AE machine (no GT energy hatches) and were removed.
        builder.widget(createPowerSwitchButton())
            .widget(new FakeSyncWidget.BooleanSyncer(() -> getBaseMetaTileEntity().isAllowedToWork(), val -> {
                if (val) getBaseMetaTileEntity().enableWorking();
                else getBaseMetaTileEntity().disableWorking();
            }));

        // Bottom parameter strip (the quantum computer's parameter-strip background) with four
        // hover LED cells 鈥?the same mechanism as E-Storage t65/t77 (status texture +
        // dynamicTooltip(Supplier<List<String>>) + FakeSyncWidget.setOnClientUpdate 鈫?
        // notifyTooltipChange; no parametrization involved, hover tooltip only).
        builder.widget(
            new DrawableWidget().setDrawable(tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_BLANK)
                .setPos(5, doesBindPlayerInventory() ? 96 : 176)
                .setSize(166, 12));
        int stripY = doesBindPlayerInventory() ? 97 : 177;

        // 1) Status LED: ME channel + structure + power usage.
        final DrawableWidget statusLed = new DrawableWidget().setDrawable(() -> {
            if (syncChannelActive) return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GREEN[0];
            if (syncStructureValid) return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_RED[0];
            return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY;
        });
        builder.widget(
            statusLed.dynamicTooltip(() -> statusLedTooltip())
                .setPos(12, stripY)
                .setSize(6, 4))
            .widget(
                new FakeSyncWidget.BooleanSyncer(() -> isChannelActive(), val -> syncChannelActive = val)
                    .setOnClientUpdate(val -> statusLed.notifyTooltipChange()))
            .widget(
                new FakeSyncWidget.BooleanSyncer(() -> mMachine, val -> syncStructureValid = val)
                    .setOnClientUpdate(val -> statusLed.notifyTooltipChange()))
            .widget(
                new FakeSyncWidget.DoubleSyncer(() -> idlePowerUsage, val -> syncIdlePowerUsage = val)
                    .setOnClientUpdate(val -> statusLed.notifyTooltipChange()));

        // 2) Byte-pool LED: green healthy, red below the 10% red line, gray when not formed.
        final DrawableWidget bytesLed = new DrawableWidget().setDrawable(() -> {
            if (syncStructureValid && syncTotalBytes > 0) {
                return syncRedLineTriggered ? tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_RED[0]
                    : tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GREEN[0];
            }
            return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY;
        });
        builder.widget(
            bytesLed.dynamicTooltip(() -> bytesLedTooltip())
                .setPos(20, stripY)
                .setSize(6, 4))
            .widget(
                new FakeSyncWidget.LongSyncer(() -> totalBytes, val -> syncTotalBytes = val)
                    .setOnClientUpdate(val -> bytesLed.notifyTooltipChange()))
            .widget(
                new FakeSyncWidget.LongSyncer(() -> getAvailableBytes(), val -> syncAvailableBytes = val)
                    .setOnClientUpdate(val -> bytesLed.notifyTooltipChange()))
            .widget(
                new FakeSyncWidget.BooleanSyncer(() -> isRedLineTriggered(), val -> syncRedLineTriggered = val)
                    .setOnClientUpdate(val -> bytesLed.notifyTooltipChange()));

        // 3) Parallelism LED (orange when formed with parallel cores).
        final DrawableWidget parallelLed = new DrawableWidget().setDrawable(
            () -> syncStructureValid && syncParallelism > 0
                ? tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_ORANGE[0]
                : tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY);
        builder.widget(
            parallelLed.dynamicTooltip(() -> parallelLedTooltip())
                .setPos(28, stripY)
                .setSize(6, 4))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> parallelismTotal, val -> syncParallelism = val)
                    .setOnClientUpdate(val -> parallelLed.notifyTooltipChange()));

        // 4) Thread-core LED (cyan when formed with thread cores).
        final DrawableWidget coreLed = new DrawableWidget().setDrawable(
            () -> syncStructureValid && syncThreadCoreCount > 0
                ? tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_CYAN[0]
                : tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY);
        builder.widget(
            coreLed.dynamicTooltip(() -> coreLedTooltip())
                .setPos(36, stripY)
                .setSize(6, 4))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> threadCores.size(), val -> syncThreadCoreCount = val)
                    .setOnClientUpdate(val -> coreLed.notifyTooltipChange()))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> getActiveTaskCount(), val -> syncActiveTaskCount = val)
                    .setOnClientUpdate(val -> coreLed.notifyTooltipChange()))
            .widget(
                new FakeSyncWidget.BooleanSyncer(() -> virtualCPU != null, val -> syncStandbyVCPU = val)
                    .setOnClientUpdate(val -> coreLed.notifyTooltipChange()));

        // ------------------------------------------------------------------
        // t61: right-edge button column (Forge-of-the-Gods 搂5.1.2 parameters) 鈥?ONE entry:
        // the upgrade-tree overview (id 300). The milestone feed button/window are removed
        // (docs 搂5). Click replay opens the registered synced window on the server.
        // ------------------------------------------------------------------
        buildContext.addSyncedWindow(UpgradeTreeGui.OVERVIEW_WINDOW_ID, this::createUpgradeTreeOverview);
        buildContext.addSyncedWindow(UpgradeTreeGui.DETAIL_WINDOW_ID, this::createUpgradeTreeDetail);
        buildContext.addSyncedWindow(UpgradeTreeGui.MATERIAL_WINDOW_ID, this::createUpgradeTreeMaterial);
        if (doesBindPlayerInventory()) {
            // 鍗囩骇鏍?(174, 129) 鈫?power switch (174, 148).
            builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
                if (!widget.isClient()) {
                    widget.getContext()
                        .openSyncedWindow(UpgradeTreeGui.OVERVIEW_WINDOW_ID);
                }
            })
                .setPlayClickSound(true)
                .setBackground(() -> upgradeButtonBackground())
                .setPos(174, 129)
                .setSize(16, 16)
                .dynamicTooltip(() -> upgradeButtonTooltip()));
        }
    }

    /** t61: overview window 鈥?the three-layer upgrade-tree GUI shared with the storage array. */
    private ModularWindow createUpgradeTreeOverview(EntityPlayer player) {
        return UpgradeTreeGui.createOverview(upgradeTreeGuiHandler(), player);
    }

    private ModularWindow createUpgradeTreeDetail(EntityPlayer player) {
        return UpgradeTreeGui.createDetail(upgradeTreeGuiHandler(), player);
    }

    private ModularWindow createUpgradeTreeMaterial(EntityPlayer player) {
        return UpgradeTreeGui.createMaterial(upgradeTreeGuiHandler(), player);
    }

    /** t61: the upgrade-tree GUI handler (tree/selection/staging/submit + sync packs). */
    private UpgradeTreeGui.Handler upgradeTreeGuiHandler() {
        return new UpgradeTreeGui.Handler() {

            @Override
            public UpgradeTree getUpgradeTree() {
                return upgradeTree;
            }

            @Override
            public boolean isServerSide() {
                return getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isServerSide();
            }

            @Override
            public String getSelectedNodeId() {
                return selectedUpgradeNode;
            }

            @Override
            public void setSelectedNodeId(String id) {
                selectedUpgradeNode = id;
            }

            @Override
            public void markDirty() {
                MTEEcalArray.this.markDirty();
            }

            @Override
            public String syncActivatedPack() {
                if (isServerSide()) return upgradeTreePack(upgradeTree);
                return syncUpgradeActivated;
            }

            @Override
            public void applyActivatedPack(String pack) {
                syncUpgradeActivated = pack;
            }

            @Override
            public String syncSelectedNode() {
                if (isServerSide()) return selectedUpgradeNode == null ? "" : selectedUpgradeNode;
                return syncUpgradeSelected;
            }

            @Override
            public void applySelectedNode(String s) {
                syncUpgradeSelected = s;
            }

            @Override
            public String syncPaidPack() {
                if (isServerSide()) return paidPack(upgradeTree);
                return syncUpgradePaid;
            }

            @Override
            public void applyPaidPack(String s) {
                syncUpgradePaid = s;
            }

            @Override
            public ItemStackHandler getStagingHandler() {
                return upgradeStagingHandler;
            }

            @Override
            public void submitUpgradeMaterials() {
                submitUpgradeMaterialsServer();
            }
        };
    }

    /** t61: comma-joined activated ids (server supplier). */
    private static String upgradeTreePack(UpgradeTree tree) {
        StringBuilder sb = new StringBuilder();
        for (UpgradeNode node : tree.getNodes()) {
            if (tree.isActivated(node.getId())) {
                if (sb.length() > 0) sb.append(',');
                sb.append(node.getId());
            }
        }
        return sb.toString();
    }

    /** t61: "node:material:count;..." paid pack (server supplier). */
    private static String paidPack(UpgradeTree tree) {
        StringBuilder sb = new StringBuilder();
        for (UpgradeNode node : tree.getNodes()) {
            for (java.util.Map.Entry<String, Integer> e : node.getMaterialCost()
                .entrySet()) {
                int paid = tree.getPaid(node.getId(), e.getKey());
                if (paid > 0) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(node.getId())
                        .append(':')
                        .append(e.getKey())
                        .append(':')
                        .append(paid);
                }
            }
        }
        return sb.toString();
    }

    /**
     * t61 (server): consumes the staging items into the selected node's paid record and
     * activates the node once every cost entry is fulfilled (PAY_UPGRADE_COST semantics).
     */
    private void submitUpgradeMaterialsServer() {
        String nodeId = selectedUpgradeNode;
        UpgradeNode node = nodeId == null ? null : upgradeTree.getNode(nodeId);
        if (node == null || !upgradeTree.canActivate(nodeId)) return;
        java.util.Map<String, Integer> cost = node.getMaterialCost();
        if (!cost.isEmpty()) {
            // Consume staging items matching cost entries (t77: key = unlocalizedName@damage —
            // GT ingots share the unlocalized name and differ by damage, so the bare name would
            // let any GT ingot pay any ingot cost).
            for (int i = 0; i < upgradeStaging.length; i++) {
                ItemStack stack = upgradeStagingHandler.getStackInSlot(i);
                if (stack == null) continue;
                String key = ecoaegtnh.upgrade.UpgradeCosts.keyOf(stack);
                Integer need = cost.get(key);
                if (need == null) continue;
                int paid = upgradeTree.getPaid(nodeId, key);
                int remaining = need - paid;
                if (remaining <= 0) continue;
                int take = Math.min(stack.stackSize, remaining);
                upgradeTree.addPayment(nodeId, key, take);
                stack.stackSize -= take;
                if (stack.stackSize <= 0) {
                    upgradeStagingHandler.setStackInSlot(i, null);
                }
            }
        }
        if (upgradeTree.isCostFulfilled(nodeId)) {
            upgradeTree.activate(nodeId);
            upgradeTree.clearPaid(nodeId);
            markDirty();
            broadcastUpgradeActivated(node);
        } else {
            markDirty();
        }
    }

    /** Server-wide chat notice when a node activates. */
    private void broadcastUpgradeActivated(UpgradeNode node) {
        net.minecraft.server.MinecraftServer.getServer()
            .getConfigurationManager()
            .sendChatMsg(
                new net.minecraft.util.ChatComponentTranslation(
                    "ecoaegtnh.gui.upgrade.activated",
                    StatCollector.translateToLocal(node.getNameKey())));
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        // 284（t7）：同 E-Storage（MTEEcoStorageArray.drawTexts）——基类在 5.09.51.482 无条件
        // 添加"软锤启动"闲置提示行，临时列过滤后搬回（见 EcoMachineTooltipFilter）。
        screenElements.setSynced(false);
        screenElements.setSpace(0);
        DynamicPositionedColumn tmp = new DynamicPositionedColumn();
        super.drawTexts(tmp, inventorySlot);
        for (com.gtnewhorizons.modularui.api.widget.Widget w : tmp.getChildren()) {
            if (EcoMachineTooltipFilter.isIdleHintLine(w)) {
                continue;
            }
            screenElements.widget(w);
        }

        screenElements.widget(
            TextWidget.dynamicString(() -> structureRow())
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> mMachine, val -> syncStructureValid = val));

        screenElements.widget(
            TextWidget.dynamicString(() -> channelRow())
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> isChannelActive(), val -> syncChannelActive = val));

        screenElements.widget(
            TextWidget.dynamicString(() -> bytesRow())
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft))
            .widget(new FakeSyncWidget.LongSyncer(() -> totalBytes, val -> syncTotalBytes = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> getAvailableBytes(), val -> syncAvailableBytes = val))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> isRedLineTriggered(), val -> syncRedLineTriggered = val));

        screenElements.widget(
            TextWidget.dynamicString(() -> parallelismRow())
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> parallelismTotal, val -> syncParallelism = val));

        screenElements.widget(
            TextWidget.dynamicString(() -> threadRow())
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> virtualCPU != null, val -> syncStandbyVCPU = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> getActiveTaskCount(), val -> syncActiveTaskCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> getThreadsUsed(), val -> syncThreadsUsed = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> getThreadsTotal(), val -> syncThreadsTotal = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> getHyperThreadsUsed(), val -> syncHyperUsed = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> getHyperThreadsTotal(), val -> syncHyperTotal = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> getBuiltinThreadsUsed(), val -> syncBuiltinThreadsUsed = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> getBuiltinThreads(), val -> syncBuiltinThreadsTotal = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> getBuiltinHyperThreadsUsed(), val -> syncBuiltinHyperUsed = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> getBuiltinHyperThreads(), val -> syncBuiltinHyperTotal = val));

        // t114i: hyper-thread slots moved to their own row (threadRow() keeps the thread segment
        // only, so long lines no longer overflow the screen). The widget is DISABLED while no
        // hyper slot exists — DynamicPositionedColumn skips disabled children in the layout, so
        // machines without hyper threads show no extra/blank line.
        screenElements.widget(
            TextWidget.dynamicString(() -> hyperRow())
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(w -> syncHyperTotal > 0));
    }

    // ------------------------------------------------------------------
    // Text-screen rows (client-side, read the sync* fields)
    // ------------------------------------------------------------------

    /** "缁撴瀯: 鎴愬瀷/鏈垚鍨?. */
    private String structureRow() {
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.structure")
            + " "
            + (syncStructureValid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
            + StatCollector
                .translateToLocal(syncStructureValid ? "ecoaegtnh.gui.ecal.valid" : "ecoaegtnh.gui.ecal.invalid");
    }

    /** "ME 閫氶亾: 宸茶繛鎺?鏈繛鎺?缂哄け". */
    private String channelRow() {
        String state;
        if (syncChannelActive) {
            state = EnumChatFormatting.GREEN + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel.connected");
        } else if (syncStructureValid) {
            state = EnumChatFormatting.RED + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel.offline");
        } else {
            state = EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel.missing");
        }
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel") + " " + state;
    }

    /** "瀛楄妭(宸茬敤/鍙敤/鎬婚噺): X / Y / Z" + red-line marker. */
    private String bytesRow() {
        String row = EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.bytes")
            + " "
            + EnumChatFormatting.WHITE
            + formatCompact(syncTotalBytes - syncAvailableBytes)
            + EnumChatFormatting.GRAY
            + " / "
            + EnumChatFormatting.GOLD
            + formatCompact(syncAvailableBytes)
            + EnumChatFormatting.GRAY
            + " / "
            + EnumChatFormatting.GOLD
            + formatCompact(syncTotalBytes);
        if (syncRedLineTriggered) {
            row += EnumChatFormatting.RED + " ("
                + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.bytes.redline.short")
                + ")";
        }
        return row;
    }

    /** "骞惰: N". */
    private String parallelismRow() {
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.parallelism")
            + " "
            + EnumChatFormatting.GOLD
            + syncParallelism;
    }

    /**
     * t45/t114g/t114i: thread row — built-in and external slots shown SEPARATELY, totals
     * together: "线程：内置 u/t · 外置 u/t · 总计 u/t". The hyper-thread slots moved to their
     * own row ({@link #hyperRow()}), so no single line overflows the screen.
     */
    private String threadRow() {
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads")
            + " "
            + EnumChatFormatting.GOLD
            + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.builtin")
            + " "
            + EnumChatFormatting.GOLD
            + syncBuiltinThreadsUsed
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.GOLD
            + syncBuiltinThreadsTotal
            + EnumChatFormatting.GRAY
            + " \u00b7 "
            + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.external")
            + " "
            + EnumChatFormatting.GOLD
            + (syncThreadsUsed - syncBuiltinThreadsUsed)
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.GOLD
            + (syncThreadsTotal - syncBuiltinThreadsTotal)
            + EnumChatFormatting.GRAY
            + " \u00b7 "
            + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.total")
            + " "
            + EnumChatFormatting.GOLD
            + syncThreadsUsed
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.GOLD
            + syncThreadsTotal;
    }

    /**
     * t114i: hyper-thread row — "超线程：内置 u/t · 外置 u/t · 总计 u/t", shown only while any
     * hyper slot exists. The drawTexts widget is disabled when {@code syncHyperTotal <= 0} and
     * DynamicPositionedColumn skips disabled children, so machines without hyper threads get no
     * extra/blank line; the empty-string guard is a belt-and-suspenders for the text supplier.
     */
    private String hyperRow() {
        if (syncHyperTotal <= 0) {
            return "";
        }
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.hyper")
            + " "
            + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.builtin")
            + " "
            + EnumChatFormatting.GOLD
            + syncBuiltinHyperUsed
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.GOLD
            + syncBuiltinHyperTotal
            + EnumChatFormatting.GRAY
            + " \u00b7 "
            + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.external")
            + " "
            + EnumChatFormatting.GOLD
            + (syncHyperUsed - syncBuiltinHyperUsed)
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.GOLD
            + (syncHyperTotal - syncBuiltinHyperTotal)
            + EnumChatFormatting.GRAY
            + " \u00b7 "
            + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.threads.total")
            + " "
            + EnumChatFormatting.GOLD
            + syncHyperUsed
            + EnumChatFormatting.GRAY
            + "/"
            + EnumChatFormatting.GOLD
            + syncHyperTotal;
    }

    /** "鑰楄兘: X AE/t" 鈥?t40: screen row removed; kept as the status-LED tooltip line. */
    private String powerRow() {
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal(
            "ecoaegtnh.gui.ecal.power") + " " + EnumChatFormatting.GOLD + formatPower(syncIdlePowerUsage) + " AE/t";
    }

    /** Status-LED tooltip: ME channel + structure + power usage. */
    private java.util.List<String> statusLedTooltip() {
        java.util.List<String> list = new ArrayList<>();
        String channel;
        if (syncChannelActive) {
            channel = EnumChatFormatting.GREEN + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel.connected");
        } else if (syncStructureValid) {
            channel = EnumChatFormatting.RED + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel.offline");
        } else {
            channel = EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel.missing");
        }
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.channel") + " " + channel);
        list.add(structureRow());
        list.add(powerRow());
        return list;
    }

    /** Byte-pool LED tooltip: used/available/total + the 10% red-line state. */
    private java.util.List<String> bytesLedTooltip() {
        java.util.List<String> list = new ArrayList<>();
        long used = Math.max(0, syncTotalBytes - syncAvailableBytes);
        int pct = syncTotalBytes > 0 ? (int) (used * 100 / syncTotalBytes) : 0;
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.led.bytes")
                + " "
                + EnumChatFormatting.WHITE
                + formatCompact(used)
                + EnumChatFormatting.GRAY
                + " / "
                + EnumChatFormatting.GOLD
                + formatCompact(syncAvailableBytes)
                + EnumChatFormatting.GRAY
                + " / "
                + EnumChatFormatting.GOLD
                + formatCompact(syncTotalBytes)
                + EnumChatFormatting.GRAY
                + " ("
                + EnumChatFormatting.GOLD
                + pct
                + "%"
                + EnumChatFormatting.GRAY
                + ")");
        if (syncRedLineTriggered) {
            list.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.led.bytes.redline_hit"));
        } else {
            list.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.led.bytes.redline_ok"));
        }
        return list;
    }

    /** Parallelism LED tooltip. */
    private java.util.List<String> parallelLedTooltip() {
        java.util.List<String> list = new ArrayList<>();
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.led.parallelism")
                + " "
                + EnumChatFormatting.GOLD
                + syncParallelism);
        return list;
    }

    /** Thread-core LED tooltip: cores / active tasks / standby vCPU. */
    private java.util.List<String> coreLedTooltip() {
        java.util.List<String> list = new ArrayList<>();
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.led.cores")
                + " "
                + EnumChatFormatting.GOLD
                + syncThreadCoreCount);
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.tasks")
                + " "
                + EnumChatFormatting.GOLD
                + syncActiveTaskCount);
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.vcpu")
                + " "
                + (syncStandbyVCPU ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                + StatCollector.translateToLocal(
                    syncStandbyVCPU ? "ecoaegtnh.gui.ecal.vcpu.ready" : "ecoaegtnh.gui.ecal.vcpu.none"));
        return list;
    }

    // ------------------------------------------------------------------
    // GUI data sources (server-side FakeSyncWidget suppliers)
    // ------------------------------------------------------------------

    /** ME channel proxy active (formed + grid-connected). */
    public boolean isChannelActive() {
        return channel != null && channel.getProxy() != null
            && channel.getProxy()
                .isActive();
    }

    /** Total in-flight clusters across all thread drives + built-in slots. */
    public int getActiveTaskCount() {
        int n = builtinThreadClusters.size() + builtinHyperClusters.size();
        for (TileEcalThreadDrive core : threadCores) {
            n += core.getCPUs()
                .size();
        }
        return n;
    }

    // ------------------------------------------------------------------
    // t114g: built-in thread slots (1 base + B1 +3 threads / B2 +2 hyper threads).
    // ------------------------------------------------------------------

    /** t114g: built-in NORMAL thread slots — 1 base, +3 when the B1 node is activated (→ 4). */
    public int getBuiltinThreads() {
        return upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.B1) ? 4 : 1;
    }

    /** t114g: built-in HYPER thread slots — +2 when the B2 node is activated. */
    public int getBuiltinHyperThreads() {
        return upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.B2) ? 2 : 0;
    }

    /** t114g: built-in normal slots in use. */
    public int getBuiltinThreadsUsed() {
        return builtinThreadClusters.size();
    }

    /** t114g: built-in hyper slots in use. */
    public int getBuiltinHyperThreadsUsed() {
        return builtinHyperClusters.size();
    }

    /** t114g (plan C): chat notice when a job was rejected by the +10% hyper reserve pre-check. */
    public void notifyJobRejected(appeng.api.networking.security.BaseActionSource src, long jobBytes,
        long availableBytes) {
        try {
            net.minecraft.entity.player.EntityPlayerMP player = src instanceof appeng.api.networking.security.PlayerSource
                ? (net.minecraft.entity.player.EntityPlayerMP) ((appeng.api.networking.security.PlayerSource) src).player
                : null;
            if (player != null) {
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "ecoaegtnh.ecal.vcpu.submit_rejected",
                        formatCompact(jobBytes * 11 / 10),
                        formatCompact(availableBytes)));
            }
        } catch (Throwable ignored) {
            // never let the rejection notice break the submit path
        }
    }

    /** t45: normal thread slots in use 鈥?built-in + 危 (per-drive clusters not assigned as hyper). */
    public int getThreadsUsed() {
        int n = builtinThreadClusters.size();
        for (TileEcalThreadDrive core : threadCores) {
            for (CraftingCPUCluster cpu : core.getCPUs()) {
                if (!ECPUCluster.from(cpu)
                    .ecoaegtnh$isHyperAssigned()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** t45/t114g: normal thread slots total 鈥?built-in + 危 inserted thread cores' normal slots. */
    public int getThreadsTotal() {
        int n = getBuiltinThreads();
        for (TileEcalThreadDrive core : threadCores) {
            n += core.getThreads();
        }
        return n;
    }

    /** t45/t114g: hyper slots in use 鈥?built-in + 危 (per-drive clusters assigned as hyper). */
    public int getHyperThreadsUsed() {
        int n = builtinHyperClusters.size();
        for (TileEcalThreadDrive core : threadCores) {
            for (CraftingCPUCluster cpu : core.getCPUs()) {
                if (ECPUCluster.from(cpu)
                    .ecoaegtnh$isHyperAssigned()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** t45/t114g: hyper slots total 鈥?built-in + 危 inserted thread cores' hyper slots. */
    public int getHyperThreadsTotal() {
        int n = getBuiltinHyperThreads();
        for (TileEcalThreadDrive core : threadCores) {
            n += core.getHyperThreads();
        }
        return n;
    }

    /**
     * t50 red line: available pool (capped by the activated cell-chain upgrade node) below 10% of the
     * effective pool 鈥?5% in overclock mode (plan 搂7.4 / docs 搂3.3).
     */
    public boolean isRedLineTriggered() {
        long avail = Math.min(getAvailableBytes(), getBytePoolCap());
        return avail < redLineThreshold();
    }

    /** t60: the host's upgrade tree (node-activation gates). */
    public ecoaegtnh.upgrade.UpgradeTree getUpgradeTree() {
        return upgradeTree;
    }

    /**
     * t60 瓒呴妯″紡 (鍙嶈浆褰╄泲, docs 搂2): driven by the OC upgrade node 鈥?10% red line relaxes
     * to 5% and hyper-thread +10% extra storage is free once OC is activated.
     */
    public boolean isOverclocked() {
        return upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.OC);
    }

    /**
     * t65/t114: byte-pool cap by the activated cell main-chain node — N11 (Singularity) and N10
     * lift the cap to unlimited; N2 (256k) = 12M, N3 (1024k) = 64M, N4 (4096k) = 256M,
     * N5 (16M) = 1G, N6 (64M) = 4G, N7 (256M) = 16G, N8 (1024M) = 64G, N9 (4096M) = 256G.
     */
    public long getBytePoolCap() {
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N11)
            || upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N10)) return Long.MAX_VALUE;
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N9)) return BYTE_POOL_CAP[7];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N8)) return BYTE_POOL_CAP[6];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N7)) return BYTE_POOL_CAP[5];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N6)) return BYTE_POOL_CAP[4];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N5)) return BYTE_POOL_CAP[3];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N4)) return BYTE_POOL_CAP[2];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N3)) return BYTE_POOL_CAP[1];
        if (upgradeTree.isActivated(ecoaegtnh.upgrade.CalculatorUpgradeTree.N2)) return BYTE_POOL_CAP[0];
        return BYTE_POOL_CAP[0]; // N1 (free) → 12M
    }

    /** t50: red-line threshold 鈥?10% of the effective pool (5% in overclock mode). */
    private long redLineThreshold() {
        long cap = getBytePoolCap();
        long base = cap == Long.MAX_VALUE ? totalBytes : Math.min(totalBytes, cap);
        return base / (isOverclocked() ? 20 : 10);
    }

    /** t56: upgrade/unlock overview button tooltip. */
    private java.util.List<String> upgradeButtonTooltip() {
        java.util.List<String> list = new ArrayList<>();
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.upgrade.button"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.upgrade.button_tooltip"));
        return list;
    }

    /** t56: upgrade button background (icon texture from T57). t114q 已回退——恢复自家材质。 */
    private static IDrawable[] upgradeButtonBackground() {
        return new IDrawable[] { TecTechUITextures.BUTTON_STANDARD_LIGHT_16x16,
            com.gtnewhorizons.modularui.api.drawable.UITexture.fullImage("ecoaegtnh", "gui/ecal_upgrade_button") };
    }

    /** Compact number formatting for the byte readout (e.g. 2.4M / 16.8M). */
    private static String formatCompact(long v) {
        if (v >= 1_000_000_000L) {
            return String.format("%.1fB", v / 1e9);
        }
        if (v >= 1_000_000L) {
            return String.format("%.1fM", v / 1e6);
        }
        if (v >= 1_000L) {
            return String.format("%.1fK", v / 1e3);
        }
        return String.valueOf(v);
    }

    /** AE idle-power formatting: integers as-is, fractions with one decimal. */
    private static String formatPower(double p) {
        if (p == Math.floor(p)) {
            return String.valueOf((long) p);
        }
        return String.format("%.1f", p);
    }
}
