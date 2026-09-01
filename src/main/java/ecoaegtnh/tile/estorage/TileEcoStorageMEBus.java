package ecoaegtnh.tile.estorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.events.MENetworkPowerStorage;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import ecoaegtnh.EcoAEGTNHCore;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * E-Storage ME bus tile: the single AE grid connection point of the Storage Array. Exposes the
 * drive-bay cells via {@link ICellContainer} and the capacitance as {@link IAEPowerStorage}.
 */
public class TileEcoStorageMEBus extends TileEcoStoragePart implements ICellContainer, IGridProxyable, IAEPowerStorage {

    protected final AENetworkProxy proxy = new AENetworkProxy(this, "ecoaegtnh_me_bus", getVisualItemStack(), true);
    protected final MachineSource source = new MachineSource(this);
    protected int priority = 0;
    private boolean wasActive = false;
    /**
     * t55 C: whether this bus is currently connected to the AE grid. Follows the controller's
     * operational state (assembled + controller tile alive + controller allowed to work); flips
     * only when that state changes (debounced), so structure re-checks never cause reconnect
     * churn. Default false; set true in onAssembled.
     */
    private boolean connected = false;
    /** t74 diagnostic: throttle the power-flow log to one line every 5 seconds. */
    private long lastPowerLogTick = 0;

    public TileEcoStorageMEBus() {
        this.proxy.setIdlePowerUsage(1.0D);
        this.proxy.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY);
    }

    /**
     * t74 diagnostic: throttled (5 s) power-flow log line so a server session shows whether the
     * AE grid actually calls our inject/extract and how much the capacitance accepts. For
     * {@code injectAEPower} {@code result} is the amount NOT stored; for {@code extractAEPower}
     * it is the amount actually extracted.
     */
    private void logPower(String op, double amt, double result, Actionable mode) {
        if (worldObj == null || worldObj.isRemote) return;
        long t = worldObj.getTotalWorldTime();
        if (t - lastPowerLogTick < 100) return;
        lastPowerLogTick = t;
        org.apache.logging.log4j.LogManager.getLogger("ECOAEGTNH")
            .info(
                "MEBus " + op
                    + " amt="
                    + amt
                    + " result="
                    + result
                    + " mode="
                    + mode
                    + " stored="
                    + getAECurrentPower()
                    + "/"
                    + getAEMaxPower());
    }

    public ItemStack getVisualItemStack() {
        return new ItemStack(EcoAEGTNHCore.Blocks.meBus, 1, 0);
    }

    public MachineSource getSource() {
        return source;
    }

    /**
     * t55 B/C: the bus serves the grid only while the controller is present AND working. False
     * when the structure is broken, the controller block was removed, or the machine is switched
     * off (isAllowedToWork() == false) — the AE terminal then stops showing the array.
     */
    public boolean isOperational() {
        if (!assembled || controller == null || worldObj == null) return false;
        if (controller.getBaseMetaTileEntity() == null) return false;
        net.minecraft.tileentity.TileEntity ct = worldObj.getTileEntity(
            controller.getBaseMetaTileEntity()
                .getXCoord(),
            controller.getBaseMetaTileEntity()
                .getYCoord(),
            controller.getBaseMetaTileEntity()
                .getZCoord());
        if (!(ct instanceof IGregTechTileEntity igte)) return false;
        if (igte.getMetaTileEntity() != controller) return false;
        return igte.isAllowedToWork();
    }

    /** Bridge for drive-bay alteration events. */
    public void postAlteration(StorageChannel channel, List<? extends IAEStack<?>> changes) {
        if (!isOperational()) return;
        try {
            if (proxy.isActive()) {
                proxy.getStorage()
                    .postAlterationOfStoredItems(channel, changes, source);
            }
        } catch (GridAccessException ignored) {}
    }

    // ------------------------------------------------------------------
    // ICellProvider / ICellContainer
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("rawtypes")
    public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
        if (!isOperational()) return Collections.emptyList();
        List<IMEInventoryHandler> result = new ArrayList<>();
        for (TileEcoStorageDrive drive : controller.getDriveBays()) {
            IMEInventoryHandler<?> h = drive.getHandler(channel);
            if (h != null) result.add(h);
        }
        return result;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ------------------------------------------------------------------
    // IAEPowerStorage: delegate to the controller's capacitance
    // ------------------------------------------------------------------

    @Override
    public double injectAEPower(double amt, Actionable mode) {
        if (!isOperational()) return amt;
        if (amt < 0.000001) return 0;
        // When transitioning from empty to powered, notify the grid that we can now provide power
        // (mirrors reference EStorageMEChannel; AE2U EnergyGridCache only registers providers on
        // node-join + this event).
        if (mode == Actionable.MODULATE && getAECurrentPower() < 0.01 && amt > 0) {
            try {
                proxy.getGrid()
                    .postEvent(new MENetworkPowerStorage(this, MENetworkPowerStorage.PowerEventType.PROVIDE_POWER));
            } catch (GridAccessException ignored) {}
        }
        double result = controller.injectPower(amt, mode == Actionable.MODULATE);
        logPower("injectAEPower", amt, result, mode);
        return result;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier multiplier) {
        if (!isOperational()) return 0;
        if (mode == Actionable.MODULATE) {
            // When transitioning from full to non-full, notify the grid that we can be recharged.
            final boolean wasFull = getAECurrentPower() >= getAEMaxPower() - 0.001;
            if (wasFull && amt > 0) {
                try {
                    proxy.getGrid()
                        .postEvent(new MENetworkPowerStorage(this, MENetworkPowerStorage.PowerEventType.REQUEST_POWER));
                } catch (GridAccessException ignored) {}
            }
        }
        double result = multiplier
            .divide(controller.extractPower(multiplier.multiply(amt), mode == Actionable.MODULATE));
        logPower("extractAEPower", amt, result, mode);
        return result;
    }

    @Override
    public double getAEMaxPower() {
        return controller == null ? 0 : controller.getMaxEnergyStore();
    }

    @Override
    public double getAECurrentPower() {
        return controller == null ? 0 : controller.getEnergyStored();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    // ------------------------------------------------------------------
    // IGridHost / IGridProxyable / IActionHost
    // ------------------------------------------------------------------

    @Override
    public IGridNode getActionableNode() {
        return proxy.getNode();
    }

    @Override
    public AENetworkProxy getProxy() {
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {}

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return proxy.getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.DENSE;
    }

    @Override
    public void securityBreak() {
        worldObj.func_147480_a(xCoord, yCoord, zCoord, true);
    }

    // ------------------------------------------------------------------
    // Grid events
    // ------------------------------------------------------------------

    @MENetworkEventSubscribe
    public void stateChange(MENetworkPowerStatusChange c) {
        postCellArrayUpdateEvent();
        // t74: the AE2U EnergyGridCache removes a power storage from its requester list whenever
        // injectAEPower rejects once (returns amt > 0 — e.g. a transient not-operational tick).
        // The only re-add paths are node (re)join and the REQUEST_POWER event (posted when leaving
        // "full"), so a dropped requester could stay uncharged forever. Re-announce on every
        // network power-state change so the capacitance is re-added whenever the grid (re)gains
        // power.
        if (isOperational()) {
            try {
                proxy.getGrid()
                    .postEvent(new MENetworkPowerStorage(this, MENetworkPowerStorage.PowerEventType.REQUEST_POWER));
            } catch (GridAccessException ignored) {}
        }
    }

    @MENetworkEventSubscribe
    public void stateChange(MENetworkChannelsChanged c) {
        postCellArrayUpdateEvent();
    }

    protected void postCellArrayUpdateEvent() {
        boolean currentActive = proxy.isActive();
        if (wasActive != currentActive) {
            wasActive = currentActive;
            try {
                proxy.getGrid()
                    .postEvent(new MENetworkCellArrayUpdate());
            } catch (GridAccessException ignored) {}
        }
    }

    /**
     * Force the AE grid to re-query the drive-bay cell arrays after a cell was inserted into or
     * removed from a drive bay (the reference posts MENetworkCellArrayUpdate from the drive's
     * inventory-change handler).
     */
    public void forceCellArrayUpdate() {
        if (!isOperational()) return;
        try {
            if (proxy.isActive()) {
                proxy.getGrid()
                    .postEvent(new MENetworkCellArrayUpdate());
            }
        } catch (GridAccessException ignored) {}
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public boolean onAssembled(ecoaegtnh.metatileentity.MTEEcoStorageArray controller) {
        if (!super.onAssembled(controller)) return false; // claimed by another controller (t55 A)
        connected = true;
        proxy.setVisualRepresentation(getVisualItemStack());
        proxy.onReady();
        try {
            proxy.getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
        return true;
    }

    @Override
    public void onDisassembled() {
        connected = false;
        super.onDisassembled();
        proxy.invalidate();
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj == null || worldObj.isRemote) return;
        // t55 B/C (debounced): follow the controller's operational state — only act when it FLIPS.
        // Controller removed/off -> proxy.invalidate() (node leaves the grid: cells + power gone);
        // controller back/on -> proxy.onReady() re-joins + cell-array refresh.
        if ((worldObj.getTotalWorldTime() & 7) == 0) { // every 8 ticks
            boolean operational = isOperational();
            if (operational != connected) {
                connected = operational;
                if (operational) {
                    proxy.onReady();
                    try {
                        proxy.getGrid()
                            .postEvent(new MENetworkCellArrayUpdate());
                    } catch (GridAccessException ignored) {}
                } else {
                    proxy.invalidate();
                }
            }
        }
    }

    @Override
    public void invalidate() {
        proxy.invalidate();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        proxy.onChunkUnload();
        super.onChunkUnload();
    }

    @Override
    public void saveChanges(IMEInventory cellInventory) {}

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        proxy.writeToNBT(tag);
        tag.setInteger("priority", priority);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        proxy.readFromNBT(tag);
        priority = tag.getInteger("priority");
    }
}
