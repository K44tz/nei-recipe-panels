package com.neirecipepanels;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;

import com.neirecipepanels.block.RecipePanelTile;
import com.neirecipepanels.client.GuiRecipeButtonHandler;
import com.neirecipepanels.client.GuiRecipePanelConfig;
import com.neirecipepanels.client.PanelFboManager;
import com.neirecipepanels.client.PanelInputHandler;
import com.neirecipepanels.client.PanelRecipeOpener;
import com.neirecipepanels.client.RecipePanelRenderer;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new GuiRecipeButtonHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(PanelFboManager.INSTANCE);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRegistry.bindTileEntitySpecialRenderer(RecipePanelTile.class, new RecipePanelRenderer());
        GuiContainerManager.addInputHandler(new PanelInputHandler());
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
