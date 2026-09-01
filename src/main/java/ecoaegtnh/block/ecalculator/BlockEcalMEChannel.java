package ecoaegtnh.block.ecalculator;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.tile.ecalculator.TileEcalMEChannel;
import gregtech.api.GregTechAPI;

/**
 * E-Calculator ME channel block: the subsystem's single AE grid connection point
 * ({@link TileEcalMEChannel} implements IGridProxyable + IActionHost; the crafting CPU list
 * exposure arrives in phase B).
 */
public class BlockEcalMEChannel extends Block implements ITileEntityProvider {

    public static BlockEcalMEChannel INSTANCE;

    public BlockEcalMEChannel() {
        super(Material.iron);
        setHardness(5.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.ecalculator_me_channel");
        setBlockTextureName("ecoaegtnh:ecal_me_channel");
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_CALC);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEcalMEChannel();
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

    public static BlockEcalMEChannel register(String name) {
        INSTANCE = new BlockEcalMEChannel();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
