package com.kaboom.jjk.entity;

import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.network.FxSender;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * One entity class for every technique projectile. Its look is pure particles (spawned client-side from
 * {@link #tick()}), and its behaviour is chosen by {@link Kind}.
 */
public class TechniqueProjectile extends Projectile {
    public enum Kind {
        /** Lapse Blue: slow orb that drags everything nearby into itself. */
        BLUE(0xFF2F7BFF, 1.4f),
        /** Reversal Red: fast bolt that detonates into a repulsive shockwave. */
        RED(0xFFFF2A2A, 0.9f),
        /** Hollow Purple: huge orb that erases everything along its path. */
        HOLLOW_PURPLE(0xFF9B30FF, 2.6f),
        /** Fuga: flaming arrow. */
        FUGA(0xFFFF6A00, 0.8f),
        /** Maximum: Meteor — falls from the sky. */
        METEOR(0xFFFF3A00, 3.2f),
        /** Piercing Blood: hypersonic blood jet. */
        BLOOD(0xFFB0102A, 0.35f),
        /** Ember Insects: small burning bugs. */
        EMBER(0xFFFF8A2A, 0.35f),
        /** Released cursed spirit / Uzumaki. */
        CURSE(0xFF4B2A6E, 1.2f),
        /** Generic cursed energy blast (Ultra Cannon, etc). */
        ENERGY(0xFF8AD7FF, 0.8f);

        public final int color;
        public final float size;

        Kind(int color, float size) {
            this.color = color;
            this.size = size;
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TechniqueProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(TechniqueProjectile.class, EntityDataSerializers.FLOAT);

    private float damage = 6;
    private int life;
    private int maxLife = 60;
    private boolean pierce;
    private final Set<Integer> hit = new HashSet<>();

    public TechniqueProjectile(EntityType<? extends TechniqueProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static TechniqueProjectile create(Level level, LivingEntity owner, Kind kind, float damage, float scale, int maxLife, boolean pierce) {
        TechniqueProjectile p = new TechniqueProjectile(ModEntities.TECHNIQUE_PROJECTILE.get(), level);
        p.setOwner(owner);
        p.entityData.set(KIND, kind.ordinal());
        p.entityData.set(SCALE, scale);
        p.damage = damage;
        p.maxLife = maxLife;
        p.pierce = pierce;
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(SCALE, 1f);
    }

    public Kind kind() {
        return Kind.values()[Mth.clamp(entityData.get(KIND), 0, Kind.values().length - 1)];
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    @Override
    public void tick() {
        super.tick();
        Kind kind = kind();
        if (level().isClientSide()) {
            spawnTrail(kind);
        } else {
            if (++life > maxLife) {
                if (kind == Kind.BLUE) collapseBlue();
                discard();
                return;
            }
            serverBehaviour(kind);
        }

        Vec3 motion = getDeltaMovement();
        HitResult result = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (result.getType() != HitResult.Type.MISS && !level().isClientSide()) {
            if (result instanceof BlockHitResult && (kind == Kind.HOLLOW_PURPLE || kind == Kind.BLUE)) {
                // pass through: Purple erases, Blue drifts
            } else {
                onHit(result);
            }
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        if (kind == Kind.METEOR) setDeltaMovement(motion.add(0, -0.06, 0));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !hit.contains(target.getId()) && !(target instanceof TechniqueProjectile);
    }

    // ------------------------------------------------------------------ server behaviour

    private void serverBehaviour(Kind kind) {
        ServerLevel level = (ServerLevel) level();
        switch (kind) {
            case BLUE -> {
                for (LivingEntity e : AbilityEffects.livingAround(owner(), position(), 6 * scale())) {
                    Vec3 pull = position().subtract(e.position()).normalize().scale(0.35);
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull));
                    e.hurtMarked = true;
                    if (life % 10 == 0) e.hurt(JJKDamage.technique(owner()), damage * 0.25f);
                }
            }
            case HOLLOW_PURPLE -> {
                if (JJKConfig.HOLLOW_PURPLE_BREAKS_BLOCKS.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    eraseBlocks(level, 2.5 * scale() / Kind.HOLLOW_PURPLE.size);
                }
                for (LivingEntity e : AbilityEffects.livingAround(owner(), position(), 2.5)) {
                    if (hit.add(e.getId())) e.hurt(JJKDamage.technique(owner()), damage);
                }
                if (life % 4 == 0) level.playSound(null, blockPosition(), ModSounds.ABILITY_IMPACT.get(), SoundSource.PLAYERS, 0.6f, 0.5f);
            }
            case EMBER -> {
                // gentle homing toward the closest living thing ahead
                LivingEntity target = null;
                double best = 64;
                for (LivingEntity e : AbilityEffects.livingAround(owner(), position(), 8)) {
                    double d = e.distanceToSqr(this);
                    if (d < best) { best = d; target = e; }
                }
                if (target != null) {
                    Vec3 want = target.getEyePosition().subtract(position()).normalize().scale(getDeltaMovement().length());
                    setDeltaMovement(getDeltaMovement().lerp(want, 0.15));
                }
            }
            default -> {}
        }
    }

    private void eraseBlocks(ServerLevel level, double radius) {
        BlockPos c = blockPosition();
        int r = Mth.ceil(radius);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distSqr(c) > radius * radius) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) continue;
            level.removeBlock(p, false);
        }
    }

    private void collapseBlue() {
        ServerLevel level = (ServerLevel) level();
        AbilityEffects.sphere(level, position(), 1.5, ModParticles.glow(Kind.BLUE.color, 2f, 10), 40);
        for (LivingEntity e : AbilityEffects.livingAround(owner(), position(), 3)) e.hurt(JJKDamage.technique(owner()), damage);
        level.playSound(null, blockPosition(), ModSounds.ABILITY_IMPACT.get(), SoundSource.PLAYERS, 1f, 1.4f);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        hit.add(target.getId());
        Kind kind = kind();
        switch (kind) {
            case RED, FUGA, METEOR -> detonate(kind);
            case BLUE, HOLLOW_PURPLE -> {} // handled by area logic
            default -> {
                target.hurt(JJKDamage.technique(owner()), damage);
                if (kind == Kind.EMBER || kind == Kind.FUGA) target.igniteForSeconds(4);
                if (target instanceof LivingEntity l) AbilityEffects.knock(this, l, kind == Kind.ENERGY ? 1.2 : 0.4);
                if (!pierce) impactAndDiscard(kind);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Kind kind = kind();
        if (kind == Kind.RED || kind == Kind.FUGA || kind == Kind.METEOR) detonate(kind);
        else impactAndDiscard(kind);
    }

    private void detonate(Kind kind) {
        ServerLevel level = (ServerLevel) level();
        float radius = switch (kind) {
            case RED -> 5f;
            case FUGA -> 6f;
            default -> 8f;
        } * Math.max(1f, scale() / kind.size);
        for (LivingEntity e : AbilityEffects.livingAround(owner(), position(), radius)) {
            double falloff = 1 - Math.min(1, e.distanceTo(this) / radius) * 0.6;
            e.hurt(JJKDamage.technique(owner()), (float) (damage * falloff));
            AbilityEffects.knock(this, e, kind == Kind.RED ? 3.0 : 1.5);
            if (kind != Kind.RED) e.igniteForSeconds(6);
        }
        boolean grief = JJKConfig.ABILITIES_BREAK_BLOCKS.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        level.explode(this, owner() == null ? null : JJKDamage.technique(owner()), null, getX(), getY(), getZ(),
                kind == Kind.RED ? 1.5f : 2.5f, kind != Kind.RED && grief,
                grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        level.sendParticles(ModParticles.ring(kind.color, radius * 2.5f, 14), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        AbilityEffects.sphere(level, position(), radius * 0.4, ModParticles.glow(kind.color, 2.2f, 14), 80);
        level.sendParticles(ModParticles.spark(0xFFFFFFFF, 2f, 10), getX(), getY(), getZ(), 30, radius * 0.3, radius * 0.3, radius * 0.3, 0.2);
        FxSender.shakeAround(level, position(), 24, kind == Kind.RED ? 0.6f : 1.0f, 12);
        discard();
    }

    private void impactAndDiscard(Kind kind) {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ModParticles.spark(kind.color, 1.5f, 8), getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.1);
        discard();
    }

    private Entity owner() {
        Entity o = getOwner();
        return o != null ? o : this;
    }

    // ------------------------------------------------------------------ client visuals

    private void spawnTrail(Kind kind) {
        Level level = level();
        float s = scale();
        double x = getX(), y = getY() + getBbHeight() / 2, z = getZ();
        var r = random;
        switch (kind) {
            case BLUE, HOLLOW_PURPLE, CURSE -> {
                // swirling shell
                int n = kind == Kind.HOLLOW_PURPLE ? 14 : 8;
                for (int i = 0; i < n; i++) {
                    double a = (tickCount * 0.5 + i * Math.PI * 2 / n);
                    double rad = s * 0.6;
                    double yy = (r.nextDouble() - 0.5) * s;
                    level.addParticle(ModParticles.glow(kind.color, s * 1.6f, 8), x + Math.cos(a) * rad, y + yy, z + Math.sin(a) * rad, 0, 0, 0);
                }
                level.addParticle(ModParticles.glow(0xFFFFFFFF, s * 2.5f, 3), x, y, z, 0, 0, 0);
                if (kind == Kind.HOLLOW_PURPLE) {
                    level.addParticle(ModParticles.glow(0xFFFF2A2A, s * 1.5f, 6), x + r.nextGaussian() * s * 0.3, y, z + r.nextGaussian() * s * 0.3, 0, 0, 0);
                    level.addParticle(ModParticles.glow(0xFF2F7BFF, s * 1.5f, 6), x + r.nextGaussian() * s * 0.3, y, z + r.nextGaussian() * s * 0.3, 0, 0, 0);
                }
            }
            case RED -> {
                level.addParticle(ModParticles.glow(kind.color, 3f, 6), x, y, z, 0, 0, 0);
                level.addParticle(ModParticles.spark(0xFFFFA0A0, 1.2f, 6), x, y, z, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05);
            }
            case FUGA, METEOR, EMBER -> {
                for (int i = 0; i < (kind == Kind.METEOR ? 10 : 3); i++) {
                    level.addParticle(ModParticles.glow(i % 2 == 0 ? kind.color : 0xFFFFD040, s * 2f, 10),
                            x + r.nextGaussian() * s * 0.3, y + r.nextGaussian() * s * 0.3, z + r.nextGaussian() * s * 0.3, 0, 0.02, 0);
                }
                if (kind == Kind.METEOR) level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.05, 0);
            }
            default -> level.addParticle(ModParticles.glow(kind.color, s * 2.5f, 8), x, y, z, 0, 0, 0);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected AABB makeBoundingBox() {
        float s = entityData == null ? 0.6f : Math.max(0.3f, scale() * 0.5f);
        return new AABB(getX() - s, getY(), getZ() - s, getX() + s, getY() + s * 2, getZ() + s);
    }
}
