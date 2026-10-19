package com.kaboom.jjk.ability;

import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.particle.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Per-domain look and sure-hit behaviour. */
public enum DomainType {
    UNLIMITED_VOID("unlimited_void", 0xFF8FD8FF, 3f) {
        @Override
        void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float mult) {
            // Infinite information: the target is paralysed.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 9, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, false));
            target.addEffect(new MobEffectInstance(ModEffects.NULLIFIED, 40, 0, false, false));
            target.hurt(JJKDamage.sureHit(caster), 3f * mult);
            AbilityEffects.burstAt(level, target.getEyePosition(), ModParticles.glow(0xFFFFFFFF, 1.2f, 12), 8, 0.4);
        }
    },
    MALEVOLENT_SHRINE("malevolent_shrine", 0xFFB0101A, 7f) {
        @Override
        void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float mult) {
            // Ceaseless Dismantle and Cleave.
            target.invulnerableTime = 0;
            target.hurt(JJKDamage.sureHit(caster), 7f * mult);
            level.sendParticles(ModParticles.slash(0xFFFFFFFF, 3f, 6), target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    4, 0.5, 0.5, 0.5, 0);
        }
    },
    CHIMERA_SHADOW_GARDEN("chimera_shadow_garden", 0xFF20183A, 5f) {
        @Override
        void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float mult) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            target.hurt(JJKDamage.sureHit(caster), 5f * mult);
            AbilityEffects.burstAt(level, target.position(), ModParticles.glow(0xFF000000, 2f, 16), 12, 0.6);
        }
    },
    SELF_EMBODIMENT_OF_PERFECTION("self_embodiment_of_perfection", 0xFF6FA88A, 9f) {
        @Override
        void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float mult) {
            // Touches the soul directly.
            target.hurt(JJKDamage.sureHit(caster), 9f * mult);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1, false, false));
            AbilityEffects.burstAt(level, target.getEyePosition(), ModParticles.glow(0xFF8FC8AA, 1.5f, 14), 10, 0.4);
        }
    },
    COFFIN_OF_THE_IRON_MOUNTAIN("coffin_of_the_iron_mountain", 0xFFFF5A1A, 6f) {
        @Override
        void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float mult) {
            target.igniteForSeconds(6);
            target.hurt(JJKDamage.sureHit(caster), 6f * mult);
            AbilityEffects.burstAt(level, target.position(), ModParticles.glow(0xFFFF8A2A, 1.8f, 14), 12, 0.6);
        }
    },
    INCOMPLETE_DOMAIN("incomplete_domain", 0xFF7A6CFF, 3.5f) {
        @Override
        void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float mult) {
            target.hurt(JJKDamage.sureHit(caster), 3.5f * mult);
            AbilityEffects.burstAt(level, target.position(), ModParticles.glow(0xFF7A6CFF, 1.4f, 12), 8, 0.4);
        }
    };

    public final String id;
    public final int color;
    public final float baseDamage;

    DomainType(String id, int color, float baseDamage) {
        this.id = id;
        this.color = color;
        this.baseDamage = baseDamage;
    }

    abstract void sureHit(ServerLevel level, LivingEntity caster, LivingEntity target, float multiplier);

    public static DomainType byId(String id) {
        for (DomainType t : values()) if (t.id.equals(id)) return t;
        return INCOMPLETE_DOMAIN;
    }
}
