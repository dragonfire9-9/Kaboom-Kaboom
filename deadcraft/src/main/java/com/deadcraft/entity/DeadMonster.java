package com.deadcraft.entity;

import com.deadcraft.entity.ai.DeadMeleeGoal;
import com.deadcraft.entity.ai.InvestigateNoiseGoal;
import com.deadcraft.entity.ai.KeepDistanceGoal;
import com.deadcraft.registry.ModEffects;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModItems;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Every hostile creature and boss. Behaviour is selected from {@link DeadMob.Ability}, so one
 * class covers fifteen hostiles and five bosses without duplicating AI plumbing.
 */
public class DeadMonster extends Monster implements RangedAttackMob, Animated {
    private static final EntityDataAccessor<Boolean> DECOY = SynchedEntityData.defineId(DeadMonster.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(DeadMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST_TICKS = SynchedEntityData.defineId(DeadMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GAZE_FROZEN = SynchedEntityData.defineId(DeadMonster.class, EntityDataSerializers.BOOLEAN);

    // Deliberately no field initialisers: registerGoals() and createNavigation() run inside the
    // Mob constructor, before field initialisers would execute.
    private DeadMob mob;
    @Nullable private ServerBossEvent bossEvent;
    private int primaryCooldown;
    private int secondaryCooldown;
    private int meleeCooldown;
    private int decoyLife;
    private int stingerCooldown;
    private boolean calledForHelp;
    private boolean rewound;
    private boolean roared;
    @Nullable private Vec3 noisePos;
    private int noiseTicks;

    public DeadMonster(EntityType<? extends DeadMonster> type, Level level) {
        super(type, level);
        DeadMob spec = spec();
        if (spec.flies()) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            this.setNoGravity(true);
        }
        this.xpReward = spec.isBoss() ? 250 : (int) Math.max(5, spec.health / 4);
        this.primaryCooldown = 60;
        this.secondaryCooldown = 100;
        if (spec.isBoss() && !level.isClientSide) {
            this.bossEvent = (ServerBossEvent) new ServerBossEvent(getDisplayName(), bossColor(spec), BossEvent.BossBarOverlay.NOTCHED_10)
                    .setDarkenScreen(true)
                    .setCreateWorldFog(spec.ability == DeadMob.Ability.DIM_LIGHT_DEMON);
        }
    }

    // Client-side animation clocks for the keyframe rig (see client/model/DeadRigModel).
    public final net.minecraft.world.entity.AnimationState idleAnim = new net.minecraft.world.entity.AnimationState();
    public final net.minecraft.world.entity.AnimationState attackAnim = new net.minecraft.world.entity.AnimationState();
    public final net.minecraft.world.entity.AnimationState castAnim = new net.minecraft.world.entity.AnimationState();
    public final net.minecraft.world.entity.AnimationState flyAnim = new net.minecraft.world.entity.AnimationState();
    private int lastCastTicks;

    @Override public net.minecraft.world.entity.AnimationState idleAnimation() { return idleAnim; }
    @Override public net.minecraft.world.entity.AnimationState attackAnimation() { return attackAnim; }
    @Override public net.minecraft.world.entity.AnimationState castAnimation() { return castAnim; }
    @Override public net.minecraft.world.entity.AnimationState flyAnimation() { return flyAnim; }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) attackAnim.start(tickCount);
        else super.handleEntityEvent(id);
    }

    public DeadMob spec() {
        if (mob == null) mob = ModEntities.mobOf(getType());
        return mob;
    }

    private static BossEvent.BossBarColor bossColor(DeadMob mob) {
        return switch (mob.ability) {
            case FRACTURE -> BossEvent.BossBarColor.BLUE;
            case CHRONOS -> BossEvent.BossBarColor.YELLOW;
            case SKELETON_KING -> BossEvent.BossBarColor.WHITE;
            case DIM_LIGHT_DEMON -> BossEvent.BossBarColor.PURPLE;
            case PETRIFIED -> BossEvent.BossBarColor.GREEN;
            case HIVE_MOTHER -> BossEvent.BossBarColor.YELLOW;
            case WAILING_BRIDE -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    // ---- Synced state ------------------------------------------------------------------------
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DECOY, false);
        builder.define(PHASE, 0);
        builder.define(CAST_TICKS, 0);
        builder.define(GAZE_FROZEN, false);
    }

    public boolean isDecoy() { return entityData.get(DECOY); }
    public int getPhase() { return entityData.get(PHASE); }
    /** Ticks remaining on the current spell/special animation (drives the model's casting pose). */
    public int getCastTicks() { return entityData.get(CAST_TICKS); }
    public boolean isGazeFrozen() { return entityData.get(GAZE_FROZEN); }
    private void startCast(int ticks) { entityData.set(CAST_TICKS, ticks); }

    public void makeDecoy() {
        entityData.set(DECOY, true);
        decoyLife = 200;
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1);
        setHealth(1);
    }

    // ---- AI ----------------------------------------------------------------------------------
    @Override
    protected PathNavigation createNavigation(Level level) {
        if (spec().flies()) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            nav.setCanPassDoors(true);
            return nav;
        }
        return super.createNavigation(level);
    }

    @Override
    protected void registerGoals() {
        DeadMob spec = spec();
        if (!spec.flies()) goalSelector.addGoal(0, new FloatGoal(this));
        switch (spec.ability) {
            case BANDIT, BANDIT_CAPTAIN -> goalSelector.addGoal(2, new KeepDistanceGoal(this, 1.0, 6, 14));
            case WRETCHED, STAR_FALL -> goalSelector.addGoal(2, new KeepDistanceGoal(this, 1.0, 5, 12));
            case GHOST -> {} // ghosts drift straight through walls; handled in customServerAiStep
            case HOLLOW -> goalSelector.addGoal(2, new DeadMeleeGoal(this, 1.7, true));
            default -> goalSelector.addGoal(2, new DeadMeleeGoal(this, spec.isBoss() ? 1.1 : 1.2, true));
        }
        goalSelector.addGoal(4, new InvestigateNoiseGoal(this, 1.0));
        if (spec.flies()) goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        else goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, DeadMonster.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- Hearing (voice chat, text chat, footsteps, gunshots) ------------------------------
    /** How far this creature can hear, as a multiple of the noise's base radius. */
    public double hearingMultiplier() {
        return switch (spec().ability) {
            case FORGOTTEN -> 2.0;
            case HOLLOW, GHOST, DEPART -> 1.5;
            case CHRONOS, DIM_LIGHT_DEMON -> 1.5;
            default -> 1.0;
        };
    }

    /**
     * Called by {@link com.deadcraft.world.Hearing} when a sound reaches this creature.
     * Loud or close noise makes it hunt the source; quieter noise makes it come and look.
     */
    public void hearNoise(Vec3 pos, @Nullable Player source, double strength) {
        if (isDecoy()) return;
        LivingEntity target = getTarget();
        if (source != null && strength > 0.55 && canAttack(source) && !source.isCreative() && !source.isSpectator()) {
            if (target == null || target.distanceToSqr(this) > source.distanceToSqr(this)) {
                setTarget(source);
                if (spec().ability == DeadMob.Ability.FORGOTTEN || spec().ability == DeadMob.Ability.HOLLOW) {
                    source.addEffect(new MobEffectInstance(ModEffects.DREAD, 200, 0));
                }
            }
            return;
        }
        if (target == null) {
            noisePos = pos;
            noiseTicks = 200;
        }
    }

    @Nullable
    public Vec3 getNoisePos() { return noiseTicks > 0 ? noisePos : null; }
    public void clearNoise() { noisePos = null; noiseTicks = 0; }

    // ---- Tick --------------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            idleAnim.startIfStopped(tickCount);
            int cast = getCastTicks();
            if (cast > 0 && lastCastTicks == 0) castAnim.start(tickCount);
            lastCastTicks = cast;
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (noiseTicks > 0) noiseTicks--;
        if (primaryCooldown > 0) primaryCooldown--;
        if (secondaryCooldown > 0) secondaryCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;
        if (stingerCooldown > 0) stingerCooldown--;
        if (getCastTicks() > 0) entityData.set(CAST_TICKS, getCastTicks() - 1);

        if (isDecoy() && --decoyLife <= 0) {
            vanish();
            return;
        }
        if (bossEvent != null) {
            bossEvent.setProgress(getHealth() / getMaxHealth());
            if (getPhase() == 0 && getHealth() < getMaxHealth() * 0.5f) enterPhaseTwo();
        }

        LivingEntity target = getTarget();
        if (target != null && !target.isAlive()) target = null;
        if (target != null && spec().isBoss() && !roared) {
            roared = true;
            roar();
        }
        runAbility(target);
    }

    private void runAbility(@Nullable LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        double dist = target == null ? Double.MAX_VALUE : distanceTo(target);
        boolean phase2 = getPhase() > 0;
        switch (spec().ability) {
            case HOLLOW -> tickHollow(target);
            case SEVERED, PARASITE -> {
                if (target != null && onGround() && dist > 2.5 && dist < 8 && primaryCooldown == 0) {
                    leapAt(target, spec().ability == DeadMob.Ability.PARASITE ? 0.9 : 0.75);
                    primaryCooldown = spec().ability == DeadMob.Ability.PARASITE ? 35 : 60;
                }
            }
            case DEPART -> {
                if (target != null && dist < 28 && primaryCooldown == 0) {
                    teleportBehind(target);
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                    primaryCooldown = 120;
                }
            }
            case SCORCHED -> {
                if (tickCount % 20 == 0) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2), e -> e != this && !(e instanceof DeadMonster))) {
                        e.igniteForSeconds(2);
                    }
                }
            }
            case ILLUSION -> {
                if (target != null && !isDecoy() && primaryCooldown == 0) {
                    spawnDecoys(2);
                    primaryCooldown = 180;
                }
            }
            case BANDIT -> {
                if (target != null && dist < 20 && primaryCooldown == 0 && hasLineOfSight(target)) {
                    shoot(DeadProjectile.Kind.BULLET, target, 3.0f, 5f, 4f);
                    playSound(ModSounds.REVOLVER.get(), 2f, 0.9f + random.nextFloat() * 0.2f);
                    com.deadcraft.world.Hearing.noise(level, position(), 40, null);
                    primaryCooldown = 30;
                }
            }
            case BANDIT_CAPTAIN -> {
                if (target != null && !calledForHelp) {
                    calledForHelp = true;
                    playSound(ModSounds.SHOUT.get(), 2f, 0.8f);
                    summon(DeadMob.BANDIT, 2, 4);
                }
                if (target != null && dist < 14 && primaryCooldown == 0 && hasLineOfSight(target)) {
                    for (int i = 0; i < 5; i++) shoot(DeadProjectile.Kind.PELLET, target, 2.6f, 12f, 2.5f);
                    playSound(ModSounds.SHOTGUN.get(), 2.5f, 0.9f);
                    com.deadcraft.world.Hearing.noise(level, position(), 48, null);
                    primaryCooldown = 45;
                }
            }
            case WRETCHED -> {
                if (target != null && dist < 14 && primaryCooldown == 0 && hasLineOfSight(target)) {
                    startCast(15);
                    shoot(DeadProjectile.Kind.ACID, target, 1.1f, 2f, 5f);
                    playSound(ModSounds.CHITTER.get(), 1.5f, 0.6f);
                    primaryCooldown = 50;
                }
                if (target != null && dist < 2.2 && meleeCooldown == 0) {
                    doHurtTarget(target);
                    meleeCooldown = 25;
                }
            }
            case GHOST -> tickGhost(target);
            case BLOOD_DEMON -> {
                if (getHealth() < getMaxHealth() * 0.5f && tickCount % 40 == 0) {
                    addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
                    level.sendParticles(ModParticles.BLOOD.get(), getX(), getY(1.0), getZ(), 12, 0.4, 0.4, 0.4, 0.1);
                }
            }
            case STAR_FALL -> {
                if (target != null && dist < 24 && primaryCooldown == 0) {
                    startCast(30);
                    starRain(target, 3, 2.5, DeadProjectile.Kind.STAR, 6f);
                    primaryCooldown = 80;
                }
            }
            case COLLAPSE -> {
                if (target != null && dist < 4.5 && primaryCooldown == 0 && onGround()) {
                    slam(4.5, 8f, 1.2);
                    primaryCooldown = 100;
                }
            }
            case BUTCHER -> {
                if (target != null && dist > 5 && dist < 14 && primaryCooldown == 0 && onGround()) {
                    addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 2));
                    playSound(ModSounds.BOSS_ROAR.get(), 1.5f, 1.4f);
                    leapAt(target, 0.7);
                    primaryCooldown = 120;
                }
            }
            case GRAVEWYRM -> tickBurrow(target, dist);
            case HANGED -> {
                if (target != null && dist > 3 && dist < 16 && primaryCooldown == 0 && hasLineOfSight(target)) {
                    Vec3 pull = position().subtract(target.position()).normalize().scale(0.9);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.25, pull.z));
                    target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    level.sendParticles(ModParticles.SOUL.get(), target.getX(), target.getY(0.5), target.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
                    playSound(ModSounds.WAIL.get(), 1.5f, 0.7f);
                    primaryCooldown = 90;
                }
            }
            case MIRE_HAG -> {
                if (target != null && dist < 14 && primaryCooldown == 0 && hasLineOfSight(target)) {
                    startCast(15);
                    for (int i = 0; i < 3; i++) shoot(DeadProjectile.Kind.ACID, target, 1.0f, 8f, 4f);
                    primaryCooldown = 60;
                }
                if (target != null && secondaryCooldown == 0) {
                    startCast(20);
                    summon(DeadMob.PARASITE, 2, 3);
                    secondaryCooldown = 300;
                }
            }
            case LAMPLIGHTER -> {
                if (target != null && dist < 10 && primaryCooldown == 0) {
                    startCast(20);
                    for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(10), p -> !p.isCreative() && !p.isSpectator())) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    }
                    level.sendParticles(ModParticles.EMBER.get(), getX(), getY(1.0), getZ(), 40, 1.5, 1, 1.5, 0.05);
                    primaryCooldown = 160;
                }
            }
            // ---------------- Bosses ----------------
            case HIVE_MOTHER -> {
                if (target != null && primaryCooldown == 0) {
                    startCast(25);
                    summon(DeadMob.PARASITE, phase2 ? 4 : 2, 4);
                    playSound(ModSounds.CHITTER.get(), 3f, 0.5f);
                    primaryCooldown = phase2 ? 120 : 180;
                }
                if (target != null && dist < 18 && secondaryCooldown == 0 && hasLineOfSight(target)) {
                    for (int i = 0; i < (phase2 ? 6 : 4); i++) shoot(DeadProjectile.Kind.ACID, target, 1.2f, 14f, 6f);
                    secondaryCooldown = 60;
                }
                if (target != null && dist < 5 && meleeCooldown == 0 && onGround()) {
                    slam(5, 12f, 1.2);
                    meleeCooldown = 90;
                }
                if (phase2 && target != null && dist > 6 && dist < 14 && onGround() && tickCount % 100 == 0) leapAt(target, 1.2);
            }
            case WAILING_BRIDE -> {
                if (target != null && primaryCooldown == 0 && dist < 12) {
                    startCast(30);
                    for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(12), p -> !p.isCreative() && !p.isSpectator())) {
                        Vec3 away = p.position().subtract(position()).normalize().scale(1.4);
                        p.setDeltaMovement(away.x, 0.5, away.z);
                        p.hurtMarked = true;
                        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                        p.hurt(damageSources().mobAttack(this), phase2 ? 8f : 5f);
                    }
                    level.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
                    level.sendParticles(ModParticles.SOUL.get(), getX(), getY(1.0), getZ(), 60, 3, 1, 3, 0.1);
                    playSound(ModSounds.WAIL.get(), 4f, 0.5f);
                    Fx.shake(level, position(), 24, 0.6f);
                    primaryCooldown = phase2 ? 90 : 140;
                }
                if (target != null && secondaryCooldown == 0 && dist > 7) {
                    teleportBehind(target);
                    secondaryCooldown = 160;
                }
                if (phase2 && !calledForHelp) {
                    calledForHelp = true;
                    summon(DeadMob.GHOST, 3, 5);
                }
            }
            case FRACTURE -> {
                if (target != null && primaryCooldown == 0) {
                    startCast(20);
                    shardNova(phase2 ? 16 : 10, 8f);
                    primaryCooldown = phase2 ? 60 : 85;
                }
                if (target != null && dist < 6 && secondaryCooldown == 0 && onGround()) {
                    slam(6, 14f, 1.6);
                    secondaryCooldown = 140;
                }
            }
            case CHRONOS -> {
                if (tickCount % 20 == 0) {
                    for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(14), p -> !p.isCreative() && !p.isSpectator())) {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, phase2 ? 2 : 1));
                        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 1));
                    }
                }
                if (target != null && primaryCooldown == 0 && hasLineOfSight(target)) {
                    startCast(15);
                    for (int i = 0; i < (phase2 ? 5 : 3); i++) shoot(DeadProjectile.Kind.CHRONO, target, 1.4f, 8f, 9f);
                    playSound(ModSounds.CAST.get(), 2f, 0.6f);
                    primaryCooldown = 50;
                }
                if (target != null && secondaryCooldown == 0 && dist > 6) {
                    teleportBehind(target);
                    secondaryCooldown = 140;
                }
                if (!rewound && getHealth() < getMaxHealth() * 0.3f) {
                    rewound = true;
                    setHealth(getMaxHealth() * 0.6f);
                    playSound(ModSounds.BOSS_PHASE.get(), 3f, 1.4f);
                    level.sendParticles(ModParticles.RUNE.get(), getX(), getY(0.5), getZ(), 80, 1.5, 2, 1.5, 0.05);
                    Fx.shake(level, position(), 32, 0.8f);
                }
            }
            case SKELETON_KING -> {
                if (target != null && primaryCooldown == 0 && hasLineOfSight(target)) {
                    startCast(15);
                    for (int i = 0; i < (phase2 ? 7 : 5); i++) shoot(DeadProjectile.Kind.BONE, target, 1.8f, 10f, 7f);
                    playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 3f, 0.5f);
                    primaryCooldown = 40;
                }
                if (secondaryCooldown == 0 && target != null) {
                    int nearby = level.getEntitiesOfClass(Skeleton.class, getBoundingBox().inflate(24)).size();
                    if (nearby < 6) summonVanillaSkeletons(3);
                    secondaryCooldown = 300;
                }
                if (phase2 && target != null && dist > 5 && dist < 16 && onGround() && tickCount % 80 == 0) {
                    leapAt(target, 1.4);
                }
            }
            case DIM_LIGHT_DEMON -> {
                if (primaryCooldown == 0 && target != null) {
                    for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(28), p -> !p.isCreative() && !p.isSpectator())) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                    }
                    level.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
                    playSound(ModSounds.BOSS_ROAR.get(), 3f, 0.6f);
                    primaryCooldown = 100;
                }
                if (secondaryCooldown == 0 && target != null) {
                    startCast(30);
                    starRain(target, phase2 ? 12 : 8, 5, DeadProjectile.Kind.EMBER, 7f);
                    secondaryCooldown = 120;
                }
                if (phase2 && !calledForHelp) {
                    calledForHelp = true;
                    summon(DeadMob.BLOOD_DEMON, 2, 5);
                }
            }
            case PETRIFIED -> {
                if (tickCount % 10 == 0) {
                    for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(22), p -> !p.isCreative() && !p.isSpectator())) {
                        if (isLookedAtBy(p)) {
                            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 2));
                        }
                    }
                }
                if (target != null && primaryCooldown == 0 && dist > 5 && hasLineOfSight(target)) {
                    startCast(20);
                    shoot(DeadProjectile.Kind.ROCK, target, 1.3f, 1f, 14f);
                    primaryCooldown = phase2 ? 40 : 60;
                }
                if (target != null && dist < 6 && secondaryCooldown == 0 && onGround()) {
                    slam(7, 16f, 1.8);
                    secondaryCooldown = 120;
                }
                if (phase2 && !calledForHelp) {
                    calledForHelp = true;
                    summon(DeadMob.COLLAPSE, 2, 5);
                }
            }
            default -> {}
        }
    }

    // ---- Ability building blocks --------------------------------------------------------------
    /** The Hollow only moves while nobody is watching it. */
    private void tickHollow(@Nullable LivingEntity target) {
        boolean watched = false;
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(48), p -> !p.isSpectator())) {
            if (isLookedAtBy(p)) {
                watched = true;
                if (stingerCooldown == 0 && p instanceof ServerPlayer sp && distanceTo(p) < 20) {
                    sp.playNotifySound(ModSounds.STINGER.get(), SoundSource.HOSTILE, 1.0f, 0.9f + random.nextFloat() * 0.2f);
                    stingerCooldown = 600;
                }
            }
        }
        entityData.set(GAZE_FROZEN, watched);
        if (watched) {
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (target != null) getLookControl().setLookAt(target, 360, 360);
        }
    }

    private int burrowTicks;

    /** The Gravewyrm sinks into the ground, travels unseen and bursts up beneath its target. */
    private void tickBurrow(@Nullable LivingEntity target, double dist) {
        ServerLevel level = (ServerLevel) level();
        if (burrowTicks > 0) {
            burrowTicks--;
            if (tickCount % 4 == 0) level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK, getBlockStateOn()), getX(), getY(), getZ(), 6, 0.4, 0.1, 0.4, 0.05);
            if (burrowTicks == 0) {
                setInvisible(false);
                if (target != null) {
                    moveTo(target.getX(), target.getY(), target.getZ());
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 0.9, 0));
                    target.hurtMarked = true;
                    target.hurt(damageSources().mobAttack(this), (float) spec().attack);
                    target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 100, 0));
                }
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK, getBlockStateOn()), getX(), getY(), getZ(), 40, 0.8, 0.4, 0.8, 0.2);
                playSound(net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
                Fx.shake(level, position(), 10, 0.4f);
            }
            return;
        }
        if (target != null && dist > 4 && dist < 20 && primaryCooldown == 0 && onGround()) {
            setInvisible(true);
            burrowTicks = 30;
            primaryCooldown = 160;
            playSound(net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.6f);
        }
    }

    private void tickGhost(@Nullable LivingEntity target) {
        if (target == null) {
            noPhysics = false;
            return;
        }
        noPhysics = true;
        Vec3 to = target.getEyePosition().subtract(position().add(0, getBbHeight() * 0.5, 0));
        Vec3 dir = to.lengthSqr() > 1.0e-4 ? to.normalize() : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().scale(0.7).add(dir.scale(0.09)));
        getLookControl().setLookAt(target, 30, 30);
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f);
        yBodyRot = getYRot();
        if (distanceToSqr(target) < 3.5 && meleeCooldown == 0) {
            doHurtTarget(target);
            meleeCooldown = 25;
        }
    }

    public boolean isLookedAtBy(Player player) {
        Vec3 view = player.getViewVector(1.0f).normalize();
        Vec3 to = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double len = to.length();
        if (len < 1.0e-3) return true;
        double dot = view.dot(to.scale(1.0 / len));
        return dot > 0.82 && player.hasLineOfSight(this);
    }

    private void leapAt(LivingEntity target, double power) {
        Vec3 dir = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ()).normalize();
        setDeltaMovement(dir.x * power, 0.42 + power * 0.1, dir.z * power);
        hasImpulse = true;
        playSound(ModSounds.CHITTER.get(), 1.2f, 1.3f);
    }

    private void teleportBehind(LivingEntity target) {
        Vec3 look = target.getLookAngle().multiply(1, 0, 1).normalize();
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ModParticles.SOUL.get(), getX(), getY(0.5), getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        for (int i = 0; i < 6; i++) {
            double d = 2.5 + random.nextDouble() * 1.5;
            double x = target.getX() - look.x * d;
            double z = target.getZ() - look.z * d;
            if (randomTeleport(x, target.getY(), z, false)) break;
        }
        level.sendParticles(ModParticles.SOUL.get(), getX(), getY(0.5), getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        playSound(ModSounds.BLINK.get(), 1.5f, 0.7f);
    }

    private void spawnDecoys(int count) {
        ServerLevel level = (ServerLevel) level();
        for (int i = 0; i < count; i++) {
            DeadMonster copy = (DeadMonster) getType().create(level);
            if (copy == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            copy.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getYRot(), 0);
            copy.makeDecoy();
            copy.setTarget(getTarget());
            level.addFreshEntity(copy);
            level.sendParticles(ModParticles.RUNE.get(), copy.getX(), copy.getY(0.5), copy.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
        }
        playSound(ModSounds.WAIL.get(), 1.5f, 1.5f);
    }

    private void vanish() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.RUNE.get(), getX(), getY(0.5), getZ(), 25, 0.3, 0.8, 0.3, 0.05);
        }
        playSound(ModSounds.BLINK.get(), 1f, 1.6f);
        discard();
    }

    public void shoot(DeadProjectile.Kind kind, LivingEntity target, float speed, float inaccuracy, float damage) {
        DeadProjectile p = new DeadProjectile(level(), this, kind, damage);
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY(0.5) - p.getY();
        if (kind.gravity > 0) dy += Math.sqrt(dx * dx + dz * dz) * kind.gravity * 6.0;
        p.shoot(dx, dy, dz, speed, inaccuracy);
        level().addFreshEntity(p);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        shoot(DeadProjectile.Kind.BONE, target, 1.6f, 6f, 5f);
    }

    private void starRain(LivingEntity target, int count, double spread, DeadProjectile.Kind kind, float damage) {
        playSound(ModSounds.CAST.get(), 2f, 0.5f);
        for (int i = 0; i < count; i++) {
            DeadProjectile p = new DeadProjectile(level(), this, kind, damage);
            double ox = (random.nextDouble() - 0.5) * 2 * spread;
            double oz = (random.nextDouble() - 0.5) * 2 * spread;
            p.setPos(target.getX() + ox, target.getY() + 14 + random.nextDouble() * 6, target.getZ() + oz);
            p.setDeltaMovement((random.nextDouble() - 0.5) * 0.1, -1.1 - random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.1);
            level().addFreshEntity(p);
        }
    }

    private void shardNova(int count, float damage) {
        playSound(ModSounds.BOSS_SLAM.get(), 2f, 1.4f);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            DeadProjectile p = new DeadProjectile(level(), this, DeadProjectile.Kind.SHARD, damage);
            p.setPos(getX(), getY(0.5), getZ());
            p.shoot(Math.cos(a), 0.05, Math.sin(a), 1.2f, 0f);
            level().addFreshEntity(p);
        }
    }

    private void slam(double radius, float damage, double knock) {
        ServerLevel level = (ServerLevel) level();
        startCast(15);
        playSound(ModSounds.BOSS_SLAM.get(), 3f, spec().isBoss() ? 0.7f : 1.0f);
        level.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 0.1, getZ(), 1, 0, 0, 0, 0);
        BlockState below = level.getBlockState(blockPosition().below());
        if (!below.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below),
                    getX(), getY() + 0.2, getZ(), 80, radius * 0.4, 0.2, radius * 0.4, 0.3);
        }
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(radius, 2, radius),
                e -> e != this && !(e instanceof DeadMonster) && e.isAlive());
        for (LivingEntity e : hit) {
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.push(away.x * knock, 0.5 + knock * 0.2, away.z * knock);
                e.hurtMarked = true;
            }
        }
        Fx.shake(level, position(), radius * 4, spec().isBoss() ? 1.0f : 0.5f);
    }

    private void summon(DeadMob what, int count, double radius) {
        ServerLevel level = (ServerLevel) level();
        for (int i = 0; i < count; i++) {
            Mob m = ModEntities.type(what).create(level);
            if (m == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos pos = BlockPos.containing(getX() + Math.cos(a) * radius, getY(), getZ() + Math.sin(a) * radius);
            m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            m.setTarget(getTarget());
            level.addFreshEntity(m);
            level.sendParticles(ModParticles.SOUL.get(), m.getX(), m.getY(0.5), m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
        }
    }

    private void summonVanillaSkeletons(int count) {
        ServerLevel level = (ServerLevel) level();
        for (int i = 0; i < count; i++) {
            Skeleton s = EntityType.SKELETON.create(level);
            if (s == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos pos = BlockPos.containing(getX() + Math.cos(a) * 4, getY(), getZ() + Math.sin(a) * 4);
            s.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            s.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            s.setTarget(getTarget());
            level.addFreshEntity(s);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.getX(), s.getY(0.5), s.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        }
        playSound(ModSounds.BOSS_PHASE.get(), 2f, 1.2f);
    }

    private void roar() {
        playSound(ModSounds.BOSS_ROAR.get(), 4f, 1f);
        if (level() instanceof ServerLevel level) Fx.shake(level, position(), 40, 0.7f);
    }

    private void enterPhaseTwo() {
        entityData.set(PHASE, 1);
        playSound(ModSounds.BOSS_PHASE.get(), 4f, 0.8f);
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.EMBER.get(), getX(), getY(0.5), getZ(), 120, 2, 2, 2, 0.2);
        Fx.shake(level, position(), 48, 1.2f);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 0, false, false));
        if (spec().ability == DeadMob.Ability.FRACTURE) summon(DeadMob.CRACKED, 3, 5);
    }

    // ---- Combat hooks -------------------------------------------------------------------------
    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean hit = super.doHurtTarget(entity);
        level().broadcastEntityEvent(this, (byte) 4);
        if (hit) {
            playSound(ModSounds.HIT.get(), 1f, 0.7f + random.nextFloat() * 0.3f);
            if (entity instanceof LivingEntity le) applyHitEffects(le);
        }
        return hit;
    }

    private void applyHitEffects(LivingEntity victim) {
        switch (spec().ability) {
            case SEVERED -> victim.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 100, 0));
            case CRACKED -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            case SCORCHED -> victim.igniteForSeconds(5);
            case PARASITE -> victim.addEffect(new MobEffectInstance(ModEffects.INFESTED, 120, 0));
            case FORGOTTEN -> {
                victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                victim.addEffect(new MobEffectInstance(ModEffects.DREAD, 300, 0));
            }
            case GHOST -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            case BLOOD_DEMON, DIM_LIGHT_DEMON -> {
                heal((float) spec().attack * 0.35f);
                victim.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 100, 1));
            }
            case ROTWALKER -> victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
            case CHRONOS -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
            case BUTCHER -> {
                victim.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 120, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            case GRAVEWYRM -> victim.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 80, 0));
            case HANGED -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            case MIRE_HAG -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            case LAMPLIGHTER -> victim.igniteForSeconds(4);
            case HIVE_MOTHER -> victim.addEffect(new MobEffectInstance(ModEffects.INFESTED, 160, 1));
            case WAILING_BRIDE -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            default -> {}
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDecoy() && !level().isClientSide && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            vanish();
            return false;
        }
        if (spec().ability == DeadMob.Ability.PETRIFIED && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.5f;
        }
        if (spec().ability == DeadMob.Ability.GHOST && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && !(source.getDirectEntity() instanceof DeadProjectile)) {
            return false; // arrows pass straight through ghosts; spells and bullets do not
        }
        boolean result = super.hurt(source, amount);
        if (result && spec().ability == DeadMob.Ability.CRACKED && random.nextFloat() < 0.3f && level() instanceof ServerLevel) {
            shardNova(6, 4f);
        }
        if (result && level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.BLOOD.get(), getX(), getY(0.6), getZ(), 6, getBbWidth() * 0.3, getBbHeight() * 0.2, getBbWidth() * 0.3, 0.12);
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level) || isDecoy()) return;
        if (spec().ability == DeadMob.Ability.COLLAPSE) {
            level.explode(this, getX(), getY(0.5), getZ(), 2.5f, Level.ExplosionInteraction.NONE);
        }
        if (spec().isBoss()) {
            level.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 0.2, getZ(), 2, 0, 0, 0, 0);
            level.sendParticles(ModParticles.SOUL.get(), getX(), getY(0.5), getZ(), 200, 2, 3, 2, 0.15);
            level.sendParticles(ModParticles.STAR.get(), getX(), getY(0.5), getZ(), 80, 2, 3, 2, 0.3);
            Fx.shake(level, position(), 64, 1.5f);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return !isDecoy() && super.shouldDropLoot();
    }

    @Override
    public boolean shouldDropExperience() {
        return !isDecoy() && super.shouldDropExperience();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !spec().flies() && !spec().isBoss() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof DeadMonster || other instanceof Rotwalker || super.isAlliedTo(other);
    }

    // ---- Equipment & spawning -----------------------------------------------------------------
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, type, data);
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return result;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        switch (spec().ability) {
            case BANDIT -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.RUSTED_REVOLVER.get()));
            case BANDIT_CAPTAIN -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.DOOMSDAY_SHOTGUN.get()));
            case SKELETON_KING -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BONE_CLEAVER.get()));
            default -> {}
        }
        setDropChance(EquipmentSlot.MAINHAND, spec().isBoss() ? 0f : 0.04f);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return spec().isBoss() || super.requiresCustomPersistence();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !spec().isBoss() && !isDecoy() && super.removeWhenFarAway(distance);
    }

    // ---- Boss bar ------------------------------------------------------------------------------
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossEvent != null) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        if (bossEvent != null) bossEvent.setName(getDisplayName());
    }

    // ---- Persistence ---------------------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", getPhase());
        tag.putBoolean("Rewound", rewound);
        tag.putBoolean("CalledForHelp", calledForHelp);
        tag.putBoolean("Decoy", isDecoy());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, tag.getInt("Phase"));
        rewound = tag.getBoolean("Rewound");
        calledForHelp = tag.getBoolean("CalledForHelp");
        if (tag.getBoolean("Decoy")) discard(); // decoys never survive a reload
        if (bossEvent != null) bossEvent.setName(getDisplayName());
    }

    // ---- Sounds --------------------------------------------------------------------------------
    @Override
    protected SoundEvent getAmbientSound() {
        if (spec().ability == DeadMob.Ability.HOLLOW) return null; // The Hollow is silent
        return ModSounds.ambient(spec()).value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.hurt(spec()).value(); }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.death(spec()).value(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (spec().archetype == DeadMob.Archetype.BRUTE) {
            playSound(ModSounds.STONE_STEP.get(), spec().isBoss() ? 1.2f : 0.6f, spec().isBoss() ? 0.6f : 0.9f);
        } else if (!spec().flies()) {
            super.playStepSound(pos, state);
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return spec().isBoss() ? 160 : 240;
    }

    @Override
    protected float getSoundVolume() {
        return spec().isBoss() ? 3f : 1f;
    }

    // ---- Client visuals ------------------------------------------------------------------------
    private void clientParticles() {
        RandomSource r = random;
        double w = getBbWidth() * 0.6;
        switch (spec().ability) {
            case SCORCHED, DIM_LIGHT_DEMON -> {
                if (r.nextInt(3) == 0) level().addParticle(ModParticles.EMBER.get(), getRandomX(w), getRandomY(), getRandomZ(w), 0, 0.03, 0);
            }
            case GHOST, ILLUSION, DEPART -> {
                if (r.nextInt(4) == 0) level().addParticle(ModParticles.SOUL.get(), getRandomX(w), getRandomY(), getRandomZ(w), 0, 0.01, 0);
            }
            case STAR_FALL, CHRONOS -> {
                if (r.nextInt(3) == 0) level().addParticle(ModParticles.STAR.get(), getRandomX(w), getRandomY(), getRandomZ(w), 0, 0.01, 0);
            }
            case FRACTURE -> {
                if (r.nextInt(2) == 0) level().addParticle(ModParticles.SPARK.get(), getRandomX(w), getRandomY(), getRandomZ(w), 0, 0.02, 0);
            }
            case BLOOD_DEMON -> {
                if (r.nextInt(6) == 0) level().addParticle(ModParticles.BLOOD.get(), getRandomX(w), getY(0.8), getRandomZ(w), 0, 0, 0);
            }
            case CRACKED, COLLAPSE, PETRIFIED -> {
                if (r.nextInt(8) == 0) level().addParticle(ModParticles.ASH.get(), getRandomX(w), getRandomY(), getRandomZ(w), 0, -0.01, 0);
            }
            default -> {}
        }
        if (getCastTicks() > 0 && r.nextInt(2) == 0) {
            level().addParticle(ModParticles.RUNE.get(), getRandomX(1.2), getY() + 0.1, getRandomZ(1.2), 0, 0.05, 0);
        }
    }
}
