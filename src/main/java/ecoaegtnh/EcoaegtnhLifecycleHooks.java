package ecoaegtnh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ecoaegtnh.metatileentity.MTEEcalArray;

/**
 * H2 (audit): lifecycle hooks that refund in-flight vCPU jobs when the server stops or a
 * dimension unloads. Lives in its OWN class (not on MTEEcalArray): {@code EventBus.register()}
 * reflects over the registered class's methods, and reflecting over MTEEcalArray trips
 * NoClassDefFoundError on the dedicated server for its @SideOnly(CLIENT) signatures
 * (IResourceManager/ISidedInventory — same trap as t30). This class has no client references.
 */
public final class EcoaegtnhLifecycleHooks {

    /** World instances of the E-Calculator controller (weak refs — no leak). */
    private static final Set<MTEEcalArray> ACTIVE_CONTROLLERS = Collections
        .newSetFromMap(new WeakHashMap<MTEEcalArray, Boolean>());

    private static boolean registered = false;

    private EcoaegtnhLifecycleHooks() {}

    /** Called once from mod init (EcoAERegistry.init). */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        EcoaegtnhLifecycleHooks hooks = new EcoaegtnhLifecycleHooks();
        FMLCommonHandler.instance()
            .bus()
            .register(hooks);
        MinecraftForge.EVENT_BUS.register(hooks);
    }

    public static void registerController(MTEEcalArray controller) {
        ACTIVE_CONTROLLERS.add(controller);
    }

    /** Server stopping — cancel every in-flight job (refunds materials into the grid). */
    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        for (MTEEcalArray controller : new ArrayList<>(ACTIVE_CONTROLLERS)) {
            if (controller.getBaseMetaTileEntity() != null) {
                controller.cancelAllInFlight("server stopping");
            }
        }
    }

    /** A dimension unloads — refund jobs whose controller lives in that world. */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        for (MTEEcalArray controller : new ArrayList<>(ACTIVE_CONTROLLERS)) {
            if (controller.getBaseMetaTileEntity() != null && controller.getBaseMetaTileEntity()
                .getWorld() == event.world) {
                controller.cancelAllInFlight("dimension unload");
            }
        }
    }
}
