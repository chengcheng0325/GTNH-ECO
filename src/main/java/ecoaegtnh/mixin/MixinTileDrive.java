package ecoaegtnh.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.tile.storage.TileDrive;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;

/**
 * t66: automation path — hoppers/pipes insert into the ME drive through the vanilla IInventory
 * {@code isItemValidForSlot} (SRG func_94041_b), which delegates to
 * {@code CellRegistry.isCellHandled}. Reject ECO cells there too (see MixinSlotRestrictedInput
 * for the GUI-slot counterpart and the design rationale). The ECO array bay is unaffected: its
 * insert path is TileEcoStorageDrive.interactWithCell / setInventorySlotContents, which never
 * consult AE2U's drive.
 */
@Mixin(TileDrive.class)
public abstract class MixinTileDrive {

    /**
     * The AE2U release jar reobfuscates this vanilla override to {@code func_94041_b}; the mixin
     * AP has no SRG mapping for AE2U classes, so {@code remap = false} keeps the literal runtime
     * name (no refmap entry is produced).
     */
    @Inject(method = "func_94041_b", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecoaegtnh$rejectEcoCells(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof ItemEcoStorageCell) {
            cir.setReturnValue(false);
        }
    }
}
