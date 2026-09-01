package ecoaegtnh.block.estorage;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
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
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;
import gregtech.api.GregTechAPI;

/**
 * E-Storage drive bay block. Holds one storage cell; the tile exposes the cell to the AE grid
 * through the ME bus.
 * <p>
 * The block stores a horizontal facing in the metadata (vanilla furnace convention: 2=N, 3=S,
 * 4=W, 5=E, set from the player's facing on placement) and renders a two-state front: the empty
 * front texture, or the filled/highlighted front texture when a storage cell is inserted.
 */
public class BlockEcoStorageDrive extends Block implements ITileEntityProvider {

    /** Metadata facing (vanilla furnace convention): 2=N, 3=S, 4=W, 5=E. */
    public static final int META_NORTH = 2;
    public static final int META_SOUTH = 3;
    public static final int META_WEST = 4;
    public static final int META_EAST = 5;

    public static BlockEcoStorageDrive INSTANCE;

    /**
     * t88: custom 3D render id, assigned by the client renderer during {@code init}
     * (0 = vanilla cube on the server / before registration, which is never rendered
     * there anyway). The world block is drawn by the TileEcoStorageDrive TESR; the
     * item form by the matching ISBRH (ecoaegtnh.client.render.*).
     */
    public static int renderId = 0;

    @SideOnly(Side.CLIENT)
    private IIcon iconFrontEmpty;
    @SideOnly(Side.CLIENT)
    private IIcon iconFrontFilled;
    /** Per-cell-type filled front textures (t33): item = gold, fluid = blue, essentia = purple. */
    @SideOnly(Side.CLIENT)
    private IIcon iconFrontFilledItem;
    @SideOnly(Side.CLIENT)
    private IIcon iconFrontFilledFluid;
    @SideOnly(Side.CLIENT)
    private IIcon iconFrontFilledEssentia;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;

    public BlockEcoStorageDrive() {
        super(Material.iron);
        setHardness(20.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.storage_array_drive");
        setBlockTextureName("ecoaegtnh:storage_array_drives_front");
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_STORAGE);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEcoStorageDrive();
    }

    // ------------------------------------------------------------------
    // Facing (metadata 2-5, vanilla furnace convention)
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
    // Two-state rendering: front (empty/filled) vs sides
    // ------------------------------------------------------------------

    /** t88: the drive bay uses a custom 3D renderer (TESR in world, ISBRH as item). */
    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        if (side == facingFromMeta(meta).ordinal()) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEcoStorageDrive drive && drive.getCellStack() != null) {
                return filledIconFor(
                    drive.getCellStack()
                        .getItem());
            }
            return iconFrontEmpty;
        }
        return iconSide;
    }

    /**
     * Filled front icon matching the inserted cell's type (t33): item=gold, fluid=blue,
     * essentia=purple, anything else = the default cyan filled texture.
     */
    @SideOnly(Side.CLIENT)
    private IIcon filledIconFor(net.minecraft.item.Item item) {
        if (item instanceof ecoaegtnh.item.estorage.ItemEcoStorageCellItem) {
            return iconFrontFilledItem;
        }
        if (item instanceof ecoaegtnh.item.estorage.ItemEcoStorageCellFluid) {
            return iconFrontFilledFluid;
        }
        if (item instanceof ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia) {
            return iconFrontFilledEssentia;
        }
        return iconFrontFilled;
    }

    /** t88: block-atlas icon for the side faces (used by the 3D TESR for the shell). */
    @SideOnly(Side.CLIENT)
    public IIcon getSideIcon() {
        return iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == facingFromMeta(meta).ordinal() ? iconFrontEmpty : iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.blockIcon = reg.registerIcon("ecoaegtnh:storage_array_drives_front");
        iconFrontEmpty = reg.registerIcon("ecoaegtnh:storage_array_drives_front");
        iconFrontFilled = reg.registerIcon("ecoaegtnh:storage_array_drives_front_filled");
        iconFrontFilledItem = reg.registerIcon("ecoaegtnh:storage_array_drives_front_filled_item");
        iconFrontFilledFluid = reg.registerIcon("ecoaegtnh:storage_array_drives_front_filled_fluid");
        iconFrontFilledEssentia = reg.registerIcon("ecoaegtnh:storage_array_drives_front_filled_essentia");
        iconSide = reg.registerIcon("ecoaegtnh:storage_array_drives_side");
    }

    // ------------------------------------------------------------------
    // Sneak-right-click interaction (insert / extract a storage cell)
    // ------------------------------------------------------------------

    /**
     * Sneak-right-click interaction (mirrors the reference EStorageEventHandler, simplified):
     * sneak + held storage cell + empty bay -> insert one cell; sneak + empty hand + bay with a
     * cell -> extract the cell into the hand. Only the server mutates the slot; the click is
     * consumed so no other interaction (GUI etc.) fires.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            // The server performs the slot change; consume the click on the client too so the
            // interaction feels synchronous (the drive bay has no GUI of its own).
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEcoStorageDrive drive)) {
            return false;
        }
        return drive.interactWithCell(player);
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
        // P1-3: drop the stored cell (with all its NBT contents) before the tile is destroyed,
        // otherwise breaking the drive bay would destroy the cell and all stored items.
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEcoStorageDrive drive) {
            ItemStack cell = drive.getCellStack();
            drive.setInventorySlotContents(0, null);
            if (cell != null && !world.isRemote) {
                float f = 0.7F;
                double dx = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                double dy = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                double dz = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                EntityItem entityitem = new EntityItem(world, x + dx, y + dy, z + dz, cell);
                entityitem.delayBeforeCanPickup = 10;
                world.spawnEntityInWorld(entityitem);
            }
        }
        if (GregTechAPI.isMachineBlock(block, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    public static BlockEcoStorageDrive register(String name) {
        INSTANCE = new BlockEcoStorageDrive();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
