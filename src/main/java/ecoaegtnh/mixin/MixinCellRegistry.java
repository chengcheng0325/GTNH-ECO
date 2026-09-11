package ecoaegtnh.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.core.features.registries.CellRegistry;
import ecoaegtnh.ae2.EcoStorageCellHandler;

/**
 * t129：ECO 盘必须始终由我们自己的 {@link EcoStorageCellHandler} 解析，绝不能再落到 AE2U 原生
 * {@code appeng.me.storage.CellInventory}。
 * <p>
 * 崩溃现象（用户实测 284）：ECO 盘放进 AE2 的 IO 端口倒盘 →
 * {@code java.lang.ArrayIndexOutOfBoundsException: 63 at CellInventory.loadCellItems}。
 * <p>
 * 根因（已由 javap 逐字节码核实，勿重复调查）：
 * <ol>
 * <li>695 的 {@code appeng.core.Registration} 把 {@code BasicCellHandler} 作为 handlers 列表的
 * <b>第一个</b>条目注册（随后才是 Creative / Void，我们的 handler 在 postInit 追加在最后）；</li>
 * <li>{@code CellRegistry.getCellInventory} 遍历列表，返回<b>第一个</b> {@code isCell()} 命中的 handler
 * 的结果 —— 于是 ECO 盘永远命中 BasicCellHandler；</li>
 * <li>{@code BasicCellHandler.isCell = CellInventory.isCell = item instanceof IStorageCell && isStorageCell()}，
 * 我们的盘命中 → 用原生 CellInventory 解析；其静态数组 {@code itemSlots}/{@code itemSlotCount} 的长度由
 * 服务器上第一个被构造的盘决定（原生盘 maxItemTypes = 63），而我们 MAX_TYPES = 315；</li>
 * <li>NBT 里已存类型数 "it" &gt; 63 时，惰性加载（{@code getCellItems() → loadCellItems()}，由
 * {@code transferContents → getAvailableItems} 触发）即越界崩溃。</li>
 * </ol>
 * <p>
 * 为什么这里必须"连 null 一起返回"（本任务批准方案 A 的加强版）：695 的
 * {@code TileIOPort.getInv(ItemStack)} 在首次解析一个盘时会<b>同时</b>查询
 * {@code FLUIDS} 与 {@code ITEMS} 两个通道，并分别缓存到 {@code cachedFluid}/{@code cachedItem}。
 * 如果我们在"非本盘通道"（如物品盘查 FLUIDS）返回 null 后放行，列表首位的 BasicCellHandler 仍会立刻
 * 命中并造出原生 CellInventory —— 倒盘时照样 AIOOBE。因此：只要 {@code isCell(stack)} 为真（确定是我们
 * 的盘），就直接以我们的结果（可能是 null = 该通道下无本盘库存）短路，绝不放行给任何原生 handler。
 * <p>
 * 影响面仅限 {@code EcoStorageCellHandler.isCell()} 为真的栈（我们自己的盘）；非 ECO 盘原样走 vanilla
 * 循环，{@code isCellHandled}/{@code getHandler} 语义不变。priority = 2000 确保不会再被别的 CellRegistry
 * mixin 抢先改序；remap = false：AE2U 类在 release jar 内保留 MCP 名字。
 */
@Mixin(value = CellRegistry.class, priority = 2000, remap = false)
public abstract class MixinCellRegistry {

    /**
     * 695 只有这一个重载（javap 已确认：不存在 IAEStackType 版），因此只拦这一个。
     */
    @Inject(
        method = "getCellInventory(Lnet/minecraft/item/ItemStack;Lappeng/api/storage/ISaveProvider;Lappeng/api/storage/StorageChannel;)Lappeng/api/storage/IMEInventoryHandler;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    @SuppressWarnings("rawtypes")
    private void ecoaegtnh$preferEcoCellInventory(ItemStack is, ISaveProvider host, StorageChannel channel,
        CallbackInfoReturnable<IMEInventoryHandler> cir) {
        // 不是我们的盘（含 is == null）→ 原样交给 vanilla 循环。
        if (!EcoStorageCellHandler.INSTANCE.isCell(is)) return;
        // 是我们的盘 → 只认我们自己的结果；返回 null 表示"该通道下本盘没有库存"，
        // 用来阻断 BasicCellHandler 的原生 CellInventory 解析路径（见类注释）。
        IMEInventoryHandler eco = EcoStorageCellHandler.INSTANCE.getCellInventory(is, host, channel);
        cir.setReturnValue(eco);
    }
}
