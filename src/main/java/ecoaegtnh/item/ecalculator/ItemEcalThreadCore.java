package ecoaegtnh.item.ecalculator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * t35: E-Calculator thread core (线程核心) insertable item — supplies thread slots to a
 * {@code BlockEcalThreadDrive} (1 slot). Six kinds: normal {@code ecal_thread_core_1/4/16}
 * (threads 1/4/16, no hyper) and hyper {@code ecal_thread_core_hyper_2/4/8}, usable on ANY
 * controller tier (全档自由). Registry {@code ecal_thread_core_<n>} /
 * {@code ecal_thread_core_hyper_<n>}, textures follow (T36 art).
 * <p>
 * t114s (user): the hyper cores' SUPPLIED thread counts are doubled while the registry suffix
 * stays — hyper_2 = 0+4, hyper_4 = 4+8, hyper_8 = 8+16. The suffix is now passed explicitly
 * (it used to derive from hyperThreads, which would collide once counts doubled).
 */
public class ItemEcalThreadCore extends Item {

    protected final int threads;
    protected final int hyperThreads;

    public ItemEcalThreadCore(int threads, int hyperThreads, String suffix) {
        this.threads = threads;
        this.hyperThreads = hyperThreads;
        setMaxStackSize(1);
        setCreativeTab(ecoaegtnh.EcoAEGTNHCore.TAB_CALC);
        setUnlocalizedName("ecoaegtnh.ecal_thread_core_" + suffix);
        setTextureName("ecoaegtnh:ecal_thread_core_" + suffix);
    }

    /** Normal thread slots this core supplies (hyper cores: 0/4/8). */
    public int getThreads() {
        return threads;
    }

    /** Hyper-thread slots this core supplies (normal cores: 0). */
    public int getHyperThreads() {
        return hyperThreads;
    }

    /**
     * t50 (milestone gate, docs §4.1): thread-branch line level required to insert this core —
     * normal 1 → Lv1+, 4 → Lv2+, 16 → Lv3+; hyper (t114s: 0+4 / 4+8 / 8+16) → Lv5+.
     */
    public int getRequiredMilestoneLevel() {
        if (hyperThreads > 0) {
            return hyperThreads >= 4 ? 5 : 4;
        }
        if (threads >= 16) return 3;
        if (threads >= 4) return 2;
        return 1;
    }

    /**
     * t60/t114f→t128b (upgrade tree, docs §2): the thread-chain / hyper node required to insert
     * this core — normal 1 → T1, 4 → T2, ≥16 → T3 (t128b: the 32/64-thread cores are gone, so any
     * legacy 32/64-thread stack is truncated onto T3); hyper by hyperThreads: 4 → H2, 8+ → H3
     * (t114s: hyper_2 supplies 0+4 → H2, hyper_4 4+8 → H3, hyper_8 8+16 → H3).
     */
    public String getRequiredUpgradeNode() {
        if (hyperThreads > 0) {
            return hyperThreads >= 8 ? ecoaegtnh.upgrade.CalculatorUpgradeTree.H3
                : hyperThreads >= 4 ? ecoaegtnh.upgrade.CalculatorUpgradeTree.H2
                    : ecoaegtnh.upgrade.CalculatorUpgradeTree.H1;
        }
        if (threads >= 16) {
            return ecoaegtnh.upgrade.CalculatorUpgradeTree.T3;
        }
        if (threads >= 4) {
            return ecoaegtnh.upgrade.CalculatorUpgradeTree.T2;
        }
        return ecoaegtnh.upgrade.CalculatorUpgradeTree.T1;
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
        if (hyperThreads > 0) {
            // t114f/t114s: describe THIS core's own tier (hyper_2 → 0+4, hyper_4 → 4+8,
            // hyper_8 → 8+16), not the whole ladder; the lang text escapes its literal % as %%
            // so String.format does not throw ("Format error:" + raw %s — user report).
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "ecoaegtnh.tooltip.ecal.thread_core.hyper",
                    threads,
                    hyperThreads,
                    threads,
                    hyperThreads));
        } else {
            lines.add(StatCollector.translateToLocalFormatted("ecoaegtnh.tooltip.ecal.thread_core", threads));
        }
    }

    public static ItemEcalThreadCore register(String name, int threads, int hyperThreads, String suffix) {
        ItemEcalThreadCore core = new ItemEcalThreadCore(threads, hyperThreads, suffix);
        GameRegistry.registerItem(core, name);
        return core;
    }
}
