package ecoaegtnh.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.client.model.DriveBayGeometry;
import ecoaegtnh.item.estorage.ItemEcoStorageCellEssentia;
import ecoaegtnh.item.estorage.ItemEcoStorageCellFluid;
import ecoaegtnh.item.estorage.ItemEcoStorageCellItem;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;

/**
 * TileEntitySpecialRenderer for the E-Storage drive bay (t88).
 *
 * <p>
 * The drive bay is drawn entirely by this renderer (the block render pass draws
 * nothing - see {@link EcoStorageDriveRenderer}): a 16x16x16 shell textured with the
 * registered block atlas icon ({@code storage_array_drives_side}, bound through
 * {@link TextureMap#locationBlocksTexture} - direct PNG binding does not work for
 * block faces in 1.7.10), a recessed front panel with a deep single-slot pocket
 * (dark cavity, gold contacts, cyan glow strip) and green/cyan LEDs. When a storage
 * cell is inserted a coloured cell card is drawn inside the pocket and the LEDs
 * switch to cyan (per cell type: item=gold, fluid=blue, essentia=purple, other=cyan).
 * Geometry comes from the baked {@link DriveBayGeometry} arrays (generated from the
 * Blender model), compiled once into display lists.
 *
 * <p>
 * The 1.7.10 lightmap texture unit (unit 1) stays enabled during the TESR pass; a
 * stale texture-coordinate pair would multiply every fragment (textured AND flat
 * colour) towards black. It is therefore explicitly disabled here and the world
 * brightness is applied via glColor instead (flat GT-style shading).
 *
 * <p>
 * All logic (insert/extract, tier checks, AE grid wiring) lives in
 * {@link TileEcoStorageDrive} and is untouched by this visual change.
 */
@SideOnly(Side.CLIENT)
public class RenderEcoStorageDrive extends net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer {

    /** Cell front colours (t33 palette, from the real textures). */
    private static final float[] CELL_COLOR_ITEM = rgb(0.984F, 0.706F, 0.337F);
    private static final float[] CELL_COLOR_FLUID = rgb(0.322F, 0.639F, 1.0F);
    private static final float[] CELL_COLOR_ESSENTIA = rgb(0.690F, 0.424F, 1.0F);
    private static final float[] CELL_COLOR_OTHER = rgb(0.302F, 0.765F, 1.0F);
    private static final float[] LED_GREEN = rgb(0.05F, 0.92F, 0.30F);
    private static final float[] LED_CYAN = rgb(0.302F, 0.765F, 1.0F);

    private static final int[] DISPLAY_LISTS = new int[DriveBayGeometry.MATERIAL_COUNT];
    private static boolean listsCompiled = false;
    /** True when the block atlas icon for the shell was available at compile time. */
    private static boolean shellTextured = false;

    /** Cell type ids (match the block's filled-icon logic in BlockEcoStorageDrive). */
    public static final int CELL_NONE = 0;
    public static final int CELL_ITEM = 1;
    public static final int CELL_FLUID = 2;
    public static final int CELL_ESSENTIA = 3;
    public static final int CELL_OTHER = 4;

    private static float[] rgb(float r, float g, float b) {
        return new float[] { r, g, b };
    }

    /** Classifies an inserted cell (null stack -> CELL_NONE). */
    public static int cellTypeFor(ItemStack stack) {
        if (stack == null) return CELL_NONE;
        Item item = stack.getItem();
        if (item instanceof ItemEcoStorageCellItem) return CELL_ITEM;
        if (item instanceof ItemEcoStorageCellFluid) return CELL_FLUID;
        if (item instanceof ItemEcoStorageCellEssentia) return CELL_ESSENTIA;
        if (item instanceof ecoaegtnh.item.estorage.ItemEcoStorageCell) return CELL_OTHER;
        return CELL_NONE;
    }

    /** Display-list render of the full (empty) model - shared with the inventory renderer. */
    public static void renderModel(float amb, int cellType) {
        ensureCompiled();
        disableLightmapUnit();
        float k = brightnessK(amb);
        if (shellTextured) {
            // Block faces must sample the block ATLAS through the registered IIcon
            // (a standalone PNG bound directly does not render in 1.7.10).
            bindColor(k, k, k);
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            glCall(DriveBayGeometry.M_SHELL);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        } else {
            // Fallback: plain graphite shell (no atlas icon available).
            int[] c = DriveBayGeometry.COLOR[DriveBayGeometry.M_SHELL];
            bindColor(c[0] / 255.0F * k, c[1] / 255.0F * k, c[2] / 255.0F * k);
            glCall(DriveBayGeometry.M_SHELL);
        }

        for (int m = 0; m < DriveBayGeometry.MATERIAL_COUNT; m++) {
            if (m == DriveBayGeometry.M_SHELL || m == DriveBayGeometry.M_LEDS || m == DriveBayGeometry.M_CELLFRONT) {
                continue;
            }
            if (m == DriveBayGeometry.M_CELLEDGE && cellType == CELL_NONE) {
                continue; // cell edge only exists with an inserted cell
            }
            int[] c = DriveBayGeometry.COLOR[m];
            if (c == null) continue;
            if (DriveBayGeometry.EMISSIVE[m]) {
                bindColor(c[0] / 255.0F, c[1] / 255.0F, c[2] / 255.0F);
            } else if (isInterior(m)) {
                // t92: the slot interior is the visual focus - never let it collapse
                // towards black (walls/back wall keep a readable brightness floor even
                // in shadow, so the slot depth and the glowing strip stay visible).
                float ki = interiorK(amb);
                bindColor(c[0] / 255.0F * ki, c[1] / 255.0F * ki, c[2] / 255.0F * ki);
            } else {
                bindColor(c[0] / 255.0F * k, c[1] / 255.0F * k, c[2] / 255.0F * k);
            }
            glCall(m);
        }

        // LEDs: green empty, cyan filled
        float[] led = cellType == CELL_NONE ? LED_GREEN : LED_CYAN;
        bindColor(led[0], led[1], led[2]);
        glCall(DriveBayGeometry.M_LEDS);

        if (cellType != CELL_NONE) {
            float[] cell = cellColor(cellType);
            bindColor(cell[0], cell[1], cell[2]);
            glCall(DriveBayGeometry.M_CELLFRONT);
        }
    }

    /**
     * t88-patch: brightness multiplier for the shell and the non-emissive parts.
     * The old formula (amb*0.85+0.12) collapsed to ~0.2 in shaded spots, making the
     * drive bay the darkest element on the wall (lum~12 vs housing 42-43). This
     * version lifts the floor to 0.35 and reaches 1.0 (texture at full colour) by
     * amb~0.43, so the shell reads at its design luminance (48-58) in normal light.
     */
    private static float brightnessK(float amb) {
        float k = amb * 1.5F + 0.35F;
        if (k > 1.0F) k = 1.0F;
        return k;
    }

    /** Slot-interior materials (walls, back wall, cell edge): keep readable in shadow. */
    private static boolean isInterior(int material) {
        return material == DriveBayGeometry.M_CAVITY || material == DriveBayGeometry.M_CAVITYBACK
            || material == DriveBayGeometry.M_CELLEDGE;
    }

    /**
     * t92: interior brightness multiplier - same curve as the shell but with a 0.85
     * floor, so the pocket walls/back never collapse to black even in darkness and
     * the slot reads as a deep-but-visible recess (slightly darker than the shell).
     */
    private static float interiorK(float amb) {
        float ki = brightnessK(amb);
        if (ki < 0.85F) ki = 0.85F;
        return ki;
    }

    /**
     * 1.7.10 keeps the lightmap texture unit (unit 1) enabled across the TESR pass;
     * with a stale coordinate pair every fragment would be multiplied towards black.
     * Disable it so flat colours / atlas textures are tinted by glColor only.
     */
    private static void disableLightmapUnit() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static float[] cellColor(int cellType) {
        switch (cellType) {
            case CELL_ITEM:
                return CELL_COLOR_ITEM;
            case CELL_FLUID:
                return CELL_COLOR_FLUID;
            case CELL_ESSENTIA:
                return CELL_COLOR_ESSENTIA;
            default:
                return CELL_COLOR_OTHER;
        }
    }

    private static void bindColor(float r, float g, float b) {
        GL11.glColor4f(r, g, b, 1.0F);
    }

    private static void glCall(int material) {
        if (DISPLAY_LISTS[material] != 0) {
            GL11.glCallList(DISPLAY_LISTS[material]);
        }
    }

    private static void ensureCompiled() {
        if (listsCompiled) return;
        // Shell texture: the registered block-atlas icon (UVs are stable after the
        // atlas is stitched, so baking them into the display list is safe).
        IIcon sideIcon = BlockEcoStorageDrive.INSTANCE == null ? null : BlockEcoStorageDrive.INSTANCE.getSideIcon();
        shellTextured = sideIcon != null;
        float u0 = shellTextured ? sideIcon.getMinU() : 0.0F;
        float u1 = shellTextured ? sideIcon.getMaxU() : 1.0F;
        float v0 = shellTextured ? sideIcon.getMinV() : 0.0F;
        float v1 = shellTextured ? sideIcon.getMaxV() : 1.0F;
        for (int m = 0; m < DriveBayGeometry.MATERIAL_COUNT; m++) {
            float[] pos = DriveBayGeometry.POS[m];
            float[] uv = DriveBayGeometry.UV[m];
            int list = GL11.glGenLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            int triCount = DriveBayGeometry.TRIANGLES[m];
            for (int t = 0; t < triCount; t++) {
                for (int v = 0; v < 3; v++) {
                    int pi = (t * 3 + v) * 3;
                    if (uv != null) {
                        int ui = (t * 3 + v) * 2;
                        // 0..1 face UVs -> atlas icon UV range.
                        GL11.glTexCoord2f(u0 + uv[ui] * (u1 - u0), v0 + uv[ui + 1] * (v1 - v0));
                    }
                    GL11.glVertex3f(pos[pi], pos[pi + 1], pos[pi + 2]);
                }
            }
            GL11.glEnd();
            GL11.glEndList();
            DISPLAY_LISTS[m] = list;
        }
        listsCompiled = true;
    }

    /** Facing rotation (metadata 2=N, 3=S, 4=W, 5=E) for the model front (+z). */
    private static float rotationFor(int meta) {
        switch (meta) {
            case BlockEcoStorageDrive.META_NORTH:
                return 180.0F;
            case BlockEcoStorageDrive.META_EAST:
                return 90.0F;
            case BlockEcoStorageDrive.META_WEST:
                return 270.0F;
            default:
                return 0.0F; // south
        }
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEcoStorageDrive)) return;
        TileEcoStorageDrive te = (TileEcoStorageDrive) tile;
        World world = te.getWorldObj();
        if (world == null) return;

        ensureCompiled();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x + 0.5F, (float) y + 0.5F, (float) z + 0.5F);
        GL11.glRotatef(rotationFor(te.getBlockMetadata()), 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);

        // Ambient darkening (day/night + block light), flat GT-style shading.
        float amb = world.getLightBrightness(te.xCoord, te.yCoord, te.zCoord);
        if (amb < 0.05F) amb = 0.05F;
        renderModel(amb, cellTypeFor(te.getCellStack()));

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
