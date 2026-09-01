package ecoaegtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import ecoaegtnh.ecalculator.ECPUCluster;
import ecoaegtnh.ecalculator.ECPUStatus;

/**
 * t116b/t116d: make the AE2U craft-confirm screen treat a busy vCPU as a valid merge target for a
 * repeated request of the same output. Vanilla {@code ContainerCraftConfirm.cpuMatches} only
 * matches a busy CPU when {@code getStorage() >= usedBytes + getUsedStorage()}; for a vCPU the
 * vanilla availableStorage field is "task bytes", so that condition can never hold. We redirect
 * {@code getStorage()} to the vCPU's EFFECTIVE available storage (pool remaining + current task
 * bytes) → the condition becomes {@code poolFree >= newJobBytes}, the busy vCPU becomes selectable
 * and the Start button turns into "Merge".
 * <p>
 * t116d: cpuMatches also runs on the CLIENT container instance, where the status rows were rebuilt
 * from the sync packet (NBT) and {@code getServerCluster()} is null — so the effective value must
 * travel on the row itself (MixinCraftingCPUStatus serializes it into NBT). Non-vCPU rows keep the
 * vanilla value.
 */
@Mixin(value = ContainerCraftConfirm.class, priority = 2000, remap = false)
public abstract class MixinContainerCraftConfirm {

    @Redirect(
        method = "cpuMatches",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/CraftingCPUStatus;getStorage()J",
            remap = false),
        remap = false)
    private long ecoaegtnh$redirectCpuMatchesStorage(final CraftingCPUStatus status) {
        ECPUStatus es = ECPUStatus.from(status);
        if (es.ecoaegtnh$isVCPU()) {
            return es.ecoaegtnh$getEffectiveStorage();
        }
        return status.getStorage();
    }

    @Redirect(
        method = "onCPUUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingCPU;getAvailableStorage()J",
            remap = false),
        remap = false)
    private long ecoaegtnh$redirectOnCpuUpdateStorage(final ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster) {
            ECPUCluster ec = ECPUCluster.from((CraftingCPUCluster) cpu);
            if (ec != null) {
                long eff = ec.ecoaegtnh$effectiveAvailableStorage();
                if (eff >= 0) {
                    return eff;
                }
            }
        }
        return cpu.getAvailableStorage();
    }
}
