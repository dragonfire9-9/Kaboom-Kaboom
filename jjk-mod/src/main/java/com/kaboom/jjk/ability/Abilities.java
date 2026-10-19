package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.entity.ShikigamiType;
import com.kaboom.jjk.entity.TechniqueProjectile;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.kaboom.jjk.ability.AbilityEffects.*;

/**
 * Static registry of every ability. Order of registration is the order shown on the HUD.
 * Ability ids are referenced by {@link Technique#abilities}.
 */
public final class Abilities {
    private static final Map<String, Ability> REGISTRY = new LinkedHashMap<>();

    private static final int CE = 0xFF7A6CFF;

    static {
        // ---- Universal (all cursed energy users)
        universal("cursed_strike", 10, 30, Grade.GRADE_4, CE, melee(4, 7f, 0.6, CE, 0.03));
        universal("ce_reinforcement", 20, 400, Grade.GRADE_3, CE,
                selfBuff(200, 0, CE, MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE));
        universal("simple_domain", 30, 600, Grade.GRADE_2, 0xFF9AD8FF,
                chain(selfBuff(200, 0, 0xFF9AD8FF, ModEffects.SIMPLE_DOMAIN), ringAround(3, 0xFF9AD8FF)));
        universal("domain_amplification", 45, 600, Grade.GRADE_1, 0xFF4B3AFF,
                selfBuff(160, 0, 0xFF4B3AFF, ModEffects.DOMAIN_AMPLIFICATION));

        // ---- Heavenly Restriction (physical) — cost nothing, pure body
        physical("heavenly_dash", 40, 0xFFDDDDDD, dash(2.4, 0xFFDDDDDD));
        physical("ground_slam", 120, 0xFF8A7A6A, burst(5, 10f, 1.4, 0xFF8A7A6A));

        // ---- Limitless
        tech("infinity", Technique.LIMITLESS, 40, 600, Grade.GRADE_4, 0xFFBFF4FF,
                toggle(ModEffects.INFINITY, 0xFFBFF4FF));
        tech("blue", Technique.LIMITLESS, 35, 100, Grade.GRADE_4, 0xFF2F7BFF, projectile(TechniqueProjectile.Kind.BLUE, 0.35, 8f, 1.4f, 50, true));
        tech("red", Technique.LIMITLESS, 50, 140, Grade.GRADE_2, 0xFFFF2A2A, projectile(TechniqueProjectile.Kind.RED, 2.4, 16f, 0.9f, 30, false));
        tech("hollow_purple", Technique.LIMITLESS, 160, 900, Grade.GRADE_1, 0xFF9B30FF, projectile(TechniqueProjectile.Kind.HOLLOW_PURPLE, 1.1, 45f, 2.6f, 70, true));

        // ---- Shrine
        tech("dismantle", Technique.SHRINE, 20, 25, Grade.GRADE_4, 0xFFFFFFFF, slashLine(20, 9f, 10));
        tech("cleave", Technique.SHRINE, 40, 80, Grade.GRADE_3, 0xFFF2F2F2, cleave(8, 10f));
        tech("fuga", Technique.SHRINE, 140, 800, Grade.GRADE_1, 0xFFFF6A00, projectile(TechniqueProjectile.Kind.FUGA, 1.8, 34f, 0.8f, 60, false));

        // ---- Ten Shadows (summons arrive with the entity phase; for now they strike from the shadows)
        tech("divine_dogs", Technique.TEN_SHADOWS, 25, 600, Grade.GRADE_4, 0xFF1A1A1A, summon(ShikigamiType.DIVINE_DOG, 2));
        tech("nue", Technique.TEN_SHADOWS, 35, 600, Grade.GRADE_3, 0xFFFFF27A, chain(summon(ShikigamiType.NUE, 1), lightning(24, 8f)));
        tech("toad", Technique.TEN_SHADOWS, 25, 500, Grade.GRADE_3, 0xFF4F7A3A, summon(ShikigamiType.TOAD, 1));
        tech("max_elephant", Technique.TEN_SHADOWS, 55, 900, Grade.GRADE_2, 0xFF5A6A9A, summon(ShikigamiType.MAX_ELEPHANT, 1));
        tech("rabbit_escape", Technique.TEN_SHADOWS, 30, 300, Grade.GRADE_2, 0xFFEFEFEF,
                chain(summon(ShikigamiType.SHADOW_RABBIT, 8), selfBuff(100, 1, 0xFFEFEFEF, MobEffects.MOVEMENT_SPEED, MobEffects.INVISIBILITY), dash(1.6, 0xFFEFEFEF)));
        tech("mahoraga", Technique.TEN_SHADOWS, 250, 2400, Grade.SPECIAL_GRADE, 0xFFE8E4D0, summonMahoraga());

        // ---- Idle Transfiguration
        tech("soul_touch", Technique.IDLE_TRANSFIGURATION, 25, 60, Grade.GRADE_4, 0xFF6FA88A, melee(3.5, 12f, 0.2, 0xFF6FA88A, 0));
        tech("polymorphic_soul_isomer", Technique.IDLE_TRANSFIGURATION, 60, 400, Grade.GRADE_2, 0xFF8FC8AA, burst(5, 14f, 1.0, 0xFF8FC8AA));
        tech("body_repel", Technique.IDLE_TRANSFIGURATION, 30, 200, Grade.GRADE_3, 0xFF5A8A6A,
                chain(selfBuff(160, 1, 0xFF5A8A6A, MobEffects.DAMAGE_RESISTANCE, MobEffects.REGENERATION)));

        // ---- Disaster Flames
        tech("ember_insects", Technique.DISASTER_FLAMES, 25, 60, Grade.GRADE_4, 0xFFFF8A2A, volley(TechniqueProjectile.Kind.EMBER, 5, 0.8, 4f, 0.35f, 60));
        tech("flame_burst", Technique.DISASTER_FLAMES, 45, 160, Grade.GRADE_3, 0xFFFF5A1A, burst(5, 12f, 1.2, 0xFFFF5A1A));
        tech("maximum_meteor", Technique.DISASTER_FLAMES, 170, 1200, Grade.GRADE_1, 0xFFFF3A00, meteor(40f));

        // ---- Cursed Spirit Manipulation
        tech("absorb_curse", Technique.CURSED_SPIRIT_MANIPULATION, 20, 60, Grade.GRADE_4, 0xFF4B3A5E, absorbCurse(6));
        tech("release_curse", Technique.CURSED_SPIRIT_MANIPULATION, 40, 200, Grade.GRADE_3, 0xFF6B4A8E, releaseCurse(projectile(TechniqueProjectile.Kind.CURSE, 1.2, 12f, 1.2f, 50, false)));
        tech("uzumaki", Technique.CURSED_SPIRIT_MANIPULATION, 180, 1200, Grade.GRADE_1, 0xFF2A1A3E, uzumaki());

        // ---- Cursed Speech
        tech("dont_move", Technique.CURSED_SPEECH, 30, 200, Grade.GRADE_4, 0xFFC9C3B0,
                debuffArea(10, 60, 9, 0, 0xFFC9C3B0, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS));
        tech("blast_away", Technique.CURSED_SPEECH, 40, 160, Grade.GRADE_3, 0xFFE9E3D0, burst(7, 8f, 3.0, 0xFFE9E3D0));
        tech("crush", Technique.CURSED_SPEECH, 60, 300, Grade.GRADE_2, 0xFFA9A390,
                debuffArea(8, 40, 2, 14f, 0xFFA9A390, MobEffects.MOVEMENT_SLOWDOWN));

        // ---- Blood Manipulation
        tech("piercing_blood", Technique.BLOOD_MANIPULATION, 30, 70, Grade.GRADE_4, 0xFFB0102A, projectile(TechniqueProjectile.Kind.BLOOD, 3.5, 12f, 0.35f, 20, true));
        tech("supernova", Technique.BLOOD_MANIPULATION, 50, 200, Grade.GRADE_3, 0xFFD0203A, burst(5, 12f, 1.2, 0xFFD0203A));
        tech("convergence", Technique.BLOOD_MANIPULATION, 25, 400, Grade.GRADE_2, 0xFF80101A,
                selfBuff(300, 1, 0xFF80101A, MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED));

        // ---- Projection Sorcery
        tech("projection_dash", Technique.PROJECTION_SORCERY, 20, 40, Grade.GRADE_4, 0xFF7FFFB2, dash(2.8, 0xFF7FFFB2));
        tech("frame_freeze", Technique.PROJECTION_SORCERY, 45, 240, Grade.GRADE_3, 0xFF5FDF92,
                debuffArea(5, 40, 9, 4f, 0xFF5FDF92, MobEffects.MOVEMENT_SLOWDOWN));

        // ---- Rare techniques
        tech("swap", Technique.BOOGIE_WOOGIE, 15, 30, Grade.GRADE_4, 0xFFFFD15C, swap(30, 0xFFFFD15C));
        tech("ratio_strike", Technique.RATIO, 25, 60, Grade.GRADE_4, 0xFFE8E0C8, melee(4, 15f, 1.0, 0xFFE8E0C8, 0.04));
        tech("overtime", Technique.RATIO, 30, 1200, Grade.GRADE_3, 0xFFC8C0A8,
                selfBuff(600, 1, 0xFFC8C0A8, MobEffects.DAMAGE_BOOST, MobEffects.DIG_SPEED));
        tech("resonance", Technique.STRAW_DOLL, 30, 100, Grade.GRADE_4, 0xFF9C7A4A, beam(24, 10f, 0.2, 0xFF9C7A4A));
        tech("hairpin", Technique.STRAW_DOLL, 40, 140, Grade.GRADE_3, 0xFFBC9A6A, burst(4, 11f, 0.8, 0xFFBC9A6A));
        tech("divergent_fist", Technique.BLACK_FLASH_AFFINITY, 15, 30, Grade.GRADE_4, 0xFF301020, melee(4, 10f, 1.0, 0xFF601020, 0.12));
        tech("focus", Technique.BLACK_FLASH_AFFINITY, 30, 600, Grade.GRADE_3, 0xFF200810,
                selfBuff(300, 0, 0xFF200810, MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED));
        tech("ultra_cannon", Technique.PUPPET_MANIPULATION, 60, 200, Grade.GRADE_4, 0xFF8AD7FF, projectile(TechniqueProjectile.Kind.ENERGY, 2.2, 16f, 1.0f, 40, false));
        tech("puppet_guard", Technique.PUPPET_MANIPULATION, 30, 400, Grade.GRADE_3, 0xFF8A97A8,
                selfBuff(200, 1, 0xFF8A97A8, MobEffects.DAMAGE_RESISTANCE));

        // ---- Common techniques
        tech("construct_blade", Technique.CONSTRUCTION, 15, 40, Grade.GRADE_4, 0xFFB5A27A, melee(5, 8f, 0.4, 0xFFB5A27A, 0));
        tech("construct_wall", Technique.CONSTRUCTION, 25, 300, Grade.GRADE_3, 0xFF95825A,
                selfBuff(160, 1, 0xFF95825A, MobEffects.DAMAGE_RESISTANCE));
        tech("punchline", Technique.COMEDIAN, 20, 100, Grade.GRADE_4, 0xFFFF9FE0,
                debuffArea(6, 60, 0, 3f, 0xFFFF9FE0, MobEffects.LEVITATION, MobEffects.GLOWING));
        tech("reinforce", Technique.CE_REINFORCEMENT, 15, 300, Grade.GRADE_4, CE,
                selfBuff(240, 1, CE, MobEffects.DAMAGE_BOOST));
        tech("ce_burst", Technique.CE_REINFORCEMENT, 30, 120, Grade.GRADE_3, CE, burst(4, 9f, 1.5, CE));
    }

    private static void tech(String id, Technique t, double cost, int cooldown, Grade grade, int color, AbilityEffect effect) {
        REGISTRY.put(id, new Ability(id, Ability.Kind.TECHNIQUE, t, cost, cooldown, grade, color, effect));
    }

    private static void universal(String id, double cost, int cooldown, Grade grade, int color, AbilityEffect effect) {
        REGISTRY.put(id, new Ability(id, Ability.Kind.UNIVERSAL, null, cost, cooldown, grade, color, effect));
    }

    private static void physical(String id, int cooldown, int color, AbilityEffect effect) {
        REGISTRY.put(id, new Ability(id, Ability.Kind.PHYSICAL, null, 0, cooldown, Grade.GRADE_4, color, effect));
    }

    /** All ability ids, in registration order (used to register one sound per ability). */
    public static List<String> ids() {
        return List.copyOf(REGISTRY.keySet());
    }

    public static Ability get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    public static Collection<Ability> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Technique abilities first (in technique order), then universal/physical ones. */
    public static List<Ability> availableFor(CursedEnergyData data) {
        List<Ability> out = new ArrayList<>();
        for (String id : data.getTechnique().abilities) {
            Ability a = REGISTRY.get(id);
            if (a != null && a.usableBy(data) && data.getGrade().atLeast(a.requiredGrade())) out.add(a);
        }
        for (Ability a : REGISTRY.values()) {
            if (a.kind() != Ability.Kind.TECHNIQUE && a.usableBy(data) && data.getGrade().atLeast(a.requiredGrade())) out.add(a);
        }
        return out;
    }

    private Abilities() {}
}
