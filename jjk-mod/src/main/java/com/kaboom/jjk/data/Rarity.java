package com.kaboom.jjk.data;

import com.kaboom.jjk.config.JJKConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum Rarity {
    COMMON(0xFFB8B8B8, ChatFormatting.GRAY),
    RARE(0xFF4FA3FF, ChatFormatting.BLUE),
    EPIC(0xFFB04FFF, ChatFormatting.DARK_PURPLE),
    LEGENDARY(0xFFFFB627, ChatFormatting.GOLD),
    SPECIAL_GRADE(0xFFFF3040, ChatFormatting.RED);

    public final int color;
    public final ChatFormatting format;

    Rarity(int color, ChatFormatting format) {
        this.color = color;
        this.format = format;
    }

    public int techniqueWeight() {
        return switch (this) {
            case COMMON -> JJKConfig.WEIGHT_COMMON.get();
            case RARE -> JJKConfig.WEIGHT_RARE.get();
            case EPIC -> JJKConfig.WEIGHT_EPIC.get();
            case LEGENDARY -> JJKConfig.WEIGHT_LEGENDARY.get();
            case SPECIAL_GRADE -> JJKConfig.WEIGHT_SPECIAL_GRADE.get();
        };
    }

    public Component displayName() {
        return Component.translatable("jjk.rarity." + name().toLowerCase()).withStyle(format);
    }

    public static Rarity byOrdinal(int i) {
        Rarity[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
