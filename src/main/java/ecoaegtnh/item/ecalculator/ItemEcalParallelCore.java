package ecoaegtnh.item.ecalculator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * t35: E-Calculator parallel core (并行核心) insertable item — supplies parallelism to a
 * {@code BlockEcalParallelDrive} (1 slot). Eleven sizes (1/4/16/64/256/1024/4096/16384/65536/
 * 262144/16777216, ×4 increments), usable on ANY controller tier (全档自由 — no tier gate).
 * Registry {@code ecal_parallel_core_<value>}, texture {@code ecal_parallel_core_<value>}
 * (T36 art).
 * <p>
 * t130 (user spec, docs/t130-parallel-core-recipe-spec.md §1 — 284 侧): two new sizes
 * {@code 262144} and {@code 16777216} join the ladder. Both reuse this version's EXISTING gate
 * mapping ("沿用现状", no new nodes, the nine already-shipped tiers keep their tiers): the two new
 * values fall through to milestone <b>Lv5</b> and upgrade node <b>P9</b> — see
 * {@link #getRequiredMilestoneLevel()} / {@link #getRequiredUpgradeNode()}. (290b1/290b2 have a
 * 3-node parallel branch and therefore map the same two values to P3; the difference comes from
 * each version's own upgrade tree, not from a gate change — captain-confirmed.)
 */
public class ItemEcalParallelCore extends Item {

    /** The eleven parallelism values (×4 increments); t130 appended 262144 and 16777216. */
    public static final int[] SIZES = { 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 16777216 };

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
     * <p>
     * t130: the two new tiers (262144/16777216) also take the Lv5 fallthrough — the mapping for
     * the nine existing tiers is untouched (bytecode-verified: {@code iconst_5; ireturn} tail).
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
     * <p>
     * t130 (284): the two new tiers 262144 / 16777216 take the UNCONDITIONAL P9 fallthrough at the
     * end of this method — every comparison is a {@code <=} chain, so nothing between 65537 and
     * {@code Integer.MAX_VALUE} can return null (bytecode-verified: last two instructions are
     * {@code ldc "P9"; areturn}, and the method contains no {@code aconst_null}). This version's
     * nine-node parallel branch (P1..P9) is NOT restructured by t130 — new tiers intentionally
     * share P9 with 65536.
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
