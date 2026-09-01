package ecoaegtnh.network;

import appeng.api.config.CellType;

/**
 * t85: access contract for the mixin-added per-container network-tool cell-tab selection.
 * <p>
 * {@link ecoaegtnh.mixin.MixinContainerNetworkStatus} makes AE2U's
 * {@code ContainerNetworkStatus} implement this interface so the server-side packet handler can
 * store the client's selection without AE2U source changes or reflection.
 */
public interface INetworkToolCellTypeHolder {

    /** @param cellType the network-tool tab the client selected (ITEM/FLUID/ESSENTIA). */
    void ecoaegtnh$setSelectedCellType(CellType cellType);
}
