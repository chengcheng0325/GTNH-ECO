package ecoaegtnh.item.ecalculator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * t35: E-Calculator parallel core (并行核心) insertable item — supplies parallelism to a
 * {@code BlockEcalParallelDrive} (1 slot). Nine sizes (1/4/16/64/256/1024/4096/16384/65536,
 * ×4 increments), usable on ANY controller tier (全档自由 — no tier gate). Registry
 * {@code ecal_parallel_core_<value>}, texture {@code ecal_parallel_core_<value>} (T36 art).
 */
public class ItemEcalParallelCore extends Item {

    /** The nine parallelism values (×4 increments). */
    public static final int[] SIZES = { 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536 };

    protected final int parallelism;

    public ItemEcalParallelCore(int parallelism) {
        this.parallelism = parallelism;
        setMaxStackSize(1);
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_CALC);
        setUnlocalizedName("ecoaegtnh.ecal_parallel_core_" + parallelism);
        setTextureName("ecoaegtnh:ecal_parallel_core_" + parallelism);
    }

    /** Parallelism this core supplies to the drive (and thus to the host's total). */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * t50 (milestone gate, docs §4.1): parallel-branch line level required to insert this core —
     * 1/4/16 → Lv1+, 64/256 → Lv2+, 1024/4096 → Lv3+, 16384 → Lv4+, 65536 → Lv5+.
     */
    public int getRequiredMilestoneLevel() {
        if (parallelism <= 16) return 1;
        if (parallelism <= 256) return 2;
        if (parallelism <= 4096) return 3;
        if (parallelism <= 16384) return 4;
        return 5;
    }

    /**
     * t65 (upgrade tree, docs §2 revision): the parallel-branch node required to insert this
     * core — 1 → P1, 4 → P2, 16 → P3, 64 → P4, 256 → P5, 1024 → P6, 4096 → P7, 16384 → P8,
     * 65536 → P9.
     */
    public String getRequiredUpgradeNode() {
        if (parallelism <= 1) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P1;
        if (parallelism <= 4) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P2;
        if (parallelism <= 16) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P3;
        if (parallelism <= 64) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P4;
        if (parallelism <= 256) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P5;
        if (parallelism <= 1024) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P6;
        if (parallelism <= 4096) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P7;
        if (parallelism <= 16384) return ecoaegtnh.upgrade.CalculatorUpgradeTree.P8;
        return ecoaegtnh.upgrade.CalculatorUpgradeTree.P9;
    }

    @Override
    public boolean doesSneakBypassUse(net.minecraft.world.World world, int x, int y, int z,
        net.minecraft.entity.player.EntityPlayer player) {
        // t20 (same root cause as E-Storage t25 / ItemEcalCell): vanilla skips
        // block.onBlockActivated for sneak + held item; true routes shift+right-click to the drive.
        return true;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocalFormatted("ecoaegtnh.tooltip.ecal.parallel_core", parallelism));
    }

    public static ItemEcalParallelCore register(String name, int parallelism) {
        ItemEcalParallelCore core = new ItemEcalParallelCore(parallelism);
        GameRegistry.registerItem(core, name);
        return core;
    }
}
