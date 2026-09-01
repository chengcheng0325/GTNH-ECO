package ecoaegtnh.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.container.slot.SlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;

/**
 * t66 (user: "ECO 盘应该是不可放入" ME 驱动器/箱子 — ECO cells only work in the ECO array's own
 * drive bays): the ME drive and ME chest GUIs place cells through
 * {@link SlotRestrictedInput} STORAGE_CELLS slots; their filter is the vanilla Slot
 * {@code isItemValid} (SRG func_75214_a), which delegates to {@code CellRegistry.isCellHandled}.
 * AE2U has no per-item "forbid drive insertion" flag, and {@code IStorageCell.isStorageCell()}
 * cannot return false (the CellInventory base REQUIRES it, and the ECO bays construct
 * EcoStorageCellInventory through CellInventory), so this mixin rejects ECO cells at the slot
 * level. The ECO array bay path never goes through AE2U slots (TileEcoStorageDrive.buildHandler
 * calls our EcoStorageCellHandler directly), so ECO bays keep working unchanged.
 * <p>
 * t95 (user: IO 端口 + 元件工作台要能放，驱动器/箱子仍不能放): the t66 block was unconditional, so it
 * also rejected ECO cells in the ME-IO port (ContainerIOPort:55 also uses STORAGE_CELLS, host
 * TileIOPort) and the cell workbench (ContainerCellWorkbench:93 uses WORKBENCH_CELL). The block is
 * now whitelisted to the drive-like hosts only: {@code which == STORAGE_CELLS} AND the slot's
 * inventory is a {@link TileDrive} (ContainerDrive passes the tile itself, ContainerDrive:29) or
 * a {@link TileChest} (ContainerChest:30). Every other STORAGE_CELLS user (IO port's "cells"
 * sub-inventory, future uses such as spatial IO) and WORKBENCH_CELL slots pass through untouched.
 */
@Mixin(SlotRestrictedInput.class)
public abstract class MixinSlotRestrictedInput {

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void ecoaegtnh$rejectEcoCellsInDriveLike(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.getItem() instanceof ItemEcoStorageCell && isDriveLikeStorageCellSlot()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Drive-like whitelist check via the standard mixin {@code (SlotRestrictedInput)(Object)this}
     * cast (the AP cannot @Shadow members inherited from the vanilla Slot parent, and target
     * methods need @Shadow — the cast avoids both): {@code getItemType()} is the public accessor
     * for the slot's {@code which} field, and ContainerDrive/ContainerChest pass the tile itself
     * as the slot inventory (public vanilla {@code Slot.inventory} field).
     */
    private boolean isDriveLikeStorageCellSlot() {
        final SlotRestrictedInput slot = (SlotRestrictedInput) (Object) this;
        return slot.getItemType() == PlacableItemType.STORAGE_CELLS
            && (slot.inventory instanceof TileDrive || slot.inventory instanceof TileChest);
    }
}
