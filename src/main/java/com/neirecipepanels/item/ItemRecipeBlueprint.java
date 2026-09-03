package com.neirecipepanels.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Blank consumable. The NEI recipe-screen button spends one to imprint a {@link ItemRecipePanel}. */
public class ItemRecipeBlueprint extends Item {

    public ItemRecipeBlueprint() {
        setUnlocalizedName("recipeBlueprint");
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName("paper");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.neirecipepanels.blueprint.1"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.neirecipepanels.blueprint.2"));
    }
}
