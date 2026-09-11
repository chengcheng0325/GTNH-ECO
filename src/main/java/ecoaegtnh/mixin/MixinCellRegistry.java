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
 * t129: ECO cells must always be resolved by our own {@link EcoStorageCellHandler}, never by
 * AE2U's native {@code CellInventory}. {@code CellRegistry.getCellInventory} walks its handler
 * list and takes the <em>first</em> handler whose {@code isCell()} returns true — handler order
 * is therefore load-order dependent, and a foreign handler that claims our stack first hands the
 * cell to {@code appeng.me.storage.CellInventory}, whose static {@code itemSlots} array is sized
 * by the first cell constructed on the server (63 slots for the vanilla cell). Our cells declare
 * {@code MAX_TYPES = 315}, so any stored "it" count above 63 makes
 * {@code CellInventory.loadCellItems} throw {@code ArrayIndexOutOfBoundsException} — observed as
 * a hard server crash when an ECO cell is put into an AE2 IO port.
 * <p>
 * The vanilla GUI/automation path is not enough: the IO port (and every other native AE2 block)
 * resolves cells through this registry, so we short-circuit both overloads at HEAD and return our
 * own inventory whenever we recognise the stack. The non-null check keeps us transparent: if our
 * handler declines the stack/type, the vanilla loop runs unchanged.
 * <p>
 * priority = 2000 (same guard level as MixinCraftingGridCache) so no other CellRegistry mixin can
 * re-order the lookup before us. remap = false: AE2U keeps MCP names in its release jar.
 */
@Mixin(value = CellRegistry.class, priority = 2000, remap = false)
public abstract class MixinCellRegistry {

    /**
     * beta-977 overload with the legacy {@code StorageChannel} parameter.
     */
    @Inject(
        method = "getCellInventory(Lnet/minecraft/item/ItemStack;Lappeng/api/storage/ISaveProvider;Lappeng/api/storage/StorageChannel;)Lappeng/api/storage/IMEInventoryHandler;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    @SuppressWarnings("rawtypes")
    private void ecoaegtnh$preferEcoCellInventory(ItemStack is, ISaveProvider host, StorageChannel channel,
        CallbackInfoReturnable<IMEInventoryHandler> cir) {
        if (!EcoStorageCellHandler.INSTANCE.isCell(is)) return;
        IMEInventoryHandler eco = EcoStorageCellHandler.INSTANCE.getCellInventory(is, host, channel);
        if (eco != null) cir.setReturnValue(eco);
    }

    /**
     * beta-977 overload with the generic {@code IAEStackType} parameter (used by the IO port and
     * other type-registry driven call sites). javap of
     * Applied-Energistics-2-Unofficial-rv3-beta-977-GTNH confirms both overloads exist.
     */
    @Inject(
        method = "getCellInventory(Lnet/minecraft/item/ItemStack;Lappeng/api/storage/ISaveProvider;Lappeng/api/storage/data/IAEStackType;)Lappeng/api/storage/IMEInventoryHandler;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    @SuppressWarnings("rawtypes")
    private void ecoaegtnh$preferEcoCellInventoryTyped(ItemStack is, ISaveProvider host, IAEStackType<?> type,
        CallbackInfoReturnable<IMEInventoryHandler> cir) {
        if (!EcoStorageCellHandler.INSTANCE.isCell(is)) return;
        IMEInventoryHandler eco = EcoStorageCellHandler.INSTANCE.getCellInventory(is, host, type);
        if (eco != null) cir.setReturnValue(eco);
    }
}
