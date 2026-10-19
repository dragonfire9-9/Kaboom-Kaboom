package com.kaboom.jjk.npc;

import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.entity.CurseType;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.entity.TechniqueProjectile;
import com.kaboom.jjk.particle.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** One of Geto's followers: a hostile sorcerer who fires cursed energy and calls up small curses. */
public class CurseUserEntity extends Monster {
    private int castCooldown = 40;
    private int summonCooldown = 200;

    public CurseUserEntity(EntityType<? extends CurseUserEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ARMOR, 4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, SorcererNpc.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        LivingEntity target = getTarget();
        if (target == null) return;
        if (--castCooldown <= 0 && hasLineOfSight(target)) {
            Vec3 dir = target.getEyePosition().subtract(getEyePosition()).normalize();
            TechniqueProjectile p = TechniqueProjectile.create(level(), this, TechniqueProjectile.Kind.CURSE, 7f, 0.8f, 40, false);
            Vec3 start = getEyePosition().add(dir);
            p.setPos(start.x, start.y - 0.3, start.z);
            p.setDeltaMovement(dir.scale(1.1));
            level().addFreshEntity(p);
            castCooldown = 50 + random.nextInt(30);
        }
        if (--summonCooldown <= 0) {
            CurseEntity fly = ModEntities.curse(CurseType.FLY_HEAD).create(level());
            if (fly != null) {
                fly.moveTo(getX(), getY() + 1.5, getZ(), getYRot(), 0);
                fly.setOwner(this, 20 * 30);
                level().addFreshEntity(fly);
                ((ServerLevel) level()).sendParticles(ModParticles.glow(0xFF4B2A6E, 2f, 20), getX(), getY() + 1.5, getZ(), 20, 0.3, 0.3, 0.3, 0.02);
            }
            summonCooldown = 300 + random.nextInt(200);
        }
    }
}
