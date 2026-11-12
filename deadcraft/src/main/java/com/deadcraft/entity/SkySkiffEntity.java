package com.deadcraft.entity;

import com.deadcraft.registry.ModItems;
import com.deadcraft.registry.ModParticles;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The Sky Skiff: a hover craft. Fly where you look; W/S throttle. Settles gently when empty. */
public class SkySkiffEntity extends VehicleEntity {
    private float throttle;

    public SkySkiffEntity(EntityType<? extends SkySkiffEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected ItemStack asItem() { return new ItemStack(ModItems.SKY_SKIFF.get()); }

    @Override
    protected Vec3 seatOffset() { return new Vec3(0, 0.55, 0.1); }

    @Override
    protected int maxPassengers() { return 1; }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        float target = player.zza > 0 ? 1f : player.zza < 0 ? -0.3f : 0f;
        throttle = Mth.approach(throttle, target, 0.03f);
        setThrottle(throttle);
        float yaw = Mth.approachDegrees(getYRot(), player.getYRot(), 6f);
        setRot(yaw, Mth.clamp(player.getXRot(), -45f, 45f) * 0.5f);
        yRotO = getYRot();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public void travel(Vec3 input) {
        if (!isControlledByLocalInstance()) {
            super.travel(input);
            return;
        }
        Vec3 vel = getDeltaMovement();
        if (getControllingPassenger() instanceof Player player) {
            Vec3 look = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), -60f, 60f), getYRot());
            double max = 0.9;
            Vec3 desired = look.scale(throttle * max);
            vel = vel.add(desired.subtract(vel).scale(0.08));
        } else {
            // Unpiloted: drift down until hovering just above the ground.
            boolean nearGround = !level().noCollision(this, getBoundingBox().move(0, -1.2, 0));
            vel = new Vec3(vel.x * 0.9, nearGround ? vel.y * 0.5 : Math.max(vel.y - 0.01, -0.25), vel.z * 0.9);
        }
        vel = vel.add(0, Math.sin(tickCount * 0.12) * 0.004, 0);
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);
        calculateEntityAnimation(false);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (level().isClientSide) {
            Vec3 back = Vec3.directionFromRotation(0, getYRot()).scale(-1.1);
            Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90).scale(0.8);
            for (int s = -1; s <= 1; s += 2) {
                if (random.nextInt(isVehicle() ? 1 : 4) == 0) {
                    level().addParticle(ModParticles.SOUL.get(), getX() + back.x + side.x * s, getY() + 0.2, getZ() + back.z + side.z * s,
                            -back.x * 0.05, -0.02, -back.z * 0.05);
                }
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }
}
