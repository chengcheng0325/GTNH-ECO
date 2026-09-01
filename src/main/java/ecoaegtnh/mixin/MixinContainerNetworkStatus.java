package ecoaegtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.config.CellType;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.core.AEConfig;
import ecoaegtnh.network.INetworkToolCellTypeHolder;

/**
 * t85 (server half): make {@code ContainerNetworkStatus.detectAndSendChanges} use the
 * per-container cell-tab selection instead of the server-local AEConfig.
 * <p>
 * AE2U reads {@code AEConfig.instance.selectedCellType()} there; on a dedicated server that value
 * is always the default ITEM because the client's selection is never synced (see
 * {@link MixinGuiNetworkStatus}). The redirected read returns the value set from the client packet
 * (defaults to ITEM, so behaviour is unchanged until a selection arrives). The container also
 * implements {@link INetworkToolCellTypeHolder} so the packet handler can store the selection.
 * <p>
 * The target is the SRG literal the release jar keeps for the Container override
 * (func_75142_b = detectAndSendChanges); remap = false, same as MixinTileDrive. The redirected
 * AEConfig method keeps its MCP name in the release jar.
 */
@Mixin(ContainerNetworkStatus.class)
public abstract class MixinContainerNetworkStatus implements INetworkToolCellTypeHolder {

    /** Server-side network-tool cell tab; set from the client (t85), defaults to ITEM. */
    @Unique
    private CellType ecoaegtnh$cellTypeSelection = CellType.ITEM;

    @Override
    public void ecoaegtnh$setSelectedCellType(CellType cellType) {
        this.ecoaegtnh$cellTypeSelection = cellType;
    }

    @Redirect(
        method = "func_75142_b",
        at = @At(value = "INVOKE", target = "Lappeng/core/AEConfig;selectedCellType()Lappeng/api/config/CellType;"),
        remap = false)
    private CellType ecoaegtnh$redirectSelectedCellType(AEConfig instance) {
        return this.ecoaegtnh$cellTypeSelection;
    }
}
