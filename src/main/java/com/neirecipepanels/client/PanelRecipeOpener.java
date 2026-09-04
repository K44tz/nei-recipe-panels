package com.neirecipepanels.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.neirecipepanels.RecipeSnapshot;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.Recipe;

/** Opens a panel's recipe (or its usage) in NEI, navigating to the exact recipe via the stored RecipeId. */
public final class PanelRecipeOpener {

    private PanelRecipeOpener() {}

    public static void open(NBTTagCompound snapshot, boolean usage) {
        if (snapshot == null) return;
        Recipe.RecipeId id = RecipeSnapshot.parseRecipeId(RecipeSnapshot.peekRecipeId(snapshot));
        if (id == null) return;

        ItemStack result = id.getResult();
        if (result == null) return;

        if (usage) {
            GuiUsageRecipe.openRecipeGui("item", result);
            return;
        }

        if (GuiCraftingRecipe.openRecipeGui("recipeId", result, id)) {
            return;
        }

        // no direct match: open every recipe for the result, then jump to the stored one
        GuiRecipe<?> gui = GuiCraftingRecipe.createRecipeGui("item", true, result);
        if (gui == null) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(gui);
        gui.openTargetRecipe(id);
    }
}
