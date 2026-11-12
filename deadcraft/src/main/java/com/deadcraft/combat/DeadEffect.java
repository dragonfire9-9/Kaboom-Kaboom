package com.deadcraft.combat;

import com.deadcraft.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class DeadEffect extends MobEffect {
    public enum Behaviour { NONE, BLEED, INFEST }

    private final Behaviour behaviour;

    public DeadEffect(MobEffectCategory category, int color, Behaviour behaviour) {
        super(category, color);
        this.behaviour = behaviour;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return switch (behaviour) {
            case BLEED -> duration % Math.max(5, 20 >> amplifier) == 0;
            case INFEST -> duration % 30 == 0;
            case NONE -> false;
        };
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        switch (behaviour) {
            case BLEED -> {
                entity.hurt(entity.damageSources().magic(), 1.0f);
                level.sendParticles(ModParticles.BLOOD.get(), entity.getX(), entity.getY(0.6), entity.getZ(),
                        6 + amplifier * 3, entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.25, entity.getBbWidth() * 0.3, 0.05);
            }
            case INFEST -> {
                if (entity.getHealth() > 1.0f) entity.hurt(entity.damageSources().magic(), 1.0f + amplifier);
                if (entity instanceof Player player) player.causeFoodExhaustion(1.5f);
            }
            case NONE -> {}
        }
        return true;
    }
}
