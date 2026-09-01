package ecoaegtnh;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import ecoaegtnh.ae2.EcoStorageCellHandler;
import ecoaegtnh.registry.RegistryBlocks;
import ecoaegtnh.registry.RegistryEcal;
import ecoaegtnh.registry.RegistryItems;
import ecoaegtnh.registry.RegistryMTE;

/**
 * Central registration entry point for ECO AE Extension (GTNH).
 */
public final class EcoAERegistry {

    private static final Logger LOG = LogManager.getLogger("ECOAEGTNH");

    private EcoAERegistry() {}

    public static void preInit(FMLPreInitializationEvent event) {
        RegistryBlocks.register();
        RegistryItems.register();
        // E-Calculator (phase A): part blocks + tile entities. Additive hook — 1.7.10 has no
        // registration mechanism without a lifecycle call site, so RegistryEcal is invoked here
        // (two additive lines; E-Storage behaviour untouched).
        RegistryEcal.registerBlocks();
    }

    public static void init(FMLInitializationEvent event) {
        // H2 (audit): server-stop / dimension-unload refund hooks (own class — reflecting over
        // MTEEcalArray from EventBus would hit @SideOnly(CLIENT) signatures on the server).
        ecoaegtnh.EcoaegtnhLifecycleHooks.init();
        // MTE registration must happen in the load phase (GT preload..postload window).
        RegistryMTE.register();
        // E-Calculator C4 controller MTE (must run AFTER RegistryMTE so the creative
        // controller-stack append in RegistryEcal.registerMTE() is not overwritten).
        RegistryEcal.registerMTE();
        // GT assembler recipes for the E-Storage machine family.
        ecoaegtnh.recipe.Recipes.register();
        // WAILA tooltips for the E-Storage drive bays (t33) and the E-Calculator drives (t43);
        // no-ops when WAILA is not installed.
        if (cpw.mods.fml.common.Loader.isModLoaded("Waila")) {
            cpw.mods.fml.common.event.FMLInterModComms
                .sendMessage("Waila", "register", "ecoaegtnh.waila.EcoStorageDriveWailaProvider.callbackRegister");
            cpw.mods.fml.common.event.FMLInterModComms
                .sendMessage("Waila", "register", "ecoaegtnh.waila.EcalDriveWailaProvider.callbackRegister");
        }
    }

    public static void postInit(FMLPostInitializationEvent event) {
        // Register the AE2U cell handler so E-Storage cells work in the ECO array's own drive bays.
        appeng.api.AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(EcoStorageCellHandler.INSTANCE);
        // t61/t66: self-verification — log the ECO cell routing state. t66 (user): ECO cells
        // must be REJECTED by ME drives/chests (they are exclusive to the ECO array bays). The
        // rejection is implemented at the placement gates (mixin on SlotRestrictedInput.isItemValid
        // for the drive/chest GUIs and on TileDrive.isItemValidForSlot for automation), so
        // isCellHandled still reports true (our handler is registered) while the drive/chest
        // slots bounce ECO cells; the ECO bay path is verified via our own handler instead.
        appeng.api.storage.ICellRegistry cellReg = appeng.api.AEApi.instance()
            .registries()
            .cell();
        LOG.info(
            "EcoStorageCellHandler registered; ECO cell routing self-check ->" + " isCellHandled(item16M)="
                + cellReg.isCellHandled(RegistryItems.itemCell(ecoaegtnh.item.estorage.CellSize.M_16))
                + " (true = handler registered; t66 slot-level mixins reject in ME drives/chests),"
                + " isCellHandled(fluid16M)="
                + cellReg.isCellHandled(RegistryItems.fluidCell(ecoaegtnh.item.estorage.CellSize.M_16))
                + ", isCellHandled(essentia16M)="
                + (RegistryItems.essentiaCell(ecoaegtnh.item.estorage.CellSize.M_16) == null ? "n/a(no TE4)"
                    : cellReg.isCellHandled(RegistryItems.essentiaCell(ecoaegtnh.item.estorage.CellSize.M_16)))
                + ", getHandler(item16M)==ours="
                + (cellReg.getHandler(RegistryItems.itemCell(ecoaegtnh.item.estorage.CellSize.M_16))
                    == EcoStorageCellHandler.INSTANCE)
                + ", ECO bay inventory (our handler, item16M) non-null="
                + (EcoStorageCellHandler.INSTANCE.getCellInventory(
                    RegistryItems.itemCell(ecoaegtnh.item.estorage.CellSize.M_16),
                    null,
                    appeng.util.item.AEItemStackType.ITEM_STACK_TYPE) != null));
    }
}
