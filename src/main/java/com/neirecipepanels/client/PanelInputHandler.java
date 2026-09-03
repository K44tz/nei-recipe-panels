package com.neirecipepanels.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Keyboard;

import com.neirecipepanels.ModItems;
import com.neirecipepanels.item.ItemRecipePanel;

import codechicken.nei.KeyManager;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;

/** Press the NEI recipe / usage key over a Recipe Panel item to open the encoded recipe. */
public class PanelInputHandler implements IContainerInputHandler {

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        boolean recipe = keyCode == keyCode("recipe.recipe", Keyboard.KEY_R);
        boolean usage = keyCode == keyCode("recipe.usage", Keyboard.KEY_U);
        if (!recipe && !usage) return false;

        ItemStack hovered = GuiContainerManager.getStackMouseOver(gui);
        if (hovered == null || hovered.getItem() != ModItems.recipePanel) return false;
        NBTTagCompound snapshot = ItemRecipePanel.getSnapshot(hovered);
        if (snapshot == null) return false;

        PanelRecipeOpener.open(snapshot, usage);
        return true;
    }

    private static int keyCode(String neiBinding, int fallback) {
        try {
            int code = KeyManager.getKeyCode(neiBinding);
            if (code != Keyboard.KEY_NONE) return code;
        } catch (Throwable ignored) {
            // binding id not present on this NEI build
        }
        return fallback;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

    @Override
    public boolean mouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {
        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mouseX, int mouseY, int button, long heldTime) {}
}
