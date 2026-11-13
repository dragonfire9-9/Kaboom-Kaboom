package dev.soulbound.module;

public enum Category {
    COMBAT("Combat", "icon_combat"),
    VISUAL("Visual", "icon_visual"),
    UTIL("Util", "icon_util"),
    MOVEMENT("Movement", "icon_movement");

    public final String displayName;
    public final String icon;

    Category(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }
}
