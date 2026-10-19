package com.kaboom.jjk.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common (server-authoritative) config: roll rates, CE economy and griefing toggles. */
public final class JJKConfig {
    public static final ModConfigSpec SPEC;

    // Rolls: rarity weights for the Cursed Energy Scroll technique pool
    public static final ModConfigSpec.IntValue WEIGHT_COMMON;
    public static final ModConfigSpec.IntValue WEIGHT_RARE;
    public static final ModConfigSpec.IntValue WEIGHT_EPIC;
    public static final ModConfigSpec.IntValue WEIGHT_LEGENDARY;
    public static final ModConfigSpec.IntValue WEIGHT_SPECIAL_GRADE;
    public static final ModConfigSpec.DoubleValue CLAN_BIAS_MULTIPLIER;
    public static final ModConfigSpec.IntValue HR_PHYSICAL_CHANCE;
    public static final ModConfigSpec.IntValue HR_CURSED_ENERGY_CHANCE;

    // Cursed energy economy
    public static final ModConfigSpec.DoubleValue BASE_MAX_CE;
    public static final ModConfigSpec.DoubleValue BASE_CE_REGEN;
    public static final ModConfigSpec.DoubleValue CE_COST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;

    // Progression
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NON_CURSE_MONSTER_XP_FRACTION;

    // Griefing
    public static final ModConfigSpec.BooleanValue HOLLOW_PURPLE_BREAKS_BLOCKS;
    public static final ModConfigSpec.BooleanValue DOMAINS_PLACE_BLOCKS;
    public static final ModConfigSpec.BooleanValue ABILITIES_BREAK_BLOCKS;
    public static final ModConfigSpec.BooleanValue DOMAIN_SLOW_MOTION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Roll scroll rates").push("rolls");
        WEIGHT_COMMON = b.comment("Weight of each Common technique").defineInRange("weightCommon", 40, 0, 10000);
        WEIGHT_RARE = b.comment("Weight of each Rare technique").defineInRange("weightRare", 22, 0, 10000);
        WEIGHT_EPIC = b.comment("Weight of each Epic technique").defineInRange("weightEpic", 10, 0, 10000);
        WEIGHT_LEGENDARY = b.comment("Weight of each Legendary technique").defineInRange("weightLegendary", 4, 0, 10000);
        WEIGHT_SPECIAL_GRADE = b.comment("Weight of each Special Grade technique").defineInRange("weightSpecialGrade", 1, 0, 10000);
        CLAN_BIAS_MULTIPLIER = b.comment("Scales how strongly a clan pushes the pool toward its hereditary techniques (1 = default)")
                .defineInRange("clanBiasMultiplier", 1.0, 0.0, 100.0);
        HR_PHYSICAL_CHANCE = b.comment("Percent chance a Heavenly Restriction Scroll grants the physical (Toji/Maki) restriction")
                .defineInRange("heavenlyRestrictionPhysicalChance", 7, 0, 100);
        HR_CURSED_ENERGY_CHANCE = b.comment("Percent chance a Heavenly Restriction Scroll grants the cursed-energy (Mechamaru) restriction")
                .defineInRange("heavenlyRestrictionCursedEnergyChance", 7, 0, 100);
        b.pop();

        b.comment("Cursed energy economy").push("cursedEnergy");
        BASE_MAX_CE = b.comment("Max CE of an Average-tier Grade 4 sorcerer").defineInRange("baseMaxCE", 100.0, 1.0, 100000.0);
        BASE_CE_REGEN = b.comment("CE regenerated per second at Average tier").defineInRange("baseRegenPerSecond", 2.0, 0.0, 10000.0);
        CE_COST_MULTIPLIER = b.comment("Multiplier applied to every ability's CE cost").defineInRange("costMultiplier", 1.0, 0.0, 100.0);
        COOLDOWN_MULTIPLIER = b.comment("Multiplier applied to every ability cooldown").defineInRange("cooldownMultiplier", 1.0, 0.0, 100.0);
        b.pop();

        b.comment("Progression").push("progression");
        XP_MULTIPLIER = b.comment("Multiplier on jujutsu experience from kills").defineInRange("xpMultiplier", 1.0, 0.0, 1000.0);
        NON_CURSE_MONSTER_XP_FRACTION = b.comment("Fraction of curse XP granted for killing ordinary hostile mobs (0 = curses only)")
                .defineInRange("nonCurseMonsterXpFraction", 0.25, 0.0, 1.0);
        b.pop();

        b.comment("World griefing").push("griefing");
        HOLLOW_PURPLE_BREAKS_BLOCKS = b.comment("Hollow Purple erases blocks along its path").define("hollowPurpleBreaksBlocks", true);
        DOMAINS_PLACE_BLOCKS = b.comment("Domain Expansions build a temporary barrier dome (always restored afterwards)").define("domainsPlaceBlocks", true);
        ABILITIES_BREAK_BLOCKS = b.comment("Other destructive techniques (Red, Fuga, Maximum: Meteor...) can break blocks").define("abilitiesBreakBlocks", false);
        b.pop();

        b.comment("Cinematics").push("cinematics");
        DOMAIN_SLOW_MOTION = b.comment("Briefly slow the server tick rate when a Domain Expansion opens (affects everyone on the server)")
                .define("domainSlowMotion", true);
        b.pop();

        SPEC = b.build();
    }

    private JJKConfig() {}
}
