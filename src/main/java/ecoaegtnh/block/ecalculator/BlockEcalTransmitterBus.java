package ecoaegtnh.block.ecalculator;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/**
 * E-Calculator superconducting transmitter bus block (no TileEntity, MVP static). In the reference
 * the LINK state between the stacked cell drives is display-only (getSuppliedBytes does not check
 * the connection), so the MVP keeps this a plain structural block; link-state textures come in
 * phase C. Required by the per-segment structure layout (plan §5.1).
 */
public class BlockEcalTransmitterBus extends Block {

    public static BlockEcalTransmitterBus INSTANCE;

    public BlockEcalTransmitterBus() {
        super(Material.iron);
        setHardness(20.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.ecalculator_transmitter_bus");
        setBlockTextureName("ecoaegtnh:ecal_transmitter_bus");
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_CALC);
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

    public static BlockEcalTransmitterBus register(String name) {
        INSTANCE = new BlockEcalTransmitterBus();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
