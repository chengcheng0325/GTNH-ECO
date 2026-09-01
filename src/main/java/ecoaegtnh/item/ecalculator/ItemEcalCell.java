package ecoaegtnh.item.ecalculator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.registry.GameRegistry;
import ecoaegtnh.EcoAEGTNHCore;

/**
 * E-Calculator flash cell (闪存晶阵) item: pure byte memory for the byte pool (not an AE2
 * IStorageCell). t28: nine sizes in three controller tiers (9 尺寸对齐 E-Storage t76) —
 * k-level 256k/1024k/4096k → C4 hosts, M-level 16M/64M/256M → C6 hosts, big-M
 * 1024M/4096M/16384M → C9 hosts only. Capacity follows the storage-disk formula (k-level
 * value×1024, M/big-M value×1000×1024; see {@link CellSize}).
 * <p>
 * Display name is size-style ("ECO 闪存晶阵 (256k)"), registry {@code ecalculator_cell_<label>}.
 * The old 3-tier {@code ecalculator_cell_c4/c6/c9} registrations were removed by t28 —
 * 64M→64m, 1024M→1024m, 16384M→16384m take over (recipe references synced by T29).
 */
public class ItemEcalCell extends Item {

    public static final int TIER_C4 = 0;
    public static final int TIER_C6 = 1;
    public static final int TIER_C9 = 2;

    protected final CellSize size;

    public ItemEcalCell(CellSize size) {
        this.size = size;
        setMaxStackSize(1);
        setCreativeTab(EcoAEGTNHCore.TAB_CALC);
        setUnlocalizedName("ecoaegtnh.ecalculator_cell_" + size.label);
        // t28: texture name follows the size label ("ecal_cell_256k" ... "ecal_cell_16384m");
        // T27 model-artist supplies the art (missing files fall back to placeholder copies).
        setTextureName("ecoaegtnh:ecal_cell_" + size.label);
    }

    /**
     * t28: controller tier this cell requires, mapped onto the host tier scale — k→C4 档, M→C6
     * 档, 大M→C9 档. Semantics preserved from the old C4/C6/C9 cells so TileEcalCellDrive
     * (isItemValidForSlot / getSuppliedBytes) and the MTEEcalArray byte-pool aggregation keep
     * working unchanged: k-level cells are accepted by C4+ hosts, M-level by C6+, big-M by C9
     * only.
     */
    public int getTier() {
        return size.tier;
    }

    /** Cell capacity in AE bytes (k-level value×1024, M/big-M level value×1000×1024). */
    public long getTotalBytes() {
        return size.totalBytes;
    }

    /**
     * t50 (milestone gate, docs §4.1): main-cell line level required to insert this cell —
     * k-level (256k/1024k/4096k) → Lv1+, M-level (16M/64M/256M) → Lv2+, big-M 1024M/4096M →
     * Lv3+, 16384M → Lv4+.
     */
    public int getRequiredMilestoneLevel() {
        if (size.tier == TIER_C6) return 2;
        if (size.tier == TIER_C9) return size.value >= 16384 ? 4 : 3;
        return 1;
    }

    /**
     * t65/t114 (upgrade tree, docs §2 revision): the cell main-chain node required to insert this
     * cell — 256k → N2 … 16384M → N10, Singularity (奇点闪存晶阵) → N11 (Long.MAX_VALUE bytes,
     * byte-pool cap lifted to unlimited).
     */
    public String getRequiredUpgradeNode() {
        if (size == CellSize.SINGULARITY) {
            return ecoaegtnh.upgrade.CalculatorUpgradeTree.N11;
        }
        if (size.kilo) {
            if (size.value == 4096) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N4;
            if (size.value == 1024) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N3;
            return ecoaegtnh.upgrade.CalculatorUpgradeTree.N2; // 256k
        }
        if (size.value == 16384) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N10;
        if (size.value == 4096) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N9;
        if (size.value == 1024) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N8;
        if (size.value == 256) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N7;
        if (size.value == 64) return ecoaegtnh.upgrade.CalculatorUpgradeTree.N6;
        return ecoaegtnh.upgrade.CalculatorUpgradeTree.N5; // 16m
    }

    /** Size label ("256k", "16m", "16384m", ...). */
    public String getSizeLabel() {
        return size.label;
    }

    /** t114c: the cell's size enum (e.g. to count singularity cells for extra vCPUs). */
    public CellSize getSize() {
        return size;
    }

    /** Static tier gate (plan §7.4): k cell → any controller; M cell → C6+; big-M → only C9. */
    public static boolean isSupportedByTier(ItemStack stack, int controllerTier) {
        return stack != null && stack.getItem() instanceof ItemEcalCell cell && controllerTier >= cell.getTier();
    }

    @Override
    public boolean doesSneakBypassUse(net.minecraft.world.World world, int x, int y, int z,
        net.minecraft.entity.player.EntityPlayer player) {
        // t20 (same root cause as E-Storage t25, ItemEcoStorageCell:85-90): vanilla 1.7.10
        // ItemInWorldManager.activateBlockOrUseItem skips block.onBlockActivated when sneaking
        // with a held item whose doesSneakBypassUse returns false — so shift+right-click with a
        // flash cell in hand never reached BlockEcalCellDrive.onBlockActivated (t13/t18). Returning
        // true routes the sneak click to the block (the cell itself has no right-click use).
        return true;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        // t28: capacity / tier group (k 级 / M 级 / 大M 级) / host gate.
        lines.add(StatCollector.translateToLocalFormatted("ecoaegtnh.ecal.cell.tip.bytes", getTotalBytes()));
        String group = size.kilo ? "k" : size.tier == TIER_C6 ? "m" : "bigm";
        lines.add(StatCollector.translateToLocal("ecoaegtnh.ecal.cell.tip.group." + group));
        lines.add(StatCollector.translateToLocal("ecoaegtnh.ecal.cell.tip.tier." + group));
    }

    public static ItemEcalCell register(String name, CellSize size) {
        ItemEcalCell cell = new ItemEcalCell(size);
        GameRegistry.registerItem(cell, name);
        return cell;
    }
}
