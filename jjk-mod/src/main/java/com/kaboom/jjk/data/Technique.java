package com.kaboom.jjk.data;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Innate cursed techniques. Abilities are referenced by id (see {@link com.kaboom.jjk.ability.Abilities}).
 * {@code domain} is null for techniques without a Domain Expansion; those fall back to a generic domain.
 */
public enum Technique {
    NONE(Rarity.COMMON, 0xFF888888, null, List.of()),

    // Special Grade
    LIMITLESS(Rarity.SPECIAL_GRADE, 0xFF6FD8FF, "unlimited_void",
            List.of("infinity", "blue", "red", "hollow_purple")),
    SHRINE(Rarity.SPECIAL_GRADE, 0xFFE0303A, "malevolent_shrine",
            List.of("dismantle", "cleave", "fuga")),

    // Legendary
    TEN_SHADOWS(Rarity.LEGENDARY, 0xFF2A2440, "chimera_shadow_garden",
            List.of("divine_dogs", "nue", "toad", "max_elephant", "rabbit_escape", "mahoraga")),
    IDLE_TRANSFIGURATION(Rarity.LEGENDARY, 0xFF6FA88A, "self_embodiment_of_perfection",
            List.of("soul_touch", "polymorphic_soul_isomer", "body_repel")),
    DISASTER_FLAMES(Rarity.LEGENDARY, 0xFFFF7A1F, "coffin_of_the_iron_mountain",
            List.of("ember_insects", "flame_burst", "maximum_meteor")),
    CURSED_SPIRIT_MANIPULATION(Rarity.LEGENDARY, 0xFF4B3A5E, null,
            List.of("absorb_curse", "release_curse", "uzumaki")),

    // Epic
    CURSED_SPEECH(Rarity.EPIC, 0xFFC9C3B0, null,
            List.of("dont_move", "blast_away", "crush")),
    BLOOD_MANIPULATION(Rarity.EPIC, 0xFFB0102A, null,
            List.of("piercing_blood", "supernova", "convergence")),
    PROJECTION_SORCERY(Rarity.EPIC, 0xFF7FFFB2, null,
            List.of("projection_dash", "frame_freeze")),

    // Rare
    BOOGIE_WOOGIE(Rarity.RARE, 0xFFFFD15C, null, List.of("swap")),
    RATIO(Rarity.RARE, 0xFFE8E0C8, null, List.of("ratio_strike", "overtime")),
    STRAW_DOLL(Rarity.RARE, 0xFF9C7A4A, null, List.of("resonance", "hairpin")),
    BLACK_FLASH_AFFINITY(Rarity.RARE, 0xFF201018, null, List.of("divergent_fist", "focus")),
    PUPPET_MANIPULATION(Rarity.RARE, 0xFF8A97A8, null, List.of("ultra_cannon", "puppet_guard")),

    // Common
    CONSTRUCTION(Rarity.COMMON, 0xFFB5A27A, null, List.of("construct_blade", "construct_wall")),
    COMEDIAN(Rarity.COMMON, 0xFFFF9FE0, null, List.of("punchline")),
    CE_REINFORCEMENT(Rarity.COMMON, 0xFF7A6CFF, null, List.of("reinforce", "ce_burst"));

    public final Rarity rarity;
    public final int color;
    public final String domain;
    public final List<String> abilities;

    Technique(Rarity rarity, int color, String domain, List<String> abilities) {
        this.rarity = rarity;
        this.color = color;
        this.domain = domain;
        this.abilities = abilities;
    }

    public String id() { return name().toLowerCase(); }

    public boolean hasDomain() { return domain != null; }

    public Component displayName() {
        return Component.translatable("jjk.technique." + id()).withStyle(rarity.format);
    }

    public Component description() {
        return Component.translatable("jjk.technique." + id() + ".desc");
    }

    public static Technique byId(String id) {
        for (Technique t : values()) if (t.id().equals(id)) return t;
        return NONE;
    }
}
