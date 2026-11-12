package com.deadcraft.client.sound;

import com.deadcraft.entity.RuinRunnerEntity;
import com.deadcraft.entity.VehicleEntity;
import com.deadcraft.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Engine / thruster loop that follows a vehicle; pitch rises with throttle. */
public class VehicleSound extends AbstractTickableSoundInstance {
    private final VehicleEntity vehicle;

    public VehicleSound(VehicleEntity vehicle) {
        super(vehicle instanceof RuinRunnerEntity ? ModSounds.ENGINE.get() : ModSounds.HOVER.get(), SoundSource.NEUTRAL,
                SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.6f;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || !vehicle.isVehicle()) {
            stop();
            return;
        }
        x = vehicle.getX();
        y = vehicle.getY();
        z = vehicle.getZ();
        float t = Math.abs(vehicle.getThrottle());
        pitch = 0.75f + t * 0.75f;
        volume = 0.45f + t * 0.45f;
    }
}
