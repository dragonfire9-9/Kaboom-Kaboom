package com.deadcraft.client.sound;

import com.deadcraft.config.DeadConfig;
import com.deadcraft.entity.VehicleEntity;
import com.deadcraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The player's personal sound layer: gear rustle on every stride, pounding steps when running,
 * ragged breathing after a long sprint, a heartbeat when close to death, and a thud on landing.
 */
public final class MovementSounds {
    private static double strideDist;
    private static int sprintTicks, breathCooldown, recoverBreaths, heartCooldown;
    private static boolean wasOnGround = true;
    private static float fall;
    private static VehicleSound vehicleSound;

    private MovementSounds() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused()) return;
        vehicleLoop(mc, p);
        if (!DeadConfig.MOVEMENT_SOUNDS.get() || p.isSpectator()) return;
        var r = p.getRandom();

        double speed = p.getDeltaMovement().horizontalDistance();
        if (p.onGround() && !p.isPassenger() && speed > 0.02) {
            strideDist += speed;
            double stride = p.isSprinting() ? 1.55 : p.isCrouching() ? 0.9 : 1.2;
            if (strideDist > stride) {
                strideDist = 0;
                float vol = p.isCrouching() ? 0.08f : p.isSprinting() ? 0.38f : 0.22f;
                play(p, p.isSprinting() ? ModSounds.STEP_RUN.get() : ModSounds.STEP_GEAR.get(), vol, 0.9f + r.nextFloat() * 0.2f);
            }
        }

        if (p.isSprinting()) {
            sprintTicks++;
            if (sprintTicks > 60 && --breathCooldown <= 0) {
                play(p, ModSounds.BREATH.get(), 0.32f, 0.95f + r.nextFloat() * 0.1f);
                breathCooldown = 26;
            }
            if (sprintTicks > 60) recoverBreaths = 3;
        } else {
            sprintTicks = 0;
            if (recoverBreaths > 0 && --breathCooldown <= 0) {
                play(p, ModSounds.BREATH.get(), 0.22f, 0.9f);
                breathCooldown = 34;
                recoverBreaths--;
            }
        }

        float hp = p.getHealth();
        if (hp > 0 && hp <= 7f && --heartCooldown <= 0) {
            play(p, ModSounds.HEARTBEAT.get(), 0.45f + (7f - hp) * 0.06f, 1f);
            heartCooldown = (int) (10 + hp * 3);
        }

        if (p.onGround() && !wasOnGround && fall > 3f) {
            play(p, ModSounds.LAND.get(), Math.min(1f, 0.3f + fall * 0.05f), 0.9f + r.nextFloat() * 0.15f);
        }
        wasOnGround = p.onGround();
        fall = p.fallDistance;
    }

    private static void vehicleLoop(Minecraft mc, LocalPlayer p) {
        if (p.getVehicle() instanceof VehicleEntity v) {
            if (vehicleSound == null || vehicleSound.isStopped() || !mc.getSoundManager().isActive(vehicleSound)) {
                vehicleSound = new VehicleSound(v);
                mc.getSoundManager().play(vehicleSound);
            }
        }
    }

    private static void play(LocalPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
