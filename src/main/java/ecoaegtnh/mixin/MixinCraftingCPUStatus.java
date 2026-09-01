package ecoaegtnh.mixin;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.container.implementations.CraftingCPUStatus;
import ecoaegtnh.ecalculator.ECPUCluster;
import ecoaegtnh.ecalculator.ECPUStatus;

/**
 * M3 (t25): tags ECO vCPU rows in the AE2 crafting-status table with their controller tier and
 * available thread counts. Adapted from the 1.12.2 reference (S:MixinCraftingCPUStatus.java) to
 * rv3 per plan §6.5:
 * <ul>
 * <li>{@code <init>(ICraftingCPU,int)} @RETURN — server-side row creation: record tier + t33
 * dynamic remaining thread totals (Σ of free slots; running rows hide the threads line via the
 * assigned flag, standby rows show the remaining values) through
 * {@link ECPUCluster#ecoaegtnh$getHostThreads()}/{@link ECPUCluster#ecoaegtnh$getHostHyperThreads()};
 * <li>{@code <init>(NBTTagCompound)} @RETURN + {@code writeToNBT} @RETURN — rv3's packet path
 * reuses the NBT constructor ({@code CraftingCPUStatus(ByteBuf)} → readNBTFromPacket → NBT ctor,
 * and writeToPacket → writeToNBT), so these two injections cover BOTH the NBT and the ByteBuf
 * sync paths; they MUST run on the client too (the client rebuilds each row from the packet) —
 * hence this mixin lives in the default (both-sides) group, not the server group.
 * </ul>
 * priority = 2000 (R14 mitigation, same rationale as M1/M2). All members keep MCP names in the
 * release jar (JREL verified) → remap=false + literal names.
 */
@Mixin(value = CraftingCPUStatus.class, priority = 2000, remap = false)
public abstract class MixinCraftingCPUStatus implements ECPUStatus {

    /** Controller tier (0/1/2); -1 = vanilla row. */
    @Unique
    private int ecoaegtnh$ecLevel = -1;
    /** Host remaining thread slots shown for this row (t33: Σ free normal slots). */
    @Unique
    private int ecoaegtnh$ecThreads = 0;
    /** Host remaining hyper-thread slots shown for this row. */
    @Unique
    private int ecoaegtnh$ecHyperThreads = 0;
    /** 1 = running vCPU (assigned to a thread core), 0 = standby vCPU. */
    @Unique
    private int ecoaegtnh$ecAssigned = 0;
    /** t114h: per-machine vCPU sequence id (running rows display "ECO vCPU #id"). */
    @Unique
    private int ecoaegtnh$ecVCPUId = 0;
    /** t116d: vCPU effective storage (pool remaining + task bytes); -1 = vanilla CPU row. */
    @Unique
    private long ecoaegtnh$ecEffectiveStorage = -1L;

    @Inject(method = "<init>(Lappeng/api/networking/crafting/ICraftingCPU;I)V", at = @At("RETURN"))
    private void ecoaegtnh$injectInit(final ICraftingCPU cluster, final int serial, final CallbackInfo ci) {
        if (cluster instanceof ECPUCluster ec) {
            this.ecoaegtnh$ecLevel = ec.ecoaegtnh$getControllerTier();
            // t33: remaining values are host-wide (owner set for the cluster's whole lifetime);
            // running rows hide the line (assigned), standby rows show the remaining values.
            this.ecoaegtnh$ecThreads = ec.ecoaegtnh$getHostThreads();
            this.ecoaegtnh$ecHyperThreads = ec.ecoaegtnh$getHostHyperThreads();
            this.ecoaegtnh$ecAssigned = ec.ecoaegtnh$getController() != null ? 1 : 0;
            this.ecoaegtnh$ecVCPUId = ec.ecoaegtnh$getVCPUId(); // t114h
            // t116d: vCPU rows carry the effective storage for the client-side merge check.
            long eff = ec.ecoaegtnh$effectiveAvailableStorage();
            if (eff >= 0) {
                this.ecoaegtnh$ecEffectiveStorage = eff;
            }
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"))
    private void ecoaegtnh$injectInit(final NBTTagCompound i, final CallbackInfo ci) {
        if (i.hasKey("ecLevel")) {
            this.ecoaegtnh$ecLevel = i.getInteger("ecLevel");
            this.ecoaegtnh$ecThreads = i.getInteger("ecThreads");
            this.ecoaegtnh$ecHyperThreads = i.getInteger("ecHyperThreads");
            this.ecoaegtnh$ecAssigned = i.getInteger("ecAssigned");
            this.ecoaegtnh$ecVCPUId = i.getInteger("ecVCPUId");
            if (i.hasKey("ecEffStorage")) {
                this.ecoaegtnh$ecEffectiveStorage = i.getLong("ecEffStorage");
            }
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ecoaegtnh$injectWriteToNBT(final NBTTagCompound i, final CallbackInfo ci) {
        if (this.ecoaegtnh$ecLevel < 0) {
            return; // vanilla row — leave the NBT untouched
        }
        i.setInteger("ecLevel", this.ecoaegtnh$ecLevel);
        i.setInteger("ecThreads", this.ecoaegtnh$ecThreads);
        i.setInteger("ecHyperThreads", this.ecoaegtnh$ecHyperThreads);
        i.setInteger("ecAssigned", this.ecoaegtnh$ecAssigned);
        i.setInteger("ecVCPUId", this.ecoaegtnh$ecVCPUId);
        if (this.ecoaegtnh$ecEffectiveStorage >= 0) {
            i.setLong("ecEffStorage", this.ecoaegtnh$ecEffectiveStorage);
        }
    }

    @Unique
    @Override
    public int ecoaegtnh$getLevel() {
        return ecoaegtnh$ecLevel;
    }

    @Unique
    @Override
    public int ecoaegtnh$getThreads() {
        return ecoaegtnh$ecThreads;
    }

    @Unique
    @Override
    public int ecoaegtnh$getHyperThreads() {
        return ecoaegtnh$ecHyperThreads;
    }

    @Unique
    @Override
    public boolean ecoaegtnh$isAssigned() {
        return ecoaegtnh$ecAssigned != 0;
    }

    @Unique
    @Override
    public int ecoaegtnh$getVCPUId() {
        return ecoaegtnh$ecVCPUId;
    }

    @Unique
    @Override
    public boolean ecoaegtnh$isVCPU() {
        return ecoaegtnh$ecEffectiveStorage >= 0;
    }

    @Unique
    @Override
    public long ecoaegtnh$getEffectiveStorage() {
        return ecoaegtnh$ecEffectiveStorage;
    }
}
