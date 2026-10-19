package com.kaboom.jjk.entity;

import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.network.FxSender;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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

/**
 * A cursed spirit. One class for every {@link CurseType}; the type is resolved from the EntityType.
 * Invisible to players who cannot perceive curses (handled by the renderer). Released curses
 * (Cursed Spirit Manipulation) have an owner and fight for them instead.
 */
public class CurseEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private CurseType curseType;
    private UUID ownerUUID;
    private int specialCooldown = 60;
    private int lifeLeft = -1;

    public CurseEntity(EntityType<? extends CurseEntity> type, Level level) {
        super(type, level);
        if (curseType().flying) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        }
        this.xpReward = curseType().xp() / 2;
    }

    public CurseType curseType() {
        if (curseType == null) curseType = ModEntities.curseTypeOf(getType());
        return curseType;
    }

    public static AttributeSupplier.Builder attributes(CurseType t) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, t.health)
                .add(Attributes.ATTACK_DAMAGE, t.damage)
                .add(Attributes.MOVEMENT_SPEED, t.speed)
                .add(Attributes.FLYING_SPEED, t.speed * 1.6)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ARMOR, t.grade.ordinal() * 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, t.grade.ordinal() * 0.12)
                .add(Attributes.ENTITY_INTERACTION_RANGE, t == CurseType.ROPPONGI_CURSE ? 4.5 : 3.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (ModEntities.curseTypeOf(getType()).flying) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            nav.setCanPassDoors(true);
            return nav;
        }
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        if (ModEntities.curseTypeOf(getType()).flying) goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        else goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Heavenly Restriction users have no cursed energy to sense: curses only notice them up close.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> ownerUUID == null && !com.kaboom.jjk.event.ArmorBonuses.curseNeutral((Player) p)
                        && (ModAttachments.get((Player) p).getRestriction() != HeavenlyRestriction.PHYSICAL || p.distanceToSqr(this) < 9)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, true, false, v -> ownerUUID == null));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, com.kaboom.jjk.npc.SorcererNpc.class, 10, true, false, v -> ownerUUID == null));
    }

    // ------------------------------------------------------------------ ownership (released curses)

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwner(LivingEntity owner, int lifetime) {
        this.ownerUUID = owner.getUUID();
        this.lifeLeft = lifetime;
        setPersistenceRequired();
    }

    private LivingEntity owner() {
        if (ownerUUID == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(ownerUUID);
        return e instanceof LivingEntity l ? l : null;
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            if (tickCount % 4 == 0) {
                level().addParticle(ModParticles.glow(ownerUUID != null ? 0xFF4B3AFF : 0xFF2A1A30, 1.4f, 20),
                        getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.01, 0);
            }
            return;
        }
        if (ownerUUID != null) {
            if (lifeLeft > 0 && --lifeLeft == 0) {
                discard();
                return;
            }
            if (tickCount % 10 == 0) {
                LivingEntity owner = owner();
                if (owner == null) {
                    discard();
                    return;
                }
                LivingEntity t = AllyTargeting.pick(this, owner);
                if (t != null) setTarget(t);
                else if (distanceToSqr(owner) > 100) getNavigation().moveTo(owner, 1.2);
            }
        }
        if (getTarget() != null && --specialCooldown <= 0) special(getTarget());
    }

    /** Signature moves per curse. */
    private void special(LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        switch (curseType()) {
            case FINGER_BEARER -> {
                if (distanceToSqr(target) < 36) {
                    for (LivingEntity e : AbilityEffects.livingAround(this, position(), 5)) {
                        if (isAlly(e)) continue;
                        e.hurt(damageSources().mobAttack(this), (float) curseType().damage * 0.8f);
                        AbilityEffects.knock(this, e, 1.6);
                    }
                    level.sendParticles(ModParticles.ring(0xFFC89488, 10f, 14), getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
                    FxSender.shakeAround(level, position(), 16, 0.7f, 12);
                    triggerAnim("attack_controller", "attack");
                }
                specialCooldown = 100;
            }
            case RAINBOW_DRAGON -> {
                Vec3 dir = target.position().subtract(position()).normalize();
                setDeltaMovement(dir.scale(1.6));
                hurtMarked = true;
                specialCooldown = 80;
            }
            case SMALLPOX_DEITY -> {
                if (distanceToSqr(target) < 100) {
                    // Graveyard: the coffin swallows the target.
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 5));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                    level.sendParticles(ModParticles.glow(0xFF5A3C28, 2f, 30), target.getX(), target.getY() + 1, target.getZ(), 30, 0.6, 1, 0.6, 0);
                    triggerAnim("attack_controller", "attack");
                }
                specialCooldown = 140;
            }
            case ROPPONGI_CURSE -> {
                if (distanceToSqr(target) < 49) {
                    Vec3 dir = position().subtract(target.position()).normalize().scale(0.8);
                    target.setDeltaMovement(target.getDeltaMovement().add(dir));
                    target.hurtMarked = true;
                }
                specialCooldown = 120;
            }
            default -> specialCooldown = 200;
        }
    }

    private boolean isAlly(LivingEntity e) {
        return e instanceof CurseEntity c && (ownerUUID == null ? c.ownerUUID == null : ownerUUID.equals(c.ownerUUID))
                || ownerUUID != null && e.getUUID().equals(ownerUUID);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("attack_controller", "attack");
        return hit;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isAlly(target) && super.canAttack(target);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !curseType().flying && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!curseType().flying) super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return ownerUUID == null && super.removeWhenFarAway(distance);
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.forCurse(curseType().id, "ambient");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.forCurse(curseType().id, "hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.forCurse(curseType().id, "death");
    }

    // ------------------------------------------------------------------ spawning

    public static boolean checkCurseSpawnRules(EntityType<CurseEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                               BlockPos pos, RandomSource random) {
        CurseType t = ModEntities.curseTypeOf(type);
        // Strong curses are rare outside negative-emotion hotspots (see CurseSpawner).
        if (t.grade.ordinal() >= 3 && random.nextFloat() > 0.25f) return false;
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    // ------------------------------------------------------------------ save

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
        lifeLeft = tag.contains("JJKLife") ? tag.getInt("JJKLife") : -1;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.setAndContinue(state.isMoving() || curseType().flying && getDeltaMovement().lengthSqr() > 0.002 ? MOVE : IDLE)));
        controllers.add(new AnimationController<>(this, "attack_controller", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
