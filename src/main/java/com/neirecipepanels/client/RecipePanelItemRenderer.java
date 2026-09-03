package com.neirecipepanels.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.neirecipepanels.NeiRecipePanels;
import com.neirecipepanels.item.ItemRecipePanel;

/** In inventory slots, draws an imprinted panel as its recipe's result item while Shift is held. */
public class RecipePanelItemRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY && GuiScreen.isShiftKeyDown()
            && ItemRecipePanel.getResult(item) != null;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        ItemStack result = ItemRecipePanel.getResult(item);
        if (result == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        try {
            RenderItem.getInstance()
                .renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), result, 0, 0);
        } catch (Throwable t) {
            NeiRecipePanels.LOG.warn("Recipe panel: could not render result icon", t);
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
