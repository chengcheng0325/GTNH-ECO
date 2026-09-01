package ecoaegtnh.mixin;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.config.Settings;
import appeng.client.gui.implementations.GuiNetworkStatus;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.core.AEConfig;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.network.C2SNetworkCellTypeSelected;

/**
 * t85 (client half): forward the network-tool cell-tab selection to the server.
 * <p>
 * AE2U's {@code GuiNetworkStatus.actionPerformed} mutates only the LOCAL
 * {@code AEConfig.selectedCellType} for the cell-type button (Settings.CELL_TYPE) and never sends
 * a packet (verified in the rv3-beta-1000 bytecode — only OpenReshuffle / ToggleDiagnostics /
 * ToggleFlowTracking / PacketNetworkStatusSelected are sent). On a dedicated server the server's
 * AEConfig stays ITEM, so {@code ContainerNetworkStatus.detectAndSendChanges} always sent
 * {@code sg.getItemCells()} and every tab rendered item cells. This mixin sends the new selection
 * over our own channel right after the button handler ran (TAIL — {@code nextCellType} already
 * rotated the local value).
 * <p>
 * Client-only (listed under "client" in mixins.ecoaegtnh.json — GuiNetworkStatus does not exist
 * on a dedicated server). The target is the SRG literal the release jar keeps for the GuiScreen
 * override (func_146284_a = actionPerformed); remap = false, same as MixinTileDrive.
 */
@Mixin(GuiNetworkStatus.class)
public abstract class MixinGuiNetworkStatus {

    @Inject(method = "func_146284_a", at = @At("TAIL"), remap = false)
    private void ecoaegtnh$syncCellTypeSelection(GuiButton btn, CallbackInfo ci) {
        if (btn instanceof GuiImgButton && ((GuiImgButton) btn).getSetting() == Settings.CELL_TYPE) {
            EcoAEGTNHCore.NETWORK.sendToServer(new C2SNetworkCellTypeSelected(AEConfig.instance.selectedCellType()));
        }
    }
}
