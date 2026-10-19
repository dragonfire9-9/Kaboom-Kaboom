package com.kaboom.jjk.effect;

import com.kaboom.jjk.ability.AbilityEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Prison Realm seal: pins the target in place and draws the cube around it. */
public class SealedEffect extends MobEffect {
    public SealedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (entity.level() instanceof ServerLevel level && entity.tickCount % 10 == 0) {
            AABB box = entity.getBoundingBox().inflate(0.4);
            var dust = AbilityEffects.dust(0xFF2A1A3A, 1.0f);
            // cube edges
            double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
            for (double x : xs) for (double y : ys) AbilityEffects.particleLine(level, new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ), dust, 2);
            for (double x : xs) for (double z : zs) AbilityEffects.particleLine(level, new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z), dust, 2);
            for (double y : ys) for (double z : zs) AbilityEffects.particleLine(level, new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z), dust, 2);
        }
        return true;
    }
}
