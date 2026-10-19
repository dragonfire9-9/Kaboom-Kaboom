package com.kaboom.jjk.data;

import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Sorcerer clans. Each clan biases the technique pool and grants passive bonuses
 * (applied in {@link CursedEnergyData} and {@link com.kaboom.jjk.event.PlayerDataEvents}).
 */
public enum Clan {
    CLANLESS(45, Rarity.COMMON, Map.of()),
    FUJIWARA(15, Rarity.RARE, Map.of(Technique.STRAW_DOLL, 3.0, Technique.RATIO, 3.0, Technique.DISASTER_FLAMES, 2.0)),
    ZENIN(12, Rarity.EPIC, Map.of(Technique.TEN_SHADOWS, 6.0, Technique.PROJECTION_SORCERY, 5.0, Technique.CONSTRUCTION, 3.0)),
    KAMO(12, Rarity.EPIC, Map.of(Technique.BLOOD_MANIPULATION, 8.0, Technique.CURSED_SPIRIT_MANIPULATION, 1.5)),
    INUMAKI(8, Rarity.EPIC, Map.of(Technique.CURSED_SPEECH, 10.0)),
    GOJO(5, Rarity.LEGENDARY, Map.of(Technique.LIMITLESS, 8.0)),
    RYOMEN(3, Rarity.SPECIAL_GRADE, Map.of(Technique.SHRINE, 12.0, Technique.DISASTER_FLAMES, 2.0));

    public final int weight;
    public final Rarity rarity;
    private final Map<Technique, Double> bias;

    Clan(int weight, Rarity rarity, Map<Technique, Double> bias) {
        this.weight = weight;
        this.rarity = rarity;
        this.bias = bias;
    }

    /** Multiplier applied to the weight of {@code t} when this clan rolls a technique. */
    public double bias(Technique t) {
        return bias.getOrDefault(t, 1.0);
    }

    // --- passives -------------------------------------------------------------

    public double costMultiplier() {
        return switch (this) {
            case GOJO -> 0.7;       // Six Eyes: near-perfect CE efficiency
            case CLANLESS -> 0.95;
            default -> 1.0;
        };
    }

    public double maxCEMultiplier() {
        return switch (this) {
            case KAMO, RYOMEN -> 1.1;
            case CLANLESS -> 1.05;
            default -> 1.0;
        };
    }

    public double regenMultiplier() {
        return switch (this) {
            case FUJIWARA -> 1.2;
            case GOJO -> 1.15;
            case CLANLESS -> 1.05;
            default -> 1.0;
        };
    }

    /** Bonus to melee damage when wielding a weapon (Zenin) or in general (Clanless). */
    public double weaponDamageMultiplier() {
        return switch (this) {
            case ZENIN -> 1.25;
            case CLANLESS -> 1.05;
            default -> 1.0;
        };
    }

    public double techniqueDamageMultiplier() {
        return switch (this) {
            case RYOMEN -> 1.15;
            case CLANLESS -> 1.05;
            default -> 1.0;
        };
    }

    /** Gojo clan members carry the Six Eyes: they can perceive the cursed energy of others. */
    public boolean seesCursedEnergy() {
        return this == GOJO;
    }

    public String id() { return name().toLowerCase(); }

    public Component displayName() {
        return Component.translatable("jjk.clan." + id()).withStyle(rarity.format);
    }

    public Component description() {
        return Component.translatable("jjk.clan." + id() + ".desc");
    }

    public static Clan byId(String id) {
        for (Clan c : values()) if (c.id().equals(id)) return c;
        return CLANLESS;
    }
}
