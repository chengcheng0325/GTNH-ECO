package ecoaegtnh.metatileentity;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlockAnyMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.ae2.EcoStorageCellHandler;
import ecoaegtnh.block.estorage.BlockEcoStorageCapacitance;
import ecoaegtnh.block.estorage.BlockEcoStorageCasing;
import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.block.estorage.BlockEcoStorageMEBus;
import ecoaegtnh.block.estorage.BlockEcoStorageVent;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;
import ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia;
import ecoaegtnh.item.estorage.ItemEcoStorageCellFluid;
import ecoaegtnh.item.estorage.ItemEcoStorageCellItem;
import ecoaegtnh.tile.estorage.TileEcoStorageCapacitance;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;
import ecoaegtnh.tile.estorage.TileEcoStorageMEBus;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.structure.error.TranslatableText;
import gregtech.api.util.MultiblockTooltipBuilder;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

/**
 * ECO E-Storage Array controller (GT multiblock), L4/L6/L9 = tiers A/B/C.
 * <p>
 * Structure per DESIGN.md §1.7/§2.5 (t30 layout, user-confirmed): the controller anchor is the
 * head's front slice; the 1..12 drive columns extend to the RIGHT of the controller (the facing's
 * right-hand side, perpendicular to the front) — one 2-deep x 3-tall column per rightward step —
 * and the 2-deep x 3-tall head (controller + ME bus + 10 casing) sits at the column root, with the
 * ME bus in the controller's back slice at the right corner. Each drive column: 3 drive bays on the
 * controller plane (front half, C=0), 2 capacitance at the back plane (C=1, top/bottom) + 1 vent
 * (C=1, middle). The column far end is a full 2x3 casing face.
 * <p>
 * The StructureLib shape is orientation-independent: shape position (A=n+2, B=1, C=0) is the
 * controller anchor; A=0 is the far end of the columns, A=1..n the column slices, A=n+1 the head
 * right slice, A=n+2 the controller slice; C=0 is the controller plane, C=1 the back plane. The
 * structure is checked with base offsets (n+2, 1, 0).
 * <p>
 * t32 migration: extends TecTech {@code TTMultiblockBase} (real TecTech ModularUI2 GUI via
 * {@code TTMultiblockBaseGui}; theme switched to INTERGALACTIC_STANDARD in t43), pure AE power
 * (no GT EU energy hatches — all power comes from the connected ME network through the ME bus),
 * and no maintenance ({@code hasMaintenanceChecks = false}, inherited from MTEMultiBlockBase).
 */
public class MTEEcoStorageArray extends TTMultiblockBase implements ISurvivalConstructable {

    public static final int TIER_A = 0; // L4
    public static final int TIER_B = 1; // L6
    public static final int TIER_C = 2; // L9

    /** Structure piece name prefix; shapes "size1".."size12". */
    private static final String PIECE_PREFIX = "size";
    private static final int MAX_DRIVE_COLUMNS = 12;

    private static final IStructureDefinition<MTEEcoStorageArray> STRUCTURE_DEFINITION = buildDefinitions();

    /**
     * Drive-bay structure element (t32 fix): accepts a drive bay of any facing (metadata 2-5 from
     * t25), but PLACES it with a proper facing instead of the meta-0 default (which rendered every
     * autoplaced bay as facing north): creative/hologram autoplace faces the bay like the
     * controller's front; survival autoplace hands the item to the actor so the bay's own
     * onBlockPlacedBy derives the facing from the player's look direction.
     */
    private static final class DriveElement implements IStructureElement<MTEEcoStorageArray> {

        @Override
        public boolean check(MTEEcoStorageArray t, World world, int x, int y, int z) {
            return world.getBlock(x, y, z) == BlockEcoStorageDrive.INSTANCE;
        }

        @Override
        public boolean spawnHint(MTEEcoStorageArray t, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, BlockEcoStorageDrive.INSTANCE, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTEEcoStorageArray t, World world, int x, int y, int z, ItemStack trigger) {
            world.setBlock(x, y, z, BlockEcoStorageDrive.INSTANCE, facingToDriveMeta(t), 2);
            return true;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTEEcoStorageArray t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            // The item placement path triggers BlockEcoStorageDrive.onBlockPlacedBy, which derives
            // the facing from the player's look direction (natural when building by hand).
            return StructureUtility.survivalPlaceBlock(
                BlockEcoStorageDrive.INSTANCE,
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
        public BlocksToPlace getBlocksToPlace(MTEEcoStorageArray t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            return BlocksToPlace.create(BlockEcoStorageDrive.INSTANCE, facingToDriveMeta(t));
        }

        /** Drive-bay metadata facing matching the controller's front (2=N, 3=S, 4=W, 5=E). */
        private static int facingToDriveMeta(MTEEcoStorageArray t) {
            ForgeDirection dir = t.getExtendedFacing()
                .getDirection();
            if (dir == ForgeDirection.SOUTH) return BlockEcoStorageDrive.META_SOUTH;
            if (dir == ForgeDirection.WEST) return BlockEcoStorageDrive.META_WEST;
            if (dir == ForgeDirection.EAST) return BlockEcoStorageDrive.META_EAST;
            return BlockEcoStorageDrive.META_NORTH;
        }
    }

    private static IStructureDefinition<MTEEcoStorageArray> buildDefinitions() {
        StructureDefinition.Builder<MTEEcoStorageArray> builder = StructureDefinition.<MTEEcoStorageArray>builder()
            // Pure AE power (t32): the shell is plain casing; GT energy hatches are no longer part
            // of the structure (all power comes from the ME network via the ME bus).
            .addElement('C', ofBlock(BlockEcoStorageCasing.INSTANCE, 0))
            // 'D' accepts any meta (placed drive bays hold their horizontal facing 2-5 in the
            // metadata, t25) and places bays with a sensible facing (t32 autoplace fix).
            .addElement('D', new DriveElement())
            .addElement('E', ofBlockAnyMeta(BlockEcoStorageCapacitance.INSTANCE))
            .addElement('V', ofBlock(BlockEcoStorageVent.INSTANCE, 0))
            .addElement('M', ofBlock(BlockEcoStorageMEBus.INSTANCE, 0));

        // Shape axes (StructureLib): outer String[] = C slices (front-back), inner string = B lines
        // (top-bottom), chars = A (left-right). Controller anchor '~' sits at (A=n+2, B=1, C=0); the
        // structure is checked with base offsets (n+2, 1, 0).
        // Layout (world mapping for a controller facing EAST: C=0 = x=0 controller plane, C=1 = x=-1
        // back plane, A+ = north/left, A- = south/right; the columns extend along A- = +z = right):
        // A=0 (far end of the drive columns, z=+n+2): full 2x3 end-cap casing face
        // A=1..n (drive column slices, z=+n+1..+2): C=0 plane = D/D/D (3 drives), C=1 plane = E/V/E
        // A=n+1 (head right slice, z=+1): C=1 plane has the ME bus at B=1 (back-side right corner)
        // A=n+2 (controller slice, z=0): C=0 plane has the controller at B=1, rest casing
        for (int n = 1; n <= MAX_DRIVE_COLUMNS; n++) {
            // Per B line, chars A=0..n+2: A=0 = end cap casing, A=1..n = column cells,
            // A=n+1 = head right slice, A=n+2 = controller slice.
            // (Java 8 runtime: no String.repeat, so use the local fill helper.)
            String driveRow = "C" + repeat('D', n) + "CC"; // C=0 plane, B=0/2 (drive top/bottom)
            String driveMidRow = "C" + repeat('D', n) + "C~"; // C=0 plane, B=1 (drive + controller)
            String capRow = "C" + repeat('E', n) + "CC"; // C=1 plane, B=0/2 (capacitance top/bottom)
            String ventMidRow = "C" + repeat('V', n) + "MC"; // C=1 plane, B=1 (vent + ME bus)
            String[][] slices = { { driveRow, driveMidRow, driveRow }, { capRow, ventMidRow, capRow }, };
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

    /**
     * t62: upgrade tree for the storage array — 12 nodes across three independent chains
     * (docs/ECO_UPGRADE_TREE_DESIGN.md §3: item I1★-I4 / fluid F1★-F4 / essentia E1★-E4); cell
     * insertion and the formation check are gated by node activation. The three-layer GUI
     * (ids 300/301/302) is shared with the calculator host via ecoaegtnh.upgrade.UpgradeTreeGui.
     */
    protected final ecoaegtnh.upgrade.UpgradeTree upgradeTree = ecoaegtnh.upgrade.StorageUpgradeTree.newInstance();

    /** t61: server-side selected node id (client sync target: syncUpgradeSelected). */
    private String selectedUpgradeNode = null;

    /** t61: staging slots for the material-submit window (16; not persisted — cleared on restart). */
    protected final net.minecraft.item.ItemStack[] upgradeStaging = new net.minecraft.item.ItemStack[16];

    /** t61: MUI1-visible handler over the staging slots (separate from the GT mInventory). */
    protected final com.cleanroommc.modularui.utils.item.ItemStackHandler upgradeStagingHandler = new com.cleanroommc.modularui.utils.item.ItemStackHandler(
        upgradeStaging);

    // t61 GUI sync targets (client-side suppliers read these; server suppliers push via
    // FakeSyncWidget.StringSyncer).
    private String syncUpgradeActivated = "";
    private String syncUpgradeSelected = "";
    private String syncUpgradePaid = "";

    // Parts collected during structure check.
    protected final List<TileEcoStorageDrive> driveBays = new ArrayList<>();
    protected final PriorityQueue<TileEcoStorageCapacitance> energyCellsMin = new PriorityQueue<>(
        Comparator.comparingDouble(TileEcoStorageCapacitance::getEnergyStored));
    protected final PriorityQueue<TileEcoStorageCapacitance> energyCellsMax = new PriorityQueue<>(
        Comparator.comparingDouble(TileEcoStorageCapacitance::getEnergyStored)
            .reversed());
    protected TileEcoStorageMEBus meBus = null;
    protected int driveColumnLength = 0;
    /** Previously assembled parts, to disassemble the ones that disappear. */
    protected final List<TileEcoStorageDrive> prevDriveBays = new ArrayList<>();
    protected final List<TileEcoStorageCapacitance> prevCaps = new ArrayList<>();
    protected TileEcoStorageMEBus prevMeBus = null;

    /**
     * t58: MUI1 GUI sync targets. The MUI1 text suppliers run CLIENT-side, where the controller's
     * driveBays/energy lists are empty — so the FakeSyncWidget setters write the server-synced
     * values into these fields and the text suppliers read them (direct reads showed 0).
     */
    private boolean syncStructureValid = false;
    private int syncDriveCount = 0;
    private int syncDriveColumnLength = 0;
    private boolean syncMeBusConnected = false;
    private long syncEnergyStored = 0;
    private long syncEnergyMax = 0;
    /** t96: computed AE idle power usage (AE/t) — server-side value (t69 plan B+C). */
    private double idlePowerUsage = 0;
    /** t96: client-side copy of {@link #idlePowerUsage} for the status-LED tooltip. */
    private double syncIdlePowerUsage = 0;
    // t73: per-cell-family stats for the IO LED hover tooltips (server-synced, client-read).
    private int syncItemCellCount = 0;
    private int syncItemStoredTypes = 0;
    private int syncItemTotalTypes = 0;
    private long syncItemUsedBytes = 0;
    private long syncItemTotalBytes = 0;
    private int syncFluidCellCount = 0;
    private int syncFluidStoredTypes = 0;
    private int syncFluidTotalTypes = 0;
    private long syncFluidUsedBytes = 0;
    private long syncFluidTotalBytes = 0;
    private int syncEssentiaCellCount = 0;
    private int syncEssentiaStoredTypes = 0;
    private int syncEssentiaTotalTypes = 0;
    private long syncEssentiaUsedBytes = 0;
    private long syncEssentiaTotalBytes = 0;

    public MTEEcoStorageArray(int aID, String aName, String aNameRegional, int tier) {
        super(aID, aName, aNameRegional);
        this.tier = tier;
    }

    public MTEEcoStorageArray(String aName, int tier) {
        super(aName);
        this.tier = tier;
    }

    /**
     * t44 (no-maintenance root fix): the field {@code hasMaintenanceChecks} is initialized from
     * this method BEFORE the constructor body runs, so the base {@code MTEMultiBlockBase}
     * constructor's {@code if (!shouldCheckMaintenance()) fixAllIssues();} now runs with the field
     * already false — all six maintenance bits (mWrench..mCrowbar) are set true from the very
     * start. Setting the field in the constructor body was too late: at super() time it still had
     * the default {@code true}, so {@code fixAllIssues()} was skipped on fresh placement and the
     * machine could be reported/stopped as "machine damage" (NO_REPAIR), which then persisted in
     * the tile NBT and kept showing in the GUI.
     */
    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * t79: hide the base-class "soft mallet to start" idle hints
     * (gt.interact.desc.mb.idle.1/2/3 + the running line) — this is a pure AE machine that never
     * "runs" (checkProcessing_EM returns NONE, isActive stays false), so those lines were
     * permanently displayed. MTEMultiBlockBase.drawTexts gates them behind this hook (base
     * default true; same pattern as kekztech MTELapotronicSuperCapacitor / tectech
     * MTEActiveTransformer). TTMultiblockBase adds no idle-hint logic of its own, so this single
     * override is sufficient.
     */
    @Override
    public boolean showMachineStatusInGUI() {
        return false;
    }

    public int getTier() {
        return tier;
    }

    /** t62: the array's upgrade tree (12 storage nodes across the item/fluid/essentia chains). */
    public ecoaegtnh.upgrade.UpgradeTree getUpgradeTree() {
        return upgradeTree;
    }

    public List<TileEcoStorageDrive> getDriveBays() {
        return driveBays;
    }

    private java.util.List<String> upgradeButtonTooltip() {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add(
            net.minecraft.util.EnumChatFormatting.GOLD
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.ecal.upgrade.button"));
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.ecal.upgrade.button_tooltip"));
        return list;
    }

    private static com.gtnewhorizons.modularui.api.drawable.IDrawable[] upgradeButtonBackground() {
        // t114q 已回退——恢复自家材质（ecal_upgrade_button）。
        return new com.gtnewhorizons.modularui.api.drawable.IDrawable[] {
            tectech.thing.gui.TecTechUITextures.BUTTON_STANDARD_LIGHT_16x16,
            com.gtnewhorizons.modularui.api.drawable.UITexture.fullImage("ecoaegtnh", "gui/ecal_upgrade_button") };
    }

    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        // t62: upgrade-tree activation state (12 storage nodes; activation persists).
        net.minecraft.nbt.NBTTagCompound treeTag = new net.minecraft.nbt.NBTTagCompound();
        upgradeTree.writeToNBT(treeTag);
        aNBT.setTag("upgradeTree", treeTag);
    }

    /**
     * t79 (godforge MTEForgeOfGods.setItemNBT:1001-1017 同款): GT calls this when the machine is
     * mined — the full NBT (incl. this array's upgrade tree) goes into the dropped item, so
     * placing the drop restores the unlocks through loadNBTData. Each array keeps its OWN tree
     * instance (StorageUpgradeTree.newInstance()), so two arrays never share unlocks.
     */
    @Override
    public void setItemNBT(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("upgradeTree")) {
            upgradeTree.readFromNBT(aNBT.getCompoundTag("upgradeTree"));
        }
    }

    public TileEcoStorageMEBus getMEBus() {
        return meBus;
    }

    /**
     * t50: ME-bus grid connection status for the GUI IO strip (synced to the client; the getter
     * is evaluated server-side by the sync framework). True when the structure has an ME bus and
     * its AE grid proxy is active.
     */
    public boolean isMEBusConnected() {
        return meBus != null && meBus.getProxy() != null
            && meBus.getProxy()
                .isActive();
    }

    public int getDriveColumnLength() {
        return driveColumnLength;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEEcoStorageArray(this.mName, this.tier);
    }

    public boolean isStructureValid() {
        return mMachine;
    }

    // ------------------------------------------------------------------
    // IAlignment (MTEEnhancedMultiBlockBase implements IAlignment and manages the ExtendedFacing
    // end-to-end: placement facing sync via onFacingChange -> toolSetDirection -> setExtendedFacing,
    // NBT persistence, wrench alignment changes and client sync. Overriding getExtendedFacing/
    // setExtendedFacing here caused a setFrontFacing -> onFacingChange -> ... recursion at placement
    // (t35 crash); TecTech machines (MTEDataBank, MTEQuantumComputer) do not override them. Only
    // getAlignmentLimits restricts the allowed orientations: horizontal directions, no rotation,
    // no flip — the base uses it to validate/correct every alignment change.)
    // ------------------------------------------------------------------

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && f.isNotFlipped();
    }

    // ------------------------------------------------------------------
    // No maintenance (t32 user-confirmed; t37 GUI fix)
    // ------------------------------------------------------------------

    /**
     * t37: hide the terminal maintenance-status icon. The base implementation returns
     * {@code getDefaultHasMaintenanceChecks()} (hard-coded true), so the MUI2 terminal rendered
     * the maintenance hoverable ("needs maintenance" / tool list) even with
     * {@code hasMaintenanceChecks = false}. Returning {@code shouldCheckMaintenance()} keeps the
     * icon in sync with the actual maintenance setting: false here (t32 disables maintenance), so
     * the icon is never shown and no maintenance hatch/tools are ever required.
     */
    @Override
    public boolean supportsMaintenanceIssueHoverable() {
        return shouldCheckMaintenance();
    }

    // ------------------------------------------------------------------
    // Structure check
    // ------------------------------------------------------------------

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        driveBays.clear();
        energyCellsMin.clear();
        energyCellsMax.clear();
        meBus = null;
        driveColumnLength = 0;

        boolean ok = false;
        for (int n = MAX_DRIVE_COLUMNS; n >= 1; n--) {
            // Controller anchor '~' sits at shape (A=n+2, B=1, C=0), so the structure is checked
            // with base offsets (n+2, 1, 0): n+2 blocks to the right of the controller (the far end
            // of the columns).
            if (STRUCTURE_DEFINITION.check(
                this,
                PIECE_PREFIX + n,
                aBaseMetaTileEntity.getWorld(),
                getExtendedFacing(),
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord(),
                n + 2,
                1,
                0,
                true)) {
                driveColumnLength = n;
                ok = true;
                break;
            }
        }
        if (!ok) {
            disassembleAll();
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        scanStructureVolume(aBaseMetaTileEntity, errors);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        List<StructureError> errors = new ArrayList<>();
        checkMachine(aBaseMetaTileEntity, aStack, errors);
        return errors.isEmpty();
    }

    /**
     * Iterates every cell of the matched shape using the same facing-relative conversion as the
     * structure check, and collects the part tiles (drive bays, capacitance cells, ME bus).
     */
    private void scanStructureVolume(IGregTechTileEntity base, List<StructureError> errors) {
        int aMax = driveColumnLength + 2;
        int offsetA = driveColumnLength + 2;
        ExtendedFacing facing = getExtendedFacing();
        int[] abc = new int[3];
        int[] xyz = new int[3];
        int baseX = base.getXCoord();
        int baseY = base.getYCoord();
        int baseZ = base.getZCoord();
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
                    if (te instanceof TileEcoStorageDrive drive) {
                        // t55 A: only collect bays that accept ownership by THIS controller
                        // (a bay already claimed by another controller is excluded).
                        if (drive.onAssembled(this)) driveBays.add(drive);
                    } else if (te instanceof TileEcoStorageCapacitance cap) {
                        if (cap.onAssembled(this)) {
                            cap.setCapacityByMeta(
                                base.getWorld()
                                    .getBlockMetadata(wx, wy, wz));
                            energyCellsMin.add(cap);
                            energyCellsMax.add(cap);
                        }
                    } else if (te instanceof TileEcoStorageMEBus bus) {
                        if (bus.onAssembled(this)) {
                            if (meBus == null) meBus = bus;
                            else errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
                        } else {
                            // The ME bus is claimed by another controller — this structure cannot
                            // serve the grid through it.
                            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
                        }
                    }
                    // t32: pure AE power — no GT energy hatches in the structure, so nothing to
                    // register here (all power flows through the ME bus from the AE grid).
                }
            }
        }
        if (driveBays.isEmpty() || meBus == null) {
            disassembleAll();
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        // t62: upgrade-tree gate at formation — close the pre-assembly bypass. While no
        // controller was assembled, TileEcoStorageDrive.isCellSupported() deferred the check
        // (returned true), so an oversized cell (64M without I5/F5/E5, 16384M without I9/F9/E9,
        // 人造宇宙 without I10/F10/E10) could be inserted before formation. Re-validate statically here (bay coordinates +
        // this controller's upgrade tree; the static helper does not touch the bay's controller
        // reference, preserving t55 claim independence): any bay holding a cell whose chain node
        // is not activated fails the structure check with a clear error. Post-formation insertion
        // of an unsupported cell stays rejected by the bay itself (its controller field is set
        // by then).
        for (TileEcoStorageDrive drive : driveBays) {
            net.minecraft.item.ItemStack cell = drive.getCellStack();
            if (cell != null && !TileEcoStorageDrive.isSupportedByUpgradeNode(cell, this)) {
                String nodeId = TileEcoStorageDrive.requiredUpgradeNode(cell);
                String nodeName = nodeId == null ? "?"
                    : net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.upgrade.node." + nodeId + ".name");
                disassembleAll();
                errors.add(
                    StructureErrors
                        .of("ecoaegtnh.structure.error.cell_tier_exceeded", TranslatableText.literal(nodeName)));
                return;
            }
        }

        // Disassemble parts that are no longer present; assemble new ones.
        for (TileEcoStorageDrive old : prevDriveBays) {
            if (!driveBays.contains(old)) old.onDisassembled();
        }
        for (TileEcoStorageCapacitance old : prevCaps) {
            // t67: capacitance is shareable — drop only THIS controller's claim.
            if (!energyCellsMin.contains(old)) old.onDisassembled(this);
        }
        if (prevMeBus != null && prevMeBus != meBus) prevMeBus.onDisassembled();

        for (TileEcoStorageDrive drive : driveBays) {
            if (!prevDriveBays.contains(drive)) drive.onAssembled(this);
        }
        for (TileEcoStorageCapacitance cap : energyCellsMin) {
            if (!prevCaps.contains(cap)) cap.onAssembled(this);
        }
        if (prevMeBus != meBus) meBus.onAssembled(this);

        prevDriveBays.clear();
        prevDriveBays.addAll(driveBays);
        prevCaps.clear();
        prevCaps.addAll(energyCellsMin);
        prevMeBus = meBus;
        recalculateEnergyUsage();
    }

    /** Disassembles all currently/ previously assembled parts. */
    protected void disassembleAll() {
        for (TileEcoStorageDrive drive : driveBays) {
            drive.onDisassembled();
        }
        for (TileEcoStorageDrive old : prevDriveBays) {
            if (!driveBays.contains(old)) old.onDisassembled();
        }
        for (TileEcoStorageCapacitance cap : energyCellsMin) {
            // t67: capacitance is shareable — drop only THIS controller's claim.
            cap.onDisassembled(this);
        }
        for (TileEcoStorageCapacitance old : prevCaps) {
            if (!energyCellsMin.contains(old)) old.onDisassembled(this);
        }
        if (meBus != null) meBus.onDisassembled();
        if (prevMeBus != null && prevMeBus != meBus) prevMeBus.onDisassembled();
        prevDriveBays.clear();
        prevCaps.clear();
        prevMeBus = null;
    }

    /**
     * t59: when the controller block is removed, release every claimed part (onDisassembled clears
     * the ownership), so a re-placed controller can re-form the structure. Without this, the t55
     * ownership claims stayed on the dead controller and the rebuilt structure reported
     * "incomplete" (all parts refused re-claim).
     */
    @Override
    public void onRemoval() {
        super.onRemoval();
        disassembleAll();
    }

    // ------------------------------------------------------------------
    // StructureLib constructable / preview
    // ------------------------------------------------------------------

    /**
     * TTMultiblockBase hook (its {@code getStructureDefinition} is final and delegates here); used
     * by the hologram projector, NEI preview and structure-lib construct/survival machinery.
     */
    @Override
    public IStructureDefinition<MTEEcoStorageArray> getStructure_EM() {
        return STRUCTURE_DEFINITION;
    }

    /**
     * Structure length for build/preview purposes: the formed column length if the machine is
     * assembled, otherwise the GTNH structure channel ("length", defaults to the controller stack
     * size) clamped to 1..12 — so holding a stack of N controllers projects/builds an N-column
     * structure (same convention as MTEAssemblyLine / MTEIndustrialCokeOven).
     */
    private int structureLengthFor(ItemStack stack) {
        return driveColumnLength > 0 ? driveColumnLength
            : gregtech.common.misc.GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stack, 1, MAX_DRIVE_COLUMNS);
    }

    /** @return the A offset for the given drive column length (controller anchor at A=length+2). */
    private static int offsetAFor(int length) {
        return length + 2;
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
            offsetAFor(length),
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
            offsetAFor(length),
            1,
            0,
            elementBudget,
            env,
            false);
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        // t38: localized via lang keys (client chat display by the StructureLib projector).
        return new String[] { net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.columns"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.column_detail"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.head"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.power"),
            net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.structure.desc.length"),
            net.minecraft.util.StatCollector
                .translateToLocalFormatted("ecoaegtnh.structure.desc.current_length", driveColumnLength) };
    }

    // ------------------------------------------------------------------
    // Power aggregation (AE energy, double) - delegated by the ME bus
    // ------------------------------------------------------------------

    public double injectPower(double amt, boolean modulate) {
        double remaining = amt;
        if (modulate) {
            List<TileEcoStorageCapacitance> toReInsert = new ArrayList<>();
            TileEcoStorageCapacitance cell;
            while ((cell = energyCellsMin.poll()) != null) {
                double prev = remaining;
                remaining = cell.injectPower(remaining, false);
                toReInsert.add(cell);
                if (remaining <= 0 || prev <= remaining) break;
            }
            energyCellsMin.addAll(toReInsert);
        } else {
            double simulated = remaining;
            for (TileEcoStorageCapacitance cell : energyCellsMin) {
                simulated = cell.injectPower(simulated, true);
                if (simulated <= 0) break;
            }
            remaining = simulated;
        }
        return remaining;
    }

    public double extractPower(double amt, boolean modulate) {
        double extracted = 0;
        if (modulate) {
            List<TileEcoStorageCapacitance> toReInsert = new ArrayList<>();
            TileEcoStorageCapacitance cell;
            while ((cell = energyCellsMax.poll()) != null) {
                double before = extracted;
                extracted += cell.extractPower(amt - extracted, false);
                toReInsert.add(cell);
                if (extracted >= amt || before >= extracted) break;
            }
            energyCellsMax.addAll(toReInsert);
        } else {
            for (TileEcoStorageCapacitance cell : energyCellsMax) {
                extracted += cell.extractPower(amt - extracted, true);
                if (extracted >= amt) break;
            }
        }
        return extracted;
    }

    public double getEnergyStored() {
        double total = 0;
        for (TileEcoStorageCapacitance cell : energyCellsMax) {
            total += cell.getEnergyStored();
        }
        return total;
    }

    public double getMaxEnergyStore() {
        double total = 0;
        for (TileEcoStorageCapacitance cell : energyCellsMax) {
            total += cell.getMaxEnergyStore();
        }
        return total;
    }

    /**
     * Recompute AE idle power usage and push it to the ME bus proxy.
     * <p>
     * t69 (user-selected plan B+C): {@code idlePowerUsage = tierBase + 0.5 x installedCellCount +
     * SUM idleDrain(installed cells)} — tier base by controller tier (L4=2.0, L6=4.0, L9=8.0),
     * 0.5 per bay that actually HOLDS an ECO cell (empty bays and non-ECO stacks do not count),
     * plus each installed cell's own idle drain. Replaces the old flat {@code 64 + SUM}.
     * Example (L6, 6 bays, 4 installed 16M): 4.0 + 4x0.5 + 4x4.0 = 22 AE/t.
     */
    public void recalculateEnergyUsage() {
        double idleDrain = tierBaseForPower();
        int installedCells = 0;
        for (TileEcoStorageDrive drive : driveBays) {
            ItemStack cell = drive.getCellStack();
            if (cell != null && cell.getItem() instanceof ecoaegtnh.item.estorage.ItemEcoStorageCell ecoCell) {
                installedCells++;
                idleDrain += ecoCell.getIdleDrain();
            }
        }
        idleDrain += installedCells * 0.5;
        this.idlePowerUsage = idleDrain;
        if (meBus != null) {
            meBus.getProxy()
                .setIdlePowerUsage(idleDrain);
        }
    }

    /** t69 plan B: controller-tier base drain — L4=2.0, L6=4.0, L9=8.0. */
    private double tierBaseForPower() {
        if (tier == TIER_C) {
            return 8.0;
        }
        if (tier == TIER_B) {
            return 4.0;
        }
        return 2.0;
    }

    /** Bridge for drive-bay alteration events. */
    public void postAlteration(appeng.api.storage.data.IAEStackType<?> type,
        List<? extends appeng.api.storage.data.IAEStack<?>> changes) {
        if (meBus != null) {
            meBus.postAlteration(type, changes);
        }
    }

    // ------------------------------------------------------------------
    // Processing loop
    // ------------------------------------------------------------------

    /** TTMultiblockBase recipe entry (its {@code checkProcessing} is final); no recipes. */
    @Override
    protected @org.jetbrains.annotations.NotNull CheckRecipeResult checkProcessing_EM() {
        return CheckRecipeResultRegistry.NONE;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (!mMachine) return;
        // t44: a no-maintenance machine can never legitimately stop with NO_REPAIR. If an older
        // build persisted that reason (plus the wasShutdown flag) in the tile NBT, clear it so the
        // GUI never shows "machine damage" again. (With getDefaultHasMaintenanceChecks()==false
        // the maintenance bits are all true from construction, so the machine never stops with
        // NO_REPAIR going forward.)
        if (aBaseMetaTileEntity.getLastShutDownReason()
            == gregtech.api.util.shutdown.ShutDownReasonRegistry.NO_REPAIR) {
            aBaseMetaTileEntity.setShutDownReason(gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE);
            aBaseMetaTileEntity.setShutdownStatus(false);
        }
        // t115 (perf): drive handler caches are now purely event-driven — every cellStack change
        // path (setInventorySlotContents / decrStackSize / getStackInSlotOnClosing / interactWithCell
        // / readFromNBT) invalidates the cached IMEInventoryHandlers via onCellChanged, so the
        // periodic full rebuild below is removed (reference: ae2fc caches once per cell change).
        // t32: pure AE power — no GT EU path. All power is drawn from / supplied to the AE grid
        // through the ME bus (TileEcoStorageMEBus is the IAEPowerStorage endpoint; the controller
        // aggregates the capacitance cells' AE energy).
    }

    // ------------------------------------------------------------------
    // Info / tooltip / texture
    // ------------------------------------------------------------------

    /**
     * MTETooltipMultiBlockBase hook (getDescription is final there; the returned builder is cached
     * once per MTE id, so only static lines belong here — the live column length is shown by
     * {@link #getStructureDescription} and the GUI instead).
     */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        // t51: single unified array — the machine-type line no longer shows a tier suffix.
        tt.addMachineType(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.machinetype"))
            .addInfo(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.info.mass_storage"))
            .addInfo(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.info.power"))
            .addInfo(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.info.no_maintenance"))
            .beginVariableStructureBlock(4, MAX_DRIVE_COLUMNS + 3, 3, 3, 2, 2, false)
            .addController(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.controller"))
            .addCasing(
                "2+",
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.storage_array_casing.name"),
                false)
            .addCasing(
                "1+",
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.storage_array_drive.name"),
                false)
            .addCasing(
                "1+",
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.storage_array_capacitance.name"),
                false)
            .addCasing(
                "1",
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.storage_array_vent.name"),
                false)
            .addCasing(
                "1",
                net.minecraft.util.StatCollector.translateToLocal("tile.ecoaegtnh.storage_array_me_bus.name"),
                false)
            .addStructureFooter(net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.tooltip.footer.placement"))
            .toolTipFinisher();
        return tt;
    }

    // ------------------------------------------------------------------
    // GUI (t54): IDENTICAL mechanism to the Quantum Computer (MTEQuantumComputer).
    // The quantum computer does not override useMui2/getGui -> it uses the TTMultiblockBase
    // default MUI1 GUI: CommonMetaTileEntity.openGui routes (GTGuis.GLOBAL_SWITCH_MUI2 && useMui2)
    // == false -> GTUIInfos.openGTTileEntityUI -> addUIWidgets (MUI1). We removed the MUI2
    // overrides (useMui2/getGuiTheme/getGui) and the MTEEcoStorageArrayGui class so this machine
    // renders the exact same MUI1 layout: screen_blue background, scrollable text screen
    // (drawTexts), power pass / safe void / power switch buttons, controller slot + heat sink,
    // bottom parameter-strip bar. The MUI1 GUI does not use GTGuiTheme (MUI2-only); the TecTech
    // look comes from TecTechUITextures directly.
    // ------------------------------------------------------------------

    @Override
    public void addUIWidgets(com.gtnewhorizons.modularui.api.screen.ModularWindow.Builder builder,
        com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext) {
        if (doesBindPlayerInventory()) {
            builder.widget(
                new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
                    .setDrawable(tectech.thing.gui.TecTechUITextures.BACKGROUND_SCREEN_BLUE)
                    .setPos(4, 4)
                    .setSize(190, 91));
        } else {
            builder.widget(
                new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
                    .setDrawable(tectech.thing.gui.TecTechUITextures.BACKGROUND_SCREEN_BLUE_NO_INVENTORY)
                    .setPos(4, 4)
                    .setSize(190, 171));
        }
        final com.gtnewhorizons.modularui.common.widget.SlotWidget inventorySlot = new com.gtnewhorizons.modularui.common.widget.SlotWidget(
            new com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot(
                inventoryHandler,
                getControllerSlotIndex()) {

                @Override
                public int getSlotStackLimit() {
                    return getInventoryStackLimit();
                }
            })
                // t111 (godforge parity): keep the controller slot out of shift-click routing —
                // otherwise a shift+clicked backpack item would land here instead of the upgrade
                // material window's staging slots (both were priority 0, this one registered
                // first). Manual clicks still work; only QUICK_MOVE transfer is disabled.
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
                    new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
                        .setDrawable(tectech.thing.gui.TecTechUITextures.PICTURE_HEAT_SINK_SMALL)
                        .setPos(173, 185)
                        .setSize(18, 6));
        }

        final com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn screenElements = new com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn();
        drawTexts(screenElements, inventorySlot);
        builder.widget(
            new com.gtnewhorizons.modularui.common.widget.Scrollable().setVerticalScroll()
                .widget(screenElements)
                .setPos(10, 7)
                .setSize(182, doesBindPlayerInventory() ? 79 : 165));

        // t64: power switch only — the power-pass / safe-void buttons are removed (T58 parity
        // with the calculator host).
        com.gtnewhorizons.modularui.api.widget.Widget powerSwitchButton = createPowerSwitchButton();
        builder.widget(powerSwitchButton)
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer(
                    () -> getBaseMetaTileEntity().isAllowedToWork(),
                    val -> {
                        if (val) getBaseMetaTileEntity().enableWorking();
                        else getBaseMetaTileEntity().disableWorking();
                    }));

        // ------------------------------------------------------------------
        // t62: button column — ONE entry: the upgrade-tree overview (174, 129, upgrade-tree GUI
        // ids 300/301/302; the milestone feed button/window 202 are removed — the storage array
        // now gates cell insertion by the storage upgrade tree). The click replays to the server,
        // where ModularUIContext.openSyncedWindow opens the registered synced window (id clear
        // of GT's cover 1..6 / power-panel 8 / LED 100+).
        // ------------------------------------------------------------------
        buildContext
            .addSyncedWindow(ecoaegtnh.upgrade.UpgradeTreeGui.OVERVIEW_WINDOW_ID, this::createUpgradeTreeOverview);
        buildContext.addSyncedWindow(ecoaegtnh.upgrade.UpgradeTreeGui.DETAIL_WINDOW_ID, this::createUpgradeTreeDetail);
        buildContext
            .addSyncedWindow(ecoaegtnh.upgrade.UpgradeTreeGui.MATERIAL_WINDOW_ID, this::createUpgradeTreeMaterial);
        if (doesBindPlayerInventory()) {
            builder
                .widget(new com.gtnewhorizons.modularui.common.widget.ButtonWidget().setOnClick((clickData, widget) -> {
                    if (!widget.isClient()) {
                        widget.getContext()
                            .openSyncedWindow(ecoaegtnh.upgrade.UpgradeTreeGui.OVERVIEW_WINDOW_ID);
                    }
                })
                    .setPlayClickSound(true)
                    .setBackground(() -> upgradeButtonBackground())
                    .setPos(174, 129)
                    .setSize(16, 16)
                    .dynamicTooltip(() -> upgradeButtonTooltip()));
        }

        // Bottom IO strip (the quantum computer's parameter-strip background). t65 (user
        // preference: "IO 状态应该是鼠标放到 io 方格上显示"): the persistent t58 IO text line is
        // replaced by three LED cells with HOVER tooltips — the same mechanism as the quantum
        // computer's parameter LEDs (TTMultiblockBase.addParameterLED: a widget with a status
        // texture + dynamicTooltip(Supplier<List<String>>) + FakeSyncWidget
        // .setOnClientUpdate(w -> notifyTooltipChange())). No parametrization is involved (this
        // machine is not IParametrized — the LED config popups are omitted, hover tooltip only).
        builder.widget(
            new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
                .setDrawable(tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_BLANK)
                .setPos(5, doesBindPlayerInventory() ? 96 : 176)
                .setSize(166, 12));
        int stripY = doesBindPlayerInventory() ? 97 : 177;

        // t77 (user): FOUR LED cells CONSECUTIVE at the strip start (parameter-strip slots
        // 0/1/2/3 -> x = 12/20/28/36), each with its OWN content and color: status (green=bus
        // connected + formed / red=formed-but-offline / gray=not formed), item cells (orange,
        // matching the item-cell texture), fluid cells (blue), essentia cells (red — closest to
        // the purple essentia texture among TecTechUITextures' BLUE/CYAN/GREEN/ORANGE/RED set).
        // Same MUI1 mechanism as t65/t73.

        // 1) Status LED: ME bus connection + structure + energy.
        final com.gtnewhorizons.modularui.common.widget.DrawableWidget statusLed = new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
            .setDrawable(() -> {
                if (syncMeBusConnected) return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GREEN[0];
                if (syncStructureValid) return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_RED[0];
                return tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY;
            });
        builder.widget(
            statusLed.dynamicTooltip(() -> statusTooltip())
                .setPos(12, stripY)
                .setSize(6, 4))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer(
                    () -> isMEBusConnected(),
                    val -> syncMeBusConnected = val).setOnClientUpdate(val -> statusLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer(
                    () -> mMachine,
                    val -> syncStructureValid = val).setOnClientUpdate(val -> statusLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> Math.round(getEnergyStored()),
                    val -> syncEnergyStored = val).setOnClientUpdate(val -> statusLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> (long) getMaxEnergyStore(),
                    val -> syncEnergyMax = val).setOnClientUpdate(val -> statusLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.DoubleSyncer(
                    () -> idlePowerUsage,
                    val -> syncIdlePowerUsage = val).setOnClientUpdate(val -> statusLed.notifyTooltipChange()));

        // 2) Item-cell LED (orange = item-cell color).
        final com.gtnewhorizons.modularui.common.widget.DrawableWidget itemLed = new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
            .setDrawable(
                () -> syncItemCellCount > 0 ? tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_ORANGE[0]
                    : tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY);
        builder.widget(
            itemLed.dynamicTooltip(() -> itemTooltip())
                .setPos(20, stripY)
                .setSize(6, 4))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> cellCountOf(ItemEcoStorageCellItem.class),
                    val -> syncItemCellCount = val).setOnClientUpdate(val -> itemLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> storedTypesOf(ItemEcoStorageCellItem.class),
                    val -> syncItemStoredTypes = val).setOnClientUpdate(val -> itemLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> totalTypesOf(ItemEcoStorageCellItem.class),
                    val -> syncItemTotalTypes = val).setOnClientUpdate(val -> itemLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> usedBytesOf(ItemEcoStorageCellItem.class),
                    val -> syncItemUsedBytes = val).setOnClientUpdate(val -> itemLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> totalBytesOf(ItemEcoStorageCellItem.class),
                    val -> syncItemTotalBytes = val).setOnClientUpdate(val -> itemLed.notifyTooltipChange()));

        // 3) Fluid-cell LED (blue = fluid-cell color).
        final com.gtnewhorizons.modularui.common.widget.DrawableWidget fluidLed = new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
            .setDrawable(
                () -> syncFluidCellCount > 0 ? tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_BLUE[0]
                    : tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY);
        builder.widget(
            fluidLed.dynamicTooltip(() -> fluidTooltip())
                .setPos(28, stripY)
                .setSize(6, 4))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> cellCountOf(ItemEcoStorageCellFluid.class),
                    val -> syncFluidCellCount = val).setOnClientUpdate(val -> fluidLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> storedTypesOf(ItemEcoStorageCellFluid.class),
                    val -> syncFluidStoredTypes = val).setOnClientUpdate(val -> fluidLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> totalTypesOf(ItemEcoStorageCellFluid.class),
                    val -> syncFluidTotalTypes = val).setOnClientUpdate(val -> fluidLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> usedBytesOf(ItemEcoStorageCellFluid.class),
                    val -> syncFluidUsedBytes = val).setOnClientUpdate(val -> fluidLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> totalBytesOf(ItemEcoStorageCellFluid.class),
                    val -> syncFluidTotalBytes = val).setOnClientUpdate(val -> fluidLed.notifyTooltipChange()));

        // 4) Essentia-cell LED (t82: self-made purple texture — TecTech has no purple).
        final com.gtnewhorizons.modularui.common.widget.DrawableWidget essentiaLed = new com.gtnewhorizons.modularui.common.widget.DrawableWidget()
            .setDrawable(
                () -> syncEssentiaCellCount > 0 ? ECO_PARAMETER_PURPLE
                    : tectech.thing.gui.TecTechUITextures.PICTURE_PARAMETER_GRAY);
        builder.widget(
            essentiaLed.dynamicTooltip(() -> essentiaTooltip())
                .setPos(36, stripY)
                .setSize(6, 4))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> cellCountOf(ItemEcoStorageCellEssentia.class),
                    val -> syncEssentiaCellCount = val).setOnClientUpdate(val -> essentiaLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> storedTypesOf(ItemEcoStorageCellEssentia.class),
                    val -> syncEssentiaStoredTypes = val).setOnClientUpdate(val -> essentiaLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> totalTypesOf(ItemEcoStorageCellEssentia.class),
                    val -> syncEssentiaTotalTypes = val).setOnClientUpdate(val -> essentiaLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> usedBytesOf(ItemEcoStorageCellEssentia.class),
                    val -> syncEssentiaUsedBytes = val).setOnClientUpdate(val -> essentiaLed.notifyTooltipChange()))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> totalBytesOf(ItemEcoStorageCellEssentia.class),
                    val -> syncEssentiaTotalBytes = val).setOnClientUpdate(val -> essentiaLed.notifyTooltipChange()));
    }

    // ------------------------------------------------------------------
    // t61: upgrade-tree GUI (shared three-layer windows, ids 300/301/302)
    // ------------------------------------------------------------------

    private com.gtnewhorizons.modularui.api.screen.ModularWindow createUpgradeTreeOverview(
        net.minecraft.entity.player.EntityPlayer player) {
        return ecoaegtnh.upgrade.UpgradeTreeGui.createOverview(upgradeTreeGuiHandler(), player);
    }

    private com.gtnewhorizons.modularui.api.screen.ModularWindow createUpgradeTreeDetail(
        net.minecraft.entity.player.EntityPlayer player) {
        return ecoaegtnh.upgrade.UpgradeTreeGui.createDetail(upgradeTreeGuiHandler(), player);
    }

    private com.gtnewhorizons.modularui.api.screen.ModularWindow createUpgradeTreeMaterial(
        net.minecraft.entity.player.EntityPlayer player) {
        return ecoaegtnh.upgrade.UpgradeTreeGui.createMaterial(upgradeTreeGuiHandler(), player);
    }

    private ecoaegtnh.upgrade.UpgradeTreeGui.Handler upgradeTreeGuiHandler() {
        return new ecoaegtnh.upgrade.UpgradeTreeGui.Handler() {

            @Override
            public ecoaegtnh.upgrade.UpgradeTree getUpgradeTree() {
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
                MTEEcoStorageArray.this.markDirty();
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
            public com.cleanroommc.modularui.utils.item.ItemStackHandler getStagingHandler() {
                return upgradeStagingHandler;
            }

            @Override
            public void submitUpgradeMaterials() {
                submitUpgradeMaterialsServer();
            }
        };
    }

    /** t61: comma-joined activated ids (server supplier). */
    private static String upgradeTreePack(ecoaegtnh.upgrade.UpgradeTree tree) {
        StringBuilder sb = new StringBuilder();
        for (ecoaegtnh.upgrade.UpgradeNode node : tree.getNodes()) {
            if (tree.isActivated(node.getId())) {
                if (sb.length() > 0) sb.append(',');
                sb.append(node.getId());
            }
        }
        return sb.toString();
    }

    /** t61: "node:material:count;..." paid pack (server supplier). */
    private static String paidPack(ecoaegtnh.upgrade.UpgradeTree tree) {
        StringBuilder sb = new StringBuilder();
        for (ecoaegtnh.upgrade.UpgradeNode node : tree.getNodes()) {
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
     * t61 (server): consumes the staging items into the selected node's paid record and activates
     * the node once every cost entry is fulfilled (placeholder-empty costs activate directly).
     */
    private void submitUpgradeMaterialsServer() {
        String nodeId = selectedUpgradeNode;
        ecoaegtnh.upgrade.UpgradeNode node = nodeId == null ? null : upgradeTree.getNode(nodeId);
        if (node == null || !upgradeTree.canActivate(nodeId)) return;
        java.util.Map<String, Integer> cost = node.getMaterialCost();
        if (!cost.isEmpty()) {
            // Consume staging items matching cost entries (t77: key = unlocalizedName@damage —
            // GT ingots share the unlocalized name and differ by damage, so the bare name would
            // let any GT ingot pay any ingot cost).
            for (int i = 0; i < upgradeStaging.length; i++) {
                net.minecraft.item.ItemStack stack = upgradeStagingHandler.getStackInSlot(i);
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
    private void broadcastUpgradeActivated(ecoaegtnh.upgrade.UpgradeNode node) {
        net.minecraft.server.MinecraftServer.getServer()
            .getConfigurationManager()
            .sendChatMsg(
                new net.minecraft.util.ChatComponentTranslation(
                    "ecoaegtnh.gui.upgrade.activated",
                    net.minecraft.util.StatCollector.translateToLocal(node.getNameKey())));
    }

    /**
     * Quantum-computer-style text screen (MUI1): the base status lines (idle/running/shutdown,
     * maintenance lines auto-hidden by the t44 all-fixed bits) followed by the E-Storage
     * statistics (Structure/Drives/Columns/Energy + energy bar). t58: values sync via
     * FakeSyncWidgets into the {@code sync*} fields (client-side suppliers read those fields —
     * direct reads of the client's empty lists showed 0); the IO status line lives on the bottom
     * strip, not here.
     */
    @Override
    protected void drawTexts(com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn screenElements,
        com.gtnewhorizons.modularui.common.widget.SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements
            .widget(
                com.gtnewhorizons.modularui.common.widget.TextWidget
                    .dynamicString(
                        () -> net.minecraft.util.EnumChatFormatting.GRAY
                            + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.storage_stats.structure")
                            + " "
                            + (syncStructureValid ? net.minecraft.util.EnumChatFormatting.GREEN
                                : net.minecraft.util.EnumChatFormatting.RED)
                            + net.minecraft.util.StatCollector.translateToLocal(
                                syncStructureValid ? "ecoaegtnh.gui.storage_stats.valid"
                                    : "ecoaegtnh.gui.storage_stats.invalid"))
                    .setSynced(false)
                    .setTextAlignment(com.gtnewhorizons.modularui.api.math.Alignment.CenterLeft))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer(
                    () -> mMachine,
                    val -> syncStructureValid = val));

        screenElements
            .widget(
                com.gtnewhorizons.modularui.common.widget.TextWidget
                    .dynamicString(
                        () -> net.minecraft.util.EnumChatFormatting.GRAY
                            + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.storage_stats.drives")
                            + " "
                            + net.minecraft.util.EnumChatFormatting.GOLD
                            + syncDriveCount)
                    .setSynced(false)
                    .setTextAlignment(com.gtnewhorizons.modularui.api.math.Alignment.CenterLeft))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> getDriveBays().size(),
                    val -> syncDriveCount = val));

        screenElements
            .widget(
                com.gtnewhorizons.modularui.common.widget.TextWidget
                    .dynamicString(
                        () -> net.minecraft.util.EnumChatFormatting.GRAY
                            + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.storage_stats.columns")
                            + " "
                            + net.minecraft.util.EnumChatFormatting.GOLD
                            + syncDriveColumnLength)
                    .setSynced(false)
                    .setTextAlignment(com.gtnewhorizons.modularui.api.math.Alignment.CenterLeft))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.IntegerSyncer(
                    () -> getDriveColumnLength(),
                    val -> syncDriveColumnLength = val));

        screenElements
            .widget(
                com.gtnewhorizons.modularui.common.widget.TextWidget
                    .dynamicString(
                        () -> net.minecraft.util.EnumChatFormatting.GRAY
                            + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.storage_stats.energy")
                            + " "
                            + net.minecraft.util.EnumChatFormatting.GOLD
                            + formatCompact(syncEnergyStored)
                            + net.minecraft.util.EnumChatFormatting.GRAY
                            + " / "
                            + net.minecraft.util.EnumChatFormatting.GOLD
                            + formatCompact(syncEnergyMax))
                    .setSynced(false)
                    .setTextAlignment(com.gtnewhorizons.modularui.api.math.Alignment.CenterLeft))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    // t74: round instead of truncating so small stored amounts (e.g. < 1 AE)
                    // show up in the GUI instead of displaying a misleading constant 0.
                    () -> Math.round(getEnergyStored()),
                    val -> syncEnergyStored = val))
            .widget(
                new com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.LongSyncer(
                    () -> (long) getMaxEnergyStore(),
                    val -> syncEnergyMax = val));
    }

    /**
     * Hover tooltip for the STATUS IO LED (t77, cell 1): ME bus connection + structure state +
     * energy. Client-side, reads the {@code sync*} fields.
     */
    private java.util.List<String> statusTooltip() {
        java.util.List<String> list = new ArrayList<>();
        list.add(
            net.minecraft.util.EnumChatFormatting.WHITE
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.mebus"));
        String meBus;
        if (syncMeBusConnected) {
            meBus = net.minecraft.util.EnumChatFormatting.GREEN
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.mebus.connected");
        } else if (syncStructureValid) {
            meBus = net.minecraft.util.EnumChatFormatting.RED
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.mebus.offline");
        } else {
            meBus = net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.mebus.missing");
        }
        list.add(meBus);
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.storage_stats.structure")
                + " "
                + (syncStructureValid ? net.minecraft.util.EnumChatFormatting.GREEN
                    : net.minecraft.util.EnumChatFormatting.RED)
                + net.minecraft.util.StatCollector.translateToLocal(
                    syncStructureValid ? "ecoaegtnh.gui.storage_stats.valid" : "ecoaegtnh.gui.storage_stats.invalid"));
        int energyPct = syncEnergyMax > 0 ? (int) (syncEnergyStored * 100 / syncEnergyMax) : 0;
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.storage_stats.energy")
                + " "
                + net.minecraft.util.EnumChatFormatting.AQUA
                + formatCompact(syncEnergyStored)
                + " / "
                + formatCompact(syncEnergyMax)
                + net.minecraft.util.EnumChatFormatting.GRAY
                + " ("
                + net.minecraft.util.EnumChatFormatting.GOLD
                + energyPct
                + "%"
                + net.minecraft.util.EnumChatFormatting.GRAY
                + ")");
        // t96: AE idle power usage (t69 plan B+C) — internal value, AE/t.
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.power_usage")
                + " "
                + net.minecraft.util.EnumChatFormatting.GOLD
                + formatPower(syncIdlePowerUsage)
                + " AE/t");
        return list;
    }

    /** Item-cell LED tooltip (t77, cell 2). */
    private java.util.List<String> itemTooltip() {
        return cellTooltip(
            "ecoaegtnh.gui.io.item_cells",
            syncItemCellCount,
            syncItemStoredTypes,
            syncItemTotalTypes,
            syncItemUsedBytes,
            syncItemTotalBytes);
    }

    /** Fluid-cell LED tooltip (t77, cell 3). */
    private java.util.List<String> fluidTooltip() {
        return cellTooltip(
            "ecoaegtnh.gui.io.fluid_cells",
            syncFluidCellCount,
            syncFluidStoredTypes,
            syncFluidTotalTypes,
            syncFluidUsedBytes,
            syncFluidTotalBytes);
    }

    /** Essentia-cell LED tooltip (t77, cell 4). */
    private java.util.List<String> essentiaTooltip() {
        return cellTooltip(
            "ecoaegtnh.gui.io.essentia_cells",
            syncEssentiaCellCount,
            syncEssentiaStoredTypes,
            syncEssentiaTotalTypes,
            syncEssentiaUsedBytes,
            syncEssentiaTotalBytes);
    }

    /**
     * One cell-family tooltip: "物品盘: 2 / 类型 12/315 (3.8%) / 字节 4.1K/16.4M (0.03%)".
     * Client-side, reads the synced values for that family only (t77: each LED shows its own data).
     */
    private java.util.List<String> cellTooltip(String nameKey, int count, long storedTypes, long totalTypes,
        long usedBytes, long totalBytes) {
        java.util.List<String> list = new ArrayList<>();
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY + net.minecraft.util.StatCollector.translateToLocal(nameKey)
                + ": "
                + net.minecraft.util.EnumChatFormatting.GOLD
                + count);
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.types")
                + " "
                + net.minecraft.util.EnumChatFormatting.WHITE
                + storedTypes
                + "/"
                + totalTypes
                + net.minecraft.util.EnumChatFormatting.GRAY
                + " ("
                + net.minecraft.util.EnumChatFormatting.GOLD
                + percentOf(storedTypes, totalTypes)
                + "%"
                + net.minecraft.util.EnumChatFormatting.GRAY
                + ")");
        list.add(
            net.minecraft.util.EnumChatFormatting.GRAY
                + net.minecraft.util.StatCollector.translateToLocal("ecoaegtnh.gui.io.bytes")
                + " "
                + net.minecraft.util.EnumChatFormatting.WHITE
                + formatCompact(usedBytes)
                + "/"
                + formatCompact(totalBytes)
                + net.minecraft.util.EnumChatFormatting.GRAY
                + " ("
                + net.minecraft.util.EnumChatFormatting.GOLD
                + percentOf(usedBytes, totalBytes)
                + "%"
                + net.minecraft.util.EnumChatFormatting.GRAY
                + ")");
        return list;
    }

    private static int percentOf(long part, long total) {
        return total > 0 ? (int) (part * 100 / total) : 0;
    }

    // ------------------------------------------------------------------
    // t73: server-side per-family cell stats (FakeSyncWidget suppliers)
    // ------------------------------------------------------------------

    /** Installed cell stacks of one family (server-side sync source). */
    private java.util.List<net.minecraft.item.ItemStack> cellStacksOf(Class<? extends ItemEcoStorageCell> family) {
        java.util.List<net.minecraft.item.ItemStack> out = new ArrayList<>();
        for (TileEcoStorageDrive drive : driveBays) {
            net.minecraft.item.ItemStack cell = drive.getCellStack();
            if (cell != null && family.isInstance(cell.getItem())) {
                out.add(cell);
            }
        }
        return out;
    }

    private int cellCountOf(Class<? extends ItemEcoStorageCell> family) {
        return cellStacksOf(family).size();
    }

    private int storedTypesOf(Class<? extends ItemEcoStorageCell> family) {
        return (int) sumStat(family, Stat.STORED_TYPES);
    }

    private int totalTypesOf(Class<? extends ItemEcoStorageCell> family) {
        return (int) sumStat(family, Stat.TOTAL_TYPES);
    }

    private long usedBytesOf(Class<? extends ItemEcoStorageCell> family) {
        return sumStat(family, Stat.USED_BYTES);
    }

    private long totalBytesOf(Class<? extends ItemEcoStorageCell> family) {
        return sumStat(family, Stat.TOTAL_BYTES);
    }

    /** Which per-cell statistic a family sum aggregates. */
    private enum Stat {
        STORED_TYPES,
        TOTAL_TYPES,
        USED_BYTES,
        TOTAL_BYTES
    }

    /**
     * Sums one cell statistic across one family (server-side FakeSyncWidget source). The cell
     * inventory is built through our handler with a null save provider (read-only pattern, same
     * as the item tooltip).
     */
    private long sumStat(Class<? extends ItemEcoStorageCell> family, Stat stat) {
        long sum = 0;
        for (net.minecraft.item.ItemStack cell : cellStacksOf(family)) {
            appeng.api.storage.IMEInventoryHandler<?> handler = EcoStorageCellHandler.INSTANCE
                .getCellInventory(cell, null, ((ItemEcoStorageCell) cell.getItem()).getStackType());
            if (handler instanceof appeng.api.storage.ICellInventoryHandler<?>cellHandler
                && cellHandler.getCellInv() != null) {
                appeng.api.storage.ICellInventory<?> inv = cellHandler.getCellInv();
                switch (stat) {
                    case STORED_TYPES:
                        sum += inv.getStoredItemTypes();
                        break;
                    case TOTAL_TYPES:
                        sum += inv.getTotalItemTypes();
                        break;
                    case USED_BYTES:
                        sum += inv.getUsedBytes();
                        break;
                    case TOTAL_BYTES:
                        sum += inv.getTotalBytes();
                        break;
                }
            }
        }
        return sum;
    }

    /** Compact number formatting for the energy readout (e.g. 2.4M / 16.8M). */
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

    /**
     * t96: AE idle-power formatting for the status-LED tooltip — integers as-is ("22"), the
     * 0.5-per-cell fractions with one decimal ("2.5").
     */
    private static String formatPower(double p) {
        if (p == Math.floor(p)) {
            return String.valueOf((long) p);
        }
        return String.format("%.1f", p);
    }

    // ------------------------------------------------------------------
    // Custom controller textures (t18/t26): assets/ecoaegtnh/textures/blocks/
    // storage_array_controller_front.png (facing face) and
    // storage_array_controller_side.png (other faces), registered through the GT MTE icon hook
    // (BlockMachines.registerBlockIcons -> MTE.registerIcons on the client).
    // ------------------------------------------------------------------

    /** Front (facing) panel texture; null on the server (icon registration is client-side). */
    private static IIconContainer controllerIconFront;
    /** Side/other-face panel texture; null on the server. */
    private static IIconContainer controllerIconSide;

    // Essentia-cell LED texture (t82): TecTech has no purple LED, so a self-made
    // 6x4 solid purple (176,108,255) texture is used (t114q 已回退——恢复自家材质)。
    // assets/ecoaegtnh/textures/gui/picture/parameter_purple.png
    private static final com.gtnewhorizons.modularui.api.drawable.UITexture ECO_PARAMETER_PURPLE = com.gtnewhorizons.modularui.api.drawable.UITexture
        .fullImage("ecoaegtnh", "gui/picture/parameter_purple");

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        super.registerIcons(aBlockIconRegister);
        final IIcon iconFront = aBlockIconRegister
            .registerIcon(EcoAEGTNHCore.MODID + ":storage_array_controller_front");
        final IIcon iconSide = aBlockIconRegister.registerIcon(EcoAEGTNHCore.MODID + ":storage_array_controller_side");
        controllerIconFront = iconContainer(iconFront, "blocks/storage_array_controller_front");
        controllerIconSide = iconContainer(iconSide, "blocks/storage_array_controller_side");
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
                return new ResourceLocation(EcoAEGTNHCore.MODID, texturePath);
            }
        };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstone) {
        if (controllerIconFront != null) {
            // side == facing is the front face (the direction the controller points).
            return new ITexture[] { TextureFactory.of(side == facing ? controllerIconFront : controllerIconSide) };
        }
        // Fallback (server-side render calls / icon registration not yet run): stable titanium casing.
        return new ITexture[] {
            TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.MACHINE_CASING_STABLE_TITANIUM) };
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getPollutionPerTick(ItemStack aStack) {
        return 0;
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }
}
