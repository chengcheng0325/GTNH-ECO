package ecoaegtnh.milestone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * t52: real material-value table for the milestone feed (docs/ECO_MILESTONE_DESIGN.md §3.1 —
 * 混合线投料). The feed slots convert inserted items into milestone progress via
 * {@link #valueOf(ItemStack)}.
 * <p>
 * Table (docs §3.1, values are BASE CONSTANTS per item — 装机后调, design §7.3):
 * <ul>
 * <li><b>GT line</b> (ore-dict ingot/dust/plate): Iron 10 → Aluminium 20 → Titanium 50 →
 * Iridium 200 → Neutronium 1000 → StellarAlloy 5000 (恒星物质 in GT5U 5.09.54.20 =
 * {@code Materials.StellarAlloy});</li>
 * <li><b>AE line</b>: calculation/engineering processors 30 → basic circuit board
 * (GT {@code Circuit_Board_Basic}, AE 线点缀) 60 → logic processor 120 → ME storage
 * components (cell1kPart 240, doubling per size step up to cell16384kPart 30720).</li>
 * </ul>
 * The whole stack is consumed by the feed (不可重设), so the returned value scales with
 * {@code stackSize}. The default provider IS the real table — the AE2 definitions are only
 * touched lazily on the first feed call (class-load safe); {@link #setProvider(Provider)}
 * still allows overriding.
 */
public final class MaterialValue {

    @FunctionalInterface
    public interface Provider {

        /** Value of one item stack in milestone progress units (0 = no value). */
        long getValue(ItemStack stack);
    }

    /** Lazy real table (AE2 definitions touched only at first feed). */
    private static Provider provider = stack -> Table.PROVIDER.getValue(stack);

    private MaterialValue() {}

    /** Replaces the default table provider (e.g. for balance overrides). */
    public static void setProvider(Provider p) {
        if (p != null) provider = p;
    }

    public static long valueOf(ItemStack stack) {
        return stack == null ? 0L : provider.getValue(stack);
    }

    /** One value-table entry: canonical item stack + per-item value. */
    private static final class ValueEntry {

        final ItemStack stack;
        final long value;

        ValueEntry(ItemStack stack, long value) {
            this.stack = stack;
            this.value = value;
        }
    }

    /** Lazily-built table (first feed initializes it). */
    private static final class Table {

        static final Provider PROVIDER = build();

        private static Provider build() {
            // GT line — ore-dict ingot/dust/plate names (any form counts the same base value).
            final Map<String, Long> oreValues = new HashMap<>();
            final String[] gtNames = { "Iron", "Aluminium", "Titanium", "Iridium", "Neutronium", "StellarAlloy" };
            final long[] gtValues = { 10L, 20L, 50L, 200L, 1000L, 5000L };
            for (int i = 0; i < gtNames.length; i++) {
                oreValues.put("ingot" + gtNames[i], gtValues[i]);
                oreValues.put("dust" + gtNames[i], gtValues[i]);
                oreValues.put("plate" + gtNames[i], gtValues[i]);
            }

            // AE line — exact item+damage match against the canonical definitions.
            final List<ValueEntry> aeEntries = new ArrayList<>();
            final appeng.api.definitions.IDefinitions defs = appeng.api.AEApi.instance()
                .definitions();
            final appeng.api.definitions.IMaterials m = defs.materials();
            add(aeEntries, m.calcProcessor(), 30L); // 处理器
            add(aeEntries, m.engProcessor(), 30L); // 处理器
            add(aeEntries, m.logicProcessor(), 120L); // 逻辑处理器
            long componentValue = 240L; // 存储元件 1k..16384k, ×2 per size step
            add(aeEntries, m.cell1kPart(), componentValue);
            add(aeEntries, m.cell4kPart(), componentValue *= 2);
            add(aeEntries, m.cell16kPart(), componentValue *= 2);
            add(aeEntries, m.cell64kPart(), componentValue *= 2);
            add(aeEntries, m.cell256kPart(), componentValue *= 2);
            add(aeEntries, m.cell1024kPart(), componentValue *= 2);
            add(aeEntries, m.cell4096kPart(), componentValue *= 2);
            add(aeEntries, m.cell16384kPart(), componentValue *= 2);
            ItemStack board = gregtech.api.enums.ItemList.Circuit_Board_Basic.get(1); // 电路板 (AE 线点缀)
            if (board != null) {
                aeEntries.add(new ValueEntry(board, 60L));
            }

            return stack -> {
                if (stack == null || stack.stackSize <= 0) return 0L;
                long unit = 0L;
                for (int id : OreDictionary.getOreIDs(stack)) {
                    Long v = oreValues.get(OreDictionary.getOreName(id));
                    if (v != null) {
                        unit = v;
                        break;
                    }
                }
                if (unit == 0L) {
                    for (ValueEntry e : aeEntries) {
                        ItemStack canon = e.stack;
                        if (stack.getItem() == canon.getItem() && stack.getItemDamage() == canon.getItemDamage()) {
                            unit = e.value;
                            break;
                        }
                    }
                }
                return unit * stack.stackSize;
            };
        }

        private static void add(List<ValueEntry> list, appeng.api.definitions.IItemDefinition def, long value) {
            ItemStack s = def.maybeStack(1)
                .orNull();
            if (s != null) {
                list.add(new ValueEntry(s, value));
            }
        }
    }
}
