package com.neirecipepanels.client;

import codechicken.nei.recipe.GuiRecipeButton;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Injects a per-recipe "imprint panel" button into NEI's recipe screen. */
public class GuiRecipeButtonHandler {

    @SubscribeEvent
    public void onUpdateRecipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        event.buttonList.add(new GuiRecipePanelButton(event.recipeWidget.getRecipeHandlerRef()));
    }
}
