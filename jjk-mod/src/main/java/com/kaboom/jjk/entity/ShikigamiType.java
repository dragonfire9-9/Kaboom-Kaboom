package com.kaboom.jjk.entity;

/** Ten Shadows shikigami summoned from the user's shadow. */
public enum ShikigamiType {
    DIVINE_DOG("divine_dog", 30, 6, 0.38, false, 0.8f, 1.1f, 20 * 45),
    NUE("nue", 24, 5, 0.30, true, 1.2f, 0.8f, 20 * 40),
    TOAD("toad", 30, 3, 0.25, false, 1.0f, 0.8f, 20 * 30),
    MAX_ELEPHANT("max_elephant", 80, 10, 0.22, false, 2.0f, 2.4f, 20 * 30),
    SHADOW_RABBIT("shadow_rabbit", 4, 1, 0.42, false, 0.4f, 0.4f, 20 * 20);

    public final String id;
    public final double health, damage, speed;
    public final boolean flying;
    public final float width, height;
    public final int lifetime;

    ShikigamiType(String id, double health, double damage, double speed, boolean flying, float width, float height, int lifetime) {
        this.id = id;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.flying = flying;
        this.width = width;
        this.height = height;
        this.lifetime = lifetime;
    }
}
