package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reusable building blocks for abilities. Each factory returns an {@link AbilityEffect}.
 * Damage is scaled by {@link CursedEnergyData#getTechniqueDamageMultiplier()} and range by
 * {@link CursedEnergyData#getRangeMultiplier()}.
 */
public final class AbilityEffects {

    // ------------------------------------------------------------------ factories

    /** Straight line attack: hits the first living entity in the crosshair. */
    public static AbilityEffect beam(double range, float damage, double knockback, int color) {
        return (player, data, ability) -> {
            double r = range * data.getRangeMultiplier();
            Optional<LivingEntity> target = lookTarget(player, r);
            Vec3 start = player.getEyePosition();
            Vec3 end = target.map(t -> t.position().add(0, t.getBbHeight() / 2, 0))
                    .orElse(start.add(player.getLookAngle().scale(r)));
            particleLine(player.serverLevel(), start, end, dust(color, 1.4f), 3);
            playAt(player, ModSounds.ABILITY_CAST.get(), 1.0f, 1.0f);
            if (target.isEmpty()) return true; // still a valid cast: you fired into the air
            LivingEntity t = target.get();
            t.hurt(JJKDamage.technique(player), (float) (damage * data.getTechniqueDamageMultiplier()));
            if (knockback > 0) knock(player, t, knockback);
            burstAt(player.serverLevel(), t.position().add(0, t.getBbHeight() / 2, 0), dust(color, 2f), 20, 0.4);
            return true;
        };
    }

    /** Area burst centred on the player. */
    public static AbilityEffect burst(double radius, float damage, double knockback, int color) {
        return (player, data, ability) -> {
            double r = radius * data.getRangeMultiplier();
            for (LivingEntity e : livingAround(player, player.position(), r)) {
                e.hurt(JJKDamage.technique(player), (float) (damage * data.getTechniqueDamageMultiplier()));
                if (knockback > 0) knock(player, e, knockback);
            }
            ring(player.serverLevel(), player.position().add(0, 0.2, 0), r, dust(color, 2f));
            burstAt(player.serverLevel(), player.position().add(0, 1, 0), ModParticles.CURSED_ENERGY.get(), 40, 0.6);
            playAt(player, ModSounds.ABILITY_IMPACT.get(), 1.2f, 0.8f);
            return true;
        };
    }

    /** Pulls everything near the aimed point toward it (Blue-style). */
    public static AbilityEffect pull(double range, double radius, float damage, int color) {
        return (player, data, ability) -> {
            Vec3 point = aimPoint(player, range * data.getRangeMultiplier());
            for (LivingEntity e : livingAround(player, point, radius)) {
                Vec3 dir = point.subtract(e.position()).normalize().scale(1.1);
                e.setDeltaMovement(e.getDeltaMovement().add(dir));
                e.hurtMarked = true;
                e.hurt(JJKDamage.technique(player), (float) (damage * data.getTechniqueDamageMultiplier()));
            }
            sphere(player.serverLevel(), point, 1.5, dust(color, 1.6f), 60);
            playAt(player, ModSounds.ABILITY_CAST.get(), 1.0f, 0.6f);
            return true;
        };
    }

    /** Applies effects to the user. */
    @SafeVarargs
    public static AbilityEffect selfBuff(int duration, int amplifier, int color, Holder<MobEffect>... effects) {
        return (player, data, ability) -> {
            for (Holder<MobEffect> effect : effects) player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
            burstAt(player.serverLevel(), player.position().add(0, 1, 0), dust(color, 1.2f), 30, 0.5);
            playAt(player, ModSounds.ABILITY_CAST.get(), 0.8f, 1.4f);
            return true;
        };
    }

    /** Applies effects to everything around the user (Cursed Speech style). */
    @SafeVarargs
    public static AbilityEffect debuffArea(double radius, int duration, int amplifier, float damage, int color, Holder<MobEffect>... effects) {
        return (player, data, ability) -> {
            List<LivingEntity> targets = livingAround(player, player.position(), radius * data.getRangeMultiplier());
            for (LivingEntity e : targets) {
                for (Holder<MobEffect> effect : effects) e.addEffect(new MobEffectInstance(effect, duration, amplifier), player);
                if (damage > 0) e.hurt(JJKDamage.technique(player), (float) (damage * data.getTechniqueDamageMultiplier()));
                burstAt(player.serverLevel(), e.position().add(0, e.getBbHeight() / 2, 0), dust(color, 1.2f), 10, 0.3);
            }
            ring(player.serverLevel(), player.position().add(0, 1.5, 0), radius, dust(color, 1.5f));
            playAt(player, ModSounds.ABILITY_CAST.get(), 1.0f, 0.5f);
            return true;
        };
    }

    /** Launches the user along their look direction. */
    public static AbilityEffect dash(double strength, int color) {
        return (player, data, ability) -> {
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * strength, Math.max(0.25, look.y * strength * 0.6), look.z * strength);
            player.hurtMarked = true;
            player.fallDistance = 0;
            burstAt(player.serverLevel(), player.position().add(0, 1, 0), dust(color, 1.2f), 25, 0.3);
            playAt(player, ModSounds.ABILITY_DASH.get(), 1.0f, 1.0f);
            return true;
        };
    }

    /** Swaps positions with the targeted entity (Boogie Woogie). */
    public static AbilityEffect swap(double range, int color) {
        return (player, data, ability) -> {
            Optional<LivingEntity> target = lookTarget(player, range * data.getRangeMultiplier());
            if (target.isEmpty()) return false;
            LivingEntity t = target.get();
            Vec3 a = player.position();
            Vec3 b = t.position();
            burstAt(player.serverLevel(), a.add(0, 1, 0), dust(color, 1.5f), 20, 0.4);
            burstAt(player.serverLevel(), b.add(0, 1, 0), dust(color, 1.5f), 20, 0.4);
            t.teleportTo(a.x, a.y, a.z);
            player.teleportTo(b.x, b.y, b.z);
            playAt(player, ModSounds.ABILITY_CLAP.get(), 1.2f, 1.0f);
            return true;
        };
    }

    /** Restores health to the user. */
    public static AbilityEffect heal(float amount, int color) {
        return (player, data, ability) -> {
            if (player.getHealth() >= player.getMaxHealth()) return false;
            player.heal(amount);
            burstAt(player.serverLevel(), player.position().add(0, 1, 0), dust(color, 1f), 25, 0.5);
            playAt(player, ModSounds.ABILITY_HEAL.get(), 0.8f, 1.2f);
            return true;
        };
    }

    /** Runs several effects in order; succeeds if any succeeded. */
    public static AbilityEffect chain(AbilityEffect... effects) {
        return (player, data, ability) -> {
            boolean any = false;
            for (AbilityEffect e : effects) any |= e.execute(player, data, ability);
            return any;
        };
    }

    // ------------------------------------------------------------------ helpers

    public static Optional<LivingEntity> lookTarget(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        HitResult block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        final Vec3 fEnd = end;
        AABB box = new AABB(start, end).inflate(1.0);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator()).stream()
                .filter(e -> e.getBoundingBox().inflate(0.4).clip(start, fEnd).isPresent())
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(start)));
    }

    public static Vec3 aimPoint(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        HitResult hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    public static List<LivingEntity> livingAround(Entity source, Vec3 center, double radius) {
        return source.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e != source && e.isAlive() && !e.isSpectator() && e.distanceToSqr(center) <= radius * radius);
    }

    public static void knock(Entity from, LivingEntity target, double strength) {
        Vec3 dir = target.position().subtract(from.position()).normalize();
        target.knockback(strength, -dir.x, -dir.z);
        target.hurtMarked = true;
    }

    public static ParticleOptions dust(int argb, float scale) {
        return new DustParticleOptions(new Vector3f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f), scale);
    }

    public static void particleLine(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, int perBlock) {
        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, (int) (delta.length() * perBlock));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(delta.scale(i / (double) steps));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    public static void burstAt(ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.05);
    }

    public static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions particle) {
        int points = (int) Math.max(16, radius * 12);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void sphere(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int count) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - (i / (double) (count - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i;
            level.sendParticles(particle, center.x + Math.cos(theta) * r * radius, center.y + y * radius,
                    center.z + Math.sin(theta) * r * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void playAt(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void playAt(ServerPlayer player, Supplier<SoundEvent> sound, float volume, float pitch) {
        playAt(player, sound.get(), volume, pitch);
    }

    private AbilityEffects() {}
}
