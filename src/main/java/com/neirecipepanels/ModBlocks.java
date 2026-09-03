package com.neirecipepanels;

import net.minecraft.block.Block;

import com.neirecipepanels.block.RecipePanelBlock;
import com.neirecipepanels.block.RecipePanelTile;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    public static Block recipePanel;

    private ModBlocks() {}

    public static void register() {
        recipePanel = new RecipePanelBlock();
        GameRegistry.registerBlock(recipePanel, null, "recipe_panel");
        GameRegistry.registerTileEntity(RecipePanelTile.class, NeiRecipePanels.MODID + ":recipe_panel");
    }
}
