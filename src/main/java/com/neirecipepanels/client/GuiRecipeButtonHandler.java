package com.neirecipepanels.client;

import codechicken.nei.recipe.GuiRecipeButton;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Adds the "imprint panel" button to NEI's recipe screen, in the column above the favourite / overlay buttons. */
public class GuiRecipeButtonHandler {

    @SubscribeEvent
    public void onUpdateRecipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        int x = Math.min(166, event.recipeWidget.w) - 12;
        int y = event.recipeWidget.h - 18 - 13 * event.buttonList.size();
        event.buttonList.add(new GuiRecipePanelButton(event.recipeWidget.getRecipeHandlerRef(), x, y));
    }
}
