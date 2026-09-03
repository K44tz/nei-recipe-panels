package com.neirecipepanels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;

/**
 * A serialisable copy of one NEI recipe: the recipe name, NEI's RecipeId (as JSON), the result
 * with its pixel position, and every ingredient / byproduct slot (pixel position, chance, the
 * captured stack and its cycling alternatives). The placed panel resolves the live handler from
 * the RecipeId and only uses this for the frozen stacks and the tooltip.
 */
public class RecipeSnapshot {

    private static final int VERSION = 2;
    private static final int MAX_STRING = 12000;

    private static final String TAG_VERSION = "ver";
    private static final String TAG_NAME = "name";
    private static final String TAG_RECIPE_ID = "recipeId";
    private static final String TAG_RESULT = "result";
    private static final String TAG_RESULT_X = "rx";
    private static final String TAG_RESULT_Y = "ry";
    private static final String TAG_DISPLAY_RESULT = "dresult";
    private static final String TAG_INGREDIENTS = "ingredients";
    private static final String TAG_OTHERS = "others";
    private static final String TAG_PX = "x";
    private static final String TAG_PY = "y";
    private static final String TAG_STACK = "s";
    private static final String TAG_ALTS = "alts";
    private static final String TAG_CHANCE = "chance";

    public static class Slot {

        public final int relx;
        public final int rely;
        public final ItemStack stack;
        public final List<ItemStack> alternatives;
        public final int chance;

        Slot(int relx, int rely, ItemStack stack, List<ItemStack> alternatives, int chance) {
            this.relx = relx;
            this.rely = rely;
            this.stack = stack;
            this.alternatives = alternatives;
            this.chance = chance;
        }
    }

    public final String recipeName;
    public final String recipeIdJson;
    public final ItemStack result;
    public final int resultX;
    public final int resultY;
    /** Result item for labelling / the item icon when {@link #result} has no pixel position (GT machines). */
    public final ItemStack displayResult;
    public final List<Slot> ingredients;
    public final List<Slot> others;

    private RecipeSnapshot(String recipeName, String recipeIdJson, ItemStack result, int resultX, int resultY,
        ItemStack displayResult, List<Slot> ingredients, List<Slot> others) {
        this.recipeName = recipeName;
        this.recipeIdJson = recipeIdJson;
        this.result = result;
        this.resultX = resultX;
        this.resultY = resultY;
        this.displayResult = displayResult;
        this.ingredients = ingredients;
        this.others = others;
    }

    public static RecipeSnapshot capture(IRecipeHandler handler, int recipeIndex) {
        Recipe.RecipeId recipeId = recipeId(handler, recipeIndex);
        PositionedStack rawResult = handler.getResultStack(recipeIndex);
        ItemStack result = rawResult != null ? primaryStack(rawResult) : null;
        return new RecipeSnapshot(
            orEmpty(handler.getRecipeName()),
            recipeIdJson(recipeId),
            result,
            rawResult != null ? rawResult.relx : 0,
            rawResult != null ? rawResult.rely : 0,
            result == null ? idResult(recipeId) : null,
            captureSlots(handler.getIngredientStacks(recipeIndex)),
            captureSlots(handler.getOtherStacks(recipeIndex)));
    }

    private static List<Slot> captureSlots(List<PositionedStack> raw) {
        List<Slot> slots = new ArrayList<>();
        for (PositionedStack ps : orEmpty(raw)) {
            slots.add(new Slot(ps.relx, ps.rely, primaryStack(ps), permutations(ps), ps.getChance()));
        }
        return slots;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_VERSION, VERSION);
        tag.setString(TAG_NAME, recipeName);
        tag.setString(TAG_RECIPE_ID, recipeIdJson);
        if (result != null) {
            tag.setTag(TAG_RESULT, result.writeToNBT(new NBTTagCompound()));
            tag.setInteger(TAG_RESULT_X, resultX);
            tag.setInteger(TAG_RESULT_Y, resultY);
        }
        if (displayResult != null) {
            tag.setTag(TAG_DISPLAY_RESULT, displayResult.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag(TAG_INGREDIENTS, writeSlots(ingredients));
        tag.setTag(TAG_OTHERS, writeSlots(others));
        return tag;
    }

    public static RecipeSnapshot readFromNBT(NBTTagCompound tag) {
        ItemStack result = tag.hasKey(TAG_RESULT) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_RESULT))
            : null;
        ItemStack displayResult = tag.hasKey(TAG_DISPLAY_RESULT)
            ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_DISPLAY_RESULT))
            : null;
        return new RecipeSnapshot(
            tag.getString(TAG_NAME),
            tag.getString(TAG_RECIPE_ID),
            result,
            tag.getInteger(TAG_RESULT_X),
            tag.getInteger(TAG_RESULT_Y),
            displayResult,
            readSlots(tag.getTagList(TAG_INGREDIENTS, Constants.NBT.TAG_COMPOUND)),
            readSlots(tag.getTagList(TAG_OTHERS, Constants.NBT.TAG_COMPOUND)));
    }

    /**
     * Server-side bounds check on a client-supplied snapshot. Rejects (returns null) anything over the
     * size caps; otherwise returns a canonical copy with strings truncated, alternatives capped and
     * every stack size forced to 1 (the snapshot is cosmetic, it must never carry a real quantity).
     */
    public static NBTTagCompound sanitize(NBTTagCompound raw, int maxSlots, int maxAlts, int maxBytes) {
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
        if (s.ingredients.size() > maxSlots || s.others.size() > maxSlots) {
            return null;
        }
        RecipeSnapshot clean = new RecipeSnapshot(
            trim(s.recipeName),
            trim(s.recipeIdJson),
            single(s.result),
            s.resultX,
            s.resultY,
            single(s.displayResult),
            clampSlots(s.ingredients, maxAlts),
            clampSlots(s.others, maxAlts));
        return clean.writeToNBT();
    }

    private static List<Slot> clampSlots(List<Slot> slots, int maxAlts) {
        List<Slot> out = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            List<ItemStack> alts = new ArrayList<>();
            for (ItemStack alt : slot.alternatives) {
                if (alts.size() >= maxAlts) {
                    break;
                }
                ItemStack a = single(alt);
                if (a != null) {
                    alts.add(a);
                }
            }
            out.add(new Slot(slot.relx, slot.rely, single(slot.stack), alts, slot.chance));
        }
        return out;
    }

    private static ItemStack single(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private static String trim(String s) {
        return s != null && s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s;
    }

    public static String peekRecipeId(NBTTagCompound tag) {
        return tag.getString(TAG_RECIPE_ID);
    }

    public static ItemStack peekResult(NBTTagCompound tag) {
        NBTTagCompound stack = tag.hasKey(TAG_RESULT) ? tag.getCompoundTag(TAG_RESULT)
            : tag.hasKey(TAG_DISPLAY_RESULT) ? tag.getCompoundTag(TAG_DISPLAY_RESULT) : null;
        return stack != null ? ItemStack.loadItemStackFromNBT(stack) : null;
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

    private static String recipeIdJson(Recipe.RecipeId recipeId) {
        try {
            return recipeId != null ? recipeId.toJsonObject()
                .toString() : "";
        } catch (Throwable t) {
            return "";
        }
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

    private static NBTTagList writeSlots(List<Slot> slots) {
        NBTTagList list = new NBTTagList();
        for (Slot slot : slots) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger(TAG_PX, slot.relx);
            c.setInteger(TAG_PY, slot.rely);
            c.setInteger(TAG_CHANCE, slot.chance);
            if (slot.stack != null) {
                c.setTag(TAG_STACK, slot.stack.writeToNBT(new NBTTagCompound()));
            }
            if (slot.alternatives.size() > 1) {
                NBTTagList alts = new NBTTagList();
                for (ItemStack alt : slot.alternatives) {
                    if (alt != null) {
                        alts.appendTag(alt.writeToNBT(new NBTTagCompound()));
                    }
                }
                c.setTag(TAG_ALTS, alts);
            }
            list.appendTag(c);
        }
        return list;
    }

    private static List<Slot> readSlots(NBTTagList list) {
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            ItemStack stack = c.hasKey(TAG_STACK) ? ItemStack.loadItemStackFromNBT(c.getCompoundTag(TAG_STACK)) : null;
            List<ItemStack> alts = new ArrayList<>();
            if (c.hasKey(TAG_ALTS)) {
                NBTTagList altList = c.getTagList(TAG_ALTS, Constants.NBT.TAG_COMPOUND);
                for (int j = 0; j < altList.tagCount(); j++) {
                    ItemStack alt = ItemStack.loadItemStackFromNBT(altList.getCompoundTagAt(j));
                    if (alt != null) {
                        alts.add(alt);
                    }
                }
            } else if (stack != null) {
                alts.add(stack);
            }
            out.add(new Slot(c.getInteger(TAG_PX), c.getInteger(TAG_PY), stack, alts, c.getInteger(TAG_CHANCE)));
        }
        return out;
    }

    /**
     * The canonical stack for a slot. NEI rotates {@link PositionedStack#item} through the
     * cycling permutations on a timer, so it depends on when the capture happened; the
     * first entry of {@link PositionedStack#items} is stable across captures.
     */
    private static ItemStack primaryStack(PositionedStack ps) {
        if (ps.items != null && ps.items.length > 0) {
            return ps.items[0];
        }
        return ps.item;
    }

    private static List<ItemStack> permutations(PositionedStack ps) {
        return ps.items != null ? Arrays.asList(ps.items) : Collections.<ItemStack>emptyList();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : Collections.<T>emptyList();
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }
}
