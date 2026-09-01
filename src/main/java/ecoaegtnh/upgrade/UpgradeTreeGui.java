package ecoaegtnh.upgrade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.drawable.FallbackableUITexture;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.drawable.RotatedDrawable;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.drawable.shapes.Rectangle;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.widget.Interactable;
import com.gtnewhorizons.modularui.api.widget.Interactable.ClickResult;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicTextWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import tectech.thing.gui.TecTechUITextures;

/**
 * t61: the three-layer upgrade-tree GUI (docs/ECO_UPGRADE_TREE_DESIGN.md §5, Forge-of-the-Gods
 * style, MUI1) shared by the calculator host and the storage array:
 * <ul>
 * <li>{@link #OVERVIEW_WINDOW_ID} (300): the tree — nodes laid out by prerequisite depth
 * (parents left/up, children right/down) with connector lines between them (Forge-of-the-Gods
 * UpgradeTreePanel style: activated edges solid bright, inactive edges dim dashed), node buttons
 * colored by state (activated = green highlight / submittable = gold / locked = gray),
 * left-click opens the detail window.</li>
 * <li>{@link #DETAIL_WINDOW_ID} (301): the selected node's name, effect, prerequisites and
 * material needs (paid/required) with a "材料投入" button.</li>
 * <li>{@link #MATERIAL_WINDOW_ID} (302): material needs with paid progress + a 16-slot staging
 * area + the "消耗材料" submit button (server consumes the staging items into the node's paid
 * record and activates the node when fulfilled).</li>
 * </ul>
 * The windows are built through the {@link Handler} callback so both machines reuse the same
 * code; activation/paid/selection state syncs via FakeSyncWidget string packs.
 */
public final class UpgradeTreeGui {

    /** Overview window id (300) — clear of GT's cover 1..6 / power-panel 8 / LED 100+ / 200-203. */
    public static final int OVERVIEW_WINDOW_ID = 300;
    /** Node-detail window id (301). */
    public static final int DETAIL_WINDOW_ID = 301;
    /** Material-submit window id (302). */
    public static final int MATERIAL_WINDOW_ID = 302;

    /**
     * t70: per-layer window sizes (godforge §5.2 — overview 300×300, detail 300×300, material
     * 190×115). No bottom player inventory; the three layers are full-screen synced windows.
     */
    private static final int OVERVIEW_W = 300;
    private static final int OVERVIEW_H = 300;
    private static final int DETAIL_W = 300;
    private static final int DETAIL_H = 300;
    private static final int MATERIAL_W = 190;
    private static final int MATERIAL_H = 115;

    /** t70: godforge node-button textures (tectech assets gui/button/purple + _pressed). */
    private static final UITexture NODE_BG = UITexture.fullImage("tectech", "gui/button/purple");
    private static final UITexture NODE_BG_PRESSED = UITexture.fullImage("tectech", "gui/button/purple_pressed");

    /**
     * t67: background for the three upgrade windows — ecal_upgrade_bg (T66 art; while the texture
     * is missing the fallbackable texture silently falls back to the vanilla panel). Evaluated
     * lazily inside a Supplier on a full-size DrawableWidget (never at window-construction time),
     * so the server never touches the resource manager during synced-window construction.
     */
    private static final FallbackableUITexture UPGRADE_BG = new FallbackableUITexture(
        UITexture.fullImage("ecoaegtnh", "gui/ecal_upgrade_bg"),
        ModularUITextures.VANILLA_BACKGROUND);

    /**
     * Callbacks the building machine provides: the tree, the server-side selection, the sync
     * pack accessors (server supplier / client setter), the staging handler and the server-side
     * submit action.
     */
    public interface Handler {

        UpgradeTree getUpgradeTree();

        boolean isServerSide();

        /** Server-side selected node id (read by the detail/material windows). */
        String getSelectedNodeId();

        /** Server-side: remember the node opened in the detail window. */
        void setSelectedNodeId(String id);

        void markDirty();

        /** Server supplier: comma-joined activated node ids. */
        String syncActivatedPack();

        /** Client setter: apply the activated pack (drives the overview colors). */
        void applyActivatedPack(String pack);

        /** Server supplier / client setter of the selected node id. */
        String syncSelectedNode();

        void applySelectedNode(String s);

        /** Server supplier / client setter of the paid pack ("node:mat:count;..."). */
        String syncPaidPack();

        void applyPaidPack(String s);

        /** The 16-slot staging handler (server consumes it in {@link #submitUpgradeMaterials()}). */
        ItemStackHandler getStagingHandler();

        /** Server-side: consume staging into the selected node's paid record, activate when done. */
        void submitUpgradeMaterials();
    }

    private UpgradeTreeGui() {}

    // ------------------------------------------------------------------
    // Layer 1: tree overview
    // ------------------------------------------------------------------

    /** t69: rectangular node buttons (godforge §5.2.1: 40×15) with the short name inside. */
    private static final int NODE_W = 40;
    private static final int NODE_H = 15;
    /** t69: row spacing (36px) / branch-column spacing (42px calc, 48px storage). */
    private static final int ROW_DY = 36;
    private static final int COL_DX = 42;
    private static final int STORAGE_COL_DX = 48;

    /**
     * t67: full-size background layer under every upgrade window (fallbackable, lazy).
     */
    private static void addBackground(ModularWindow.Builder builder, int w, int h) {
        builder.widget(
            new DrawableWidget().setDrawable(() -> UPGRADE_BG.get())
                .setPos(0, 0)
                .setSize(w, h));
    }

    /**
     * t71: a Scrollable that never grab-scrolls — its onClick returns IGNORE so it is never
     * recorded as the clicked Interactable (lastClicked), which means mouse-dragging over the
     * tree does NOT scroll the content (the stock Scrollable.onClick returns ACCEPT and its
     * onMouseDragged scrolls by the grabbed offset — that collided with the window drag).
     * Semantics: drag = window pan (the window-drag listener), mouse wheel = content scroll,
     * scrollbar = scroll. Wheel scrolling (onMouseScroll) is inherited unchanged.
     */
    private static final class NoDragScrollable extends Scrollable {

        @Override
        public ClickResult onClick(int button, boolean isDoubleClick) {
            return ClickResult.IGNORE;
        }
    }

    /**
     * t70: real-time window dragging (the stock MUI1 DraggableWindowWrapper only applies the new
     * position on drag END — onDrag is empty, which is why the content seemed not to follow).
     * This listener moves the window on every mouse-drag tick (onDrag) so nodes/lines follow the
     * cursor live. It never consumes clicks (IGNORE), so node buttons keep working.
     */
    private static void enableWindowDrag(ModularWindow w) {
        w.addInteractionListener(new Interactable() {

            private Pos2d offset;

            @Override
            public ClickResult onClick(int button, boolean isDoubleClick) {
                if (button == 0 && w.getContext() != null
                    && w.getContext()
                        .getCursor() != null) {
                    offset = w.getContext()
                        .getCursor()
                        .getPos()
                        .subtract(w.getPos());
                }
                return ClickResult.IGNORE;
            }

            @Override
            public boolean onClickReleased(int button) {
                if (button == 0) offset = null;
                return false;
            }

            @Override
            public void onMouseDragged(int button, long time) {
                // t76: only move the window while the LEFT button is PHYSICALLY held — a click
                // that opened the NEI GUI (or any other screen swap) eats the mouse-up, so the
                // stale offset used to make the window follow the cursor afterwards ("sticky"
                // mouse). isButtonDown guards that case; the call only ever runs on the client
                // (server has no lwjgl input classes and never dispatches drags).
                if (button == 0 && offset != null
                    && org.lwjgl.input.Mouse.isButtonDown(0)
                    && w.getContext() != null
                    && w.getContext()
                        .getCursor() != null) {
                    w.setPos(
                        w.getContext()
                            .getCursor()
                            .getPos()
                            .subtract(offset));
                    w.markNeedsRebuild();
                }
            }

            @Override
            public boolean onMouseScroll(int amount) {
                return false;
            }

            @Override
            public boolean onKeyPressed(char c, int key) {
                return false;
            }
        });
    }

    /** Builds the overview window: dependency-tree node layout + connector lines + back button. */
    public static ModularWindow createOverview(Handler h, EntityPlayer player) {
        ModularWindow.Builder builder = ModularWindow.builder(OVERVIEW_W, OVERVIEW_H);
        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND);
        addBackground(builder, OVERVIEW_W, OVERVIEW_H);

        // Title (also carries the single activated-pack syncer for this window — t69 removed the
        // bottom hint bar).
        builder
            .widget(
                TextWidget
                    .dynamicString(
                        () -> EnumChatFormatting.GOLD
                            + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.overview.title"))
                    .setSynced(false)
                    .setTextAlignment(Alignment.CenterLeft)
                    .setPos(30, 10)
                    .setSize(200, 12))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncActivatedPack(), s -> h.applyActivatedPack(s)));

        // t73: close button — godforge transparent red × texture (no white box).
        builder.widget(redCloseButton((clickData, widget) -> {
            if (!widget.isClient()) {
                com.gtnewhorizons.modularui.api.screen.ModularUIContext ctx = widget.getContext();
                if (ctx.isWindowOpen(OVERVIEW_WINDOW_ID)) {
                    ctx.closeWindow(OVERVIEW_WINDOW_ID);
                }
            }
        }).setPos(10, 10)
            .dynamicTooltip(() -> backTooltip()));

        // t70: the tree lives in a vertical Scrollable (292×957 godforge proportions scaled to the
        // 300×300 window → 280×262 viewport); t71: NoDragScrollable — dragging pans the window,
        // the mouse wheel / scrollbar scroll the content. Connector lines (rotated 6px tectech
        // textures, opaque when active) are added BEFORE the buttons.
        Scrollable tree = new NoDragScrollable().setVerticalScroll();
        tree.setPos(10, 28)
            .setSize(280, 262);
        java.util.Map<String, int[]> pos = treePositions(h);
        for (UpgradeNode node : h.getUpgradeTree()
            .getNodes()) {
            int[] to = pos.get(node.getId());
            if (to == null) continue;
            for (String prereq : node.getPrerequisites()) {
                int[] from = pos.get(prereq);
                if (from == null) continue;
                tree.widget(
                    connectorLine(
                        h,
                        prereq,
                        node.getId(),
                        from[0] + NODE_W / 2,
                        from[1] + NODE_H / 2,
                        to[0] + NODE_W / 2,
                        to[1] + NODE_H / 2));
            }
        }
        for (UpgradeNode node : h.getUpgradeTree()
            .getNodes()) {
            int[] p = pos.get(node.getId());
            if (p == null) continue;
            tree.widget(
                nodeButton(h, node.getId()).setPos(p[0], p[1])
                    .setSize(NODE_W, NODE_H));
            // t69: short name centered INSIDE the button (text overlays the button, clicks
            // pass through to it).
            DynamicTextWidget name = TextWidget.dynamicString(
                () -> EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("ecoaegtnh.upgrade.node." + node.getId() + ".short"));
            name.setSynced(false);
            name.setTextAlignment(Alignment.Center);
            name.setPos(p[0], p[1] + 2);
            name.setSize(NODE_W, 11);
            tree.widget(name);
        }
        builder.widget(tree);

        ModularWindow win = builder.build();
        enableWindowDrag(win);
        return win;
    }

    /**
     * t69/t70: node positions for the overview, per tree — rectangular 40×15 buttons on a
     * 36px-row grid, horizontally centered in the 280px scroll area:
     * <ul>
     * <li>calculator tree (has the OC terminal): cell main chain column x=57 (N1..N10), thread
     * branch x=99 (from the N4 row), hyper branch x=141 (from the T2 row), parallel branch x=183
     * (from the N4 row) and the OC terminal below the parallel branch (same column). Every
     * connector runs right/down (dx, dy >= 0) from the parent's center to the child's.</li>
     * <li>storage tree (no OC): three independent vertical chains (I/F/E columns x=72/120/168,
     * 48px apart, centered).</li>
     * </ul>
     */
    private static java.util.Map<String, int[]> treePositions(Handler h) {
        if (h.getUpgradeTree()
            .getNode("OC") != null) {
            return calculatorPositions();
        }
        return storagePositions();
    }

    /** t70/t114: calculator branch layout (columns N=57 / T=99 / H=141 / P=183, rows 36px, centered). */
    private static java.util.Map<String, int[]> calculatorPositions() {
        java.util.Map<String, int[]> pos = new java.util.LinkedHashMap<>();
        int x0 = 57;
        for (int i = 1; i <= 11; i++) { // t114: N11 = Singularity flash cell (奇点闪存晶阵)
            pos.put("N" + i, new int[] { x0, (i - 1) * ROW_DY });
        }
        for (int i = 1; i <= 5; i++) { // t114f: T4/T5 = 32/64-thread cores
            pos.put("T" + i, new int[] { x0 + COL_DX, 3 * ROW_DY + (i - 1) * ROW_DY });
        }
        for (int i = 1; i <= 3; i++) {
            pos.put("H" + i, new int[] { x0 + 2 * COL_DX, 4 * ROW_DY + (i - 1) * ROW_DY });
        }
        for (int i = 1; i <= 9; i++) {
            pos.put("P" + i, new int[] { x0 + 3 * COL_DX, 3 * ROW_DY + (i - 1) * ROW_DY });
        }
        // t114g: built-in thread branch (B1 ← N4, B2 ← B1) on its own column x0 + 4*COL_DX.
        pos.put("B1", new int[] { x0 + 4 * COL_DX, 3 * ROW_DY });
        pos.put("B2", new int[] { x0 + 4 * COL_DX, 4 * ROW_DY });
        pos.put("OC", new int[] { x0 + 3 * COL_DX, 3 * ROW_DY + 8 * ROW_DY + ROW_DY });
        return pos;
    }

    /**
     * t69/t70/t112/t114: storage-tree positions — three independent vertical chains (I/F/E columns
     * x=72/120/168, 48px apart, rows 36px; group horizontally centered in the 280px scroll area).
     * t114: one node per cell — the item chain has 10 rows (256k..人造宇宙), the fluid chain 11
     * (+F11 无限水) and the essentia chain 11 (+E11 魔导源质); 11×36 = 396px exceeds the 262px
     * viewport, so the tree scrolls (the overview window is scrollable since t70).
     */
    private static java.util.Map<String, int[]> storagePositions() {
        java.util.Map<String, int[]> pos = new java.util.LinkedHashMap<>();
        String[] chains = { "I", "F", "E" };
        int[] rows = { 10, 11, 11 }; // t114: F/E chains gain the infinite-water / arcane node.
        int x0 = 72;
        for (int c = 0; c < chains.length; c++) {
            for (int i = 1; i <= rows[c]; i++) {
                pos.put(chains[c] + i, new int[] { x0 + c * STORAGE_COL_DX, (i - 1) * ROW_DY });
            }
        }
        return pos;
    }

    /**
     * t70: connector texture name by the FROM node's chain (godforge §5.2.2 / §5.2.6) — N/I
     * blue, T/F orange, H/E purple, P green, O (OC terminal) red. Textures are the tectech
     * 6px connector lines: {color}.png (translucent) vs {color}_opaque.png (activated).
     */
    private static String connectorName(String fromId) {
        if (fromId == null || fromId.isEmpty()) return "blue";
        switch (fromId.charAt(0)) {
            case 'T':
            case 'F':
                return "orange";
            case 'H':
            case 'E':
                return "purple";
            case 'P':
                return "green";
            case 'O':
                return "red";
            case 'B':
                return "orange"; // t114g: built-in thread branch
            default:
                return "blue"; // N / I
        }
    }

    /**
     * t70: connector line between two node centers — the godforge way: a 6px-wide rotated
     * tectech connector texture (atan2 orientation, rotated around the widget center), switching
     * to the _opaque texture when BOTH endpoints are activated (opaque vs translucent). Built as
     * a DrawableWidget with a lazy Supplier, so the server never touches resources.
     */
    private static DrawableWidget connectorLine(Handler h, String fromId, String toId, int fx, int fy, int tx, int ty) {
        String name = connectorName(fromId);
        final UITexture translucent = UITexture.fullImage("tectech", "gui/picture/connector_" + name);
        final UITexture opaque = UITexture.fullImage("tectech", "gui/picture/connector_" + name + "_opaque");
        float dx = tx - fx;
        float dy = ty - fy;
        int dist = (int) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1) dist = 1;
        final float rotation = (float) (Math.atan2(dy, dx) - Math.PI / 2);
        DrawableWidget w = new DrawableWidget();
        w.setDrawable(() -> {
            String pack = h.syncActivatedPack();
            boolean active = pack != null && pack.contains(fromId) && pack.contains(toId);
            return new RotatedDrawable(active ? opaque : translucent).setRotationRadian(rotation);
        });
        w.setPos((fx + tx) / 2 - 3, (fy + ty) / 2 - dist / 2);
        w.setSize(6, dist);
        return w;
    }

    /** One node button: state-colored background + tooltip; left-click opens the detail. */
    private static ButtonWidget nodeButton(Handler h, String nodeId) {
        ButtonWidget button = new ButtonWidget();
        button.setOnClick((clickData, widget) -> {
            if (!widget.isClient()) {
                h.setSelectedNodeId(nodeId);
                // t67 (godforge §5.2.5 state machine): the detail replaces the tree overview.
                com.gtnewhorizons.modularui.api.screen.ModularUIContext ctx = widget.getContext();
                if (ctx.isWindowOpen(OVERVIEW_WINDOW_ID)) {
                    ctx.closeWindow(OVERVIEW_WINDOW_ID);
                }
                ctx.openSyncedWindow(DETAIL_WINDOW_ID);
            }
        });
        button.setPlayClickSound(true);
        // t70: godforge node style — purple button base (+ pressed on hover) with the state
        // color overlay on top (the hover overlay is a live drawable so the state color stays
        // current — setHoveredBackground takes a static array).
        button.setBackground(() -> new IDrawable[] { NODE_BG, nodeStateOverlay(h, nodeId) });
        button.setHoveredBackground(NODE_BG_PRESSED, dynamicStateOverlay(h, nodeId));
        button.dynamicTooltip(() -> nodeTooltip(h, nodeId));
        return button;
    }

    /** t70: state color — activated green / submittable gold / locked gray. */
    private static int stateColor(Handler h, String nodeId) {
        String pack = h.syncActivatedPack();
        boolean active = pack != null && pack.contains(nodeId);
        boolean submittable = false;
        if (!active) {
            UpgradeNode node = h.getUpgradeTree()
                .getNode(nodeId);
            if (node != null && pack != null) {
                submittable = true;
                for (String p : node.getPrerequisites()) {
                    // t113b: defensive null guard — a malformed prerequisite (null element)
                    // must not crash the client render loop (crash 2026-08-31: contains(null)).
                    if (p == null || !pack.contains(p)) {
                        submittable = false;
                        break;
                    }
                }
            }
        }
        return active ? 0x8000FF00 : submittable ? 0x80FFB000 : 0x60606060;
    }

    /** t70: state color overlay (evaluated at build time — used by the regular background). */
    private static IDrawable nodeStateOverlay(Handler h, String nodeId) {
        int color = stateColor(h, nodeId);
        return new Rectangle().setColor(color, color, color, color);
    }

    /** t70: live state-color overlay (re-evaluated every frame — used by the hover background). */
    private static IDrawable dynamicStateOverlay(Handler h, String nodeId) {
        return new IDrawable() {

            @Override
            public void draw(float x0, float y0, float width, float height, float partialTicks) {
                int color = stateColor(h, nodeId);
                new Rectangle().setColor(color, color, color, color)
                    .draw(x0, y0, width, height, partialTicks);
            }
        };
    }

    /** Node tooltip: name + state (+ prerequisites when locked). */
    private static List<String> nodeTooltip(Handler h, String nodeId) {
        List<String> list = new ArrayList<>();
        UpgradeNode node = h.getUpgradeTree()
            .getNode(nodeId);
        if (node == null) {
            list.add(EnumChatFormatting.GRAY + nodeId);
            return list;
        }
        String pack = h.syncActivatedPack();
        boolean active = pack != null && pack.contains(nodeId);
        list.add(
            (active ? EnumChatFormatting.GREEN : EnumChatFormatting.GOLD)
                + StatCollector.translateToLocal(node.getNameKey()));
        if (active) {
            list.add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.state.active"));
        } else {
            String[] prereqs = node.getPrerequisites();
            if (prereqs.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (String p : prereqs) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(p);
                }
                list.add(
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.requires")
                        + " "
                        + sb);
            } else {
                list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.state.ready"));
            }
        }
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.overview.click"));
        return list;
    }

    // ------------------------------------------------------------------
    // Layer 2: node detail
    // ------------------------------------------------------------------

    /**
     * Builds the detail window for the currently selected node (t67 layout: title on top,
     * effect introduction in the middle, crafting area with the material needs below and the
     * "打开材料输入口" button on its left). Opening the material window (302) hides this window;
     * closing 302 restores it.
     */
    public static ModularWindow createDetail(Handler h, EntityPlayer player) {
        ModularWindow.Builder builder = ModularWindow.builder(DETAIL_W, DETAIL_H);
        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND);
        addBackground(builder, DETAIL_W, DETAIL_H);

        // t73: detail back button — godforge transparent red × (returns to the tree overview).
        builder.widget(redCloseButton((clickData, widget) -> {
            if (!widget.isClient()) {
                // t67 (godforge §5.2.5): the detail's back button returns to the tree overview.
                com.gtnewhorizons.modularui.api.screen.ModularUIContext ctx = widget.getContext();
                if (ctx.isWindowOpen(DETAIL_WINDOW_ID)) {
                    ctx.closeWindow(DETAIL_WINDOW_ID);
                }
                ctx.openSyncedWindow(OVERVIEW_WINDOW_ID);
            }
        }).setPos(10, 10)
            .dynamicTooltip(() -> backTooltip()));

        // Title (node name).
        builder.widget(
            TextWidget.dynamicString(() -> detailTitle(h))
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft)
                .setPos(30, 10)
                .setSize(200, 12));

        // Introduction: effect description.
        builder.widget(
            TextWidget.dynamicString(() -> detailEffect(h))
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft)
                .setPos(10, 30)
                .setSize(280, 12));

        // t78: node's OWN name line (replaces the old '前置: xxx' prerequisites display — the
        // user reads this as the node's name; the prerequisites are still visible in the tooltip).
        builder.widget(
            TextWidget.dynamicString(() -> detailNodeName(h))
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft)
                .setPos(10, 44)
                .setSize(280, 12));

        // t78: 材料投入 button — hidden/disabled once the node is activated (done nodes have
        // nothing to submit; the label switches to '已完成 ✓'), prettified with the godforge
        // boxed_exclamation_point icon on a standard base. Opening the material window (302)
        // hides this window; closing 302 restores it.
        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (!widget.isClient()) {
                com.gtnewhorizons.modularui.api.screen.ModularUIContext ctx = widget.getContext();
                if (ctx.isWindowOpen(DETAIL_WINDOW_ID)) {
                    ctx.closeWindow(DETAIL_WINDOW_ID);
                }
                ctx.openSyncedWindow(MATERIAL_WINDOW_ID);
            }
        })
            .setPlayClickSound(true)
            .setEnabled(w -> !detailNodeActive(h))
            .setBackground(
                () -> detailNodeActive(h) ? new IDrawable[] { TecTechUITextures.BUTTON_STANDARD_16x16 }
                    : new IDrawable[] { TecTechUITextures.BUTTON_STANDARD_LIGHT_16x16, MATERIAL_BUTTON_ICON })
            .setPos(10, 76)
            .setSize(16, 16)
            .dynamicTooltip(() -> materialButtonTooltip()));

        builder.widget(
            TextWidget.dynamicString(() -> detailMaterialButtonLabel(h))
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft)
                .setPos(30, 78)
                .setSize(150, 12))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncActivatedPack(), s -> h.applyActivatedPack(s)))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncSelectedNode(), s -> h.applySelectedNode(s)))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncPaidPack(), s -> h.applyPaidPack(s)));

        ModularWindow win = builder.build();
        enableWindowDrag(win);
        return win;
    }

    /** t78: true when the currently selected node is activated (client pack). */
    private static boolean detailNodeActive(Handler h) {
        UpgradeNode node = nodeOf(h);
        return node != null && h.syncActivatedPack() != null
            && h.syncActivatedPack()
                .contains(node.getId());
    }

    /** t78: the node's own name (gold, green when activated) — replaces the prerequisites line. */
    private static String detailNodeName(Handler h) {
        UpgradeNode node = nodeOf(h);
        if (node == null) return "";
        return (detailNodeActive(h) ? EnumChatFormatting.GREEN : EnumChatFormatting.GOLD)
            + StatCollector.translateToLocal(node.getNameKey());
    }

    /** t78: material-button label — '已完成 ✓' when activated, else the material-button text. */
    private static String detailMaterialButtonLabel(Handler h) {
        if (detailNodeActive(h)) {
            return EnumChatFormatting.GREEN + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.state.active")
                + " \u2713";
        }
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.detail.material_button");
    }

    private static String detailTitle(Handler h) {
        UpgradeNode node = nodeOf(h);
        if (node == null) {
            return EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.detail.empty");
        }
        boolean active = h.syncActivatedPack() != null && h.syncActivatedPack()
            .contains(node.getId());
        return (active ? EnumChatFormatting.GREEN : EnumChatFormatting.GOLD) + node.getId()
            + " "
            + StatCollector.translateToLocal(node.getNameKey());
    }

    private static String detailEffect(Handler h) {
        UpgradeNode node = nodeOf(h);
        if (node == null) return "";
        // t67 (godforge §5.2.3): body text WHITE.
        return EnumChatFormatting.WHITE + StatCollector.translateToLocal(node.getEffectKey());
    }

    /** Localized name of a cost material (key = unlocalized item name; ".name" fallback). */
    private static String materialName(String unlocalizedName) {
        String key = unlocalizedName + ".name";
        String localized = StatCollector.translateToLocal(key);
        return localized.equals(key) ? unlocalizedName : localized;
    }

    private static UpgradeNode nodeOf(Handler h) {
        String id = h.syncSelectedNode();
        return id == null ? null
            : h.getUpgradeTree()
                .getNode(id);
    }

    private static int paidOf(Handler h, String nodeId, String materialKey) {
        String pack = h.syncPaidPack();
        if (pack == null) return 0;
        String prefix = nodeId + ":" + materialKey + ":";
        int idx = pack.indexOf(prefix);
        if (idx < 0) return 0;
        String rest = pack.substring(idx + prefix.length());
        int end = rest.indexOf(';');
        if (end < 0) end = rest.length();
        try {
            return Integer.parseInt(rest.substring(0, end));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Layer 3: material submit
    // ------------------------------------------------------------------

    /**
     * Builds the material window (t70/t71: godforge §5.2.4 190×115 — light VANILLA background
     * (NO dark starfield), 12 requirement slots 3×4 on the left, 16 staging slots 4×4 on the
     * right, 180×18 consume button at the bottom).
     */
    public static ModularWindow createMaterial(Handler h, EntityPlayer player) {
        ModularWindow.Builder builder = ModularWindow.builder(MATERIAL_W, MATERIAL_H);
        // t71: godforge BACKGROUND_STANDARD equivalent — the light vanilla panel (the dark
        // ecal_upgrade_bg starfield is NOT layered here, matching the reference screenshot).
        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND);

        // t67: back button (top-right, godforge panelCloseButton position) closes 302 and
        // RESTORES the detail window (301); t73: godforge transparent red × texture.
        builder.widget(redCloseButton((clickData, widget) -> {
            if (!widget.isClient()) {
                com.gtnewhorizons.modularui.api.screen.ModularUIContext ctx = widget.getContext();
                if (ctx.isWindowOpen(MATERIAL_WINDOW_ID)) {
                    ctx.closeWindow(MATERIAL_WINDOW_ID);
                }
                ctx.openSyncedWindow(DETAIL_WINDOW_ID);
            }
        }).setPos(170, 4)
            .dynamicTooltip(() -> backTooltip()));

        // t71: godforge title "支付升级费用" (Pay Upgrade Cost), centered.
        builder
            .widget(
                TextWidget
                    .dynamicString(
                        () -> EnumChatFormatting.DARK_GRAY
                            + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.material.title"))
                    .setSynced(false)
                    .setTextAlignment(Alignment.Center)
                    .setPos(5, 5)
                    .setSize(180, 12))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncPaidPack(), s -> h.applyPaidPack(s)))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncSelectedNode(), s -> h.applySelectedNode(s)));

        // t70 (godforge §5.2.4): requirement slots (3 columns × 4 rows, 36×18) on the left.
        materialSlots(builder, h);

        // t70/t75/t76: 16 staging slots 4×4 (18×18, no gaps) on the right of the requirement
        // grid — base = GT standard_pressed with hover highlight (matching the requirement slots).
        // t111 (godforge parity): shift+clicking a backpack item transfers it straight into the
        // staging area. MUI1's ModularUIContainer#transferStackInSlot routes vanilla QUICK_MOVE
        // clicks through transferItem, filling canInsert slots in ascending shiftClickPriority
        // order — so the staging slots get a NEGATIVE priority (-1) to be tried BEFORE the main
        // window's controller slot (priority 0) and the backpack (0, skipped: same handler).
        for (int i = 0; i < 16; i++) {
            int x = 113 + (i % 4) * 18;
            int y = 16 + (i / 4) * 18;
            final SlotWidget slot = new SlotWidget(new BaseSlot(h.getStagingHandler(), i).setShiftClickPriority(-1));
            slot.setBackground(() -> new IDrawable[] { slotBaseDrawable(slot) });
            builder.widget(
                slot.setPos(x, y)
                    .setSize(18, 18));
        }

        // Submit button ("消耗材料") — t67/t69 godforge §5.2.4: wide 180×18 bar with a centered
        // gold caption overlay (MUI1 ButtonWidget has no built-in text).
        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (!widget.isClient()) {
                h.submitUpgradeMaterials();
            }
        })
            .setPlayClickSound(true)
            .setBackground(() -> new IDrawable[] { TecTechUITextures.BUTTON_STANDARD_LIGHT_16x16 })
            .setPos(5, 92)
            .setSize(180, 18)
            .dynamicTooltip(() -> submitTooltip()))
            .widget(new FakeSyncWidget.StringSyncer(() -> h.syncSelectedNode(), s -> h.applySelectedNode(s)));

        builder.widget(
            TextWidget.dynamicString(
                () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.material.submit"))
                .setSynced(false)
                .setTextAlignment(Alignment.Center)
                .setPos(5, 95)
                .setSize(180, 12));

        ModularWindow win = builder.build();
        enableWindowDrag(win);
        return win;
    }

    private static List<String> materialButtonTooltip() {
        List<String> list = new ArrayList<>();
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.material.open"));
        return list;
    }

    /**
     * t76 (captain-confirmed root cause): open the NEI RECIPE view for an item — used for BOTH
     * left and right clicks. NEI 2.8.111-GTNH has an internal bug: GuiUsageRecipe
     * .getUsageOrCatalystHandler:116 throws ArrayIndexOutOfBoundsException for items without a
     * usage page, so the right-click usage path is disabled until NEI is fixed (a pre-check or
     * real usage support would be a follow-up). Client-only, exceptions swallowed.
     */
    private static void openNeiRecipe(net.minecraft.item.ItemStack stack) {
        if (stack == null) return;
        try {
            codechicken.nei.recipe.GuiCraftingRecipe.openRecipeGui("item", stack);
        } catch (Throwable t) {
            // ignore — NEI queries must never break the upgrade GUI.
        }
    }

    /**
     * t76: slot-base drawable with hover feedback — the GT standard_pressed base plus a light
     * white overlay while the widget is hovered (vanilla inventory-slot hover feel). The widget
     * must be attached to a window before drawing (isHovering needs its context).
     */
    private static IDrawable slotBaseDrawable(com.gtnewhorizons.modularui.api.widget.Widget w) {
        return new IDrawable() {

            @Override
            public void draw(float x0, float y0, float width, float height, float partialTicks) {
                SLOT_BASE.draw(x0, y0, width, height, partialTicks);
                if (w.isHovering()) {
                    new Rectangle().setColor(0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF)
                        .draw(x0, y0, width, height, partialTicks);
                }
            }
        };
    }

    /**
     * t73: 16×16 close button with the godforge ORIGINAL texture — tectech
     * gui/button/transparent_x_10x10.png (transparent base, red ×), replacing the t72
     * light-base + red-glyph version. No white box, no text overlay needed.
     */
    private static final UITexture CLOSE_X = UITexture.fullImage("tectech", "gui/button/transparent_x_10x10");

    /** t74/t75: GT standard_pressed slot base (dark pressed style) for requirement + staging slots. */
    private static final UITexture SLOT_BASE = UITexture.fullImage("gregtech", "gui/button/standard_pressed");

    /** t78: godforge material-button icon (boxed exclamation point, tectech assets). */
    private static final UITexture MATERIAL_BUTTON_ICON = UITexture
        .fullImage("tectech", "gui/button/boxed_exclamation_point");

    /** t73: close button with the godforge transparent red × texture; onClick keeps its semantics. */
    private static ButtonWidget redCloseButton(
        java.util.function.BiConsumer<com.gtnewhorizons.modularui.api.widget.Widget.ClickData, com.gtnewhorizons.modularui.api.widget.Widget> onClick) {
        ButtonWidget b = new ButtonWidget();
        b.setOnClick(onClick);
        b.setPlayClickSound(true);
        b.setBackground(() -> new IDrawable[] { CLOSE_X });
        b.setSize(16, 16);
        return b;
    }

    /**
     * t69/t71/t72/t76 (godforge §5.2.4): the material-requirement slot grid — up to 12 slots in a
     * 3-column × 4-row layout (36×18 each). Slot-style: item icon + "xN" count, paid state =
     * count color (red/yellow/green ✓). t76: the slot is a ButtonWidget with the standard_pressed
     * base + hover highlight (white overlay), left click opens the NEI recipe view, right click
     * the usage view (godforge SlotLikeButtonWidget parity; exceptions swallowed — the MUI1
     * native transferRect right-click path passed EMPTY args to GuiUsageRecipe which is why it
     * errored, so we call the NEI APIs directly with the real stack).
     */
    private static void materialSlots(ModularWindow.Builder builder, Handler h) {
        UpgradeNode node = nodeOf(h);
        if (node == null) return;
        int idx = 0;
        for (java.util.Map.Entry<String, Integer> e : node.getMaterialCost()
            .entrySet()) {
            if (idx >= 12) break;
            final String key = e.getKey();
            final int need = e.getValue();
            final String nodeId = node.getId();
            final int x = 5 + (idx % 3) * 36;
            final int y = 16 + (idx / 3) * 18;
            final net.minecraft.item.ItemStack stack = UpgradeCosts.stackFor(key);
            if (stack != null) {
                final net.minecraft.item.ItemStack slotStack = stack;
                final ButtonWidget slot = new ButtonWidget();
                slot.setOnClick((clickData, widget) -> {
                    if (widget.isClient()) {
                        // t76: BOTH buttons open the recipe view — the right-click usage path
                        // (GuiUsageRecipe) crashes on NEI 2.8.111-GTNH for items without a usage
                        // page (getUsageOrCatalystHandler:116 ArrayIndexOutOfBoundsException).
                        openNeiRecipe(slotStack);
                    }
                });
                slot.setPlayClickSound(true);
                slot.setBackground(() -> new IDrawable[] { slotBaseDrawable(slot) });
                slot.dynamicTooltip(() -> java.util.Collections.singletonList(slotStack.getDisplayName()));
                builder.widget(
                    slot.setPos(x, y)
                        .setSize(18, 18));
                // Item icon (left half of the 36×18 slot).
                builder.widget(
                    new DrawableWidget().setDrawable(() -> new ItemDrawable(UpgradeCosts.stackFor(key)))
                        .setPos(x + 1, y + 1)
                        .setSize(16, 16));
            } else {
                // No item to show — plain slot base.
                builder.widget(
                    new DrawableWidget().setDrawable(() -> SLOT_BASE)
                        .setPos(x, y)
                        .setSize(18, 18));
            }
            // Remaining count "xN" (right half) — red/yellow by paid progress, green ✓ when paid.
            builder.widget(
                TextWidget.dynamicString(() -> materialSlotText(h, nodeId, key, need))
                    .setSynced(false)
                    .setTextAlignment(Alignment.CenterLeft)
                    .setPos(x + 18, y + 3)
                    .setSize(18, 12));
            idx++;
        }
    }

    /** t69: slot text — green ✓ when fully paid, else "x{need-paid}" in red/yellow. */
    private static String materialSlotText(Handler h, String nodeId, String key, int need) {
        int paid = paidOf(h, nodeId, key);
        if (paid >= need) {
            return EnumChatFormatting.GREEN + "\u2713";
        }
        EnumChatFormatting color = paid > 0 ? EnumChatFormatting.YELLOW : EnumChatFormatting.RED;
        return color + "x" + (need - paid);
    }

    private static List<String> submitTooltip() {
        List<String> list = new ArrayList<>();
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.material.submit"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.upgrade.material.submit_tip"));
        return list;
    }

    private static List<String> backTooltip() {
        List<String> list = new ArrayList<>();
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("ecoaegtnh.gui.ecal.milestone.back"));
        return list;
    }
}
