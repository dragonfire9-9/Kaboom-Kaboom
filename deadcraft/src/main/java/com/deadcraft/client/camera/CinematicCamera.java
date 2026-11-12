package com.deadcraft.client.camera;

import com.deadcraft.config.DeadConfig;
import com.deadcraft.network.ClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The cinematic camera. Walking adds a slow, heavy sway; running adds a quicker, tighter one.
 * On top of that: leaning into strafes, a spring-damped dip on landing, a roll when dodging,
 * gun recoil and screen shake. The same state drives first-person weapon sway.
 */
public final class CinematicCamera {
    private static long lastNanos;
    private static float time;
    private static float walkAmp, sprintBlend, lean;
    private static float landOffset, landVelocity;
    private static float dodgeRoll;
    private static float recoil, recoilVel;
    private static float localTrauma;
    private static boolean wasOnGround = true;
    private static float lastFall;
    private static float lastYaw, lastPitch, swayX, swayY;
    private static float handSprint;

    private CinematicCamera() {}

    public static void recoil(float amount) {
        recoilVel += amount * 0.9f;
        localTrauma = Math.min(1f, localTrauma + amount * 0.02f);
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.setXRot(p.getXRot() - amount * 0.35f);
            p.setYRot(p.getYRot() + (float) (Math.random() - 0.5) * amount * 0.15f);
        }
    }

    public static void addShake(float amount) {
        localTrauma = Math.min(1.5f, localTrauma + amount);
    }

    public static void dodge(float side) {
        dodgeRoll = side == 0 ? 0f : 9f * Math.signum(side);
        landVelocity += 4f;
    }

    /** Called once per frame from the camera event. */
    private static float step() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Mth.clamp((now - lastNanos) / 1.0e9f, 0f, 0.1f);
        lastNanos = now;
        time += dt;
        return dt;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isSpectator()) return;
        float dt = step();
        float pt = (float) event.getPartialTick();
        float intensity = DeadConfig.CAMERA_SWAY.get().floatValue();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) intensity *= 0.35f;

        double speed = p.getDeltaMovement().horizontalDistance();
        boolean moving = p.onGround() && speed > 0.02 && !p.isPassenger();
        walkAmp = Mth.approach(walkAmp, moving ? 1f : 0f, dt * 3.5f);
        sprintBlend = Mth.approach(sprintBlend, p.isSprinting() && moving ? 1f : 0f, dt * 2.5f);
        float strafe = p.input != null ? p.input.leftImpulse : 0f;
        lean = Mth.lerp(Math.min(1f, dt * 6f), lean, -strafe * (1.2f + sprintBlend));

        float walk = Mth.lerp(pt, p.walkDistO, p.walkDist);
        float phase = walk * Mth.PI;
        // Walking: slow and heavy. Running: faster, tighter, with a forward lean.
        float rollAmp = Mth.lerp(sprintBlend, 0.9f, 1.4f) * walkAmp;
        float pitchAmp = Mth.lerp(sprintBlend, 0.55f, 0.9f) * walkAmp;
        float roll = Mth.sin(phase) * rollAmp + lean;
        float pitch = Mth.abs(Mth.cos(phase)) * pitchAmp + sprintBlend * 1.2f;
        float yaw = Mth.sin(phase * 0.5f) * 0.45f * walkAmp * (1f - 0.6f * sprintBlend);
        // Idle breathing
        float idle = 1f - walkAmp;
        pitch += Mth.sin(time * 1.3f) * 0.25f * idle;
        roll += Mth.sin(time * 0.6f) * 0.18f * idle;

        // Landing spring
        if (p.onGround() && !wasOnGround && lastFall > 1.2f) landVelocity += Math.min(14f, lastFall * 2.2f);
        wasOnGround = p.onGround();
        lastFall = p.fallDistance > 0 ? p.fallDistance : lastFall * 0.5f;
        landVelocity -= (landOffset * 90f + landVelocity * 11f) * dt;
        landOffset += landVelocity * dt;
        pitch += landOffset;

        // Dodge roll and gun recoil springs
        dodgeRoll = Mth.lerp(Math.min(1f, dt * 7f), dodgeRoll, 0f);
        roll += dodgeRoll;
        recoilVel -= (recoil * 140f + recoilVel * 16f) * dt;
        recoil += recoilVel * dt;
        pitch -= recoil;

        // Screen shake (trauma model: offset = trauma² × noise)
        float trauma = Math.min(1.5f, ClientState.shake + localTrauma);
        ClientState.shake = Math.max(0, ClientState.shake - dt * 1.2f);
        localTrauma = Math.max(0, localTrauma - dt * 1.8f);
        float tt = trauma * trauma * 5f;
        float sx = (float) (Math.sin(time * 37.1) + Math.sin(time * 21.7)) * 0.5f;
        float sy = (float) (Math.sin(time * 41.3 + 1) + Math.sin(time * 17.9)) * 0.5f;
        float sr = (float) (Math.sin(time * 29.3 + 2));

        event.setRoll(event.getRoll() + (roll + sr * tt * 0.6f) * intensity + sr * tt * 0.4f * (1 - intensity));
        event.setPitch(event.getPitch() + pitch * intensity + sy * tt);
        event.setYaw(event.getYaw() + yaw * intensity + sx * tt);
    }

    public static void onFov(ComputeFovModifierEvent event) {
        float intensity = DeadConfig.CAMERA_SWAY.get().floatValue();
        event.setNewFovModifier(event.getNewFovModifier() * (1f + 0.035f * sprintBlend * intensity) * (1f - landOffset * 0.002f));
    }

    /** First-person weapon sway, bob, sprint pose and recoil. */
    public static void onRenderHand(RenderHandEvent event) {
        float k = DeadConfig.VIEW_MODEL_SWAY.get().floatValue();
        if (k <= 0) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        PoseStack pose = event.getPoseStack();
        float pt = event.getPartialTick();
        float yaw = p.getViewYRot(pt), pitchNow = p.getViewXRot(pt);
        float dYaw = Mth.wrapDegrees(yaw - lastYaw), dPitch = pitchNow - lastPitch;
        lastYaw = yaw;
        lastPitch = pitchNow;
        swayX = Mth.lerp(0.12f, swayX, Mth.clamp(dYaw, -12f, 12f));
        swayY = Mth.lerp(0.12f, swayY, Mth.clamp(dPitch, -12f, 12f));
        float walk = Mth.lerp(pt, p.walkDistO, p.walkDist) * Mth.PI;
        ItemStack stack = event.getItemStack();
        boolean weapon = !stack.isEmpty();
        handSprint = Mth.lerp(0.1f, handSprint, p.isSprinting() && walkAmp > 0.5f && weapon ? 1f : 0f);

        pose.translate(-swayX * 0.006f * k, swayY * 0.006f * k, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-swayX * 0.4f * k));
        pose.translate(Mth.sin(walk) * 0.012f * walkAmp * k, -Mth.abs(Mth.cos(walk)) * 0.018f * walkAmp * k - landOffset * 0.006f, 0);
        // Sprint pose: weapon lowered and canted inward.
        pose.translate(0.04f * handSprint, -0.09f * handSprint, 0.02f * handSprint);
        pose.mulPose(Axis.XP.rotationDegrees(14f * handSprint));
        pose.mulPose(Axis.YP.rotationDegrees((event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? -16f : 16f) * handSprint));
        // Recoil kick
        pose.translate(0, 0, recoil * 0.012f);
        pose.mulPose(Axis.XP.rotationDegrees(-recoil * 1.4f));
    }
}
