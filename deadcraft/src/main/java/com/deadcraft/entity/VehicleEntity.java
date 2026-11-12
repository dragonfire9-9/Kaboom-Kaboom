package com.deadcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Base for drivable vehicles. Vehicles are living entities so they reuse the vanilla rider
 * input pipeline (the same one horses and camels use) and sync smoothly in multiplayer.
 */
public abstract class VehicleEntity extends Mob {
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    /** Wheel / rotor spin for the renderer, client side only. */
    public float spin;
    public float spinO;

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(THROTTLE, 0f);
    }

    public float getThrottle() { return entityData.get(THROTTLE); }
    protected void setThrottle(float t) { entityData.set(THROTTLE, t); }

    protected abstract ItemStack asItem();

    @Override
    protected void registerGoals() {}

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!isVehicle()) {
            if (!level().isClientSide) player.startRiding(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Sneak-punch to pick the vehicle back up.
        if (source.getEntity() instanceof Player p && p.isShiftKeyDown() && !isVehicle()) {
            if (!level().isClientSide) {
                if (!p.getAbilities().instabuild) spawnAtLocation(asItem());
                discard();
            }
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return seatOffset();
    }

    protected abstract Vec3 seatOffset();

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity le) {
            le.yBodyRot = getYRot();
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = getCollisionHorizontalEscapeVector(getBbWidth(), passenger.getBbWidth(), getYRot() + 90f);
        Vec3 pos = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), BlockPos.containing(position().add(side)), true);
        return pos != null ? pos : super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spinO = spin;
            spin += (float) getDeltaMovement().horizontalDistance() * 1.6f * Math.signum(getThrottle() == 0 ? 1 : getThrottle());
        }
    }

    @Override
    public boolean isPushable() { return !isVehicle(); }
    @Override
    public boolean canBeLeashed() { return false; }
    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
    @Override
    public boolean requiresCustomPersistence() { return true; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}
    @Override
    public boolean isPersistenceRequired() { return true; }
    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().size() < maxPassengers(); }
    protected int maxPassengers() { return 2; }
}
