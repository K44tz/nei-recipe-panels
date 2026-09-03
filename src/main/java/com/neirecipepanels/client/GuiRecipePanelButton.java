package com.neirecipepanels.client;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.neirecipepanels.ModItems;
import com.neirecipepanels.NeiRecipePanels;
import com.neirecipepanels.RecipeSnapshot;
import com.neirecipepanels.network.MakeRecipePanelMessage;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.RecipeHandlerRef;

/** Per-recipe button in NEI's recipe screen; sits with the favourite / overlay buttons and imprints a panel. */
public class GuiRecipePanelButton extends GuiRecipeButton {

    private static final int BUTTON_ID = 44251001;

    public GuiRecipePanelButton(RecipeHandlerRef handlerRef) {
        super(handlerRef, 0, 0, BUTTON_ID, "P");
    }

    @Override
    public void update() {
        super.update();
        this.enabled = hasBlueprint(Minecraft.getMinecraft().thePlayer);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (!this.enabled || !contains(mouseX, mouseY)) {
            return;
        }
        NBTTagCompound snapshot = RecipeSnapshot.capture(handlerRef.handler, handlerRef.recipeIndex)
            .writeToNBT();
        NeiRecipePanels.NETWORK.sendToServer(new MakeRecipePanelMessage(snapshot));
    }

    @Override
    public List<String> handleTooltip(List<String> tooltip) {
        tooltip.add(this.enabled ? "Imprint a recipe panel" : "Needs a Recipe Blueprint");
        return tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyCode) {}

    @Override
    public void drawItemOverlay() {}

    private static boolean hasBlueprint(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == ModItems.recipeBlueprint) {
                return true;
            }
        }
        return false;
    }
}
