package ecoaegtnh;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ecoaegtnh.block.estorage.BlockEcoStorageCapacitance;
import ecoaegtnh.block.estorage.BlockEcoStorageCasing;
import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.block.estorage.BlockEcoStorageMEBus;
import ecoaegtnh.block.estorage.BlockEcoStorageVent;

@Mod(
    modid = EcoAEGTNHCore.MODID,
    name = EcoAEGTNHCore.NAME,
    version = Tags.VERSION,
    useMetadata = true,
    dependencies = "required-after:gregtech;after:appliedenergistics2;")
public class EcoAEGTNHCore {

    public static final String MODID = "ecoaegtnh";
    public static final String NAME = "ECO AE Extension (GTNH)";

    @Instance(MODID)
    public static EcoAEGTNHCore instance;

    @SidedProxy(clientSide = "ecoaegtnh.ClientProxy", serverSide = "ecoaegtnh.CommonProxy")
    public static CommonProxy proxy;

    /**
     * t41 (user decision): the creative pages are consolidated into TWO tabs — "ECO 存储"
     * (TAB_STORAGE: E-Storage machines, storage cells, components, housings) and "ECO 计算"
     * (TAB_CALC: E-Calculator controllers, part blocks, core items, flash cells). The old four
     * E-Storage tabs (machines/cells/components/housings) and the E-Calculator core-item tab are
     * removed; every block/item registers to one of the two tabs and both tabs override
     * displayAllReleventItems with the explicit user-requested order (t104 lesson).
     */
    public static CreativeTabs TAB_STORAGE;
    /** t12 (plan §9.2, user decision 5): the E-Calculator family tab ("计算"). */
    public static CreativeTabs TAB_CALC;

    /**
     * t85: mod network channel. Currently carries only C2SNetworkCellTypeSelected (network-tool
     * cell-tab selection, client → server); registered in preInit on both sides so the client can
     * send and the server can receive.
     */
    public static SimpleNetworkWrapper NETWORK;

    /** Registered block instances (populated during preInit). */
    public static final class Blocks {

        public static BlockEcoStorageCasing casing;
        public static BlockEcoStorageCapacitance capacitance;
        public static BlockEcoStorageDrive drive;
        public static BlockEcoStorageVent vent;
        public static BlockEcoStorageMEBus meBus;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // t41: the E-Storage tab — machines (controllers + part blocks), then the 27 storage
        // cells, 27 storage components and 9 storage housings, small → large per type (t104
        // explicit-order lesson: the plain registry order did not hold on the client).
        TAB_STORAGE = new CreativeTabs("ecoaegtnh.storage") {

            @Override
            public Item getTabIconItem() {
                return Item.getItemFromBlock(Blocks.drive);
            }

            @SideOnly(Side.CLIENT)
            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void displayAllReleventItems(List list) {
                // Machines: the ONE Storage Array controller (t52 — the tiered machines are
                // merged into a single controller by the milestone/upgrade-tree system) + drive
                // bay, casing, capacitance, ME bus, vent.
                if (ecoaegtnh.registry.RegistryMTE.L4 != null) {
                    list.add(ecoaegtnh.registry.RegistryMTE.L4.getStackForm(1));
                }
                list.add(new ItemStack(Blocks.drive));
                list.add(new ItemStack(Blocks.casing));
                list.add(new ItemStack(Blocks.capacitance));
                list.add(new ItemStack(Blocks.meBus));
                list.add(new ItemStack(Blocks.vent));
                // Storage cells: item → fluid → essentia, small → large (t114: family-gated
                // sizes — INF_WATER only on the fluid chain, ARCANE only on the essentia chain).
                for (ecoaegtnh.item.estorage.CellSize size : ecoaegtnh.item.estorage.CellSize.values()) {
                    if (size.allowed(ecoaegtnh.item.estorage.StorageType.ITEM)) {
                        list.add(ecoaegtnh.registry.RegistryItems.itemCell(size));
                    }
                }
                for (ecoaegtnh.item.estorage.CellSize size : ecoaegtnh.item.estorage.CellSize.values()) {
                    if (size.allowed(ecoaegtnh.item.estorage.StorageType.FLUID)) {
                        list.add(ecoaegtnh.registry.RegistryItems.fluidCell(size));
                    }
                }
                for (ecoaegtnh.item.estorage.CellSize size : ecoaegtnh.item.estorage.CellSize.values()) {
                    net.minecraft.item.ItemStack s = ecoaegtnh.registry.RegistryItems.essentiaCell(size);
                    if (s != null && size.allowed(ecoaegtnh.item.estorage.StorageType.ESSENTIA)) {
                        list.add(s);
                    }
                }
                // Storage components, same per-type order.
                for (ecoaegtnh.item.estorage.CellSize size : ecoaegtnh.item.estorage.CellSize.values()) {
                    if (size.allowed(ecoaegtnh.item.estorage.StorageType.ITEM)) {
                        list.add(ecoaegtnh.registry.RegistryItems.itemComponent(size));
                    }
                }
                for (ecoaegtnh.item.estorage.CellSize size : ecoaegtnh.item.estorage.CellSize.values()) {
                    if (size.allowed(ecoaegtnh.item.estorage.StorageType.FLUID)) {
                        list.add(ecoaegtnh.registry.RegistryItems.fluidComponent(size));
                    }
                }
                for (ecoaegtnh.item.estorage.CellSize size : ecoaegtnh.item.estorage.CellSize.values()) {
                    net.minecraft.item.ItemStack s = ecoaegtnh.registry.RegistryItems.essentiaComponent(size);
                    if (s != null && size.allowed(ecoaegtnh.item.estorage.StorageType.ESSENTIA)) {
                        list.add(s);
                    }
                }
                // Storage housings, small → large per type.
                for (int tier = 0; tier < 3; tier++) {
                    list.add(ecoaegtnh.registry.RegistryItems.itemHousing(tier));
                }
                for (int tier = 0; tier < 3; tier++) {
                    list.add(ecoaegtnh.registry.RegistryItems.fluidHousing(tier));
                }
                for (int tier = 0; tier < 3; tier++) {
                    net.minecraft.item.ItemStack s = ecoaegtnh.registry.RegistryItems.essentiaHousing(tier);
                    if (s != null) {
                        list.add(s);
                    }
                }
            }
        };
        // t12 (plan §9.2): the E-Calculator family tab. displayAllReleventItems forces the
        // explicit order (t104 lesson). t23: full C4→C6→C9 controller listing. t37: the
        // parallel/thread CORE BLOCKS are gone (t35) — the tab lists the drive blocks once each
        // and the 15 insertable core items small → large (parallel 1..65536, thread 1/4/16,
        // hyper 2/4/8), plus the nine flash cells (t29). t41: all E-Calculator items/blocks
        // register to this tab (the separate core-item tab is removed).
        TAB_CALC = new CreativeTabs("ecoaegtnh.calc") {

            @Override
            public Item getTabIconItem() {
                return Item.getItemFromBlock(ecoaegtnh.registry.RegistryEcal.parallelDrive);
            }

            @SideOnly(Side.CLIENT)
            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void displayAllReleventItems(List list) {
                // t49: the single unified controller (C4/C6/C9 merged into one machine).
                if (ecoaegtnh.registry.RegistryEcal.ARRAY != null) {
                    list.add(ecoaegtnh.registry.RegistryEcal.ARRAY.getStackForm(1));
                }
                list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.casing));
                // Drive blocks (single instances — the old tiered core blocks are gone).
                list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.parallelDrive));
                list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.threadDrive));
                list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.cellDrive));
                list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.transmitterBus));
                list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.meChannel));
                // Parallel core items, small → large (SIZES is ascending: 1..65536).
                for (int parallelism : ecoaegtnh.item.ecalculator.ItemEcalParallelCore.SIZES) {
                    list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.PARALLEL_CORES.get(parallelism)));
                }
                // Thread core items, small → large (normal 1/4/16, then hyper 2/4/8).
                for (String suffix : new String[] { "1", "4", "16", "hyper_2", "hyper_4", "hyper_8" }) {
                    list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.THREAD_CORES_BY_SUFFIX.get(suffix)));
                }
                // Flash cells, nine sizes small → large (CellSize declaration order is
                // strictly increasing: 256k..4096k, 16M..256M, 1024M..16384M).
                for (ecoaegtnh.item.ecalculator.CellSize size : ecoaegtnh.item.ecalculator.CellSize.values()) {
                    list.add(new ItemStack(ecoaegtnh.registry.RegistryEcal.cell(size)));
                }
            }
        };
        // t85: register the mod channel + the client→server cell-tab selection message (the
        // handler is server-side; the client only sends).
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NETWORK.registerMessage(
            ecoaegtnh.network.C2SNetworkCellTypeSelectedHandler.class,
            ecoaegtnh.network.C2SNetworkCellTypeSelected.class,
            0,
            Side.SERVER);
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    /**
     * H2 (audit): server stopping — cancel every in-flight vCPU job (refunds materials into the
     * grid). Dispatched by FML's @Mod.EventHandler (no EventBus reflection, safe on the server).
     * T-H3 (t122 audit): orphaned clusters (adopted when their controller was removed while the
     * grid was unreachable) hold materials in the static registry — the grid is still alive at
     * this point, so refund them too; the isComplete branch of updateCraftingLogic destroys each
     * orphan once its inventory is empty.
     */
    @EventHandler
    public void onServerStopping(cpw.mods.fml.common.event.FMLServerStoppingEvent event) {
        for (ecoaegtnh.metatileentity.MTEEcalArray controller : ecoaegtnh.EcoaegtnhLifecycleHooks.activeControllers()) {
            if (controller.getBaseMetaTileEntity() != null) {
                controller.cancelAllInFlight("server stopping");
            }
        }
        for (appeng.me.cluster.implementations.CraftingCPUCluster orphan : ecoaegtnh.EcoaegtnhOrphanClusters.all()) {
            try {
                orphan.cancel();
            } catch (Exception e) {
                org.apache.logging.log4j.LogManager.getLogger("ECOAEGTNH")
                    .warn("Ecal: orphan cancel during server stopping failed", e);
            }
        }
    }
}
