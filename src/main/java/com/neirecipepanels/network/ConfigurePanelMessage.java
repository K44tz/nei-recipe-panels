package com.neirecipepanels.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.neirecipepanels.ModBlocks;
import com.neirecipepanels.PanelSettings;
import com.neirecipepanels.block.RecipePanelTile;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> server: apply the display settings edited on the panel config screen. */
public class ConfigurePanelMessage implements IMessage {

    private int x;
    private int y;
    private int z;
    private String name;
    private boolean transparent;

    public ConfigurePanelMessage() {}

    public ConfigurePanelMessage(int x, int y, int z, String name, boolean transparent) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.transparent = transparent;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
        transparent = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        buf.writeBoolean(transparent);
    }

    public static class Handler implements IMessageHandler<ConfigurePanelMessage, IMessage> {

        @Override
        public IMessage onMessage(ConfigurePanelMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTasks.submit(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, ConfigurePanelMessage message) {
            if (player == null || player.isDead) {
                return;
            }
            World world = player.worldObj;
            if (world.getBlock(message.x, message.y, message.z) != ModBlocks.recipePanel
                || !player.canPlayerEdit(message.x, message.y, message.z, 0, null)
                || player.getDistanceSq(message.x + 0.5D, message.y + 0.5D, message.z + 0.5D) > 64D) {
                return;
            }
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (!(te instanceof RecipePanelTile)) {
                return;
            }
            PanelSettings settings = new PanelSettings();
            settings.customName = PanelSettings.trim(message.name);
            settings.transparent = message.transparent;
            ((RecipePanelTile) te).setSettings(settings.isEmpty() ? null : settings.toNBT());
        }
    }
}
