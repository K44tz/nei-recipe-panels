package com.neirecipepanels.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.neirecipepanels.ModBlocks;
import com.neirecipepanels.ModItems;
import com.neirecipepanels.NeiRecipePanels;
import com.neirecipepanels.RecipeSnapshot;
import com.neirecipepanels.block.RecipePanelTile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Carries a {@link RecipeSnapshot} in NBT; placed on a block face it becomes a recipe panel block. */
public class ItemRecipePanel extends Item {

    public static final String TAG_SNAPSHOT = "snapshot";
    public static final String TAG_SETTINGS = "cfg";

    public ItemRecipePanel() {
        setUnlocalizedName("recipePanel");
        setMaxStackSize(16);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(NeiRecipePanels.MODID + ":recipe_blueprint");
    }

    public static ItemStack withSnapshot(NBTTagCompound snapshot) {
        return withData(snapshot, null);
    }

    public static ItemStack withData(NBTTagCompound snapshot, NBTTagCompound settings) {
        ItemStack stack = new ItemStack(ModItems.recipePanel);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(TAG_SNAPSHOT, snapshot);
        if (settings != null && !settings.hasNoTags()) {
            tag.setTag(TAG_SETTINGS, settings);
        }
        stack.setTagCompound(tag);
        return stack;
    }

    public static NBTTagCompound getSnapshot(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_SNAPSHOT) ? tag.getCompoundTag(TAG_SNAPSHOT) : null;
    }

    public static NBTTagCompound getSettings(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_SETTINGS) ? tag.getCompoundTag(TAG_SETTINGS) : null;
    }

    /** The imprinted recipe's result, or null on a blank panel. */
    public static ItemStack getResult(ItemStack stack) {
        NBTTagCompound snapshot = getSnapshot(stack);
        return snapshot == null ? null : RecipeSnapshot.peekResult(snapshot);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        NBTTagCompound snapshot = getSnapshot(stack);
        if (snapshot == null) {
            return false;
        }

        ForgeDirection dir = ForgeDirection.getOrientation(side);
        int px = x + dir.offsetX;
        int py = y + dir.offsetY;
        int pz = z + dir.offsetZ;

        if (!player.canPlayerEdit(px, py, pz, side, stack) || !world.isSideSolid(x, y, z, dir)) {
            return false;
        }
        Block existing = world.getBlock(px, py, pz);
        if (!existing.isAir(world, px, py, pz) && !existing.isReplaceable(world, px, py, pz)) {
            return false;
        }

        if (!world.isRemote) {
            world.setBlock(px, py, pz, ModBlocks.recipePanel, side, 3);
            TileEntity te = world.getTileEntity(px, py, pz);
            if (te instanceof RecipePanelTile) {
                ((RecipePanelTile) te).setSnapshot((NBTTagCompound) snapshot.copy());
                NBTTagCompound settings = getSettings(stack);
                if (settings != null) {
                    ((RecipePanelTile) te).setSettings((NBTTagCompound) settings.copy());
                }
            }
            if (!player.capabilities.isCreativeMode) {
                stack.stackSize--;
            }
        }
        return true;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (getSnapshot(stack) != null) {
            return StatCollector.translateToLocal("item.recipePanel.encoded.name");
        }
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        if (getSnapshot(stack) == null) {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.neirecipepanels.panel.blank"));
            return;
        }
        ItemStack result = getResult(stack);
        if (result != null) {
            tooltip.add(
                EnumChatFormatting.GRAY + StatCollector
                    .translateToLocalFormatted("tooltip.neirecipepanels.panel.recipe", result.getDisplayName()));
        }
        tooltip
            .add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.neirecipepanels.panel.hang"));
    }
}
