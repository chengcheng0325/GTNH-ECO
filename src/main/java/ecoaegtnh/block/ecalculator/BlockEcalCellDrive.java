package ecoaegtnh.block.ecalculator;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ecoaegtnh.tile.ecalculator.TileEcalCellDrive;
import gregtech.api.GregTechAPI;

/**
 * E-Calculator cell drive block. The tile holds one flash-cell slot (ItemEcalCell filter + byte
 * accounting). t15: per-face rendering (E-Storage t26 pattern) — the front face (facing metadata
 * 2-5, vanilla furnace convention) renders {@code ecal_cell_drive_front}, the other five faces
 * render {@code ecal_cell_drive}. Icons are registered on the TextureMap atlas in
 * {@link #registerBlockIcons} (1.7.10 constraint, HANDOVER §5).
 */
public class BlockEcalCellDrive extends Block implements ITileEntityProvider {

    /** Metadata facing (vanilla furnace convention): 2=N, 3=S, 4=W, 5=E (E-Storage t25). */
    public static final int META_NORTH = 2;
    public static final int META_SOUTH = 3;
    public static final int META_WEST = 4;
    public static final int META_EAST = 5;

    public static BlockEcalCellDrive INSTANCE;

    @SideOnly(Side.CLIENT)
    private IIcon iconFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconFrontFilled;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;

    public BlockEcalCellDrive() {
        super(Material.iron);
        setHardness(20.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.ecalculator_cell_drive");
        setBlockTextureName("ecoaegtnh:ecal_cell_drive"); // server-side / fallback texture
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_CALC);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEcalCellDrive();
    }

    // ------------------------------------------------------------------
    // Facing (metadata 2-5, vanilla furnace convention) — E-Storage t25 pattern
    // ------------------------------------------------------------------

    /** Sets the horizontal facing from the placer's look direction. */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int l = MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        int meta = l == 0 ? META_NORTH : l == 1 ? META_EAST : l == 2 ? META_SOUTH : META_WEST;
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
    }

    public static ForgeDirection facingFromMeta(int meta) {
        switch (meta) {
            case META_SOUTH:
                return ForgeDirection.SOUTH;
            case META_WEST:
                return ForgeDirection.WEST;
            case META_EAST:
                return ForgeDirection.EAST;
            default:
                return ForgeDirection.NORTH;
        }
    }

    /** Drops as a plain (un-faced) item; the facing is re-derived from the placer on placement. */
    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public int getDamageValue(World world, int x, int y, int z) {
        return 0;
    }

    // ------------------------------------------------------------------
    // Per-face rendering (t15, E-Storage t26 pattern; t18 adds the filled two-state front)
    // ------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        if (side == facingFromMeta(meta).ordinal()) {
            // t18: filled front when the drive holds a flash cell (E-Storage t26 reference).
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof ecoaegtnh.tile.ecalculator.TileEcalCellDrive drive && drive.getCellStack() != null) {
                return iconFrontFilled;
            }
            return iconFront;
        }
        return iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == facingFromMeta(meta).ordinal() ? iconFront : iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.blockIcon = reg.registerIcon("ecoaegtnh:ecal_cell_drive_front");
        iconFront = reg.registerIcon("ecoaegtnh:ecal_cell_drive_front");
        iconFrontFilled = reg.registerIcon("ecoaegtnh:ecal_cell_drive_front_filled");
        iconSide = reg.registerIcon("ecoaegtnh:ecal_cell_drive");
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (GregTechAPI.isMachineBlock(this, world.getBlockMetadata(x, y, z))) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
    }

    /**
     * Shift-right-click interaction (t13, mirrors the E-Storage drive bay, t16/t25): sneak + held
     * flash cell + empty slot -> insert one cell; sneak + empty hand + occupied slot -> extract the
     * cell into the hand. Only the server mutates the slot; the click is consumed on both sides so
     * no other interaction fires.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            // The server performs the slot change; consume the click on the client too so the
            // interaction feels synchronous (the drive has no GUI of its own).
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof ecoaegtnh.tile.ecalculator.TileEcalCellDrive drive)) {
            return false;
        }
        return drive.interactWithCell(player);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        // Drop the stored flash cell (with NBT) before the tile is destroyed (E-Storage drive
        // pattern, P1-3).
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof ecoaegtnh.tile.ecalculator.TileEcalCellDrive drive) {
            net.minecraft.item.ItemStack cell = drive.getCellStack();
            drive.setInventorySlotContents(0, null);
            if (cell != null && !world.isRemote) {
                float f = 0.7F;
                double dx = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                double dy = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                double dz = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                net.minecraft.entity.item.EntityItem entityitem = new net.minecraft.entity.item.EntityItem(
                    world,
                    x + dx,
                    y + dy,
                    z + dz,
                    cell);
                entityitem.delayBeforeCanPickup = 10;
                world.spawnEntityInWorld(entityitem);
            }
        }
        if (GregTechAPI.isMachineBlock(block, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    public static BlockEcalCellDrive register(String name) {
        INSTANCE = new BlockEcalCellDrive();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
