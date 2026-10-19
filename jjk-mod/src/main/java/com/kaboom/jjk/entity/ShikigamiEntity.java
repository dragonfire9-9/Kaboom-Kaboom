package com.kaboom.jjk.entity;

import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/** Ten Shadows shikigami: fights for its summoner, then melts back into the shadows. */
public class ShikigamiEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private ShikigamiType shikigamiType;
    private UUID ownerUUID;
    private int lifeLeft = 20 * 30;
    private int specialCooldown = 40;
    private float damageMultiplier = 1f;

    public ShikigamiEntity(EntityType<? extends ShikigamiEntity> type, Level level) {
        super(type, level);
        if (shikigamiType().flying) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        }
    }

    public ShikigamiType shikigamiType() {
        if (shikigamiType == null) shikigamiType = ModEntities.shikigamiTypeOf(getType());
        return shikigamiType;
    }

    public static AttributeSupplier.Builder attributes(ShikigamiType t) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, t.health)
                .add(Attributes.ATTACK_DAMAGE, t.damage)
                .add(Attributes.MOVEMENT_SPEED, t.speed)
                .add(Attributes.FLYING_SPEED, t.speed * 1.8)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, t == ShikigamiType.MAX_ELEPHANT ? 0.9 : 0.1);
    }

    public void setup(LivingEntity owner, float damageMultiplier) {
        this.ownerUUID = owner.getUUID();
        this.damageMultiplier = damageMultiplier;
        this.lifeLeft = shikigamiType().lifetime;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    private LivingEntity owner() {
        if (ownerUUID == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(ownerUUID);
        return e instanceof LivingEntity l ? l : null;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (ModEntities.shikigamiTypeOf(getType()).flying) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanFloat(true);
            return nav;
        }
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        if (ModEntities.shikigamiTypeOf(getType()).flying) goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        else goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            if (tickCount % 3 == 0) level().addParticle(ModParticles.glow(0xFF000000, 1.2f, 16), getRandomX(0.5), getY() + 0.1, getRandomZ(0.5), 0, 0.01, 0);
            return;
        }
        if (--lifeLeft <= 0 || ownerUUID == null) {
            vanish();
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || !owner.isAlive()) {
            vanish();
            return;
        }
        if (tickCount % 10 == 0) {
            LivingEntity t = AllyTargeting.pick(this, owner);
            if (t != null) setTarget(t);
            else if (distanceToSqr(owner) > 64) getNavigation().moveTo(owner, 1.3);
            if (distanceToSqr(owner) > 40 * 40) teleportTo(owner.getX(), owner.getY(), owner.getZ());
        }
        LivingEntity target = getTarget();
        if (target != null && --specialCooldown <= 0) special(target, owner);
    }

    private void special(LivingEntity target, LivingEntity owner) {
        ServerLevel level = (ServerLevel) level();
        switch (shikigamiType()) {
            case TOAD -> {
                // Tongue: drag the target to the summoner.
                if (distanceToSqr(target) < 144) {
                    Vec3 pull = owner.position().subtract(target.position()).normalize().scale(1.4);
                    target.setDeltaMovement(pull.add(0, 0.3, 0));
                    target.hurtMarked = true;
                    AbilityEffects.particleLine(level, getEyePosition(), target.getEyePosition(), ModParticles.glow(0xFFE07090, 0.8f, 8), 3);
                    triggerAnim("attack_controller", "attack");
                }
                specialCooldown = 80;
            }
            case NUE -> {
                var bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.getX(), target.getY(), target.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                target.hurt(JJKDamage.technique(owner), 6f * damageMultiplier);
                specialCooldown = 60;
            }
            case MAX_ELEPHANT -> {
                // Torrent of water from above.
                for (LivingEntity e : AbilityEffects.livingAround(this, target.position(), 4)) {
                    if (e == owner || e instanceof ShikigamiEntity) continue;
                    e.hurt(JJKDamage.technique(owner), 9f * damageMultiplier);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, -0.8, 0));
                    e.hurtMarked = true;
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, target.getX(), target.getY() + 4, target.getZ(), 120, 2, 1, 2, 0);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, target.getX(), target.getY() + 0.2, target.getZ(), 80, 2, 0.2, 2, 0.3);
                triggerAnim("attack_controller", "attack");
                specialCooldown = 120;
            }
            default -> specialCooldown = 200;
        }
    }

    private void vanish() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.glow(0xFF000000, 2f, 20), getX(), getY() + getBbHeight() / 2, getZ(), 25, 0.4, 0.4, 0.4, 0.02);
        }
        discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("attack_controller", "attack");
        return hit;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (ownerUUID != null && ownerUUID.equals(target.getUUID())) return false;
        if (target instanceof ShikigamiEntity s && ownerUUID != null && ownerUUID.equals(s.ownerUUID)) return false;
        return super.canAttack(target);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !shikigamiType().flying && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!shikigamiType().flying) super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID("JJKOwner", ownerUUID);
        tag.putInt("JJKLife", lifeLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("JJKOwner")) ownerUUID = tag.getUUID("JJKOwner");
        lifeLeft = tag.getInt("JJKLife");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.setAndContinue(state.isMoving() || shikigamiType().flying ? MOVE : IDLE)));
        controllers.add(new AnimationController<>(this, "attack_controller", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
