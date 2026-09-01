package ecoaegtnh.item.estorage;

import net.minecraft.item.Item;

import ecoaegtnh.EcoAEGTNHCore;

/**
 * t100: ECO ME storage component — the capacity-grade part of an ECO storage cell (27 variants:
 * 9 sizes x item/fluid/essentia). The finished cell recipe combines a component with a matching
 * storage housing. Materials scale with the t97 tier ladder (k=EV Titanium, 16M..256M=ZPM
 * Iridium, 1024M+=UHV Neutronium) — see Recipes.
 */
public class ItemEcoStorageComponent extends Item {

    public final StorageType type;
    public final CellSize size;

    public ItemEcoStorageComponent(StorageType type, CellSize size) {
        this.type = type;
        this.size = size;
        setCreativeTab(EcoAEGTNHCore.TAB_STORAGE);
        setUnlocalizedName("ecoaegtnh.storage_component_" + type.label + "_" + size.label);
        setTextureName("ecoaegtnh:storage_component_" + type.label + "_" + size.label);
    }
}
