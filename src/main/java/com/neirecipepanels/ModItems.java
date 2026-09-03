package com.neirecipepanels;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.neirecipepanels.item.ItemRecipeBlueprint;
import com.neirecipepanels.item.ItemRecipePanel;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static Item recipeBlueprint;
    public static Item recipePanel;

    private ModItems() {}

    public static void register() {
        recipeBlueprint = new ItemRecipeBlueprint();
        GameRegistry.registerItem(recipeBlueprint, "recipe_blueprint");

        recipePanel = new ItemRecipePanel();
        GameRegistry.registerItem(recipePanel, "recipe_panel");
    }

    public static void registerRecipes() {
        if (Config.registerBlueprintRecipe) {
            GameRegistry.addShapelessRecipe(new ItemStack(recipeBlueprint), Items.paper, Items.paper, Items.redstone);
            // Wipe an imprinted panel back to a blank blueprint (NBT is ignored by shapeless matching).
            GameRegistry.addShapelessRecipe(new ItemStack(recipeBlueprint), new ItemStack(recipePanel));
        }
    }
}
