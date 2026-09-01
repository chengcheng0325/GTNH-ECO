package ecoaegtnh.tile.ecalculator;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import ecoaegtnh.metatileentity.MTEEcalArray;

/**
 * Abstract base for all E-Calculator part tiles. Holds a reference to the assembled controller and
 * lifecycle callbacks, mirroring the E-Storage part base (TileEcoStoragePart) and the reference's
 * AbstractEPart. Phase A skeleton: ownership + assembly state only.
 */
public abstract class TileEcalPart extends TileEntity {

    protected MTEEcalArray controller = null;
    protected boolean assembled = false;

    public MTEEcalArray getController() {
        return controller;
    }

    public boolean isAssembled() {
        return assembled;
    }

    /**
     * Claims this part for the given controller (no sharing, same as E-Storage t55 A). Returns
     * false when the part is already owned by a DIFFERENT live controller. Re-claiming by the same
     * controller is idempotent. If the previous owner controller is gone, the claim is released.
     */
    public boolean onAssembled(MTEEcalArray controller) {
        if (this.controller != null && this.controller != controller) {
            if (!isOwnerAlive(this.controller)) {
                this.controller = null;
                this.assembled = false;
            } else {
                return false;
            }
        }
        this.controller = controller;
        this.assembled = true;
        markForUpdate();
        return true;
    }

    /** True while the claimed controller still exists in the world at its position. */
    protected boolean isOwnerAlive(MTEEcalArray candidate) {
        if (candidate == null || worldObj == null) return false;
        if (candidate.getBaseMetaTileEntity() == null) return false;
        TileEntity te = worldObj.getTileEntity(
            candidate.getBaseMetaTileEntity()
                .getXCoord(),
            candidate.getBaseMetaTileEntity()
                .getYCoord(),
            candidate.getBaseMetaTileEntity()
                .getZCoord());
        return te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity igte
            && igte.getMetaTileEntity() == candidate;
    }

    /** Called by the controller when the structure breaks. */
    public void onDisassembled() {
        this.controller = null;
        this.assembled = false;
        markForUpdate();
    }

    protected void markForUpdate() {
        if (worldObj == null) return;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        worldObj.func_147479_m(xCoord, yCoord, zCoord);
    }
}
