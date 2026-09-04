package com.neirecipepanels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.StackInfo;

/**
 * A recipe's display data, rebuilt from the live NEI handler on every resolve: where each slot
 * sits, which of its cycling alternatives to show there (per the panel's stored
 * {@link RecipeSnapshot}), its chance, and whether it's an NC ("not consumed") input. Never
 * persisted - recomputing it from the handler is what lets a placed panel always show the current
 * recipe instead of a copy frozen at imprint time.
 */
public class ResolvedRecipe {

    public static class Slot {

        public final int relx;
        public final int rely;
        public final ItemStack stack;
        public final int chance;
        /** Input slot NEI badges as "NC": a zero-amount stack that the recipe does not consume. */
        public final boolean notConsumed;

        Slot(int relx, int rely, ItemStack stack, int chance, boolean notConsumed) {
            this.relx = relx;
            this.rely = rely;
            this.stack = stack;
            this.chance = chance;
            this.notConsumed = notConsumed;
        }
    }

    public final String recipeName;
    public final ItemStack result;
    public final int resultX;
    public final int resultY;
    /** Result item for labelling / the item icon when {@link #result} has no pixel position (GT machines). */
    public final ItemStack displayResult;
    public final List<Slot> ingredients;
    public final List<Slot> others;

    private ResolvedRecipe(String recipeName, ItemStack result, int resultX, int resultY, ItemStack displayResult,
        List<Slot> ingredients, List<Slot> others) {
        this.recipeName = recipeName;
        this.result = result;
        this.resultX = resultX;
        this.resultY = resultY;
        this.displayResult = displayResult;
        this.ingredients = ingredients;
        this.others = others;
    }

    public static ResolvedRecipe of(IRecipeHandler handler, int recipeIndex, RecipeSnapshot snapshot,
        Recipe.RecipeId recipeId) {
        PositionedStack rawResult = handler.getResultStack(recipeIndex);
        ItemStack result = rawResult != null ? pick(rawResult, snapshot.resultPermutation) : null;
        return new ResolvedRecipe(
            // NEI's GuiRecipe calls this every frame; GT's handler lazily reads its themed NEI
            // text colour here, so this also primes it before drawForeground draws the description.
            orEmpty(handler.getRecipeName()),
            result,
            rawResult != null ? rawResult.relx : 0,
            rawResult != null ? rawResult.rely : 0,
            result == null ? idResult(recipeId) : null,
            buildSlots(handler.getIngredientStacks(recipeIndex), snapshot.ingredientPermutations, true),
            buildSlots(handler.getOtherStacks(recipeIndex), snapshot.otherPermutations, false));
    }

    private static List<Slot> buildSlots(List<PositionedStack> raw, int[] permutations, boolean input) {
        List<Slot> slots = new ArrayList<>();
        List<PositionedStack> list = orEmpty(raw);
        for (int i = 0; i < list.size(); i++) {
            PositionedStack ps = list.get(i);
            int index = permutations != null && i < permutations.length ? permutations[i] : 0;
            ItemStack primary = pick(ps, index);
            boolean notConsumed = input && primary != null && StackInfo.getAmount(primary) == 0;
            slots.add(new Slot(ps.relx, ps.rely, primary, ps.getChance(), notConsumed));
        }
        return slots;
    }

    /** The stored permutation, clamped in case the live recipe now has fewer alternatives. */
    private static ItemStack pick(PositionedStack ps, int index) {
        if (ps.items != null && ps.items.length > 0) {
            return ps.items[MathHelper.clamp_int(index, 0, ps.items.length - 1)];
        }
        return ps.item;
    }

    /** Handlers with no result stack (GT machines) still carry the result on the RecipeId. */
    private static ItemStack idResult(Recipe.RecipeId recipeId) {
        try {
            ItemStack result = recipeId != null ? recipeId.getResult() : null;
            return result != null ? result.copy() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : Collections.<T>emptyList();
    }
}
