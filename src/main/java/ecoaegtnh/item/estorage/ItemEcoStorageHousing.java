package ecoaegtnh.item.estorage;

import net.minecraft.item.Item;

import ecoaegtnh.EcoAEGTNHCore;

/**
 * t100: ECO storage housing — the controller-tier shell of an ECO storage cell (9 variants:
 * 3 controller tiers L4/L6/L9 x item/fluid/essentia). The finished cell recipe combines a
 * housing with a matching capacity component. Tier materials: L4=EV Titanium, L6=ZPM Iridium,
 * L9=UHV Neutronium (t97 ladder) — see Recipes.
 */
public class ItemEcoStorageHousing extends Item {

    public final StorageType type;
    /** Controller tier: 0 = L4, 1 = L6, 2 = L9. */
    public final int tier;

    public ItemEcoStorageHousing(StorageType type, int tier) {
        this.type = type;
        this.tier = tier;
        setCreativeTab(EcoAEGTNHCore.TAB_STORAGE);
        setUnlocalizedName("ecoaegtnh.storage_housing_" + type.label + "_l" + tierLabel());
        setTextureName("ecoaegtnh:storage_housing_" + type.label + "_l" + tierLabel());
    }

    /**
     * Tier digit ("4"/"6"/"9") for the registry/lang/texture names — the callers append the
     * {@code "_l"} prefix themselves, so returning "l4" would double the 'l' into "_ll4" and
     * break the lang keys and texture names (t106 root cause: housings showed raw keys +
     * purple-black textures because the registered name was {@code storage_housing_<type>_ll<4|6|9>}
     * while the lang keys / PNGs use the single-l {@code _l<4|6|9>} form).
     */
    public String tierLabel() {
        return tier == 2 ? "9" : tier == 1 ? "6" : "4";
    }
}
