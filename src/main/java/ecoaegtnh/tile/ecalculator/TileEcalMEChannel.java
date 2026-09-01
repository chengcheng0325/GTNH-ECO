package ecoaegtnh.tile.ecalculator;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import ecoaegtnh.block.ecalculator.BlockEcalMEChannel;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * E-Calculator ME channel tile: the subsystem's single AE grid connection point. Phase A exposes
 * the proxy (idle power, DENSE channel flags) and the CPU-list entry point {@link #getCPUs()}
 * (empty until phase B wires the controller's vCPU list). Phase B also wires
 * {@link #postCPUClusterChangeEvent()} to the vCPU create/destroy paths.
 */
public class TileEcalMEChannel extends TileEcalPart implements IGridProxyable, IActionHost {

    protected final AENetworkProxy proxy = new AENetworkProxy(
        this,
        "ecoaegtnh_ecal_channel",
        getVisualItemStack(),
        true);
    protected final MachineSource source = new MachineSource(this);
    private boolean wasActive = false;
    /** Debounced grid membership: follows the controller's operational state (see updateEntity). */
    private boolean connected = false;

    public TileEcalMEChannel() {
        this.proxy.setIdlePowerUsage(1.0D);
        this.proxy.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY);
    }

    public ItemStack getVisualItemStack() {
        return new ItemStack(BlockEcalMEChannel.INSTANCE, 1, 0);
    }

    public MachineSource getSource() {
        return source;
    }

    /** Grid-serving state: assembled + controller alive + controller allowed to work. */
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

    /**
     * Crafting CPU clusters this channel exposes to the AE grid: the controller's thread-core CPUs
     * + standby vCPU (plan §7.3). Empty while not operational.
     */
    public List<CraftingCPUCluster> getCPUs() {
        if (!isOperational() || controller == null) return Collections.emptyList();
        return controller.getClusterList();
    }

    /** Notify the AE grid that the CPU list changed (phase B call sites). */
    public void postCPUClusterChangeEvent() {
        if (!proxy.isActive()) return;
        try {
            proxy.getGrid()
                .postEvent(new MENetworkCraftingCpuChange(proxy.getNode()));
        } catch (GridAccessException ignored) {}
    }

    @MENetworkEventSubscribe
    public void stateChange(MENetworkPowerStatusChange c) {
        final boolean currentActive = this.proxy.isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            postCPUClusterChangeEvent();
        }
    }

    @MENetworkEventSubscribe
    public void stateChange(MENetworkChannelsChanged c) {
        final boolean currentActive = this.proxy.isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            postCPUClusterChangeEvent();
        }
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
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public boolean onAssembled(ecoaegtnh.metatileentity.MTEEcalArray controller) {
        if (!super.onAssembled(controller)) return false; // claimed by another controller
        connected = true;
        proxy.setVisualRepresentation(getVisualItemStack());
        proxy.onReady();
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
        // Debounced: follow the controller's operational state (proxy joins/leaves the grid).
        if ((worldObj.getTotalWorldTime() & 7) == 0) { // every 8 ticks
            boolean operational = isOperational();
            if (operational != connected) {
                connected = operational;
                if (operational) {
                    proxy.onReady();
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

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        proxy.writeToNBT(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        proxy.readFromNBT(tag);
    }
}
