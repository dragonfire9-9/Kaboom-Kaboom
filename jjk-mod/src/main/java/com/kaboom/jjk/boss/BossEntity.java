package com.kaboom.jjk.boss;

import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.network.ClientFxPayload;
import com.kaboom.jjk.network.FxSender;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Multi-phase boss with a boss bar and its own music. Behaviour is delegated to a {@link BossBrain}.
 * Phase 1 above 66% health, phase 2 above 33%, phase 3 below. Each transition is a short invulnerable roar.
 */
public class BossEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("cast");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");
    private static final int MUSIC_INTERVAL = 20 * 24;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar;
    private BossType bossType;
    private BossBrain brain;
    private int phase = 1;
    private int roarTicks;
    private int musicTimer;
    /** Mahoraga summoned by a Ten Shadows user: hunts the summoner (or fights for them once tamed). */
    private UUID summoner;
    private boolean allied;
    public int cooldownA, cooldownB, cooldownC;

    public BossEntity(EntityType<? extends BossEntity> type, Level level) {
        super(type, level);
        this.bossBar = new ServerBossEvent(Component.translatable("entity.jjk." + bossType().id),
                bossType().barColor, BossEvent.BossBarOverlay.NOTCHED_10);
        this.xpReward = 300;
        setPersistenceRequired();
    }

    public BossType bossType() {
        if (bossType == null) bossType = ModEntities.bossTypeOf(getType());
        return bossType;
    }

    private BossBrain brain() {
        if (brain == null) brain = BossBrains.create(bossType());
        return brain;
    }

    public int phase() {
        return phase;
    }

    public static AttributeSupplier.Builder attributes(BossType t) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, t.health)
                .add(Attributes.ATTACK_DAMAGE, t.damage)
                .add(Attributes.MOVEMENT_SPEED, t.speed)
                .add(Attributes.ARMOR, t.armor)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> !allied && !((Player) p).isCreative()));
    }

    // ------------------------------------------------------------------ summoned Mahoraga

    public void setSummoner(LivingEntity summoner, boolean allied) {
        this.summoner = summoner.getUUID();
        this.allied = allied;
        if (!allied) setTarget(summoner);
    }

    public UUID getSummoner() {
        return summoner;
    }

    public boolean isAllied() {
        return allied;
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());

        int newPhase = getHealth() > getMaxHealth() * 0.66f ? 1 : getHealth() > getMaxHealth() * 0.33f ? 2 : 3;
        if (newPhase > phase) enterPhase(newPhase);
        if (roarTicks > 0) {
            roarTicks--;
            getNavigation().stop();
            return;
        }

        if (allied && summoner != null && tickCount % 10 == 0) {
            LivingEntity owner = ((ServerLevel) level()).getEntity(summoner) instanceof LivingEntity l ? l : null;
            if (owner == null || tickCount > 20 * 90) {
                discard();
                return;
            }
            LivingEntity t = com.kaboom.jjk.entity.AllyTargeting.pick(this, owner);
            if (t != null) setTarget(t);
        }

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            brain().tick(this, target, phase);
            if (--musicTimer <= 0) {
                musicTimer = MUSIC_INTERVAL;
                level().playSound(null, blockPosition(), ModSounds.forBossMusic(bossType().id), SoundSource.RECORDS, 4f, 1f);
            }
        }
    }

    private void enterPhase(int newPhase) {
        phase = newPhase;
        roarTicks = 30;
        triggerAnim("action", "roar");
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ModParticles.ring(bossType().color | 0xFF000000, 14f, 20), getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        AbilityEffects.sphere(level, position().add(0, 1, 0), 3, ModParticles.glow(bossType().color | 0xFF000000, 2.5f, 20), 80);
        level.playSound(null, blockPosition(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 3f, 0.8f);
        for (ServerPlayer p : level.players()) {
            if (p.distanceTo(this) < 48) {
                FxSender.send(p, new ClientFxPayload(ClientFxPayload.Fx.SHAKE, 1.5f, 25, 0, ""));
                p.sendSystemMessage(Component.translatable("jjk.boss." + bossType().id + ".phase" + newPhase).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            }
        }
        brain().onPhaseChange(this, newPhase);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (roarTicks > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, brain().modifyIncomingDamage(this, source, amount));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("action", "attack");
        return hit;
    }

    public void playCast() {
        triggerAnim("action", "cast");
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (allied && summoner != null && summoner.equals(target.getUUID())) return false;
        return super.canAttack(target);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer killer) {
            com.kaboom.jjk.event.JJKAdvancements.award(killer, "boss_" + bossType().id);
            if (bossType() == BossType.MAHORAGA && summoner != null && summoner.equals(killer.getUUID()) && !allied) {
                // Exorcising the shikigami you summoned completes the ritual: Mahoraga is now yours.
                com.kaboom.jjk.data.ModAttachments.get(killer).unlockBonusAbility("mahoraga_tamed");
                killer.sendSystemMessage(Component.translatable("jjk.message.mahoraga_tamed").withStyle(ChatFormatting.GOLD));
                com.kaboom.jjk.network.SyncJJKDataPayload.sync(killer);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return bossType() == BossType.JOGO || super.fireImmune();
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("JJKPhase", phase);
        if (summoner != null) tag.putUUID("JJKSummoner", summoner);
        tag.putBoolean("JJKAllied", allied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phase = Math.max(1, tag.getInt("JJKPhase"));
        if (tag.hasUUID("JJKSummoner")) summoner = tag.getUUID("JJKSummoner");
        allied = tag.getBoolean("JJKAllied");
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> state.setAndContinue(state.isMoving() ? MOVE : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK).triggerableAnim("cast", CAST).triggerableAnim("roar", ROAR));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
