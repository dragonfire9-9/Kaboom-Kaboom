package com.deadcraft.entity;

import com.deadcraft.DeadCraft;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** All twenty peaceful creatures. Breed them with anything in #deadcraft:creature_food. */
public class DeadAnimal extends Animal implements Animated {
    public static final TagKey<Item> FOOD = TagKey.create(Registries.ITEM, DeadCraft.id("creature_food"));

    private DeadMob mob;

    public DeadAnimal(EntityType<? extends DeadAnimal> type, Level level) {
        super(type, level);
        if (spec().flies()) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
        }
    }

    public final net.minecraft.world.entity.AnimationState idleAnim = new net.minecraft.world.entity.AnimationState();
    public final net.minecraft.world.entity.AnimationState flyAnim = new net.minecraft.world.entity.AnimationState();
    private final net.minecraft.world.entity.AnimationState unused = new net.minecraft.world.entity.AnimationState();

    @Override public net.minecraft.world.entity.AnimationState idleAnimation() { return idleAnim; }
    @Override public net.minecraft.world.entity.AnimationState attackAnimation() { return unused; }
    @Override public net.minecraft.world.entity.AnimationState castAnimation() { return unused; }
    @Override public net.minecraft.world.entity.AnimationState flyAnimation() { return flyAnim; }

    public DeadMob spec() {
        if (mob == null) mob = ModEntities.mobOf(getType());
        return mob;
    }

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
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 10f, 1.1, 1.5));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.15, Ingredient.of(FOOD), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        if (spec().flies()) goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        else goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(FOOD);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return (AgeableMob) getType().create(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            idleAnim.startIfStopped(tickCount);
            flyAnim.animateWhen(!onGround(), tickCount);
        }
        if (level().isClientSide && spec().has(DeadMob.Features.GLOWS) && random.nextInt(8) == 0) {
            level().addParticle(spec() == DeadMob.CANDLEWICK_TOAD ? ModParticles.EMBER.get() : ModParticles.SOUL.get(),
                    getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.01, 0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !spec().flies() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!spec().flies()) super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (spec().flies() && !onGround()) {
            // Gentle lift so flyers don't plummet between path nodes.
            setDeltaMovement(getDeltaMovement().multiply(1, 0.6, 1));
        }
        super.travel(input);
    }

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.ambient(spec()).value(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.hurt(spec()).value(); }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.death(spec()).value(); }

    @Override
    public float getVoicePitch() {
        float base = spec().renderScale < 0.8f ? 1.35f : spec().renderScale > 1.1f ? 0.7f : 1.0f;
        return base * (isBaby() ? 1.4f : 1f) * (0.9f + random.nextFloat() * 0.2f);
    }

    /** Peaceful creatures spawn on any solid ground with a little light, including DeadCraft dimensions. */
    public static boolean checkSpawn(EntityType<? extends DeadAnimal> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        return below.isSolid() && level.getRawBrightness(pos, 0) > 2;
    }
}
