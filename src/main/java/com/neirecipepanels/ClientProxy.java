package com.neirecipepanels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.neirecipepanels.block.RecipePanelTile;
import com.neirecipepanels.client.GuiRecipeButtonHandler;
import com.neirecipepanels.client.GuiRecipePanelConfig;
import com.neirecipepanels.client.PanelFboManager;
import com.neirecipepanels.client.PanelInputHandler;
import com.neirecipepanels.client.PanelRecipeOpener;
import com.neirecipepanels.client.RecipePanelItemRenderer;
import com.neirecipepanels.client.RecipePanelRenderer;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new GuiRecipeButtonHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(PanelFboManager.INSTANCE);
        FMLInterModComms
            .sendMessage("Waila", "register", "com.neirecipepanels.client.RecipePanelWaila.callbackRegister");
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRegistry.bindTileEntitySpecialRenderer(RecipePanelTile.class, new RecipePanelRenderer());
        MinecraftForgeClient.registerItemRenderer(ModItems.recipePanel, new RecipePanelItemRenderer());
        GuiContainerManager.addInputHandler(new PanelInputHandler());
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager manager) {
                    PanelFboManager.INSTANCE.reload();
                }
            });
    }

    @Override
    public void openPanelRecipe(NBTTagCompound snapshot, boolean usage) {
        PanelRecipeOpener.open(snapshot, usage);
    }

    @Override
    public void openPanelConfig(int x, int y, int z) {
        TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(x, y, z);
        if (te instanceof RecipePanelTile) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiRecipePanelConfig((RecipePanelTile) te));
        }
    }
}
