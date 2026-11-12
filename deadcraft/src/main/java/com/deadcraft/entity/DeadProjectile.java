package com.deadcraft.entity;

import com.deadcraft.registry.ModEffects;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One projectile entity for bullets, spells and boss attacks; {@link Kind} sets the look and the effect. */
public class DeadProjectile extends ThrowableProjectile {
    public enum Kind {
        //      gravity, life, size, explosion, homing
        BULLET(0.0, 40, 0.18f, 0f, false),
        PELLET(0.0, 14, 0.14f, 0f, false),
        SHARD(0.0, 40, 0.45f, 0f, false),
        STAR(0.02, 80, 0.7f, 1.6f, false),
        ACID(0.035, 60, 0.45f, 0f, false),
        BONE(0.01, 60, 0.4f, 0f, false),
        ROCK(0.04, 80, 0.9f, 1.2f, false),
        SOUL(0.0, 60, 0.5f, 0f, true),
        EMBER(0.01, 60, 0.6f, 1.8f, false),
        CHRONO(0.0, 60, 0.5f, 0f, true);

        public final double gravity;
        public final int life;
        public final float size;
        public final float explosion;
        public final boolean homing;

        Kind(double gravity, int life, float size, float explosion, boolean homing) {
            this.gravity = gravity;
            this.life = life;
            this.size = size;
            this.explosion = explosion;
            this.homing = homing;
        }

        public String texture() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(DeadProjectile.class, EntityDataSerializers.INT);

    private float damage = 4f;

    public DeadProjectile(EntityType<? extends DeadProjectile> type, Level level) {
        super(type, level);
    }

    public DeadProjectile(Level level, LivingEntity owner, Kind kind, float damage) {
        super(ModEntities.PROJECTILE.get(), owner, level);
        setKind(kind);
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
    }

    public Kind getKind() {
        int i = entityData.get(KIND);
        Kind[] all = Kind.values();
        return i >= 0 && i < all.length ? all[i] : Kind.BULLET;
    }

    public void setKind(Kind kind) { entityData.set(KIND, kind.ordinal()); }
    public void setDamage(float damage) { this.damage = damage; }

    @Override
    protected double getDefaultGravity() {
        return getKind().gravity;
    }

    @Override
    public void tick() {
        Kind kind = getKind();
        if (kind.homing && !level().isClientSide && tickCount > 3) home();
        super.tick();
        if (tickCount > kind.life) {
            if (!level().isClientSide) discard();
            return;
        }
        if (level().isClientSide) trail(kind);
    }

    private void home() {
        Entity owner = getOwner();
        Vec3 vel = getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0e-3) return;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10),
                e -> e != owner && e.isAlive() && canHitEntity(e))) {
            Vec3 to = e.getEyePosition().subtract(position());
            double cos = to.normalize().dot(vel.normalize());
            if (cos < 0.6) continue;
            double score = to.lengthSqr() * (2 - cos);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            Vec3 desired = best.position().add(0, best.getBbHeight() * 0.5, 0).subtract(position()).normalize().scale(speed);
            setDeltaMovement(vel.scale(0.82).add(desired.scale(0.18)));
        }
    }

    private void trail(Kind kind) {
        ParticleOptions particle = switch (kind) {
            case BULLET, PELLET -> ModParticles.SPARK.get();
            case SHARD -> ModParticles.SPARK.get();
            case STAR, CHRONO -> ModParticles.STAR.get();
            case ACID -> ParticleTypes.ITEM_SLIME;
            case BONE -> ParticleTypes.WHITE_ASH;
            case ROCK -> ModParticles.ASH.get();
            case SOUL -> ModParticles.SOUL.get();
            case EMBER -> ModParticles.EMBER.get();
        };
        int count = kind == Kind.BULLET || kind == Kind.PELLET ? 1 : 2;
        for (int i = 0; i < count; i++) {
            level().addParticle(particle, getX() + (random.nextDouble() - 0.5) * kind.size * 0.5,
                    getY() + (random.nextDouble() - 0.5) * kind.size * 0.5, getZ() + (random.nextDouble() - 0.5) * kind.size * 0.5,
                    0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity owner = getOwner();
        if (target == owner) return false;
        // Monsters never shoot each other; players and their pets are fair game.
        if (owner instanceof Enemy && target instanceof Enemy) return false;
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = getOwner();
        DamageSource source = owner instanceof LivingEntity le
                ? damageSources().mobProjectile(this, le)
                : damageSources().thrown(this, owner);
        Kind kind = getKind();
        if (kind == Kind.PELLET || kind == Kind.BULLET) target.invulnerableTime = 0; // shotgun pellets all land
        if (target.hurt(source, damage) && target instanceof LivingEntity le) {
            switch (kind) {
                case ACID -> le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                case SHARD -> le.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 60, 0));
                case CHRONO -> le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                case EMBER, STAR -> le.igniteForSeconds(4);
                case SOUL -> le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                default -> {}
            }
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ModParticles.BLOOD.get(), target.getX(), target.getY(0.6), target.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel sl) {
            BlockState state = level().getBlockState(result.getBlockPos());
            Vec3 at = result.getLocation();
            if (!state.isAir()) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.15);
            }
            sl.sendParticles(ModParticles.SPARK.get(), at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.1);
            if (getKind() == Kind.BULLET || getKind() == Kind.PELLET) {
                sl.playSound(null, at.x, at.y, at.z, ModSounds.IMPACT.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.6f, 0.8f + random.nextFloat() * 0.4f);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        Kind kind = getKind();
        if (kind.explosion > 0) {
            level().explode(this, getX(), getY(), getZ(), kind.explosion, false, Level.ExplosionInteraction.NONE);
        }
        if (level() instanceof ServerLevel sl) {
            ParticleOptions burst = switch (kind) {
                case SOUL -> ModParticles.SOUL.get();
                case EMBER, STAR -> ModParticles.EMBER.get();
                case CHRONO -> ModParticles.RUNE.get();
                case ACID -> ParticleTypes.ITEM_SLIME;
                default -> null;
            };
            if (burst != null) sl.sendParticles(burst, getX(), getY(), getZ(), 18, 0.25, 0.25, 0.25, 0.08);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", getKind().ordinal());
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("Kind"));
        damage = tag.getFloat("Damage");
    }
}
