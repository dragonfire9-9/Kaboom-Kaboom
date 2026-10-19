package com.kaboom.jjk.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Sorcerer grade. Experience comes from exorcising curses. */
public enum Grade {
    GRADE_4(0, 1.0, ChatFormatting.GRAY),
    GRADE_3(150, 1.25, ChatFormatting.GREEN),
    GRADE_2(500, 1.6, ChatFormatting.AQUA),
    SEMI_GRADE_1(1200, 2.0, ChatFormatting.BLUE),
    GRADE_1(2500, 2.5, ChatFormatting.LIGHT_PURPLE),
    SPECIAL_GRADE(6000, 3.5, ChatFormatting.RED);

    public final int xpRequired;
    public final double ceMultiplier;
    public final ChatFormatting format;

    Grade(int xpRequired, double ceMultiplier, ChatFormatting format) {
        this.xpRequired = xpRequired;
        this.ceMultiplier = ceMultiplier;
        this.format = format;
    }

    public String id() { return name().toLowerCase(); }

    public Component displayName() {
        return Component.translatable("jjk.grade." + id()).withStyle(format);
    }

    public boolean atLeast(Grade other) { return ordinal() >= other.ordinal(); }

    public Grade next() {
        Grade[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : this;
    }

    public static Grade forXp(int xp) {
        Grade best = GRADE_4;
        for (Grade g : values()) if (xp >= g.xpRequired) best = g;
        return best;
    }

    public static Grade byId(String id) {
        for (Grade g : values()) if (g.id().equals(id)) return g;
        return GRADE_4;
    }
}
