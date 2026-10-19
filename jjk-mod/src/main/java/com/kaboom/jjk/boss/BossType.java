package com.kaboom.jjk.boss;

import net.minecraft.world.BossEvent;

/** Special grade bosses. */
public enum BossType {
    SUKUNA("sukuna", 700, 18, 0.33, 14, 0.8f, 2.3f, BossEvent.BossBarColor.RED, 0xB0101A),
    MAHITO("mahito", 450, 13, 0.32, 8, 0.7f, 2.0f, BossEvent.BossBarColor.BLUE, 0x7890B0),
    JOGO("jogo", 500, 14, 0.27, 10, 0.8f, 1.9f, BossEvent.BossBarColor.YELLOW, 0xFF6A1A),
    HANAMI("hanami", 600, 13, 0.26, 16, 0.9f, 2.6f, BossEvent.BossBarColor.GREEN, 0x6A9A5A),
    KENJAKU("kenjaku", 520, 12, 0.30, 10, 0.7f, 2.0f, BossEvent.BossBarColor.PURPLE, 0x4B2A6E),
    MAHORAGA("mahoraga", 900, 22, 0.30, 18, 1.2f, 3.4f, BossEvent.BossBarColor.WHITE, 0xE8E4D0),
    TOJI("toji", 400, 16, 0.42, 12, 0.7f, 1.95f, BossEvent.BossBarColor.WHITE, 0x202020);

    public final String id;
    public final double health, damage, speed, armor;
    public final float width, height;
    public final BossEvent.BossBarColor barColor;
    public final int color;

    BossType(String id, double health, double damage, double speed, double armor, float width, float height,
             BossEvent.BossBarColor barColor, int color) {
        this.id = id;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.armor = armor;
        this.width = width;
        this.height = height;
        this.barColor = barColor;
        this.color = color;
    }
}
