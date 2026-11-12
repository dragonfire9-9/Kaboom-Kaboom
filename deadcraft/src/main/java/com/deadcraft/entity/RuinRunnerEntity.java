package com.deadcraft.entity;

import com.deadcraft.registry.ModItems;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.world.Hearing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The Ruin Runner: a scrap-built buggy. W/S throttle, A/D steer. Loud: every creature nearby hears it. */
public class RuinRunnerEntity extends VehicleEntity {
    private float throttle;

    public RuinRunnerEntity(EntityType<? extends RuinRunnerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected ItemStack asItem() { return new ItemStack(ModItems.RUIN_RUNNER.get()); }

    @Override
    protected Vec3 seatOffset() { return new Vec3(0, 0.75, -0.1); }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        float forward = player.zza;
        float target = forward > 0 ? 1f : forward < 0 ? -0.45f : 0f;
        throttle = Mth.approach(throttle, target, target == 0 ? 0.04f : 0.025f);
        setThrottle(throttle);
        float speedFactor = Mth.clamp((float) getDeltaMovement().horizontalDistance() / 0.5f, 0f, 1f);
        float steer = player.xxa * 4.5f * Math.max(0.25f, speedFactor) * Math.signum(throttle == 0 ? 1 : throttle);
        setYRot(getYRot() - steer);
        yRotO = getYRot();
        setRot(getYRot(), 0);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        return new Vec3(0, 0, throttle);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isVehicle() && Math.abs(getThrottle()) > 0.05f && random.nextInt(2) == 0) {
            Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-1.3);
            level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX() + back.x, getY() + 0.6, getZ() + back.z, 0, 0.03, 0);
            if (random.nextInt(6) == 0) level().addParticle(ModParticles.EMBER.get(), getX() + back.x, getY() + 0.6, getZ() + back.z, 0, 0.05, 0);
        }
        if (!level().isClientSide && isVehicle() && tickCount % 20 == 0 && Math.abs(getThrottle()) > 0.1f) {
            Hearing.noise((ServerLevel) level(), position(), 36, getControllingPassenger() instanceof Player p ? p : null);
        }
    }
}
