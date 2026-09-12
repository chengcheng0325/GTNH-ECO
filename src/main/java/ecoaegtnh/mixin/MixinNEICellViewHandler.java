package ecoaegtnh.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
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
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IUsageHandler;

/**
 * t41（方案 D′，仅客户端）：给 AE2 的 NEI「存储盘内容」页（按 U 查看存储盘）加分页，每页 63 项（7 行 × 9 列）。
 * <p>
 * 背景：AE2 的 {@code NEICellViewHandler} 把整个盘的内容铺成一张「配方页」，{@code numRecipes()} 恒返回 1，
 * 槽位框按 {@code cellHandler.getTotalTypes()} 无限下行（ECO 物品盘声明 315 类型 ⇒ 35 行 ≈ 630px），
 * 超出 NEI 面板高度而被裁掉。本 mixin 只做分页，不重写绘制：
 * <ol>
 * <li>{@code numRecipes()} 返回 {@code max(1, ceil(条目数 / 63))}；</li>
 * <li>页面边界 63 = 7 行 × 9 列**恰好落在整行上**，所以「换页」等价于把每个条目按其所属页在 Y 上平移
 * {@code 126px = 7 行 × 18px}：归一化后任意页的条目都落在 0..6 行内，AE2 自己那套
 * {@code drawBackground / drawForeground / getOtherStacks / handleItemTooltip} 可**原样复用**；</li>
 * <li>每页渲染前把 {@code stacks} 的**内容**换成该页切片（{@code ArrayList} 本身可变，故无需触碰 final 字段
 * 赋值，也不需要写 AE2 的私有字段），绘制结束后无需还原——每个方法都会按传入的页索引重新装页。</li>
 * </ol>
 * 分页判据用**条目数**（{@code stacks.size()}，即实际存储的条目）而非声明类型数：物品盘声明 315 类型时，
 * 整盘条目 315 ⇒ 5 页；空盘仍为 1 页，且每页槽位框被限制在 63 个以内（不会再有 35 行溢出）。
 * <p>
 * 未做「放宽判定」：290b1 上 {@code EcoStorageCellInventoryHandler} 本就是
 * {@code appeng.me.storage.CellInventoryHandler} 的子类，NEI 的 {@code instanceof} 判定本来就通过，
 * 因此本 mixin 不改变「哪些物品被接受」，非 ECO 物品按 U 的行为与改动前完全一致。
 * <p>
 * 注册位置：{@code mixins.ecoaegtnh.json} 的 {@code client} 段（{@code NEICellViewHandler} 是客户端专属类，
 * 放进 common 段会让专用服务端 apply 失败）。remap = false：AE2U 发布 jar 保留 MCP 名（与既有 mixin 同款）。
 */
@Mixin(value = NEICellViewHandler.class, remap = false)
public abstract class MixinNEICellViewHandler {

    /** 每页条目数：7 行 × 9 列（与 NEI 配方面板高度相称）。 */
    @Unique
    private static final int ECOAEGTNH_PAGE_SIZE = 63;

    /** 每页在 Y 上的像素跨度：7 行 × 18px = 126px（页面边界为整行，故可整体平移）。 */
    @Unique
    private static final int ECOAEGTNH_PAGE_ROW_PX = 126;

    /** AE2 私有字段：本页要绘制的条目（normalized 坐标 + 每页切片）。 */
    @Shadow
    @Final
    private ArrayList<NEICellViewHandler.ViewItemStack> stacks;

    /** 整盘条目快照（每次查询重建一次），分页切片的来源。 */
    @Unique
    private ArrayList<NEICellViewHandler.ViewItemStack> ecoaegtnh$allStacks;

    /**
     * AE2 在 {@code getUsageHandler} 里重建 {@code stacks}（先 clear 再填），故在此把快照作废，
     * 下一次访问时重新取快照并归一化坐标。
     */
    @Inject(method = "getUsageHandler", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$resetSnapshot(String type, Object[] ingredients, CallbackInfoReturnable<IUsageHandler> cir) {
        ecoaegtnh$allStacks = null;
    }

    /** 页数 = max(1, ceil(条目数 / 63))，取代 AE2 恒定的 1 页。 */
    @Inject(method = "numRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecoaegtnh$pageCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ecoaegtnh$pages());
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$installPageForBackground(int recipe, CallbackInfo ci) {
        ecoaegtnh$installPage(recipe);
    }

    @Inject(method = "drawForeground", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$installPageForForeground(int recipe, CallbackInfo ci) {
        ecoaegtnh$installPage(recipe);
    }

    @Inject(method = "getOtherStacks", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$installPageForOtherStacks(int recipe, CallbackInfoReturnable<List<PositionedStack>> cir) {
        ecoaegtnh$installPage(recipe);
    }

    @Inject(method = "handleItemTooltip", at = @At("HEAD"), remap = false)
    private void ecoaegtnh$installPageForTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> tooltip, int recipe,
        CallbackInfoReturnable<List<String>> cir) {
        ecoaegtnh$installPage(recipe);
    }

    /**
     * AE2 的 drawBackground 会为「声明的类型数」画槽位框（ECO 物品盘 = 315 个 ⇒ 35 行），
     * 这里把它改写为**本页应有的槽位框数**（最多 63 个，末页更少），使框本身也随页收敛。
     * 回调参数 = 被重定向调用的接收者 + 外层方法的参数（页索引）。
     */
    @Redirect(
        method = "drawBackground",
        at = @At(value = "INVOKE", target = "Lappeng/me/storage/CellInventoryHandler;getTotalTypes()J"),
        remap = false)
    private long ecoaegtnh$pageSlotCount(CellInventoryHandler<?> handler, int recipe) {
        long remaining = handler.getTotalTypes() - (long) Math.max(0, recipe) * ECOAEGTNH_PAGE_SIZE;
        if (remaining <= 0) {
            return 0L;
        }
        return Math.min(ECOAEGTNH_PAGE_SIZE, remaining);
    }

    /** 取一次快照，并把每个条目按其所属页在 Y 上平移，使任意页都落在 0..6 行内。 */
    @Unique
    private void ecoaegtnh$ensureSnapshot() {
        if (ecoaegtnh$allStacks != null) {
            return;
        }
        ecoaegtnh$allStacks = new ArrayList<>(stacks);
        for (int i = 0; i < ecoaegtnh$allStacks.size(); i++) {
            NEICellViewHandler.ViewItemStack view = ecoaegtnh$allStacks.get(i);
            if (view == null || view.stack == null) {
                continue;
            }
            view.stack.rely -= ECOAEGTNH_PAGE_ROW_PX * (i / ECOAEGTNH_PAGE_SIZE);
        }
    }

    @Unique
    private int ecoaegtnh$pages() {
        ecoaegtnh$ensureSnapshot();
        return Math.max(1, (ecoaegtnh$allStacks.size() + ECOAEGTNH_PAGE_SIZE - 1) / ECOAEGTNH_PAGE_SIZE);
    }

    /** 把 {@code stacks} 的内容换成指定页的切片（越界安全：夹到 [0, pages-1]）。 */
    @Unique
    private void ecoaegtnh$installPage(int page) {
        ecoaegtnh$ensureSnapshot();
        int pages = Math.max(1, (ecoaegtnh$allStacks.size() + ECOAEGTNH_PAGE_SIZE - 1) / ECOAEGTNH_PAGE_SIZE);
        int current = Math.min(Math.max(page, 0), pages - 1);
        int from = current * ECOAEGTNH_PAGE_SIZE;
        int to = Math.min(from + ECOAEGTNH_PAGE_SIZE, ecoaegtnh$allStacks.size());
        stacks.clear();
        if (from < to) {
            stacks.addAll(ecoaegtnh$allStacks.subList(from, to));
        }
    }
}
