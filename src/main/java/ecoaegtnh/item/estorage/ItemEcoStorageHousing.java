package ecoaegtnh.item.estorage;

import net.minecraft.item.Item;

import ecoaegtnh.EcoAEGTNHCore;

/**
 * t100: ECO storage housing — the capacity-band shell of an ECO storage cell (9 variants:
 * 3 bands Mk.I/Mk.II/Mk.III x item/fluid/essentia; t122 naming pass: the L4/L6/L9 controller-tier
 * names are gone, the bands are k / M / big-M). The finished cell recipe combines a
 * housing with a matching capacity component. Band materials: Mk.I=EV Titanium, Mk.II=ZPM
 * Iridium, Mk.III=UHV Neutronium (t97 ladder) — see Recipes.
 */
public class ItemEcoStorageHousing extends Item {

    public final StorageType type;
    /** Capacity band: 0 = k (Mk.I), 1 = M (Mk.II), 2 = big-M (Mk.III). */
    public final int tier;

    public ItemEcoStorageHousing(StorageType type, int tier) {
        this.type = type;
        this.tier = tier;
        setCreativeTab(EcoAEGTNHCore.TAB_STORAGE);
        setUnlocalizedName("ecoaegtnh.storage_housing_" + type.label + "_l" + tierLabel());
        setTextureName("ecoaegtnh:storage_housing_" + type.label + "_l" + tierLabel());
    }

    /**
     * Internal band digit ("4"/"6"/"9") for the registry/lang/texture names — KEEP UNCHANGED
     * (changing it would break existing saves' item ids). The callers append the {@code "_l"}
     * prefix themselves, so returning "l4" would double the 'l' into "_ll4" and break the lang
     * keys and texture names (t106 root cause: housings showed raw keys + purple-black textures
     * because the registered name was {@code storage_housing_<type>_ll<4|6|9>} while the lang
     * keys / PNGs use the single-l {@code _l<4|6|9>} form). Display names (Mk.I/II/III) come
     * from the lang files.
     */
    public String tierLabel() {
        return tier == 2 ? "9" : tier == 1 ? "6" : "4";
    }
}
