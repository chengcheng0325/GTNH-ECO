package ecoaegtnh.registry;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.item.estorage.CellSize;
import ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia;
import ecoaegtnh.item.estorage.ItemEcoStorageCellFluid;
import ecoaegtnh.item.estorage.ItemEcoStorageCellItem;
import ecoaegtnh.item.estorage.ItemEcoStorageComponent;
import ecoaegtnh.item.estorage.ItemEcoStorageHousing;
import ecoaegtnh.item.estorage.StorageType;
import gregtech.api.enums.Mods;

/**
 * Registers the E-Storage cell family (t76: 10 sizes x item/fluid/essentia = 30 cells — t113 adds
 * the Artificial-Universe size; t100: + 30 ME storage components + 9 storage housings as
 * intermediate materials).
 * <p>
 * Essentia items depend on ThaumicEnergistics: they are only instantiated/registered when TE4 is
 * loaded. Static instances are exposed via the per-type {@link EnumMap}s for recipe registration.
 */
public final class RegistryItems {

    // t76/t113: 10 sizes per type (256k … 16384m + universe).
    public static final Map<CellSize, ItemEcoStorageCellItem> ITEM_CELLS = new EnumMap<>(CellSize.class);
    public static final Map<CellSize, ItemEcoStorageCellFluid> FLUID_CELLS = new EnumMap<>(CellSize.class);
    /** Essentia cells (ThaumicEnergistics); empty when TE4 is not loaded. */
    public static final Map<CellSize, ItemEcoStorageCellEssentia> ESSENTIA_CELLS = new EnumMap<>(CellSize.class);

    // t100: capacity-grade ME storage components (27) — the cell recipe input.
    public static final Map<CellSize, ItemEcoStorageComponent> COMPONENTS = new EnumMap<>(CellSize.class);
    public static final Map<CellSize, ItemEcoStorageComponent> FLUID_COMPONENTS = new EnumMap<>(CellSize.class);
    public static final Map<CellSize, ItemEcoStorageComponent> ESSENTIA_COMPONENTS = new EnumMap<>(CellSize.class);

    // t100: controller-tier storage housings (9; index = tier 0/1/2 = L4/L6/L9).
    public static final ItemEcoStorageHousing[] ITEM_HOUSINGS = new ItemEcoStorageHousing[3];
    public static final ItemEcoStorageHousing[] FLUID_HOUSINGS = new ItemEcoStorageHousing[3];
    public static final ItemEcoStorageHousing[] ESSENTIA_HOUSINGS = new ItemEcoStorageHousing[3];

    private RegistryItems() {}

    public static void register() {
        // t101: register TYPE-MAJOR (all item, then all fluid, then all essentia; size ascending
        // within each type) — the vanilla creative tab lists items in registry order, so this is
        // what makes the cells/components/housings tabs show "item 256k..16384m, fluid 256k..,
        // essentia 256k.." instead of the interleaved size-major mess (user: "创造页是乱的").
        // t114: sizes are family-gated (CellSize.allowed) — INF_WATER only registers on the fluid
        // chain, ARCANE only on the essentia chain.
        for (CellSize size : CellSize.values()) {
            if (size.allowed(StorageType.ITEM)) {
                ITEM_CELLS.put(size, registerItem(new ItemEcoStorageCellItem(size)));
            }
        }
        for (CellSize size : CellSize.values()) {
            if (size.allowed(StorageType.FLUID)) {
                FLUID_CELLS.put(size, registerItem(new ItemEcoStorageCellFluid(size)));
            }
        }
        for (CellSize size : CellSize.values()) {
            if (size.allowed(StorageType.ITEM)) {
                COMPONENTS.put(size, registerItem(new ItemEcoStorageComponent(StorageType.ITEM, size)));
            }
        }
        for (CellSize size : CellSize.values()) {
            if (size.allowed(StorageType.FLUID)) {
                FLUID_COMPONENTS.put(size, registerItem(new ItemEcoStorageComponent(StorageType.FLUID, size)));
            }
        }
        for (int tier = 0; tier < 3; tier++) {
            ITEM_HOUSINGS[tier] = registerItem(new ItemEcoStorageHousing(StorageType.ITEM, tier));
        }
        for (int tier = 0; tier < 3; tier++) {
            FLUID_HOUSINGS[tier] = registerItem(new ItemEcoStorageHousing(StorageType.FLUID, tier));
        }

        // Essentia items require ThaumicEnergistics (its AEEssentiaStackType must be registered).
        // The item classes only load TE4 classes when instantiated, so this gate is what keeps the
        // mod loadable without TE4. Use GT's Mods enum: it resolves the TE4 modid
        // ("thaumicenergistics", all lowercase) correctly — Loader.isModLoaded is case-sensitive.
        if (Mods.ThaumicEnergistics.isModLoaded()) {
            for (CellSize size : CellSize.values()) {
                if (size.allowed(StorageType.ESSENTIA)) {
                    ESSENTIA_CELLS.put(size, registerItem(new ItemEcoStorageCellEssentia(size)));
                }
            }
            for (CellSize size : CellSize.values()) {
                if (size.allowed(StorageType.ESSENTIA)) {
                    ESSENTIA_COMPONENTS
                        .put(size, registerItem(new ItemEcoStorageComponent(StorageType.ESSENTIA, size)));
                }
            }
            for (int tier = 0; tier < 3; tier++) {
                ESSENTIA_HOUSINGS[tier] = registerItem(new ItemEcoStorageHousing(StorageType.ESSENTIA, tier));
            }
        }
    }

    // --- Convenience ItemStack helpers for recipes ---

    public static ItemStack itemCell(CellSize size) {
        return new ItemStack(ITEM_CELLS.get(size));
    }

    public static ItemStack fluidCell(CellSize size) {
        return new ItemStack(FLUID_CELLS.get(size));
    }

    /** @return the essentia cell stack, or null when TE4 is not loaded (recipes skip on null). */
    public static ItemStack essentiaCell(CellSize size) {
        ItemEcoStorageCellEssentia cell = ESSENTIA_CELLS.get(size);
        return cell == null ? null : new ItemStack(cell);
    }

    public static ItemStack itemComponent(CellSize size) {
        return new ItemStack(COMPONENTS.get(size));
    }

    public static ItemStack fluidComponent(CellSize size) {
        return new ItemStack(FLUID_COMPONENTS.get(size));
    }

    /** @return the essentia component stack, or null when TE4 is not loaded. */
    public static ItemStack essentiaComponent(CellSize size) {
        ItemEcoStorageComponent comp = ESSENTIA_COMPONENTS.get(size);
        return comp == null ? null : new ItemStack(comp);
    }

    /** @param tier 0 = L4, 1 = L6, 2 = L9. */
    public static ItemStack itemHousing(int tier) {
        return new ItemStack(ITEM_HOUSINGS[tier]);
    }

    /** @param tier 0 = L4, 1 = L6, 2 = L9. */
    public static ItemStack fluidHousing(int tier) {
        return new ItemStack(FLUID_HOUSINGS[tier]);
    }

    /** @return the essentia housing stack, or null when TE4 is not loaded. */
    public static ItemStack essentiaHousing(int tier) {
        ItemEcoStorageHousing housing = ESSENTIA_HOUSINGS[tier];
        return housing == null ? null : new ItemStack(housing);
    }

    private static <T extends Item> T registerItem(T item) {
        // t40: the raw unlocalizedName ("ecoaegtnh.estorage_cell_<type>_<size>") carries no
        // "item." prefix — stripping five chars used to mangle it into "gtnh.estorage_cell_...".
        String name = item.getUnlocalizedName();
        if (name.startsWith("item.")) {
            name = name.substring("item.".length());
        }
        GameRegistry.registerItem(item, name, EcoAEGTNHCore.MODID);
        return item;
    }
}
