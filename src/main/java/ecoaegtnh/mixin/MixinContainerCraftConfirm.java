package ecoaegtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import ecoaegtnh.ecalculator.ECPUCluster;

/**
 * t116b: make the AE2U craft-confirm screen treat a busy vCPU as a valid merge target for a
 * repeated request of the same output. Vanilla {@code ContainerCraftConfirm.cpuMatches} only
 * matches a busy CPU when {@code getStorage() >= usedBytes + getUsedStorage()}; for a vCPU the
 * vanilla availableStorage field is "task bytes", so that condition can never hold. We redirect
 * {@code getStorage()} to the vCPU's EFFECTIVE available storage (pool remaining + current task
 * bytes) → the condition becomes {@code poolFree >= newJobBytes}, the busy vCPU becomes selectable
 * and the Start button turns into "Merge". {@code onCPUUpdate} is redirected the same way so the
 * displayed byte count is consistent. Non-vCPU clusters keep the vanilla value.
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
        return effectiveStorage(status.getServerCluster(), status.getStorage());
    }

    @Redirect(
        method = "onCPUUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingCPU;getAvailableStorage()J",
            remap = false),
        remap = false)
    private long ecoaegtnh$redirectOnCpuUpdateStorage(final ICraftingCPU cpu) {
        return effectiveStorage(cpu, cpu.getAvailableStorage());
    }

    private static long effectiveStorage(final ICraftingCPU cpu, final long fallback) {
        if (cpu instanceof CraftingCPUCluster) {
            ECPUCluster ec = ECPUCluster.from((CraftingCPUCluster) cpu);
            if (ec != null) {
                long eff = ec.ecoaegtnh$effectiveAvailableStorage();
                if (eff >= 0) {
                    return eff;
                }
            }
        }
        return fallback;
    }
}
