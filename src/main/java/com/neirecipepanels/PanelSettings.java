package com.neirecipepanels;

import net.minecraft.nbt.NBTTagCompound;

/** Per-panel display options edited from the shift-right-click config screen. */
public final class PanelSettings {

    public static final int MAX_NAME = 48;

    public String customName = "";
    public boolean transparent = false;

    public static PanelSettings fromNBT(NBTTagCompound tag) {
        PanelSettings settings = new PanelSettings();
        if (tag != null) {
            settings.customName = trim(tag.getString("name"));
            settings.transparent = tag.getBoolean("transparent");
        }
        return settings;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (!customName.isEmpty()) {
            tag.setString("name", customName);
        }
        if (transparent) {
            tag.setBoolean("transparent", true);
        }
        return tag;
    }

    public boolean isEmpty() {
        return customName.isEmpty() && !transparent;
    }

    public static String trim(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }
}
