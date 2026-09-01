package ecoaegtnh.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEInventoryHandler;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;

/**
 * Watcher wrapping a cell handler inside a drive bay: posts alteration events to the ME grid and
 * marks the drive as "writing" (mirrors the reference ECellDriveWatcher).
 */
public class EcoCellDriveWatcher<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    protected final TileEcoStorageDrive drive;

    public EcoCellDriveWatcher(IMEInventory<T> i, IAEStackType<T> type, TileEcoStorageDrive drive) {
        super(i, type);
        this.drive = drive;
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource src) {
        long size = input.getStackSize();
        T remainder = super.injectItems(input, mode, src);
        if (mode == Actionable.MODULATE && (remainder == null || remainder.getStackSize() != size)) {
            postAlteration(
                input.copy()
                    .setStackSize(size - (remainder == null ? 0 : remainder.getStackSize())));
        }
        return remainder;
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource src) {
        T extracted = super.extractItems(request, mode, src);
        if (mode == Actionable.MODULATE && extracted != null) {
            postAlteration(
                request.copy()
                    .setStackSize(-extracted.getStackSize()));
        }
        return extracted;
    }

    private void postAlteration(T change) {
        drive.onWriting();
        if (drive.getController() != null) {
            drive.getController()
                .postAlteration(getStackType(), java.util.Collections.singletonList(change));
        }
    }
}
