package ecoaegtnh.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.item.estorage.ItemEcoStorageCell;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA provider for the E-Storage drive bay (t33): shows the inserted storage cell (or
 * "Empty"), plus its kind and capacity (e.g. "Item Cell 16M").
 * <p>
 * t40 fix: the cell's DISPLAY NAME must be translated CLIENT-side. {@link #getNBTData} runs on the
 * server, whose locale is normally English — sending {@code cell.getDisplayName()} there shipped a
 * pre-translated English string to every client. Instead the server sends the item's
 * unlocalizedName key (and the kind lang key + capacity), and {@link #getWailaBody} resolves them
 * with the client's own language (falling back to the description-packet-synced tile when the NBT
 * data is absent).
 * <p>
 * Registered via {@code FMLInterModComms.sendMessage("Waila", "register",
 * "ecoaegtnh.waila.EcoStorageDriveWailaProvider.callbackRegister")} (same pattern as GT5U's
 * {@code gregtech.crossmod.waila.Waila}).
 */
public class EcoStorageDriveWailaProvider implements IWailaDataProvider {

    /** Item unlocalizedName key prefix, e.g. "item.ecoaegtnh.estorage_cell_item_16m". */
    private static final String NBT_CELL_KEY = "ecoCellKey";
    /** Kind lang key, e.g. "ecoaegtnh.waila.drive.kind.item". */
    private static final String NBT_CELL_KIND = "ecoCellKind";
    /** Size label ("256k", "16m", "16384m", ...) — t76: shown instead of MB so k-levels display correctly. */
    private static final String NBT_CELL_CAP = "ecoCellCap";

    /** IMC callback: register this provider for the drive bay block. */
    public static void callbackRegister(IWailaRegistrar register) {
        EcoStorageDriveWailaProvider provider = new EcoStorageDriveWailaProvider();
        register.registerBodyProvider(provider, BlockEcoStorageDrive.class);
        register.registerNBTProvider(provider, BlockEcoStorageDrive.class);
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (te instanceof TileEcoStorageDrive drive && drive.getCellStack() != null) {
            ItemStack cell = drive.getCellStack();
            // Server-side: send the lang KEY, never a translated string (t40).
            tag.setString(
                NBT_CELL_KEY,
                cell.getItem()
                    .getUnlocalizedName(cell));
            tag.setString(NBT_CELL_KIND, cellKindKey(cell));
            if (cell.getItem() instanceof ItemEcoStorageCell ecoCell) {
                tag.setString(NBT_CELL_CAP, ecoCell.getSizeLabel());
            }
        }
        return tag;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound data = accessor.getNBTData();
        String cellKey = data != null && data.hasKey(NBT_CELL_KEY) ? data.getString(NBT_CELL_KEY) : null;
        String kindKey = data != null && data.hasKey(NBT_CELL_KIND) ? data.getString(NBT_CELL_KIND) : null;
        String capLabel = data != null && data.hasKey(NBT_CELL_CAP) ? data.getString(NBT_CELL_CAP) : null;
        if (cellKey == null && accessor.getTileEntity() instanceof TileEcoStorageDrive drive
            && drive.getCellStack() != null) {
            // Fallback: read the (description-packet-synced) tile directly.
            ItemStack cell = drive.getCellStack();
            cellKey = cell.getItem()
                .getUnlocalizedName(cell);
            kindKey = cellKindKey(cell);
            if (cell.getItem() instanceof ItemEcoStorageCell ecoCell) {
                capLabel = ecoCell.getSizeLabel();
            }
        }
        if (cellKey == null) {
            currenttip.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.waila.drive.cell")
                    + " "
                    + EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal("ecoaegtnh.waila.drive.empty"));
        } else {
            // Client-side translation (t40): resolve the item name with the client's language.
            currenttip.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.waila.drive.cell")
                    + " "
                    + EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal(cellKey + ".name"));
            if (kindKey != null) {
                currenttip.add(
                    EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                        "ecoaegtnh.waila.drive.kind.line",
                        StatCollector.translateToLocal(kindKey),
                        capLabel == null ? "" : capLabel));
            }
        }
        return currenttip;
    }

    /** Lang key of the cell kind ("item"/"fluid"/"essentia"), or null for unknown cells. */
    private static String cellKindKey(ItemStack cell) {
        if (cell.getItem() instanceof ItemEcoStorageCell ecoCell) {
            String kind = ecoCell.getCellBaseName();
            if ("item".equals(kind)) {
                return "ecoaegtnh.waila.drive.kind.item";
            }
            if ("fluid".equals(kind)) {
                return "ecoaegtnh.waila.drive.kind.fluid";
            }
            if ("essentia".equals(kind)) {
                return "ecoaegtnh.waila.drive.kind.essentia";
            }
        }
        return null;
    }

    // Unused provider hooks: keep the default WAILA behavior for head/stack/tail.

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }
}
