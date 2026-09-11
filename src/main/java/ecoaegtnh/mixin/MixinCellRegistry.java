package ecoaegtnh.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStackType;
import appeng.core.features.registries.CellRegistry;
import ecoaegtnh.ae2.EcoStorageCellHandler;

/**
 * t129 (user report, 284 live test): an ECO storage cell placed in an AE2 IO port crashes the server
 * with {@code ArrayIndexOutOfBoundsException: 63} inside AE2U's own cell inventory — the native path
 * parses our cell's NBT with AE2's fixed item/fluid type limit instead of our own inventory layout
 * (ECO cells declare 315 types and are read back through {@link EcoStorageCellHandler}).
 * <p>
 * {@link CellRegistry} resolves a cell by walking its handler list and returning the FIRST handler
 * whose {@code isCell()} matches. AE2U registers its built-in handler into that same list during its
 * own init — strictly before our {@code postInit} {@code addCellHandler(...)} call — and our cells
 * implement {@code IStorageCell}, so a generic {@code isCell} hit can shadow our handler. Injecting
 * at HEAD makes the resolution order independent: whenever one of our cells is queried we answer
 * first, and only a NON-NULL result short-circuits the vanilla lookup, so every foreign cell keeps
 * the untouched AE2U behaviour.
 * <p>
 * AE2U rv3-beta-1000 exposes two overloads and its own code uses both: native blocks such as
 * {@code TileIOPort} query the {@link IAEStackType} overload (it walks
 * {@code AEStackTypeRegistry.getAllTypes()} with a {@code null} save provider), while the
 * {@link StorageChannel} overload serves the legacy API callers — both are intercepted with the
 * exact descriptors taken from the release jar (javap, see docs/t3-implementation-notes.md t129).
 */
@Mixin(value = CellRegistry.class, priority = 2000, remap = false)
public abstract class MixinCellRegistry {

    /**
     * t129: legacy {@link StorageChannel} overload. The AE2U release jar keeps its own method names
     * (no SRG mapping exists for them, which is why {@code remap = false} and the literal descriptor
     * are used — same approach as MixinTileDrive / MixinGridStorageCache).
     */
    @Inject(
        method = "getCellInventory(Lnet/minecraft/item/ItemStack;Lappeng/api/storage/ISaveProvider;Lappeng/api/storage/StorageChannel;)Lappeng/api/storage/IMEInventoryHandler;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void ecoaegtnh$preferEcoCellInventoryByChannel(final ItemStack is, final ISaveProvider host,
        final StorageChannel channel, final CallbackInfoReturnable<IMEInventoryHandler> cir) {
        if (!EcoStorageCellHandler.INSTANCE.isCell(is)) return;
        final IMEInventoryHandler<?> inv = EcoStorageCellHandler.INSTANCE.getCellInventory(is, host, channel);
        if (inv != null) {
            cir.setReturnValue(inv);
        }
    }

    /**
     * t129: the {@link IAEStackType} overload — the one AE2U's native blocks (IO port, drives, chest
     * GUI) actually call. Same contract as above: our handler wins when it produces an inventory,
     * otherwise the vanilla lookup runs unchanged.
     */
    @Inject(
        method = "getCellInventory(Lnet/minecraft/item/ItemStack;Lappeng/api/storage/ISaveProvider;Lappeng/api/storage/data/IAEStackType;)Lappeng/api/storage/IMEInventoryHandler;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void ecoaegtnh$preferEcoCellInventoryByType(final ItemStack is, final ISaveProvider host,
        final IAEStackType<?> type, final CallbackInfoReturnable<IMEInventoryHandler> cir) {
        if (!EcoStorageCellHandler.INSTANCE.isCell(is)) return;
        final IMEInventoryHandler<?> inv = EcoStorageCellHandler.INSTANCE.getCellInventory(is, host, type);
        if (inv != null) {
            cir.setReturnValue(inv);
        }
    }
}
