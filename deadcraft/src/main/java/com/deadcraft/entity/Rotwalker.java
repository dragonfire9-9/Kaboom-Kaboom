package com.deadcraft.entity;

import com.deadcraft.registry.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The zombies that overrun destroyed cities. Unlike vanilla zombies they do not burn in daylight,
 * so a ruined city stays dangerous at noon.
 */
public class Rotwalker extends Zombie {
    public Rotwalker(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean hit = super.doHurtTarget(entity);
        if (hit && entity instanceof LivingEntity le && random.nextFloat() < 0.3f) {
            le.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0));
        }
        return hit;
    }

    /** Rotwalkers shamble toward anything they hear. */
    public void hearNoise(Vec3 pos, @Nullable net.minecraft.world.entity.player.Player source, double strength) {
        if (getTarget() != null) return;
        if (source != null && strength > 0.6 && !source.isCreative() && !source.isSpectator()) {
            setTarget(source);
        } else {
            getNavigation().moveTo(pos.x, pos.y, pos.z, 1.0);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return random.nextBoolean() ? ModSounds.GROWL.get() : super.getAmbientSound();
    }
}
