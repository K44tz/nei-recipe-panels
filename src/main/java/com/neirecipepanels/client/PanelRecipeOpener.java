package com.neirecipepanels.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonParser;
import com.neirecipepanels.NeiRecipePanels;
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
        Recipe.RecipeId id = recipeId(RecipeSnapshot.peekRecipeId(snapshot));

        ItemStack result = RecipeSnapshot.peekResult(snapshot);
        if (result == null && id != null) {
            result = id.getResult(); // GT machine handlers leave getResultStack null; the id has it
        }
        if (result == null) return;

        if (usage) {
            GuiUsageRecipe.openRecipeGui("item", result);
            return;
        }

        // direct form: navigates by RecipeId within the matching handler
        if (id != null && GuiCraftingRecipe.openRecipeGui("recipeId", result, id)) {
            return;
        }

        // fall back: open every recipe for the result, then jump to the encoded one
        GuiRecipe<?> gui = GuiCraftingRecipe.createRecipeGui("item", true, result);
        if (gui == null) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(gui);
        if (id != null) {
            gui.openTargetRecipe(id);
        }
    }

    private static Recipe.RecipeId recipeId(String json) {
        try {
            if (json != null && !json.isEmpty()) {
                return Recipe.RecipeId.of(
                    new JsonParser().parse(json)
                        .getAsJsonObject());
            }
        } catch (Throwable t) {
            NeiRecipePanels.LOG.warn("Recipe panel: could not parse recipe id", t);
        }
        return null;
    }
}
