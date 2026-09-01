package ecoaegtnh;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import ecoaegtnh.block.estorage.BlockEcoStorageDrive;
import ecoaegtnh.client.render.EcoStorageDriveRenderer;
import ecoaegtnh.client.render.RenderEcoStorageDrive;
import ecoaegtnh.tile.estorage.TileEcoStorageDrive;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // t88: 3D drive bay - register the block render handler (inventory + world
        // no-op) and the tile entity special renderer that draws the full model.
        EcoStorageDriveRenderer renderer = new EcoStorageDriveRenderer();
        RenderingRegistry.registerBlockHandler(renderer);
        BlockEcoStorageDrive.renderId = renderer.getRenderId();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEcoStorageDrive.class, new RenderEcoStorageDrive());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
