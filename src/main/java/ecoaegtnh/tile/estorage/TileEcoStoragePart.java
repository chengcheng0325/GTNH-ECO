package ecoaegtnh.tile.estorage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import ecoaegtnh.metatileentity.MTEEcoStorageArray;

/**
 * Abstract base for all E-Storage part tiles. Holds a reference to the assembled controller and
 * lifecycle callbacks mirroring the reference's AbstractEPart.
 */
public abstract class TileEcoStoragePart extends TileEntity {

    protected MTEEcoStorageArray controller = null;
    protected boolean assembled = false;

    public MTEEcoStorageArray getController() {
        return controller;
    }

    public boolean isAssembled() {
        return assembled;
    }

    /**
     * Claims this part for the given controller (t55 A: no sharing). Returns false when the part
     * is already owned by a DIFFERENT controller — overlapping structures must not double-use a
     * drive bay / capacitance / ME bus (the first controller that assembles keeps ownership; the
     * other controller's scan excludes the part). Re-claiming by the same controller is idempotent.
     * <p>
     * t59: if the previous owner controller is GONE (its block was removed), the claim is released
     * so a re-placed controller can claim this part again — otherwise removing the controller left
     * every part permanently "owned" and the rebuilt structure stayed incomplete.
     */
    public boolean onAssembled(MTEEcoStorageArray controller) {
        if (this.controller != null && this.controller != controller) {
            if (!isCurrentOwnerAlive()) {
                this.controller = null;
                this.assembled = false;
            } else {
                return false; // still claimed by a live controller (t55 A: no sharing)
            }
        }
        this.controller = controller;
        this.assembled = true;
        markForUpdate();
        return true;
    }

    /** True while the claimed controller still exists in the world at its position (t59). */
    protected boolean isCurrentOwnerAlive() {
        return isOwnerAlive(controller);
    }

    /**
     * t67: owner-alive check for an arbitrary controller (shared ownership — the capacitance cell
     * keeps a list of owners; drive/ME bus use the single {@link #controller} field via
     * {@link #isCurrentOwnerAlive()}).
     */
    protected boolean isOwnerAlive(MTEEcoStorageArray candidate) {
        if (candidate == null || worldObj == null) return false;
        if (candidate.getBaseMetaTileEntity() == null) return false;
        net.minecraft.tileentity.TileEntity te = worldObj.getTileEntity(
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

    /**
     * t67: disassembly scoped to one controller. Default = single-owner semantics (drive bay / ME
     * bus — same as {@link #onDisassembled()}); {@link TileEcoStorageCapacitance} overrides this
     * with per-owner removal so one array dismantling does not drop another array's shared claim.
     */
    public void onDisassembled(MTEEcoStorageArray controller) {
        onDisassembled();
    }

    protected void markForUpdate() {
        if (worldObj == null) return;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    protected void markDirtyAndUpdate() {
        markDirty();
        markForUpdate();
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
