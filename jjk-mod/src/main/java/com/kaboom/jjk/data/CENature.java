package com.kaboom.jjk.data;

import net.minecraft.network.chat.Component;

/**
 * The "type" or texture of a sorcerer's cursed energy. Each one grants a small passive.
 */
public enum CENature {
    STANDARD(40, 0xFF7A6CFF),
    SHARP(15, 0xFF9AD8FF),     // +10% technique damage
    DENSE(15, 0xFF3C2E8F),     // +2 armor while CE > 0
    FLOWING(15, 0xFF4FFFD8),   // +20% regen
    VOLATILE(10, 0xFFFF4F7A),  // higher Black Flash chance
    PRISTINE(5, 0xFFFFFFFF);   // -10% CE cost

    public final int weight;
    public final int color;

    CENature(int weight, int color) {
        this.weight = weight;
        this.color = color;
    }

    public String id() { return name().toLowerCase(); }

    public Component displayName() {
        return Component.translatable("jjk.ce_nature." + id());
    }

    public static CENature byId(String id) {
        for (CENature n : values()) if (n.id().equals(id)) return n;
        return STANDARD;
    }
}
