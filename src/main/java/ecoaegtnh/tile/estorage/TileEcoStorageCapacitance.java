package ecoaegtnh.tile.estorage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import ecoaegtnh.metatileentity.MTEEcoStorageArray;

/**
 * E-Storage capacitance tile: pure double energy pool (AE energy units).
 * <p>
 * t67: 1) per-cell capacity is unified to 2,000,000 AE (block meta A/B/C no longer changes the
 * capacity — the tier recipes/items remain, but every cell stores the same amount); 2) the cell
 * is SHAREABLE: multiple adjacent arrays may claim the same capacitance cells at once (shared
 * energy pool), unlike drive bays / ME buses which stay single-owner (t55).
 */
public class TileEcoStorageCapacitance extends TileEcoStoragePart {

    /** t67: unified per-cell capacity (user: 2,000,000 AE), independent of block meta tier. */
    public static final double CAPACITY = 2_000_000D;

    private double energyStored = 0;
    private double maxEnergyStore = CAPACITY;

    /** t67: shared ownership — every assembled array that contains this cell claims it. */
    private final List<MTEEcoStorageArray> owners = new ArrayList<>();

    public double getEnergyStored() {
        return energyStored;
    }

    public double getMaxEnergyStore() {
        return maxEnergyStore;
    }

    /** Capacity is unified since t67; kept for the scanStructureVolume call site (meta ignored). */
    public void setCapacityByMeta(int meta) {
        maxEnergyStore = CAPACITY;
    }

    /** @return the amount that could not be stored. */
    public double injectPower(double amt, boolean simulate) {
        if (amt <= 0) return 0;
        if (simulate) {
            double fake = energyStored + amt;
            return fake > maxEnergyStore ? fake - maxEnergyStore : 0;
        }
        if (energyStored >= maxEnergyStore) return amt;
        double toInsert = Math.min(amt, maxEnergyStore - energyStored);
        energyStored += toInsert;
        markDirtyAndUpdate();
        return amt - toInsert;
    }

    /** @return the amount actually extracted. */
    public double extractPower(double amt, boolean simulate) {
        if (amt <= 0 || energyStored <= 0) return 0;
        double toExtract = Math.min(amt, energyStored);
        if (!simulate) {
            energyStored -= toExtract;
            markDirtyAndUpdate();
        }
        return toExtract;
    }

    public double getFillFactor() {
        return maxEnergyStore == 0 ? 0 : energyStored / maxEnergyStore;
    }

    // ------------------------------------------------------------------
    // t67: shared ownership — multiple arrays can claim the same cell
    // ------------------------------------------------------------------

    /**
     * t67: capacitance never rejects a controller — every assembled array that contains this cell
     * claims it (dead owners are pruned first, t59 semantics preserved per owner). Drive bays and
     * ME buses keep the t55 single-owner rejection (base implementation).
     */
    @Override
    public boolean onAssembled(MTEEcoStorageArray controller) {
        if (controller == null) return false;
        owners.removeIf(o -> o != controller && !isOwnerAlive(o));
        if (!owners.contains(controller)) {
            owners.add(controller);
        }
        this.assembled = true;
        markForUpdate();
        return true;
    }

    /** t67: drop only THIS controller's claim; other arrays keep their share of the cell. */
    public void onDisassembled(MTEEcoStorageArray controller) {
        owners.remove(controller);
        if (owners.isEmpty()) {
            this.assembled = false;
        }
        markForUpdate();
    }

    @Override
    public void onDisassembled() {
        owners.clear();
        this.assembled = false;
        markForUpdate();
    }

    @Override
    public boolean isAssembled() {
        return !owners.isEmpty();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setDouble("energyStored", energyStored);
        tag.setDouble("maxEnergyStore", maxEnergyStore);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energyStored = tag.getDouble("energyStored");
        // t67: capacity is always the unified 2M — existing saves keep the old A/B/C NBT value in
        // the tag, but the stored amount is clamped to the new capacity on load.
        maxEnergyStore = CAPACITY;
        if (energyStored > maxEnergyStore) energyStored = maxEnergyStore;
    }
}
