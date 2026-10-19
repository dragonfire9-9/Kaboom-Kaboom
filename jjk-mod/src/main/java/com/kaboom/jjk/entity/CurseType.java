package com.kaboom.jjk.entity;

import com.kaboom.jjk.data.Grade;

/** Every cursed spirit in the mod. Stats scale with grade. */
public enum CurseType {
    FLY_HEAD("fly_head", Grade.GRADE_4, 10, 3, 0.30, true, 0.7f, 0.8f, 60, 0x7A8C5A, 0xE0E080),
    ROPPONGI_CURSE("roppongi_curse", Grade.GRADE_3, 34, 6, 0.27, false, 0.8f, 2.3f, 30, 0x5A6450, 0xF0F0C0),
    FINGER_BEARER("finger_bearer", Grade.GRADE_1, 120, 12, 0.25, false, 1.4f, 2.8f, 6, 0xC89488, 0xFFE0D0),
    RAINBOW_DRAGON("rainbow_dragon", Grade.GRADE_1, 140, 11, 0.30, true, 1.2f, 1.2f, 4, 0xE0E0F0, 0xE04040),
    SMALLPOX_DEITY("smallpox_deity", Grade.GRADE_1, 130, 10, 0.24, false, 1.0f, 3.0f, 4, 0xD2CDBE, 0x5A3C28),
    /** Mahito's victims. Never spawn naturally (weight 0). */
    TRANSFIGURED_HUMAN("transfigured_human", Grade.GRADE_4, 16, 4, 0.26, false, 0.7f, 1.6f, 0, 0xC8B4A0, 0x6A8A7A);

    public final String id;
    public final Grade grade;
    public final double health;
    public final double damage;
    public final double speed;
    public final boolean flying;
    public final float width, height;
    /** Natural spawn weight (biome modifier and the curse spawner). */
    public final int weight;
    public final int eggPrimary, eggSecondary;

    CurseType(String id, Grade grade, double health, double damage, double speed, boolean flying, float width, float height,
              int weight, int eggPrimary, int eggSecondary) {
        this.id = id;
        this.grade = grade;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.flying = flying;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.eggPrimary = eggPrimary;
        this.eggSecondary = eggSecondary;
    }

    /** Jujutsu experience for exorcising one. */
    public int xp() {
        return switch (grade) {
            case GRADE_4 -> 12;
            case GRADE_3 -> 35;
            case GRADE_2 -> 70;
            case SEMI_GRADE_1 -> 110;
            case GRADE_1 -> 180;
            case SPECIAL_GRADE -> 500;
        };
    }

    public static CurseType byId(String id) {
        for (CurseType t : values()) if (t.id.equals(id)) return t;
        return FLY_HEAD;
    }
}
