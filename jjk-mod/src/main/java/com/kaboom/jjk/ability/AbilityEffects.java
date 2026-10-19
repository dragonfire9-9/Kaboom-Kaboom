package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.entity.CurseType;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.entity.ShikigamiEntity;
import com.kaboom.jjk.entity.ShikigamiType;
import com.kaboom.jjk.entity.TechniqueProjectile;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
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
            return true;
        };
    }

    /** Applies effects to the user. */
    @SafeVarargs
    public static AbilityEffect selfBuff(int duration, int amplifier, int color, Holder<MobEffect>... effects) {
        return (player, data, ability) -> {
            for (Holder<MobEffect> effect : effects) player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
            burstAt(player.serverLevel(), player.position().add(0, 1, 0), dust(color, 1.2f), 30, 0.5);
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

    /** Fires a {@link TechniqueProjectile} along the player's look direction. */
    public static AbilityEffect projectile(TechniqueProjectile.Kind kind, double speed, float damage, float scale, int life, boolean pierce) {
        return (player, data, ability) -> {
            Vec3 look = player.getLookAngle();
            TechniqueProjectile p = TechniqueProjectile.create(player.level(), player, kind,
                    (float) (damage * data.getTechniqueDamageMultiplier()), scale, (int) (life * data.getRangeMultiplier()), pierce);
            Vec3 start = player.getEyePosition().add(look.scale(1.2)).subtract(0, scale * 0.4, 0);
            p.setPos(start.x, start.y, start.z);
            p.setDeltaMovement(look.scale(speed));
            player.level().addFreshEntity(p);
            return true;
        };
    }

    /** Fires {@code count} projectiles in a fan (Ember Insects). */
    public static AbilityEffect volley(TechniqueProjectile.Kind kind, int count, double speed, float damage, float scale, int life) {
        return (player, data, ability) -> {
            for (int i = 0; i < count; i++) {
                float yaw = player.getYRot() + (i - (count - 1) / 2f) * 12f;
                Vec3 dir = Vec3.directionFromRotation(player.getXRot() - 4 + player.getRandom().nextFloat() * 8, yaw);
                TechniqueProjectile p = TechniqueProjectile.create(player.level(), player, kind,
                        (float) (damage * data.getTechniqueDamageMultiplier()), scale, life, false);
                Vec3 start = player.getEyePosition().add(dir);
                p.setPos(start.x, start.y - 0.2, start.z);
                p.setDeltaMovement(dir.scale(speed));
                player.level().addFreshEntity(p);
            }
            return true;
        };
    }

    /** Drops a meteor onto the aimed point (Maximum: Meteor). */
    public static AbilityEffect meteor(float damage) {
        return (player, data, ability) -> {
            Vec3 target = aimPoint(player, 40 * data.getRangeMultiplier());
            TechniqueProjectile p = TechniqueProjectile.create(player.level(), player, TechniqueProjectile.Kind.METEOR,
                    (float) (damage * data.getTechniqueDamageMultiplier()), 3.2f, 120, false);
            p.setPos(target.x + 6, target.y + 28, target.z);
            p.setDeltaMovement(-0.25, -0.9, 0);
            player.level().addFreshEntity(p);
            com.kaboom.jjk.network.FxSender.shakeAround(player.serverLevel(), target, 40, 0.4f, 30);
            return true;
        };
    }

    /** Close-range strike that can spark into a Black Flash. */
    public static AbilityEffect melee(double range, float damage, double knockback, int color, double blackFlashBonus) {
        return (player, data, ability) -> {
            Optional<LivingEntity> target = lookTarget(player, range);
            if (target.isEmpty()) return false;
            LivingEntity t = target.get();
            float amount = (float) (damage * data.getTechniqueDamageMultiplier());
            amount = BlackFlash.tryTrigger(player, data, t, amount, blackFlashBonus);
            t.hurt(JJKDamage.technique(player), amount);
            if (knockback > 0) knock(player, t, knockback);
            burstAt(player.serverLevel(), t.position().add(0, t.getBbHeight() / 2, 0), ModParticles.spark(color, 1.6f, 8), 12, 0.3);
            playAt(player, ModSounds.ABILITY_IMPACT.get(), 1.0f, 1.2f);
            return true;
        };
    }

    /** Invisible cutting technique: slash arcs along the line, hits everything on it (Dismantle). */
    public static AbilityEffect slashLine(double range, float damage, int arcs) {
        return (player, data, ability) -> {
            double r = range * data.getRangeMultiplier();
            Vec3 start = player.getEyePosition();
            Vec3 end = aimPoint(player, r);
            ServerLevel level = player.serverLevel();
            Vec3 delta = end.subtract(start);
            for (int i = 1; i <= arcs; i++) {
                Vec3 p = start.add(delta.scale(i / (double) arcs));
                level.sendParticles(ModParticles.slash(0xFFFFFFFF, 3.5f, 6), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            }
            AABB box = new AABB(start, end).inflate(1.2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                if (e.getBoundingBox().inflate(0.8).clip(start, end).isEmpty()) continue;
                e.invulnerableTime = 0;
                e.hurt(JJKDamage.technique(player), (float) (damage * data.getTechniqueDamageMultiplier()));
                level.sendParticles(ModParticles.slash(0xFFFF4040, 2.5f, 6), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 3, 0.3, 0.3, 0.3, 0);
            }
            return true;
        };
    }

    /** Area of slashes around the aimed target (Cleave adapts to the target's toughness). */
    public static AbilityEffect cleave(double range, float baseDamage) {
        return (player, data, ability) -> {
            Optional<LivingEntity> target = lookTarget(player, range * data.getRangeMultiplier());
            if (target.isEmpty()) return false;
            LivingEntity t = target.get();
            // Cleave scales to the target: tougher bodies take proportionally more.
            float damage = (float) ((baseDamage + t.getMaxHealth() * 0.15 + t.getArmorValue() * 0.5) * data.getTechniqueDamageMultiplier());
            t.invulnerableTime = 0;
            t.hurt(JJKDamage.technique(player), damage);
            player.serverLevel().sendParticles(ModParticles.slash(0xFFFFFFFF, 4f, 7), t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(),
                    8, 0.4, 0.5, 0.4, 0);
            com.kaboom.jjk.network.FxSender.shakeAround(player.serverLevel(), t.position(), 16, 0.5f, 8);
            return true;
        };
    }

    /** Calls down a cursed lightning strike on the aimed point (Nue). */
    public static AbilityEffect lightning(double range, float damage) {
        return (player, data, ability) -> {
            Vec3 p = aimPoint(player, range * data.getRangeMultiplier());
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(player.level());
            if (bolt == null) return false;
            bolt.moveTo(p.x, p.y, p.z);
            bolt.setVisualOnly(true);
            player.level().addFreshEntity(bolt);
            for (LivingEntity e : livingAround(player, p, 3)) {
                e.hurt(JJKDamage.technique(player), (float) (damage * data.getTechniqueDamageMultiplier()));
                e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            }
            return true;
        };
    }

    /** Toggles a long-lasting effect on the caster (Infinity). */
    public static AbilityEffect toggle(Holder<MobEffect> effect, int color) {
        return (player, data, ability) -> {
            if (player.hasEffect(effect)) {
                player.removeEffect(effect);
            } else {
                player.addEffect(new MobEffectInstance(effect, 20 * 60 * 10, 0, false, false, true));
                sphere(player.serverLevel(), player.position().add(0, 1, 0), 1.4, ModParticles.glow(color, 1.2f, 16), 50);
            }
            return true;
        };
    }

    /** Draws a ring of glow particles around the caster (Simple Domain's circle). */
    public static AbilityEffect ringAround(double radius, int color) {
        return (player, data, ability) -> {
            ring(player.serverLevel(), player.position().add(0, 0.1, 0), radius, ModParticles.glow(color, 1.5f, 40));
            return true;
        };
    }

    /** Summons Ten Shadows shikigami from the caster's shadow. */
    public static AbilityEffect summon(ShikigamiType type, int count) {
        return (player, data, ability) -> {
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < count; i++) {
                ShikigamiEntity s = ModEntities.shikigami(type).create(level);
                if (s == null) return false;
                double a = player.getRandom().nextDouble() * Math.PI * 2;
                s.moveTo(player.getX() + Math.cos(a) * 1.5, player.getY() + (type.flying ? 1.5 : 0), player.getZ() + Math.sin(a) * 1.5,
                        player.getYRot(), 0);
                s.setup(player, (float) data.getTechniqueDamageMultiplier());
                level.addFreshEntity(s);
                level.sendParticles(ModParticles.glow(0xFF000000, 2.5f, 20), s.getX(), s.getY() + 0.2, s.getZ(), 30, 0.5, 0.1, 0.5, 0.02);
            }
            return true;
        };
    }

    /** Cursed Spirit Manipulation: swallow a weakened curse into your arsenal. */
    public static AbilityEffect absorbCurse(double range) {
        return (player, data, ability) -> {
            Optional<LivingEntity> target = lookTarget(player, range);
            if (target.isEmpty() || !(target.get() instanceof CurseEntity curse) || curse.getOwnerUUID() != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("jjk.message.absorb_no_target"), true);
                return false;
            }
            boolean weak = curse.getHealth() <= curse.getMaxHealth() * 0.4f
                    || curse.curseType().grade.ordinal() < data.getGrade().ordinal();
            if (!weak) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("jjk.message.absorb_too_strong"), true);
                return false;
            }
            if (!data.absorbCurse(curse.curseType().id)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("jjk.message.absorb_full"), true);
                return false;
            }
            particleLine(player.serverLevel(), curse.position().add(0, curse.getBbHeight() / 2, 0), player.getEyePosition(),
                    ModParticles.glow(0xFF4B3A5E, 1.6f, 12), 4);
            curse.discard();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("jjk.message.absorbed",
                    data.getAbsorbedCurses().size(), CursedEnergyData.MAX_ABSORBED), true);
            return true;
        };
    }

    /** Releases the most recent absorbed curse as an ally; falls back to a cursed blast if none are stored. */
    public static AbilityEffect releaseCurse(AbilityEffect fallback) {
        return (player, data, ability) -> {
            String id = data.releaseCurse();
            if (id == null) return fallback.execute(player, data, ability);
            CurseType type = CurseType.byId(id);
            CurseEntity curse = ModEntities.curse(type).create(player.level());
            if (curse == null) return false;
            Vec3 at = aimPoint(player, 4);
            curse.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
            curse.setOwner(player, 20 * 90);
            player.level().addFreshEntity(curse);
            player.serverLevel().sendParticles(ModParticles.glow(0xFF4B3AFF, 2f, 20), at.x, at.y + 1, at.z, 30, 0.5, 0.5, 0.5, 0.02);
            return true;
        };
    }

    /** Uzumaki: compresses every stored curse into one spiral blast; power scales with what was consumed. */
    public static AbilityEffect uzumaki() {
        return (player, data, ability) -> {
            List<String> curses = data.releaseAllCurses();
            float power = 18f;
            for (String id : curses) power += 6f + CurseType.byId(id).grade.ordinal() * 6f;
            return projectile(TechniqueProjectile.Kind.CURSE, 1.6, power, 1.6f + curses.size() * 0.15f, 60, true)
                    .execute(player, data, ability);
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

    /** Tinted glow particle (the name is historical: Phase 1 used vanilla dust). */
    public static ParticleOptions dust(int argb, float scale) {
        return ModParticles.glow(argb, scale, 14);
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
