package com.neirecipepanels.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.neirecipepanels.PanelSettings;

/** Holds a placed panel's recipe snapshot + display settings and syncs them to clients. */
public class RecipePanelTile extends TileEntity {

    private static final String TAG_SNAPSHOT = "snapshot";
    private static final String TAG_SETTINGS = "cfg";

    private NBTTagCompound snapshot;
    private NBTTagCompound settings;

    public NBTTagCompound getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(NBTTagCompound snapshot) {
        this.snapshot = snapshot;
        sync();
    }

    public NBTTagCompound getSettings() {
        return settings;
    }

    public PanelSettings settings() {
        return PanelSettings.fromNBT(settings);
    }

    public void setSettings(NBTTagCompound settings) {
        this.settings = settings == null || settings.hasNoTags() ? null : settings;
        sync();
    }

    private void sync() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        snapshot = tag.hasKey(TAG_SNAPSHOT) ? tag.getCompoundTag(TAG_SNAPSHOT) : null;
        settings = tag.hasKey(TAG_SETTINGS) ? tag.getCompoundTag(TAG_SETTINGS) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (snapshot != null) {
            tag.setTag(TAG_SNAPSHOT, snapshot);
        }
        if (settings != null) {
            tag.setTag(TAG_SETTINGS, settings);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, getBlockMetadata(), tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }

    @Override
    public boolean canUpdate() {
        return false;
    }
}
