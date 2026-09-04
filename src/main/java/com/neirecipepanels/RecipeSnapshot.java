package com.neirecipepanels;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonParser;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;

/**
 * The persisted NBT payload of an imprinted recipe panel: the recipe's NEI RecipeId, and, per
 * slot, which cycling permutation was showing at imprint time. Nothing else about the recipe is
 * stored - a placed panel re-resolves the live handler and rebuilds the actual items, positions,
 * chances and NC flags fresh every time (see {@link ResolvedRecipe}), so every viewer always sees
 * the current recipe rather than a copy frozen at imprint time.
 */
public class RecipeSnapshot {

    private static final int VERSION = 3;
    private static final int MAX_STRING = 12000;

    private static final String TAG_VERSION = "ver";
    private static final String TAG_RECIPE_ID = "recipeId";
    private static final String TAG_RESULT_PERM = "resultPerm";
    private static final String TAG_INGREDIENT_PERMS = "inPerms";
    private static final String TAG_OTHER_PERMS = "otherPerms";

    public final String recipeIdJson;
    public final int resultPermutation;
    public final int[] ingredientPermutations;
    public final int[] otherPermutations;

    private RecipeSnapshot(String recipeIdJson, int resultPermutation, int[] ingredientPermutations,
        int[] otherPermutations) {
        this.recipeIdJson = recipeIdJson;
        this.resultPermutation = resultPermutation;
        this.ingredientPermutations = ingredientPermutations;
        this.otherPermutations = otherPermutations;
    }

    public static RecipeSnapshot capture(IRecipeHandler handler, int recipeIndex) {
        Recipe.RecipeId recipeId = recipeId(handler, recipeIndex);
        return new RecipeSnapshot(
            recipeIdJson(recipeId),
            permutationIndex(handler.getResultStack(recipeIndex)),
            permutationIndices(handler.getIngredientStacks(recipeIndex)),
            permutationIndices(handler.getOtherStacks(recipeIndex)));
    }

    private static int[] permutationIndices(List<PositionedStack> raw) {
        List<PositionedStack> list = orEmpty(raw);
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = permutationIndex(list.get(i));
        }
        return out;
    }

    /** Which of a slot's cycling alternatives was showing. */
    private static int permutationIndex(PositionedStack ps) {
        if (ps == null || ps.item == null) {
            return 0;
        }
        // item is a copy of items[index], not the same reference - NEI's own lookup compares by type.
        int index = ps.getPermutationIndex(ps.item);
        return index >= 0 ? index : 0;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_VERSION, VERSION);
        tag.setString(TAG_RECIPE_ID, recipeIdJson);
        tag.setInteger(TAG_RESULT_PERM, resultPermutation);
        tag.setIntArray(TAG_INGREDIENT_PERMS, ingredientPermutations);
        tag.setIntArray(TAG_OTHER_PERMS, otherPermutations);
        return tag;
    }

    public static RecipeSnapshot readFromNBT(NBTTagCompound tag) {
        return new RecipeSnapshot(
            tag.getString(TAG_RECIPE_ID),
            tag.getInteger(TAG_RESULT_PERM),
            tag.getIntArray(TAG_INGREDIENT_PERMS),
            tag.getIntArray(TAG_OTHER_PERMS));
    }

    /**
     * Server-side bounds check on a client-supplied snapshot. Rejects (returns null) anything over
     * the size caps; otherwise returns a canonical, size-trimmed copy.
     */
    public static NBTTagCompound sanitize(NBTTagCompound raw, int maxSlots, int maxBytes) {
        if (raw == null) {
            return null;
        }
        int size = gzippedSize(raw);
        if (size < 0 || size > maxBytes) {
            return null;
        }
        RecipeSnapshot s;
        try {
            s = readFromNBT(raw);
        } catch (RuntimeException e) {
            return null;
        }
        if (s.ingredientPermutations.length > maxSlots || s.otherPermutations.length > maxSlots) {
            return null;
        }
        RecipeSnapshot clean = new RecipeSnapshot(
            trim(s.recipeIdJson),
            s.resultPermutation,
            s.ingredientPermutations,
            s.otherPermutations);
        return clean.writeToNBT();
    }

    private static String trim(String s) {
        return s != null && s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s;
    }

    public static String peekRecipeId(NBTTagCompound tag) {
        return tag.getString(TAG_RECIPE_ID);
    }

    // Tooltip rendering and the inventory icon-swap resolve the live handler every frame; cache by
    // the snapshot tag's identity so hovering/holding shift over a panel isn't a per-frame NEI lookup.
    private static final Map<NBTTagCompound, ItemStack> DISPLAY_RESULT_CACHE = new WeakHashMap<>();

    /** A representative result item for tooltip / inventory-icon use, resolved from the live handler. */
    public static ItemStack resolveDisplayResult(NBTTagCompound tag) {
        if (DISPLAY_RESULT_CACHE.containsKey(tag)) {
            return DISPLAY_RESULT_CACHE.get(tag);
        }
        ItemStack result = computeDisplayResult(tag);
        DISPLAY_RESULT_CACHE.put(tag, result);
        return result;
    }

    private static ItemStack computeDisplayResult(NBTTagCompound tag) {
        RecipeSnapshot snap = readFromNBT(tag);
        Recipe.RecipeId recipeId = parseRecipeId(snap.recipeIdJson);
        if (recipeId == null) {
            return null;
        }
        try {
            RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
            if (ref != null && ref.handler != null) {
                ResolvedRecipe resolved = ResolvedRecipe.of(ref.handler, ref.recipeIndex, snap, recipeId);
                return resolved.result != null ? resolved.result : resolved.displayResult;
            }
        } catch (Throwable t) {
            // fall through to the id's own (quantity-less) result
        }
        return recipeId.getResult();
    }

    /** Size of the gzipped NBT, or -1 if it could not be written. */
    public static int gzippedSize(NBTTagCompound tag) {
        try {
            return CompressedStreamTools.compress(tag).length;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Recipe.RecipeId recipeId(IRecipeHandler handler, int recipeIndex) {
        try {
            return Recipe.RecipeId.of(handler, recipeIndex);
        } catch (Throwable t) {
            // Not every handler can produce a RecipeId; re-open and the icon fall back to the result item.
            return null;
        }
    }

    public static Recipe.RecipeId parseRecipeId(String json) {
        try {
            if (json != null && !json.isEmpty()) {
                return Recipe.RecipeId.of(
                    new JsonParser().parse(json)
                        .getAsJsonObject());
            }
        } catch (Throwable t) {
            // malformed / from an incompatible NEI version
        }
        return null;
    }

    private static String recipeIdJson(Recipe.RecipeId recipeId) {
        try {
            return recipeId != null ? recipeId.toJsonObject()
                .toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : Collections.<T>emptyList();
    }
}
