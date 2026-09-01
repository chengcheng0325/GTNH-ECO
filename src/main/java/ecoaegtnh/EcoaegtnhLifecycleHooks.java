package ecoaegtnh;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import ecoaegtnh.metatileentity.MTEEcalArray;

/**
 * H2 (audit): registry of active E-Calculator controllers for the server-stop refund hook.
 * Deliberately has NO EventBus registration: {@code EventBus.register()} reflects over the
 * registered class's methods, and reflecting over an MTE class trips NoClassDefFoundError on the
 * dedicated server for its @SideOnly(CLIENT) signatures (IResourceManager/ISidedInventory —
 * same trap as t30). The refund hook itself is dispatched by FML's @Mod.EventHandler
 * (EcoAEGTNHCore.onServerStopping), which does not reflect over the handler class.
 */
public final class EcoaegtnhLifecycleHooks {

    /** World instances of the E-Calculator controller (weak refs — no leak). */
    private static final Set<MTEEcalArray> ACTIVE_CONTROLLERS = Collections
        .newSetFromMap(new WeakHashMap<MTEEcalArray, Boolean>());

    private EcoaegtnhLifecycleHooks() {}

    /** Called from MTEEcalArray.newMetaEntity for every world instance. */
    public static void registerController(MTEEcalArray controller) {
        ACTIVE_CONTROLLERS.add(controller);
    }

    /** Snapshot of active controllers (caller iterates; null-safe per entry). */
    public static java.util.List<MTEEcalArray> activeControllers() {
        return new java.util.ArrayList<>(ACTIVE_CONTROLLERS);
    }
}
