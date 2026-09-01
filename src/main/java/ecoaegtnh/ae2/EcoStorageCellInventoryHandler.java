package ecoaegtnh.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.PrecisePriorityList;

/**
 * 284 移植版 ECO 物品盘 handler：695 的 {@code CellInventoryHandler} 构造包私有且
 * getCellType() 硬编码 ITEM，无法继承；按 AE2FC FluidCellInventoryHandler 的模式
 * 直接 extends {@link MEInventoryHandler} 并实现 ICellInventoryHandler +
 * ICellCacheRegistry（ITEM）。构造时从升级/配置槽建立白名单与分区列表（AE2U 同款）。
 * <p>
 * t8：695 的 {@code MEInventoryHandler} 构造器会把"非 IMEInventoryHandler 的内部库存"
 * 包一层 {@code MEPassThrough}（{@code getInternal()} 返回包装而非原库存，且
 * MEPassThrough.getInternal() 是 protected，外部包无法解包）——本类直接持有构造参数里
 * 的 {@link EcoStorageCellInventory} 引用用于统计，不再依赖 getInternal()；全部统计方法
 * 带 null 防御（库存异常时返回 0，绝不抛 NPE）。
 */
public class EcoStorageCellInventoryHandler extends MEInventoryHandler<IAEItemStack>
    implements ICellInventoryHandler, ICellCacheRegistry {

    private final EcoStorageCellInventory ecoInv;

    public EcoStorageCellInventoryHandler(final IMEInventory<IAEItemStack> c) {
        super(c, StorageChannel.ITEMS);
        this.ecoInv = c instanceof EcoStorageCellInventory eci ? eci : null;
        final EcoStorageCellInventory ci = this.ecoInv;
        if (ci != null) {
            final IInventory upgrades = ci.getUpgradesInventory();
            final IInventory config = ci.getConfigInventory();
            final FuzzyMode fzMode = ci.getFuzzyMode();
            boolean hasInverter = false;
            boolean hasFuzzy = false;
            boolean hasSticky = false;
            for (int x = 0; x < upgrades.getSizeInventory(); x++) {
                final ItemStack is = upgrades.getStackInSlot(x);
                if (is != null && is.getItem() instanceof IUpgradeModule) {
                    final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                    if (u != null) {
                        switch (u) {
                            case FUZZY -> hasFuzzy = true;
                            case INVERTER -> hasInverter = true;
                            case STICKY -> hasSticky = true;
                            default -> {}
                        }
                    }
                }
            }
            this.setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            if (hasSticky) {
                setSticky(true);
            }
            final IItemList<IAEItemStack> priorityList = AEApi.instance()
                .storage()
                .createItemList();
            for (int x = 0; x < config.getSizeInventory(); x++) {
                final ItemStack is = config.getStackInSlot(x);
                if (is != null) {
                    priorityList.add(AEItemStack.create(is));
                }
            }
            if (!priorityList.isEmpty()) {
                if (hasFuzzy) {
                    this.setPartitionList(new FuzzyPriorityList<>(priorityList, fzMode));
                } else {
                    this.setPartitionList(new PrecisePriorityList<>(priorityList));
                }
            }
        }
    }

    private EcoStorageCellInventory getEcoCellInv() {
        return this.ecoInv;
    }

    @Override
    public appeng.api.storage.ICellInventory getCellInv() {
        return this.getEcoCellInv();
    }

    @Override
    public boolean isPreformatted() {
        return !this.getPartitionList()
            .isEmpty();
    }

    @Override
    public boolean isFuzzy() {
        return this.getPartitionList() instanceof FuzzyPriorityList;
    }

    @Override
    public IncludeExclude getIncludeExcludeMode() {
        return this.getWhitelist();
    }

    @Override
    public boolean canGetInv() {
        return this.getEcoCellInv() != null;
    }

    @Override
    public long getTotalBytes() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        return inv == null ? 0 : inv.getTotalBytes();
    }

    @Override
    public long getFreeBytes() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        return inv == null ? 0 : inv.getFreeBytes();
    }

    @Override
    public long getUsedBytes() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        return inv == null ? 0 : inv.getUsedBytes();
    }

    @Override
    public long getTotalTypes() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        return inv == null ? 0 : inv.getTotalItemTypes();
    }

    @Override
    public long getFreeTypes() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        return inv == null ? 0 : inv.getRemainingItemTypes();
    }

    @Override
    public long getUsedTypes() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        return inv == null ? 0 : inv.getStoredItemTypes();
    }

    @Override
    public int getCellStatus() {
        final EcoStorageCellInventory inv = this.getEcoCellInv();
        if (inv == null) {
            return 1;
        }
        int val = inv.getStatusForCell();
        if ((val == 1 || val == 2) && this.isPreformatted()) {
            val = 3;
        }
        return val;
    }

    @Override
    public TYPE getCellType() {
        return TYPE.ITEM;
    }
}
