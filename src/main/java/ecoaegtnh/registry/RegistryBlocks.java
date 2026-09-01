package ecoaegtnh.registry;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.EcoAEGTNHCore;
import ecoaegtnh.block.estorage.BlockEcoStorageCapacitance;
import ecoaegtnh.block.estorage.BlockEcoStorageCasing;
import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.block.estorage.BlockEcoStorageMEBus;
import ecoaegtnh.block.estorage.BlockEcoStorageVent;
import ecoaegtnh.tile.estorage.TileEcoStorageCapacitance;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;
import ecoaegtnh.tile.estorage.TileEcoStorageMEBus;

/**
 * Registers the E-Storage part blocks and their tile entities.
 */
public final class RegistryBlocks {

    private RegistryBlocks() {}

    public static void register() {
        BlockEcoStorageCasing.register("storage_array_casing");
        BlockEcoStorageDrive.register("storage_array_drive");
        BlockEcoStorageCapacitance.register("storage_array_capacitance");
        BlockEcoStorageVent.register("storage_array_vent");
        BlockEcoStorageMEBus.register("storage_array_me_bus");

        // t13: publish the instances into EcoAEGTNHCore.Blocks. The creative tab icon
        // (getTabIconItem/getIconItemStack) and TileEcoStorageMEBus.getVisualItemStack build
        // ItemStacks from these fields; they were never assigned, so opening the creative
        // inventory (or creating the ME bus grid proxy) crashed with "ItemStack.getItem() is null".
        EcoAEGTNHCore.Blocks.casing = BlockEcoStorageCasing.INSTANCE;
        EcoAEGTNHCore.Blocks.drive = BlockEcoStorageDrive.INSTANCE;
        EcoAEGTNHCore.Blocks.capacitance = BlockEcoStorageCapacitance.INSTANCE;
        EcoAEGTNHCore.Blocks.vent = BlockEcoStorageVent.INSTANCE;
        EcoAEGTNHCore.Blocks.meBus = BlockEcoStorageMEBus.INSTANCE;

        GameRegistry.registerTileEntity(TileEcoStorageDrive.class, "ecoaegtnh.drive");
        GameRegistry.registerTileEntity(TileEcoStorageCapacitance.class, "ecoaegtnh.capacitance");
        GameRegistry.registerTileEntity(TileEcoStorageMEBus.class, "ecoaegtnh.me_bus");
    }
}
