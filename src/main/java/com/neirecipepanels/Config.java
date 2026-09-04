package com.neirecipepanels;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public enum PanelMode {
        DISABLED,
        CREATIVE_ONLY,
        OP_ONLY,
        EVERYONE
    }

    public static boolean registerBlueprintRecipe = true;

    public static PanelMode panelMode = PanelMode.EVERYONE;
    public static boolean consumeInCreative = false;
    public static int maxIngredients = 64;
    public static int maxSnapshotBytes = 16384;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);
        configuration.load();

        registerBlueprintRecipe = configuration.getBoolean(
            "registerBlueprintRecipe",
            "recipe",
            true,
            "Register the built-in crafting recipe for the Recipe Blueprint. Disable to define your own via a tweaker.");

        panelMode = parseMode(
            configuration.getString(
                "panelMode",
                "server",
                PanelMode.EVERYONE.name(),
                "Who may imprint recipe panels.",
                names(PanelMode.values())));
        consumeInCreative = configuration.getBoolean(
            "consumeInCreative",
            "server",
            false,
            "Also spend a Recipe Blueprint when a creative-mode player imprints.");
        maxIngredients = configuration.getInt(
            "maxIngredients",
            "server",
            64,
            1,
            256,
            "Reject an imprint whose recipe has more ingredient / byproduct slots than this.");
        maxSnapshotBytes = configuration.getInt(
            "maxSnapshotBytes",
            "server",
            16384,
            512,
            262144,
            "Reject an imprint whose (gzipped) recipe data exceeds this many bytes.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static PanelMode parseMode(String value) {
        try {
            return PanelMode.valueOf(
                value.trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PanelMode.EVERYONE;
        }
    }

    private static String[] names(PanelMode[] modes) {
        String[] out = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            out[i] = modes[i].name();
        }
        return out;
    }
}
