package ecoaegtnh.upgrade;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

/**
 * t63/t69/t77: material-cost helpers for the upgrade trees (docs/ECO_UPGRADE_TREE_DESIGN.md §4).
 * <p>
 * Cost keys are the ITEMS' UNLOCALIZED NAMES + DAMAGE ("unlocalizedName@damage", see
 * {@link #keyOf}) — exactly what the GUI submit chain matches, so the maps are built from the
 * canonical GT material / AE2 definition stacks and always match what the runtime items
 * produce. t77: the damage part is REQUIRED — GT ingots are all unified onto
 * MetaGeneratedItem01 ("gt.metaitem.01") with the material in the damage value, so a bare
 * unlocalized-name key would collapse iron/aluminium/titanium/... onto one slot (the last
 * registered stack won). Every stack handed out by {@link #gtIngot} / {@link #ae} is ALSO
 * registered in the source table ({@link #stackFor}) so the GUI can render the material slots
 * (icon + count) from a cost key alone. Amounts are BASE PLACEHOLDER CONSTANTS — the GT line
 * (iron → aluminium → titanium → iridium → neutronium → stellar alloy) and AE line (processors
 * → circuit board → logic processor → storage component) mix scales with the node depth;
 * 装机后调 (design §4).
 */
public final class UpgradeCosts {

    private UpgradeCosts() {}

    /** t69/t77: canonical item stack per cost key, filled by gtIngot/ae. */
    private static final Map<String, ItemStack> SOURCES = new HashMap<>();

    /**
     * t77: the cost key of a stack — "unlocalizedName@itemDamage". GT ingots share the
     * unlocalized name ("gt.metaitem.01") and differ only in damage, so the key must carry it
     * (AE materials have damage 0 and are unaffected).
     */
    public static String keyOf(net.minecraft.item.ItemStack s) {
        return s.getUnlocalizedName() + "@" + s.getItemDamage();
    }

    /**
     * Cost map from alternating (ItemStack, Integer) pairs; null stacks and non-positive
     * amounts are skipped (a missing material just drops that entry).
     */
    public static Map<String, Integer> of(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            ItemStack s = (ItemStack) pairs[i];
            int amount = ((Number) pairs[i + 1]).intValue();
            if (s != null && amount > 0) {
                m.put(keyOf(s), amount);
            }
        }
        return m;
    }

    /** One GT ingot stack (key source; null when the material is unavailable). */
    public static ItemStack gtIngot(gregtech.api.enums.Materials mat) {
        ItemStack s = mat == null ? null : mat.getIngots(1);
        if (s != null) {
            SOURCES.put(keyOf(s), s);
        }
        return s;
    }

    /** One AE material stack (null when the definition is unavailable). */
    public static ItemStack ae(appeng.api.definitions.IItemDefinition def) {
        ItemStack s = def == null ? null
            : def.maybeStack(1)
                .orNull();
        if (s != null) {
            SOURCES.put(keyOf(s), s);
        }
        return s;
    }

    /**
     * t69/t77: resolve a cost key ("unlocalizedName@damage") back to the canonical item stack
     * for GUI rendering (material slots). Returns null for keys never handed out by gtIngot/ae.
     */
    public static ItemStack stackFor(String key) {
        ItemStack s = SOURCES.get(key);
        return s == null ? null : s.copy();
    }
}
