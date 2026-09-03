package com.neirecipepanels;

import net.minecraft.nbt.NBTTagCompound;

import com.neirecipepanels.network.ConfigurePanelMessage;
import com.neirecipepanels.network.MakeRecipePanelMessage;
import com.neirecipepanels.network.ServerTasks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        ModItems.register();
        ModBlocks.register();
        NeiRecipePanels.NETWORK
            .registerMessage(MakeRecipePanelMessage.Handler.class, MakeRecipePanelMessage.class, 0, Side.SERVER);
        NeiRecipePanels.NETWORK
            .registerMessage(ConfigurePanelMessage.Handler.class, ConfigurePanelMessage.class, 1, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(ServerTasks.INSTANCE);
    }

    public void init(FMLInitializationEvent event) {
        ModItems.registerRecipes();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    /** Client-only: open the panel's recipe in NEI. No-op on the server. */
    public void openPanelRecipe(NBTTagCompound snapshot, boolean usage) {}

    /** Client-only: open the config screen for the panel at the given position. No-op on the server. */
    public void openPanelConfig(int x, int y, int z) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
