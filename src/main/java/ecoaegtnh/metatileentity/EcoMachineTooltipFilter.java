package ecoaegtnh.metatileentity;

/**
 * t7（284 移植）：过滤 GT5U 基类 GUI 的"软锤启动"闲置提示行。
 * <p>
 * 背景：5.09.54.20（2.9.0）的 {@code MTEMultiBlockBase.drawTexts} 把
 * {@code gt.interact.desc.mb.idle.1/2/3}（"Hit with Soft Mallet to (re-)start the Machine
 * if it doesn't start."）整块放在 {@code showMachineStatusInGUI()} 门控之后——290 版的两台
 * ECO 机器覆写该方法返回 false，所以不显示这三行。5.09.51.482（2.8.4）没有该钩子，三行
 * 无条件添加；纯 AE 机器从不"运行"，因此永远显示。
 * <p>
 * 本工具只被本 mod 的两台机器（MTEEcoStorageArray / MTEEcalArray）的 drawTexts 覆写调用：
 * 先让基类画进临时列，再跳过这三行后搬回真实列——其他 mod 的机器完全不受影响。
 */
public final class EcoMachineTooltipFilter {

    /** 基类 GUI 的"软锤启动"提示行 lang key（5.09.51.482 的 drawTexts 逐行添加）。 */
    private static final String[] IDLE_KEYS = { "gt.interact.desc.mb.idle.1", "gt.interact.desc.mb.idle.2",
        "gt.interact.desc.mb.idle.3" };

    private EcoMachineTooltipFilter() {}

    /** true 当该 widget 是基类添加的"软锤启动"提示行之一（按当前语言译文匹配）。 */
    public static boolean isIdleHintLine(com.gtnewhorizons.modularui.api.widget.Widget w) {
        if (!(w instanceof com.gtnewhorizons.modularui.common.widget.TextWidget tw)) {
            return false;
        }
        String raw = tw.getText()
            .getRawText();
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        for (String key : IDLE_KEYS) {
            if (raw.equals(net.minecraft.util.StatCollector.translateToLocal(key))) {
                return true;
            }
        }
        return false;
    }
}
