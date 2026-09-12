package ecoaegtnh.mixin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.integration.modules.NEIHelpers.NEICellViewHandler;
import appeng.me.storage.CellInventoryHandler;
import codechicken.nei.PositionedStack;
import ecoaegtnh.ae2.EcoStorageCellInventory;

/**
 * t40 / t43 (user report, 290b2): NEI's "cell contents" page (U on an ECO storage cell) is a single
 * recipe page whose slot grid is drawn for {@code cellHandler.getTotalTypes()} entries, nine slots
 * per row, unbounded downwards — AE2's own {@code numRecipes()} is hard-coded to 1 and
 * {@code drawBackground} never reads its {@code recipe} argument. A 315-type ECO cell therefore
 * renders 35 rows (~630 px) and everything below the NEI panel is invisible. This mixin paginates
 * that page with AE2's own drawing code: 63 entries (9 columns x 7 rows) per NEI page, which is
 * exactly the geometry AE2 itself draws for its native 63-type cells.
 * <p>
 * The target is client-only ({@code NEICellViewHandler} lives in AE2's NEI integration), so this
 * class is registered in the {@code client} list of mixins.ecoaegtnh.json — a dedicated server has
 * neither NEI nor that class and would fail to apply it.
 * <p>
 * Mechanics, all reuse of AE2's rendering (no drawing code is overwritten). AE2's page reads
 * {@code stacks} in exactly three places (verified in the rv3-beta-1000 bytecode), and every one of
 * them is page-aligned here:
 * <ul>
 * <li>{@code numRecipes()} reports {@code max(1, ceil(slots / 63))} instead of 1, so NEI offers its
 * normal page buttons / mouse-wheel paging.</li>
 * <li>{@code drawBackground(int recipe)} keeps AE2's loop and slot quads untouched; only the loop's
 * {@code cellHandler.getTotalTypes()} bound is redirected to the number of slots left on that page,
 * so the loop index becomes page-local and the grid stays 7 rows tall. (It never reads
 * {@code stacks}.)</li>
 * <li>{@code getOtherStacks(int recipe)} keeps AE2's stream/map chain; only the {@code stacks} stream
 * source is redirected to that page's slice, so the item icons land on rows 0..6.</li>
 * <li>{@code drawForeground(int recipe)} keeps AE2's byte/type text and its size-label loop; only the
 * {@code stacks} iterator it walks is redirected to the same page slice, so the stack-size labels
 * drawn by {@code StackSizeRenderer.drawStackSize(relx, rely, ...)} follow the icons instead of
 * staying on the original rows 7..34 (t43: without this the labels of page 2+ were cropped away —
 * {@code createViewItemStack} fills {@code stackSize} with {@code IAEStack.getStackSize()} for item
 * cells, so the labels are really drawn).</li>
 * <li>{@code handleItemTooltip(...)} is deliberately untouched: it matches by item identity
 * ({@code stacks.stream().filter(stack.item.equals(hovered)).findFirst()}) and never uses the entry
 * coordinates, so it is page-independent.</li>
 * </ul>
 * Page 0 and every non-ECO cell (AE2U's native 63-type cells, foreign mod cells, the creative /
 * infinite family) get the original {@code iterator()}/{@code stream()} back unchanged, so their
 * behaviour is bit-for-bit the unmodded one. The entry type
 * {@code NEICellViewHandler$ViewItemStack} is {@code public static} with public fields (verified in
 * the rv3-beta-1000 bytecode), so it is used directly instead of via reflection.
 */
@Mixin(value = NEICellViewHandler.class, remap = false)
public abstract class MixinNEICellViewHandler {

    /** 9 columns x 7 rows: the slot grid an unmodified AE2 63-type cell draws. */
    private static final int ECOAEGTNH_PAGE_SIZE = 63;
    /** Seven rows of 18 px: how far a page's icons and size labels are shifted up. */
    private static final int ECOAEGTNH_PAGE_SHIFT = 7 * 18;

    @Shadow
    private CellInventoryHandler cellHandler;

    /** Page (NEI recipe index) whose {@code draw*} / {@code getOtherStacks} call is running. */
    @Unique
    private int ecoaegtnh$page;

    @Inject(method = "numRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecoaegtnh$pageCount(final CallbackInfoReturnable<Integer> cir) {
        if (!this.ecoaegtnh$isEcoCell()) {
            return;
        }
        final int total = this.ecoaegtnh$slotCount();
        if (total > ECOAEGTNH_PAGE_SIZE) {
            cir.setReturnValue((total + ECOAEGTNH_PAGE_SIZE - 1) / ECOAEGTNH_PAGE_SIZE);
        }
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$rememberBackgroundPage(final int recipe, final CallbackInfo ci) {
        this.ecoaegtnh$page = recipe;
    }

    /**
     * Replaces the "draw every type" loop bound with this page's slot count. For a single page
     * ({@code page == 0} and at most 63 slots) the returned value equals the original one.
     */
    @Redirect(
        method = "drawBackground",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventoryHandler;getTotalTypes()J"),
        remap = false)
    private long ecoaegtnh$slotsOnPage(final CellInventoryHandler handler) {
        if (!this.ecoaegtnh$isEcoCell()) {
            return handler.getTotalTypes();
        }
        final long start = (long) this.ecoaegtnh$page * ECOAEGTNH_PAGE_SIZE;
        final long left = this.ecoaegtnh$slotCount() - start;
        if (left <= 0) {
            return 0;
        }
        return Math.min(left, ECOAEGTNH_PAGE_SIZE);
    }

    @Inject(method = "getOtherStacks", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$rememberStacksPage(final int recipe,
        final CallbackInfoReturnable<List<PositionedStack>> cir) {
        this.ecoaegtnh$page = recipe;
    }

    /** Feeds AE2's own {@code map(...).collect(...)} chain with this page's slice (item icons). */
    @Redirect(
        method = "getOtherStacks",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;stream()Ljava/util/stream/Stream;"),
        remap = false)
    private Stream<?> ecoaegtnh$stacksOnPage(final ArrayList<?> all) {
        final List<NEICellViewHandler.ViewItemStack> slice = this.ecoaegtnh$pageSlice(all);
        return slice == null ? all.stream() : slice.stream();
    }

    @Inject(method = "drawForeground", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$rememberForegroundPage(final int recipe, final CallbackInfo ci) {
        this.ecoaegtnh$page = recipe;
    }

    /**
     * t43: the stack-size labels of {@code drawForeground} walk {@code stacks} through an iterator
     * and draw each entry at its own (relx, rely). Handing them the same shifted page slice as the
     * icons keeps labels and icons on the same coordinates on every page.
     */
    @Redirect(
        method = "drawForeground",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;"),
        remap = false)
    private Iterator<?> ecoaegtnh$foregroundStacksOnPage(final ArrayList<?> all) {
        final List<NEICellViewHandler.ViewItemStack> slice = this.ecoaegtnh$pageSlice(all);
        return slice == null ? all.iterator() : slice.iterator();
    }

    /**
     * The current page's entries as fresh copies with {@code rely} moved up by seven rows per page,
     * or {@code null} when the caller must keep AE2's original data (not one of our cells, or the
     * first page): page 0 and every non-ECO cell then behave bit-for-bit as without this mixin.
     */
    @Unique
    private List<NEICellViewHandler.ViewItemStack> ecoaegtnh$pageSlice(final ArrayList<?> all) {
        final int page = this.ecoaegtnh$page;
        if (page <= 0 || !this.ecoaegtnh$isEcoCell()) {
            return null;
        }
        final int from = Math.min(all.size(), page * ECOAEGTNH_PAGE_SIZE);
        final int to = Math.min(all.size(), from + ECOAEGTNH_PAGE_SIZE);
        final List<NEICellViewHandler.ViewItemStack> pageStacks = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            final Object entry = all.get(i);
            if (!(entry instanceof NEICellViewHandler.ViewItemStack)) {
                continue;
            }
            final NEICellViewHandler.ViewItemStack view = (NEICellViewHandler.ViewItemStack) entry;
            if (view.stack == null) {
                continue;
            }
            final PositionedStack shifted = view.stack.copy();
            shifted.rely -= ECOAEGTNH_PAGE_SHIFT * page;
            pageStacks.add(new NEICellViewHandler.ViewItemStack(shifted, view.stackSize));
        }
        return pageStacks;
    }

    /** Entries the page is built from: the drawn slot count, at least the stored stack count. */
    @Unique
    private int ecoaegtnh$slotCount() {
        final long total = this.cellHandler == null ? 0L : this.cellHandler.getTotalTypes();
        if (total <= 0L) {
            return 0;
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * Only our own cell inventory paginates: every other {@code IStorageCell} (AE2U's native 63-type
     * cells, foreign mod cells, the creative/infinite family) keeps the unmodified single page, since
     * all hooks above fall back to the exact values AE2 itself would have produced.
     */
    @Unique
    private boolean ecoaegtnh$isEcoCell() {
        return this.cellHandler != null && this.cellHandler.getCellInv() instanceof EcoStorageCellInventory;
    }
}
