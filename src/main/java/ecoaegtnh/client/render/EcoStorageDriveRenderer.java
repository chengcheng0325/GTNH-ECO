package ecoaegtnh.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Render handler for the E-Storage drive bay (t88).
 *
 * <p>
 * The world pass draws nothing (the {@link RenderEcoStorageDrive} TESR renders the
 * full 3D model per tile, so the per-state front face, pocket and LEDs can react to the
 * inserted cell). The inventory pass renders the same baked model (empty state) so the
 * item/creative-tab icon shows the 3D block.
 */
@SideOnly(Side.CLIENT)
public class EcoStorageDriveRenderer implements ISimpleBlockRenderingHandler {

    private static final int RENDER_ID = RenderingRegistry.getNextAvailableRenderId();

    public static int renderId() {
        return RENDER_ID;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        GL11.glScalef(0.92F, 0.92F, 0.92F);
        GL11.glRotatef(-30.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        RenderEcoStorageDrive.renderModel(1.0F, RenderEcoStorageDrive.CELL_NONE);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        return false; // the TESR draws the world model
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return RENDER_ID;
    }
}
