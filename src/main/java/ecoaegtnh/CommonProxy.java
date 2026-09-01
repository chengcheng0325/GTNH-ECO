package ecoaegtnh;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        EcoAERegistry.preInit(event);
    }

    public void init(FMLInitializationEvent event) {
        EcoAERegistry.init(event);
    }

    public void postInit(FMLPostInitializationEvent event) {
        EcoAERegistry.postInit(event);
    }
}
