package com.deadcraft.entity;

import java.util.Arrays;
import java.util.List;

/**
 * The single source of truth for every creature in DeadCraft.
 * <p>
 * tools/gen_assets.py parses this file (one constant per line) to generate textures,
 * spawn eggs, loot tables, language entries and Blockbench models, so keep each constant
 * on one line and in the same argument order.
 * <p>
 * Arguments: id, kind, archetype, ability, health, attack, speed, armor, width, height,
 * render scale, base colour, accent colour, eye colour, feature flags.
 */
public enum DeadMob {
    // ---- Hostile (15) -------------------------------------------------------------------
    THE_HOLLOW("the_hollow", Kind.HOSTILE, Archetype.GAUNT, Ability.HOLLOW, 40, 7, 0.32, 2, 0.7f, 2.3f, 1.0f, 0x2a2a2e, 0x5b0f0f, 0xf2f2f2, 0),
    SEVERED("severed", Kind.HOSTILE, Archetype.CRAWLER, Ability.SEVERED, 26, 5, 0.34, 0, 0.9f, 0.7f, 1.0f, 0x6e4a3f, 0x8b0000, 0xff3b1f, 0),
    CRACKED("cracked", Kind.HOSTILE, Archetype.BRUTE, Ability.CRACKED, 50, 8, 0.24, 8, 1.2f, 2.0f, 0.85f, 0x5c5a57, 0xff8c1a, 0xffae42, 0),
    DEPART("depart", Kind.HOSTILE, Archetype.WRAITH, Ability.DEPART, 30, 6, 0.28, 0, 0.7f, 2.1f, 1.0f, 0x1c2333, 0x6c7a99, 0x9fd6ff, Features.FLIES),
    SCORCHED("scorched", Kind.HOSTILE, Archetype.GAUNT, Ability.SCORCHED, 34, 7, 0.30, 2, 0.7f, 2.3f, 0.95f, 0x2b1a12, 0xff5a00, 0xffd000, 0),
    PARASITE("parasite", Kind.HOSTILE, Archetype.CRAWLER, Ability.PARASITE, 12, 3, 0.40, 0, 0.6f, 0.4f, 0.55f, 0x6b7d2a, 0x3c1f4a, 0xd7ff3d, 0),
    ILLUSION("illusion", Kind.HOSTILE, Archetype.WRAITH, Ability.ILLUSION, 24, 5, 0.30, 0, 0.7f, 2.1f, 1.0f, 0x7a5cff, 0xc9b8ff, 0xffffff, Features.FLIES | Features.TRANSLUCENT),
    BANDIT("bandit", Kind.HOSTILE, Archetype.HUMANOID, Ability.BANDIT, 24, 4, 0.30, 2, 0.6f, 1.95f, 1.0f, 0x6b4a2b, 0x3a3a3a, 0x000000, 0),
    BANDIT_CAPTAIN("bandit_captain", Kind.HOSTILE, Archetype.HUMANOID, Ability.BANDIT_CAPTAIN, 40, 6, 0.30, 6, 0.6f, 1.95f, 1.05f, 0x2e1f14, 0x8b0000, 0x000000, 0),
    THE_FORGOTTEN("the_forgotten", Kind.HOSTILE, Archetype.GAUNT, Ability.FORGOTTEN, 36, 6, 0.28, 2, 0.7f, 2.3f, 1.0f, 0xb8b0a0, 0x3d3d3d, 0x7fffd4, 0),
    WRETCHED("wretched", Kind.HOSTILE, Archetype.CRAWLER, Ability.WRETCHED, 38, 6, 0.26, 2, 1.3f, 0.9f, 1.3f, 0x5a3b4a, 0x9acd32, 0xff0000, 0),
    GHOST("ghost", Kind.HOSTILE, Archetype.WRAITH, Ability.GHOST, 20, 5, 0.30, 0, 0.6f, 1.9f, 0.9f, 0xd8e6f0, 0x8fb3c9, 0x00e5ff, Features.FLIES | Features.TRANSLUCENT),
    BLOOD_DEMON("blood_demon", Kind.HOSTILE, Archetype.BRUTE, Ability.BLOOD_DEMON, 60, 10, 0.30, 6, 1.2f, 2.2f, 0.95f, 0x5e0b0b, 0x1a0505, 0xff2020, Features.HORNS),
    STAR_FALL("star_fall", Kind.HOSTILE, Archetype.WRAITH, Ability.STAR_FALL, 32, 6, 0.28, 2, 0.7f, 2.1f, 1.0f, 0x0d1b3d, 0xffe680, 0xfff6b0, Features.FLIES),
    COLLAPSE("collapse", Kind.HOSTILE, Archetype.BRUTE, Ability.COLLAPSE, 70, 11, 0.22, 10, 1.4f, 2.2f, 1.05f, 0x3e3a36, 0x7a5230, 0xff6a00, 0),
    // ---- Destroyed-city zombie ----------------------------------------------------------
    ROTWALKER("rotwalker", Kind.ZOMBIE, Archetype.ZOMBIE, Ability.ROTWALKER, 22, 4, 0.24, 2, 0.6f, 1.95f, 1.0f, 0x4f6b3a, 0x3b2f2f, 0xff0000, 0),
    // ---- Bosses (5) ---------------------------------------------------------------------
    FRACTURE("fracture", Kind.BOSS, Archetype.BRUTE, Ability.FRACTURE, 400, 16, 0.26, 12, 2.6f, 5.0f, 2.4f, 0x2b2f3a, 0x7df9ff, 0x7df9ff, 0),
    CHRONOS("chronos", Kind.BOSS, Archetype.WRAITH, Ability.CHRONOS, 360, 14, 0.30, 8, 1.6f, 4.5f, 2.2f, 0x2a2410, 0xd4af37, 0xffd700, Features.FLIES),
    SKELETON("skeleton", Kind.BOSS, Archetype.SKELETON, Ability.SKELETON_KING, 340, 14, 0.30, 10, 1.5f, 5.2f, 2.6f, 0xe8e0c8, 0x3b3b3b, 0x40ff60, 0),
    DIM_LIGHT_DEMON("dim_light_demon", Kind.BOSS, Archetype.BRUTE, Ability.DIM_LIGHT_DEMON, 480, 18, 0.30, 12, 2.6f, 5.4f, 2.5f, 0x14070a, 0x4b0082, 0xb388ff, Features.HORNS),
    PETRIFIED("petrified", Kind.BOSS, Archetype.BRUTE, Ability.PETRIFIED, 520, 20, 0.20, 18, 2.8f, 5.6f, 2.6f, 0x77736b, 0x4a6741, 0x9bff7a, 0),
    // ---- Peaceful (20). The first five drop meat that can be cooked ---------------------
    ASHGRAZER("ashgrazer", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 14, 0, 0.22, 0, 0.9f, 1.3f, 1.0f, 0x5d5650, 0x2f2a26, 0xffcc66, Features.ANTLERS | Features.TAIL | Features.FOOD),
    GLOOMHOG("gloomhog", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 12, 0, 0.25, 0, 0.9f, 0.9f, 0.8f, 0x3d2f3a, 0x7a5d70, 0xff8a8a, Features.TUSKS | Features.EARS | Features.FOOD),
    CINDERHEN("cinderhen", Kind.PEACEFUL, Archetype.FLYER, Ability.NONE, 6, 0, 0.25, 0, 0.4f, 0.7f, 0.7f, 0x8b3a1a, 0xffae42, 0xffe08a, Features.TAIL | Features.FOOD),
    MARROW_OX("marrow_ox", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 20, 0, 0.20, 0, 1.3f, 1.5f, 1.25f, 0xcfc6b0, 0x4a3a2a, 0xff5050, Features.HORNS | Features.TAIL | Features.FOOD),
    DUSKHARE("duskhare", Kind.PEACEFUL, Archetype.CRITTER, Ability.NONE, 6, 0, 0.33, 0, 0.4f, 0.5f, 0.8f, 0x5a4f6b, 0xb3a5c9, 0xd0b0ff, Features.EARS | Features.FOOD),
    LANTERN_MOTH("lantern_moth", Kind.PEACEFUL, Archetype.FLYER, Ability.NONE, 4, 0, 0.30, 0, 0.5f, 0.5f, 0.7f, 0x7a6a4f, 0xffe066, 0xfff2a0, Features.FLIES | Features.GLOWS),
    HUSH_OWL("hush_owl", Kind.PEACEFUL, Archetype.FLYER, Ability.NONE, 8, 0, 0.25, 0, 0.5f, 0.8f, 0.9f, 0x4a4238, 0xd9cbb0, 0xffd24d, Features.FLIES | Features.EARS),
    GRAVECAT("gravecat", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 10, 0, 0.30, 0, 0.6f, 0.7f, 0.6f, 0x1a1a1a, 0x555555, 0x7fff00, Features.EARS | Features.TAIL),
    WISPLET("wisplet", Kind.PEACEFUL, Archetype.CRITTER, Ability.NONE, 4, 0, 0.30, 0, 0.4f, 0.4f, 0.7f, 0x8fd3ff, 0xffffff, 0xffffff, Features.FLIES | Features.GLOWS | Features.TRANSLUCENT),
    SHELLMOURN("shellmourn", Kind.PEACEFUL, Archetype.CRITTER, Ability.NONE, 8, 0, 0.08, 0, 0.5f, 0.4f, 0.9f, 0x6b5b4b, 0xa89f91, 0xc0ffc0, Features.SHELL),
    CANDLEWICK_TOAD("candlewick_toad", Kind.PEACEFUL, Archetype.CRITTER, Ability.NONE, 8, 0, 0.22, 0, 0.5f, 0.4f, 0.9f, 0x4f5a2a, 0xffd27f, 0xffb000, Features.GLOWS),
    MOSSBACK_TORTOISE("mossback_tortoise", Kind.PEACEFUL, Archetype.CRITTER, Ability.NONE, 20, 0, 0.10, 4, 0.8f, 0.5f, 1.3f, 0x3f5a3a, 0x6b8f4e, 0xe0e070, Features.SHELL | Features.TAIL),
    EMBER_FOX("ember_fox", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 10, 0, 0.32, 0, 0.6f, 0.7f, 0.65f, 0xb34700, 0x2b1a12, 0xffae00, Features.EARS | Features.TAIL),
    VEILFAWN("veilfawn", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 12, 0, 0.30, 0, 0.7f, 1.2f, 0.85f, 0xd9d2c5, 0x8a7f72, 0xa0e0ff, Features.EARS | Features.TAIL),
    GRIMFINCH("grimfinch", Kind.PEACEFUL, Archetype.FLYER, Ability.NONE, 4, 0, 0.30, 0, 0.4f, 0.4f, 0.5f, 0x2b2b2b, 0x8b0000, 0xff4040, Features.FLIES | Features.TAIL),
    SORROWDOVE("sorrowdove", Kind.PEACEFUL, Archetype.FLYER, Ability.NONE, 4, 0, 0.30, 0, 0.4f, 0.4f, 0.55f, 0xc8c8d0, 0x6b6b7a, 0x303040, Features.FLIES | Features.TAIL),
    STORMSTAG("stormstag", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 22, 0, 0.28, 0, 1.0f, 1.6f, 1.15f, 0x2f3a4a, 0x7df9ff, 0x7df9ff, Features.ANTLERS | Features.TAIL | Features.GLOWS),
    CRAGSTRIDER("cragstrider", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 16, 0, 0.26, 2, 0.9f, 1.3f, 0.95f, 0x7a6a5a, 0x3a2f28, 0xffd080, Features.HORNS | Features.TAIL),
    LUMEN_BEETLE("lumen_beetle", Kind.PEACEFUL, Archetype.CRITTER, Ability.NONE, 4, 0, 0.20, 2, 0.5f, 0.3f, 0.6f, 0x1f3a2f, 0x39ff88, 0x39ff88, Features.SHELL | Features.GLOWS),
    HOLLOWFLEECE("hollowfleece", Kind.PEACEFUL, Archetype.QUADRUPED, Ability.NONE, 10, 0, 0.23, 0, 0.9f, 1.2f, 0.9f, 0xe6e0d6, 0x3a3a3a, 0xff0000, Features.EARS);

    public enum Kind { HOSTILE, ZOMBIE, BOSS, PEACEFUL }

    /** Model shape families. Each one is a hand-built cuboid model (see tools/gen_assets.py). */
    public enum Archetype { GAUNT, CRAWLER, WRAITH, BRUTE, HUMANOID, ZOMBIE, SKELETON, QUADRUPED, CRITTER, FLYER }

    public enum Ability {
        NONE, HOLLOW, SEVERED, CRACKED, DEPART, SCORCHED, PARASITE, ILLUSION, BANDIT, BANDIT_CAPTAIN,
        FORGOTTEN, WRETCHED, GHOST, BLOOD_DEMON, STAR_FALL, COLLAPSE, ROTWALKER,
        FRACTURE, CHRONOS, SKELETON_KING, DIM_LIGHT_DEMON, PETRIFIED
    }

    public static final class Features {
        public static final int FLIES = 1;
        public static final int TRANSLUCENT = 2;
        public static final int HORNS = 4;
        public static final int ANTLERS = 8;
        public static final int EARS = 16;
        public static final int TAIL = 32;
        public static final int SHELL = 64;
        public static final int TUSKS = 128;
        public static final int GLOWS = 256;
        public static final int FOOD = 512;
        private Features() {}
    }

    public final String id;
    public final Kind kind;
    public final Archetype archetype;
    public final Ability ability;
    public final double health;
    public final double attack;
    public final double speed;
    public final double armor;
    public final float width;
    public final float height;
    public final float renderScale;
    public final int baseColor;
    public final int accentColor;
    public final int eyeColor;
    public final int features;

    DeadMob(String id, Kind kind, Archetype archetype, Ability ability, double health, double attack, double speed,
            double armor, float width, float height, float renderScale, int baseColor, int accentColor, int eyeColor,
            int features) {
        this.id = id;
        this.kind = kind;
        this.archetype = archetype;
        this.ability = ability;
        this.health = health;
        this.attack = attack;
        this.speed = speed;
        this.armor = armor;
        this.width = width;
        this.height = height;
        this.renderScale = renderScale;
        this.baseColor = baseColor;
        this.accentColor = accentColor;
        this.eyeColor = eyeColor;
        this.features = features;
    }

    public boolean has(int feature) { return (features & feature) != 0; }
    public boolean isBoss() { return kind == Kind.BOSS; }
    public boolean isPeaceful() { return kind == Kind.PEACEFUL; }
    public boolean isHostileLike() { return kind != Kind.PEACEFUL; }
    public boolean flies() { return has(Features.FLIES); }
    /** Glowing eyes are rendered as an emissive layer for every hostile and for glowing critters. */
    public boolean hasEyeLayer() { return eyeColor != 0 && (isHostileLike() || has(Features.GLOWS)); }

    public static List<DeadMob> ofKind(Kind kind) {
        return Arrays.stream(values()).filter(m -> m.kind == kind).toList();
    }
}
