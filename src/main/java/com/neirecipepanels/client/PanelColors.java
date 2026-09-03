package com.neirecipepanels.client;

import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.common.Loader;
import gregtech.api.gui.GUIColorOverride;

/**
 * Rendered-panel text colours, read from the active resource pack through GregTech's
 * {@link GUIColorOverride} (keys as documented in GT5U's ResourcePacks guide). Returns the
 * given fallback when GregTech is not installed. Re-read every call so a resource-pack change
 * (which invalidates GregTech's shared cache) takes effect without a restart.
 */
final class PanelColors {

    private static final boolean GREGTECH = Loader.isModLoaded("gregtech");
    private static final ResourceLocation NEI_BACKGROUND = new ResourceLocation(
        "gregtech",
        "textures/gui/background/nei_single_recipe.png");

    private PanelColors() {}

    static int text(String key, int fallback) {
        return GREGTECH ? GUIColorOverride.get(NEI_BACKGROUND)
            .getTextColorOrDefault(key, fallback) : fallback;
    }
}
