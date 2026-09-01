package ecoaegtnh.mixin;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.container.implementations.CraftingCPUStatus;
import ecoaegtnh.ecalculator.ECPUStatus;

/**
 * M4 (t25): distinguishes ECO vCPU rows in the AE2 crafting-status CPU table. The 1.12.2 reference
 * redirected {@code List.get(i)} in GuiCraftingStatus.drawFG (S:MixinGuiCraftingStatus.java); in
 * rv3 the row rendering lives in {@link GuiCraftingCPUTable#drawFG} (GuiCraftingStatus.drawFG only
 * delegates — A998 verified), so this mixin targets the table widget (client group):
 * <ul>
 * <li>{@code List.get(i)} WrapOperation — observation point: remembers the row being drawn so the
 * background-tint hook below can decide per-row (loop locals are otherwise inaccessible);
 * <li>{@code ScreenColor.setGuiColor()} WrapOperation — default (unselected/unhovered) row tint:
 * ECO rows get their tier color (C4 cyan 4DBFD4 / C6 gold FF9300 / C9 purple 8815D8), vanilla rows
 * keep the original color;
 * <li>{@code CraftingCPUStatus.getName()} WrapOperations (ordinal 0 = row name, ordinal 1 = hover
 * tooltip first line) — ECO rows show "ECO vCPU" (standby) / "ECO vCPU #id" (running, t114h) in
 * the tier color; the tooltip variant appends the remaining-threads line for standby vCPUs only
 * (t33: running/assigned rows hide it — occupied threads are not "available"). Both idle and
 * running ECO rows are tagged (the tier is baked into the row by M3).
 * </ul>
 * Vanilla rows: every wrap falls through to {@code original} (level == -1 → unchanged).
 * priority = 2000 (R14). All targets keep MCP names in the release jar (JREL verified) →
 * remap=false + literal names; {@code java.util.List} is not obfuscated.
 */
@Mixin(value = GuiCraftingCPUTable.class, priority = 2000, remap = false)
public abstract class MixinGuiCraftingCPUTable {

    /** The CPU row currently being drawn (set by the List.get wrap inside drawFG's row loop). */
    @Unique
    private CraftingCPUStatus ecoaegtnh$currentRow = null;

    @WrapOperation(method = "drawFG", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object ecoaegtnh$captureRow(final List<?> list, final int index, final Operation<Object> original) {
        final Object row = original.call(list, index);
        this.ecoaegtnh$currentRow = row instanceof CraftingCPUStatus cs ? cs : null;
        return row;
    }

    @WrapOperation(
        method = "drawFG",
        // H4 (audit): ScreenColor lives in appeng/client/gui, NOT appeng/core/localization — the
        // wrong owner only matched by Mixin's imaginary fallback and would crash a strict build.
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/ScreenColor;setGuiColor()V"))
    private void ecoaegtnh$tintRow(final Operation<Void> original) {
        if (this.ecoaegtnh$currentRow instanceof ECPUStatus ec && ec.ecoaegtnh$getLevel() >= 0) {
            final float[] c = tierColor(ec.ecoaegtnh$getLevel());
            GL11.glColor4f(c[0], c[1], c[2], 1.0F);
        } else {
            original.call();
        }
    }

    @WrapOperation(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/CraftingCPUStatus;getName()Ljava/lang/String;",
            ordinal = 0))
    private String ecoaegtnh$rowName(final CraftingCPUStatus instance, final Operation<String> original) {
        return ecoaegtnh$ecName(instance, false);
    }

    @WrapOperation(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/CraftingCPUStatus;getName()Ljava/lang/String;",
            ordinal = 1))
    private String ecoaegtnh$tooltipName(final CraftingCPUStatus instance, final Operation<String> original) {
        return ecoaegtnh$ecName(instance, true);
    }

    /**
     * ECO row label — "ECO vCPU" (standby) / "ECO vCPU #id" (running, t114h), tier-colored;
     * tooltip variant appends the remaining-threads line for standby vCPUs (t33: running/assigned
     * rows hide it — occupied threads are not available; the tier color, name and job content
     * stay).
     */
    @Unique
    private String ecoaegtnh$ecName(final CraftingCPUStatus instance, final boolean tooltip) {
        if (instance instanceof ECPUStatus ec && ec.ecoaegtnh$getLevel() >= 0) {
            final int level = ec.ecoaegtnh$getLevel();
            final EnumChatFormatting color = level == 1 ? EnumChatFormatting.GOLD
                : level == 2 ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.AQUA;
            final String base = ec.ecoaegtnh$isAssigned() ? color + "ECO vCPU #" + ec.ecoaegtnh$getVCPUId()
                : color + "ECO vCPU";
            if (!tooltip) {
                return base;
            }
            if (!ec.ecoaegtnh$isAssigned()) {
                final int threads = ec.ecoaegtnh$getThreads();
                final int hyper = ec.ecoaegtnh$getHyperThreads();
                final String threadsLine = hyper > 0
                    ? StatCollector.translateToLocalFormatted("ecoaegtnh.gui.ecal.cpu.threads_hyper", threads, hyper)
                    : StatCollector.translateToLocalFormatted("ecoaegtnh.gui.ecal.cpu.threads", threads);
                return base + "\n" + EnumChatFormatting.GRAY + threadsLine;
            }
            return base;
        }
        return instance.getName();
    }

    /** C4 cyan 4DBFD4 / C6 gold FF9300 / C9 purple 8815D8 (user-specified tier colors). */
    @Unique
    private static float[] tierColor(final int level) {
        if (level == 1) return new float[] { 1.0F, 0.576F, 0.0F };
        if (level == 2) return new float[] { 0.533F, 0.082F, 0.847F };
        return new float[] { 0.302F, 0.749F, 0.831F };
    }
}
