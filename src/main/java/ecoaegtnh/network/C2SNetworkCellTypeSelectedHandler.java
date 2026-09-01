package ecoaegtnh.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * t85 server-side handler: stores the client's network-tool cell-tab selection on the open
 * {@code ContainerNetworkStatus} (mixin-added via {@link INetworkToolCellTypeHolder}).
 * <p>
 * Follows AE2U's own packet pattern (PacketNetworkStatusSelected touches the open container
 * directly on the netty IO thread); the write is a single field set on a container object.
 */
public class C2SNetworkCellTypeSelectedHandler implements IMessageHandler<C2SNetworkCellTypeSelected, IMessage> {

    @Override
    public IMessage onMessage(final C2SNetworkCellTypeSelected message, final MessageContext ctx) {
        final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player != null && player.openContainer instanceof INetworkToolCellTypeHolder holder) {
            holder.ecoaegtnh$setSelectedCellType(message.getCellType());
        }
        return null;
    }
}
