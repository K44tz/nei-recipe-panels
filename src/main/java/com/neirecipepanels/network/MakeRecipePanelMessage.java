package com.neirecipepanels.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import com.neirecipepanels.Config;
import com.neirecipepanels.ModItems;
import com.neirecipepanels.RecipeSnapshot;
import com.neirecipepanels.item.ItemRecipePanel;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: "I clicked the Panel button on this recipe." The server spends one
 * Recipe Blueprint and hands back an imprinted {@link ItemRecipePanel}.
 */
public class MakeRecipePanelMessage implements IMessage {

    private NBTTagCompound snapshot;

    public MakeRecipePanelMessage() {}

    public MakeRecipePanelMessage(NBTTagCompound snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        snapshot = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, snapshot);
    }

    public static class Handler implements IMessageHandler<MakeRecipePanelMessage, IMessage> {

        @Override
        public IMessage onMessage(MakeRecipePanelMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            NBTTagCompound raw = message.snapshot;
            ServerTasks.submit(() -> grant(player, raw));
            return null;
        }

        private static void grant(EntityPlayerMP player, NBTTagCompound raw) {
            if (player == null || player.isDead || raw == null) {
                return;
            }

            Config.PanelMode mode = Config.panelMode;
            if (mode == Config.PanelMode.DISABLED) {
                return;
            }
            boolean creative = player.capabilities.isCreativeMode;
            if (mode == Config.PanelMode.CREATIVE_ONLY && !creative) {
                deny(player);
                return;
            }
            if (mode == Config.PanelMode.OP_ONLY && !isOp(player)) {
                deny(player);
                return;
            }

            NBTTagCompound clean = RecipeSnapshot
                .sanitize(raw, Config.maxIngredients, Config.maxAlternatives, Config.maxSnapshotBytes);
            if (clean == null) {
                player.addChatMessage(new ChatComponentTranslation("neirecipepanels.chat.badSnapshot"));
                return;
            }

            boolean spend = !creative || Config.consumeInCreative;
            if (spend && !player.inventory.consumeInventoryItem(ModItems.recipeBlueprint)) {
                player.addChatMessage(new ChatComponentTranslation("neirecipepanels.chat.needBlueprint"));
                return;
            }

            ItemStack panel = ItemRecipePanel.withSnapshot(clean);
            if (!player.inventory.addItemStackToInventory(panel)) {
                player.entityDropItem(panel, 0.5F);
            }
            player.inventoryContainer.detectAndSendChanges();
        }

        private static void deny(EntityPlayerMP player) {
            player.addChatMessage(new ChatComponentTranslation("neirecipepanels.chat.notAllowed"));
        }

        private static boolean isOp(EntityPlayerMP player) {
            MinecraftServer server = MinecraftServer.getServer();
            return server != null && server.getConfigurationManager()
                .func_152596_g(player.getGameProfile());
        }
    }
}
