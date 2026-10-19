package com.kaboom.jjk.data;

import net.minecraft.network.chat.Component;

/** The amount of cursed energy a sorcerer is born with. Rolled together with the innate technique. */
public enum CETier {
    LOW(32, 0.6, 0.7, Rarity.COMMON),
    AVERAGE(36, 1.0, 1.0, Rarity.COMMON),
    HIGH(20, 1.5, 1.3, Rarity.RARE),
    MASSIVE(9, 2.2, 1.7, Rarity.EPIC),
    MONSTROUS(3, 3.5, 2.4, Rarity.SPECIAL_GRADE);

    public final int weight;
    public final double maxMultiplier;
    public final double regenMultiplier;
    public final Rarity rarity;

    CETier(int weight, double maxMultiplier, double regenMultiplier, Rarity rarity) {
        this.weight = weight;
        this.maxMultiplier = maxMultiplier;
        this.regenMultiplier = regenMultiplier;
        this.rarity = rarity;
    }

    public String id() { return name().toLowerCase(); }

    public Component displayName() {
        return Component.translatable("jjk.ce_tier." + id()).withStyle(rarity.format);
    }

    public static CETier byId(String id) {
        for (CETier t : values()) if (t.id().equals(id)) return t;
        return AVERAGE;
    }
}
