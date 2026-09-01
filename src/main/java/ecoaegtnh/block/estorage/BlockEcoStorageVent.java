package ecoaegtnh.block.estorage;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/**
 * E-Storage vent block (no TileEntity). Structural filler with a directional texture.
 */
public class BlockEcoStorageVent extends Block {

    public static BlockEcoStorageVent INSTANCE;

    public BlockEcoStorageVent() {
        super(Material.iron);
        setHardness(20.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.storage_array_vent");
        setBlockTextureName("ecoaegtnh:storage_array_vents_a");
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_STORAGE);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (GregTechAPI.isMachineBlock(this, world.getBlockMetadata(x, y, z))) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (GregTechAPI.isMachineBlock(block, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    public static BlockEcoStorageVent register(String name) {
        INSTANCE = new BlockEcoStorageVent();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
