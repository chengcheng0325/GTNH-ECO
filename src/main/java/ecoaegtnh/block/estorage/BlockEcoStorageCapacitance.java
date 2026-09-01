package ecoaegtnh.block.estorage;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.tile.estorage.TileEcoStorageCapacitance;
import gregtech.api.GregTechAPI;

/**
 * E-Storage capacitance block (A/B/C tier items, meta 0/1/2; t67: all metas store the same
 * unified 2,000,000 AE). Stores AE energy (double) in the tile. Fill level drives the icon.
 */
public class BlockEcoStorageCapacitance extends Block implements ITileEntityProvider {

    public static final int META_A = 0;
    public static final int META_B = 1;
    public static final int META_C = 2;

    public static BlockEcoStorageCapacitance INSTANCE;
    /** Item stacks for the three tiers. */
    public static net.minecraft.item.ItemStack A;
    public static net.minecraft.item.ItemStack B;
    public static net.minecraft.item.ItemStack C;

    public BlockEcoStorageCapacitance() {
        super(Material.iron);
        setHardness(20.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.storage_array_capacitance");
        setBlockTextureName("ecoaegtnh:storage_array_capacitance_a_empty");
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_STORAGE);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEcoStorageCapacitance();
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
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

    public static BlockEcoStorageCapacitance register(String name) {
        INSTANCE = new BlockEcoStorageCapacitance();
        GameRegistry.registerBlock(INSTANCE, name);
        A = new net.minecraft.item.ItemStack(INSTANCE, 1, META_A);
        B = new net.minecraft.item.ItemStack(INSTANCE, 1, META_B);
        C = new net.minecraft.item.ItemStack(INSTANCE, 1, META_C);
        return INSTANCE;
    }
}
