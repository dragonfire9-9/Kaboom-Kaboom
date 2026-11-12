package com.deadcraft.client;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.camera.CinematicCamera;
import com.deadcraft.client.hud.DeadHud;
import com.deadcraft.client.screen.DeadTitleScreen;
import com.deadcraft.client.sound.MovementSounds;
import com.deadcraft.combat.CombatEvents;
import com.deadcraft.config.DeadConfig;
import com.deadcraft.network.DodgePayload;
import com.deadcraft.network.ReloadPayload;
import com.deadcraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/** Game-bus client events: title screen swap, music, camera, view model, HUD, input. */
@EventBusSubscriber(modid = DeadCraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static long dodgeReadyAt;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (DeadConfig.CUSTOM_TITLE.get() && event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new DeadTitleScreen());
        }
    }

    @SubscribeEvent
    public static void onMusic(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && mc.screen instanceof DeadTitleScreen) event.setMusic(ModSounds.menuMusic());
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        CinematicCamera.onCameraAngles(event);
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        CinematicCamera.onFov(event);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        CinematicCamera.onRenderHand(event);
    }

    @SubscribeEvent
    public static void onHudLayer(RenderGuiLayerEvent.Pre event) {
        if (!DeadHud.active()) return;
        var name = event.getName();
        if (name.equals(VanillaGuiLayers.PLAYER_HEALTH) || name.equals(VanillaGuiLayers.FOOD_LEVEL) || name.equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MovementSounds.tick();
        LocalPlayer p = mc.player;
        if (p == null) return;
        while (DeadKeys.DODGE.consumeClick()) dodge(mc, p);
        while (DeadKeys.RELOAD.consumeClick()) PacketDistributor.sendToServer(ReloadPayload.INSTANCE);
    }

    private static void dodge(Minecraft mc, LocalPlayer p) {
        long now = p.level().getGameTime();
        if (now < dodgeReadyAt || !p.onGround() || p.isPassenger() || p.isSpectator() || p.getAbilities().flying) return;
        dodgeReadyAt = now + CombatEvents.dodgeCooldown(p);
        float strafe = p.input.leftImpulse, forward = p.input.forwardImpulse;
        if (Math.abs(strafe) < 0.1f && Math.abs(forward) < 0.1f) forward = -1f; // no input: hop back
        Vec3 local = new Vec3(strafe, 0, forward).normalize();
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        Vec3 dir = new Vec3(local.x * cos - local.z * sin, 0, local.z * cos + local.x * sin);
        p.setDeltaMovement(dir.x * 0.95, 0.22, dir.z * 0.95);
        CinematicCamera.dodge(-strafe);
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.DODGE.get(), SoundSource.PLAYERS, 0.8f, 1f, false);
        PacketDistributor.sendToServer(new DodgePayload(strafe, forward));
    }
}
