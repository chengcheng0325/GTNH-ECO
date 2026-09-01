package ecoaegtnh.network;

import appeng.api.config.CellType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * t85: client → server — the network-tool cell-tab selection.
 * <p>
 * AE2U's {@code GuiNetworkStatus} never sends the selected cell type to the server (verified in
 * the rv3-beta-1000 bytecode), so on a dedicated server the container always read ITEM from its
 * own AEConfig and every tab rendered the item-cell list. The client mixin sends this message
 * after the cell-tab button is clicked; the handler stores it on the open container via
 * {@link INetworkToolCellTypeHolder} (see {@code MixinContainerNetworkStatus}).
 */
public class C2SNetworkCellTypeSelected implements IMessage {

    private CellType cellType;

    /** Required no-arg constructor for SimpleNetworkWrapper instantiation. */
    public C2SNetworkCellTypeSelected() {}

    public C2SNetworkCellTypeSelected(CellType cellType) {
        this.cellType = cellType;
    }

    public CellType getCellType() {
        return cellType;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(cellType.ordinal());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        final CellType[] values = CellType.values();
        final int idx = buf.readInt();
        this.cellType = idx >= 0 && idx < values.length ? values[idx] : CellType.ITEM;
    }
}
