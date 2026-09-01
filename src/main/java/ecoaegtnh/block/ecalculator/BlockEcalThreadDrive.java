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
import ecoaegtnh.tile.ecalculator.TileEcalThreadDrive;
import gregtech.api.GregTechAPI;

/**
 * t35: E-Calculator thread-core drive block (线程核心驱动器) — 1 slot holding an
 * {@code ItemEcalThreadCore}; the inserted core supplies thread slots (and is the vCPU
 * container). Mirrors the cell drive (t15 pattern): facing metadata 2-5, per-face rendering with
 * a filled two-state front, shift+right-click insert/extract, breakBlock drops the core.
 */
public class BlockEcalThreadDrive extends Block implements ITileEntityProvider {

    /** Metadata facing (vanilla furnace convention): 2=N, 3=S, 4=W, 5=E (E-Storage t25). */
    public static final int META_NORTH = 2;
    public static final int META_SOUTH = 3;
    public static final int META_WEST = 4;
    public static final int META_EAST = 5;

    public static BlockEcalThreadDrive INSTANCE;

    @SideOnly(Side.CLIENT)
    private IIcon iconFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconFrontFilled;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;

    public BlockEcalThreadDrive() {
        super(Material.iron);
        setHardness(20.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setBlockName("ecoaegtnh.ecalculator_thread_drive");
        setBlockTextureName("ecoaegtnh:ecal_thread_drive"); // server-side / fallback texture
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_CALC);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEcalThreadDrive();
    }

    // ------------------------------------------------------------------
    // Facing (metadata 2-5, vanilla furnace convention) — E-Storage t25 pattern
    // ------------------------------------------------------------------

    /**
     * Sets the horizontal facing from the placer's look direction (vanilla furnace convention,
     * t44: restored — the t38 180° reversal is revoked; the grid/chip front faces the placer,
     * exactly like the cell drive).
     */
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
    // Per-face rendering (t15 pattern; filled two-state front when a core is inserted)
    // ------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        if (side == facingFromMeta(meta).ordinal()) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof ecoaegtnh.tile.ecalculator.TileEcalThreadDrive drive && drive.getCoreStack() != null) {
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
        this.blockIcon = reg.registerIcon("ecoaegtnh:ecal_thread_drive_front");
        iconFront = reg.registerIcon("ecoaegtnh:ecal_thread_drive_front");
        iconFrontFilled = reg.registerIcon("ecoaegtnh:ecal_thread_drive_front_filled");
        iconSide = reg.registerIcon("ecoaegtnh:ecal_thread_drive");
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (GregTechAPI.isMachineBlock(this, world.getBlockMetadata(x, y, z))) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
    }

    /**
     * Shift-right-click interaction (t13 pattern, mirrors the cell drive): sneak + held thread
     * core + empty slot -> insert one core; sneak + empty hand + occupied slot -> extract. Only
     * the server mutates the slot; the click is consumed on both sides.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof ecoaegtnh.tile.ecalculator.TileEcalThreadDrive drive)) {
            return false;
        }
        return drive.interactWithCore(player);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        // Drop the stored thread core (with NBT) before the tile is destroyed (cell-drive
        // pattern, P1-3). In-flight tasks are cancelled by the controller teardown (user decision:
        // breaking parts cancels tasks — no NBT persistence).
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof ecoaegtnh.tile.ecalculator.TileEcalThreadDrive drive) {
            net.minecraft.item.ItemStack core = drive.getCoreStack();
            drive.setInventorySlotContents(0, null);
            if (core != null && !world.isRemote) {
                float f = 0.7F;
                double dx = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                double dy = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                double dz = world.rand.nextFloat() * f + (1.0F - f) * 0.5D;
                net.minecraft.entity.item.EntityItem entityitem = new net.minecraft.entity.item.EntityItem(
                    world,
                    x + dx,
                    y + dy,
                    z + dz,
                    core);
                entityitem.delayBeforeCanPickup = 10;
                world.spawnEntityInWorld(entityitem);
            }
        }
        if (GregTechAPI.isMachineBlock(block, meta)) {
            GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    public static BlockEcalThreadDrive register(String name) {
        INSTANCE = new BlockEcalThreadDrive();
        GameRegistry.registerBlock(INSTANCE, name);
        return INSTANCE;
    }
}
