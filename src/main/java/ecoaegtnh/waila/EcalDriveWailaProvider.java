package ecoaegtnh.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import ecoaegtnh.block.ecalculator.BlockEcalCellDrive;
import ecoaegtnh.block.ecalculator.BlockEcalParallelDrive;
import ecoaegtnh.block.ecalculator.BlockEcalThreadDrive;
import ecoaegtnh.item.ecalculator.ItemEcalCell;
import ecoaegtnh.item.ecalculator.ItemEcalParallelCore;
import ecoaegtnh.item.ecalculator.ItemEcalThreadCore;
import ecoaegtnh.tile.ecalculator.TileEcalCellDrive;
import ecoaegtnh.tile.ecalculator.TileEcalParallelDrive;
import ecoaegtnh.tile.ecalculator.TileEcalThreadDrive;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * t43: WAILA provider for the three E-Calculator drives (parallel / thread / cell): shows the
 * inserted core (or "Empty") plus its values — parallelism, threads/hyper-threads or byte
 * capacity (e.g. "Core: ECO Parallel Core (256)" + "Parallelism: 256").
 * <p>
 * Same client/server split as {@link EcoStorageDriveWailaProvider} (t33 + t40 fix):
 * {@link #getNBTData} runs on the server and sends only the item's unlocalizedName KEY plus
 * structured numeric values (never a translated string — a dedicated server's locale is
 * English, pre-translated strings would ship English to every client); {@link #getWailaBody}
 * resolves the name with the client's own language, falling back to the description-packet-
 * synced tile when the NBT data is absent.
 * <p>
 * Registered via {@code FMLInterModComms.sendMessage("Waila", "register",
 * "ecoaegtnh.waila.EcalDriveWailaProvider.callbackRegister")} (EcoAERegistry, same pattern as
 * the E-Storage drive-bay provider).
 */
public class EcalDriveWailaProvider implements IWailaDataProvider {

    /** Item unlocalizedName key, e.g. "item.ecoaegtnh.ecal_parallel_core_256". */
    private static final String NBT_CORE_KEY = "ecoCoreKey";
    /** Parallel core: parallelism value. */
    private static final String NBT_CORE_PARALLELISM = "ecoCoreParallelism";
    /** Thread core: normal thread slots. */
    private static final String NBT_CORE_THREADS = "ecoCoreThreads";
    /** Thread core: hyper-thread slots. */
    private static final String NBT_CORE_HYPER = "ecoCoreHyper";
    /** Flash cell: byte capacity. */
    private static final String NBT_CORE_BYTES = "ecoCoreBytes";

    /** IMC callback: register this provider for all three drive blocks. */
    public static void callbackRegister(IWailaRegistrar register) {
        EcalDriveWailaProvider provider = new EcalDriveWailaProvider();
        for (Class<?> block : new Class<?>[] { BlockEcalParallelDrive.class, BlockEcalThreadDrive.class,
            BlockEcalCellDrive.class }) {
            register.registerBodyProvider(provider, block);
            register.registerNBTProvider(provider, block);
        }
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        // Server-side: send the lang KEY and raw values, never a translated string (t40).
        if (te instanceof TileEcalParallelDrive pd && pd.getCoreStack() != null) {
            ItemStack core = pd.getCoreStack();
            tag.setString(
                NBT_CORE_KEY,
                core.getItem()
                    .getUnlocalizedName(core));
            if (core.getItem() instanceof ItemEcalParallelCore ecoCore) {
                tag.setInteger(NBT_CORE_PARALLELISM, ecoCore.getParallelism());
            }
        } else if (te instanceof TileEcalThreadDrive td && td.getCoreStack() != null) {
            ItemStack core = td.getCoreStack();
            tag.setString(
                NBT_CORE_KEY,
                core.getItem()
                    .getUnlocalizedName(core));
            if (core.getItem() instanceof ItemEcalThreadCore ecoCore) {
                tag.setInteger(NBT_CORE_THREADS, ecoCore.getThreads());
                tag.setInteger(NBT_CORE_HYPER, ecoCore.getHyperThreads());
            }
        } else if (te instanceof TileEcalCellDrive cd && cd.getCellStack() != null) {
            ItemStack core = cd.getCellStack();
            tag.setString(
                NBT_CORE_KEY,
                core.getItem()
                    .getUnlocalizedName(core));
            if (core.getItem() instanceof ItemEcalCell ecoCell) {
                tag.setLong(NBT_CORE_BYTES, ecoCell.getTotalBytes());
            }
        }
        return tag;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound data = accessor.getNBTData();
        String coreKey = data != null && data.hasKey(NBT_CORE_KEY) ? data.getString(NBT_CORE_KEY) : null;
        Integer parallelism = data != null && data.hasKey(NBT_CORE_PARALLELISM) ? data.getInteger(NBT_CORE_PARALLELISM)
            : null;
        Integer threads = data != null && data.hasKey(NBT_CORE_THREADS) ? data.getInteger(NBT_CORE_THREADS) : null;
        Integer hyper = data != null && data.hasKey(NBT_CORE_HYPER) ? data.getInteger(NBT_CORE_HYPER) : null;
        Long bytes = data != null && data.hasKey(NBT_CORE_BYTES) ? data.getLong(NBT_CORE_BYTES) : null;
        if (coreKey == null && accessor.getTileEntity() != null) {
            // Fallback: read the (description-packet-synced) tile directly.
            TileEntity te = accessor.getTileEntity();
            if (te instanceof TileEcalParallelDrive pd && pd.getCoreStack() != null) {
                coreKey = pd.getCoreStack()
                    .getItem()
                    .getUnlocalizedName(pd.getCoreStack());
                parallelism = pd.getSuppliedParallelism();
            } else if (te instanceof TileEcalThreadDrive td && td.getCoreStack() != null) {
                coreKey = td.getCoreStack()
                    .getItem()
                    .getUnlocalizedName(td.getCoreStack());
                threads = td.getThreads();
                hyper = td.getHyperThreads();
            } else if (te instanceof TileEcalCellDrive cd && cd.getCellStack() != null
                && cd.getCellStack()
                    .getItem() instanceof ItemEcalCell ecoCell) {
                        coreKey = cd.getCellStack()
                            .getItem()
                            .getUnlocalizedName(cd.getCellStack());
                        bytes = ecoCell.getTotalBytes();
                    }
        }
        if (coreKey == null) {
            currenttip.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.waila.ecal.drive.core")
                    + " "
                    + EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal("ecoaegtnh.waila.ecal.drive.empty"));
        } else {
            // Client-side translation (t40): resolve the item name with the client's language.
            currenttip.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.waila.ecal.drive.core")
                    + " "
                    + EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal(coreKey + ".name"));
            if (parallelism != null) {
                currenttip.add(
                    EnumChatFormatting.GRAY + StatCollector
                        .translateToLocalFormatted("ecoaegtnh.waila.ecal.drive.parallelism", parallelism));
            }
            if (threads != null) {
                currenttip.add(
                    EnumChatFormatting.GRAY + (hyper != null && hyper > 0
                        ? StatCollector
                            .translateToLocalFormatted("ecoaegtnh.waila.ecal.drive.threads_hyper", threads, hyper)
                        : StatCollector.translateToLocalFormatted("ecoaegtnh.waila.ecal.drive.threads", threads)));
            }
            if (bytes != null) {
                currenttip.add(
                    EnumChatFormatting.GRAY + StatCollector
                        .translateToLocalFormatted("ecoaegtnh.waila.ecal.drive.bytes", formatCompact(bytes)));
            }
        }
        return currenttip;
    }

    /** Compact byte formatting for the capacity line (e.g. 262.1K / 16.4M / 16.8G). */
    private static String formatCompact(long v) {
        if (v >= 1_000_000_000L) {
            return String.format("%.1fG", v / 1e9);
        }
        if (v >= 1_000_000L) {
            return String.format("%.1fM", v / 1e6);
        }
        if (v >= 1_000L) {
            return String.format("%.1fK", v / 1e3);
        }
        return String.valueOf(v);
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
