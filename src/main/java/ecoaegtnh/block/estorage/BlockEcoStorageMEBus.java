package ecoaegtnh.block.estorage;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.tile.estorage.TileEcoStorageMEBus;
import gregtech.api.GregTechAPI;

/**
 * E-Storage ME bus block. The tile is the AE grid connection point (ICellContainer +
 * IGridProxyable + IAEPowerStorage) for the whole Storage Array.
 */
public class BlockEcoStorageMEBus extends Block implements ITileEntityProvider {

    public static BlockEcoStorageMEBus INSTANCE;

    public BlockEcoStorageMEBus() {
        super(Material.iron);
        setHardness(5.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.storage_array_me_bus");
        setBlockTextureName("ecoaegtnh:storage_array_mebus");
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_STORAGE);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEcoStorageMEBus();
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

    public static BlockEcoStorageMEBus register(String name) {
        INSTANCE = new BlockEcoStorageMEBus();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
