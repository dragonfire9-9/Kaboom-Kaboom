package com.kaboom.jjk.data;

import net.minecraft.network.chat.Component;

public enum HeavenlyRestriction {
    /** No binding vow from birth. */
    NONE(Rarity.COMMON),
    /** Toji / Maki: zero cursed energy, a monstrous body. */
    PHYSICAL(Rarity.SPECIAL_GRADE),
    /** Mechamaru: a fragile body in exchange for vast cursed energy and range. */
    CURSED_ENERGY(Rarity.LEGENDARY);

    public final Rarity rarity;

    HeavenlyRestriction(Rarity rarity) { this.rarity = rarity; }

    public String id() { return name().toLowerCase(); }

    public Component displayName() {
        return Component.translatable("jjk.heavenly_restriction." + id()).withStyle(rarity.format);
    }

    public static HeavenlyRestriction byId(String id) {
        for (HeavenlyRestriction h : values()) if (h.id().equals(id)) return h;
        return NONE;
    }
}
