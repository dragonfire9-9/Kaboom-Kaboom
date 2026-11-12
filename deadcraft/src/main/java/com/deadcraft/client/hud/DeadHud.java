package com.deadcraft.client.hud;

import com.deadcraft.DeadCraft;
import com.deadcraft.config.DeadConfig;
import com.deadcraft.network.ClientState;
import com.deadcraft.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;

import java.util.Random;

/**
 * The reworked HUD: blood and bile vials, a skull medallion, shield pips and a soul vial for mana, with a trailing "damage ghost",
 * plus the atmosphere layer (vignette, film grain, low-health pulse, bleeding edges).
 */
public final class DeadHud {
    private static final ResourceLocation STATIC = DeadCraft.id("textures/gui/static.png");
    private static final ResourceLocation VIGNETTE = DeadCraft.id("textures/gui/vignette.png");
    private static final Random RANDOM = new Random();

    private static float ghostHealth = -1;
    private static float shownHealth = -1;
    private static float shownMana = 0;
    private static float hurtFlash;
    private static float lastHealth = -1;

    private DeadHud() {}

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return DeadConfig.CUSTOM_HUD.get() && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.options.hideGui;
    }

    // ---- Atmosphere -----------------------------------------------------------------------
    public static void renderAtmosphere(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !DeadConfig.CUSTOM_HUD.get()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float time = (p.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Base vignette; deeper with Dread or Darkness.
        float dark = 0.55f + (p.hasEffect(ModEffects.DREAD) ? 0.25f : 0f) + (p.hasEffect(MobEffects.DARKNESS) ? 0.2f : 0f);
        Tint.set(g, 0f, 0f, 0f, Math.min(1f, dark));
        g.blit(VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
        // Low health: a red heartbeat pulse at the edges.
        float hp = p.getHealth() / p.getMaxHealth();
        if (hp < 0.35f || p.hasEffect(ModEffects.BLEEDING) || hurtFlash > 0.01f) {
            float beat = (float) Math.pow(Math.max(0, Math.sin(time * (4 + (1 - hp) * 6))), 6);
            float a = Mth.clamp((0.35f - hp) * 2.2f, 0f, 0.8f) * (0.5f + 0.5f * beat);
            if (p.hasEffect(ModEffects.BLEEDING)) a = Math.max(a, 0.3f + 0.15f * beat);
            a = Math.max(a, hurtFlash * 0.6f);
            Tint.set(g, 0.55f, 0f, 0f, a);
            g.blit(VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
        }
        hurtFlash = Math.max(0, hurtFlash - 0.04f);
        // Film grain
        float grain = DeadConfig.FILM_GRAIN.get().floatValue() * 0.06f;
        if (grain > 0.002f) {
            Tint.set(g, 1f, 1f, 1f, grain);
            int ox = RANDOM.nextInt(256), oy = RANDOM.nextInt(256);
            for (int x = -ox; x < w; x += 256)
                for (int y = -oy; y < h; y += 256)
                    g.blit(STATIC, x, y, 256, 256, 0, 0, 256, 256, 256, 256);
        }
        Tint.reset(g);
        RenderSystem.disableBlend();
    }

    // ---- Vials ------------------------------------------------------------------------------
    private static final ResourceLocation VIAL = DeadCraft.id("textures/gui/hud/vial_frame.png");
    private static final ResourceLocation CRACKS = DeadCraft.id("textures/gui/hud/vial_cracks.png");
    private static final ResourceLocation BLOOD = DeadCraft.id("textures/gui/hud/liquid_blood.png");
    private static final ResourceLocation BILE = DeadCraft.id("textures/gui/hud/liquid_bile.png");
    private static final ResourceLocation SOUL = DeadCraft.id("textures/gui/hud/liquid_soul.png");
    private static final ResourceLocation GHOST = DeadCraft.id("textures/gui/hud/liquid_ghost.png");
    private static final ResourceLocation MEDALLION = DeadCraft.id("textures/gui/hud/skull_medallion.png");
    private static final ResourceLocation MEDALLION_EYES = DeadCraft.id("textures/gui/hud/skull_eyes.png");
    private static final ResourceLocation SHIELDS = DeadCraft.id("textures/gui/hud/shields.png");
    private static final int VIAL_W = 89, VIAL_H = 13, LIQ_X = 5, LIQ_Y = 3, LIQ_W = 79, LIQ_H = 7;

    public static void renderBars(GuiGraphics g, DeltaTracker delta) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        int w = g.guiWidth(), h = g.guiHeight();
        float time = (p.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20f;
        int left = w / 2 - 98, right = w / 2 + 9, y = h - 42;
        float max = p.getMaxHealth();
        float health = p.getHealth();
        if (shownHealth < 0) shownHealth = ghostHealth = lastHealth = health;
        if (health < lastHealth - 0.01f) hurtFlash = 1f;
        lastHealth = health;
        shownHealth = Mth.lerp(0.3f, shownHealth, health);
        ghostHealth = ghostHealth > shownHealth ? Mth.lerp(0.04f, ghostHealth, shownHealth) : shownHealth;
        float frac = shownHealth / max;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Health: a vial of blood that sloshes, with a pale trail where you just lost health.
        int shake = hurtFlash > 0.3f ? Math.round((float) Math.sin(time * 90) * 2 * hurtFlash) : 0;
        liquid(g, GHOST, left + shake, y, ghostHealth / max, time, false, 0.8f);
        liquid(g, BLOOD, left + shake, y, frac, time, false, 1f);
        if (p.getAbsorptionAmount() > 0) liquid(g, BILE, left + shake, y, Math.min(1, p.getAbsorptionAmount() / max), time, false, 0.7f);
        Tint.set(g, 1f, 1f, 1f, 1f);
        g.blit(VIAL, left + shake, y, 0, 0, VIAL_W, VIAL_H, VIAL_W, VIAL_H);
        if (frac < 0.3f) {
            Tint.set(g, 1f, 1f, 1f, 0.55f);
            g.blit(CRACKS, left + shake, y, 0, 0, VIAL_W, VIAL_H, VIAL_W, VIAL_H);
            Tint.set(g, 1f, 1f, 1f, 1f);
        }
        number(g, mc, left + shake + VIAL_W / 2, y + 3, Mth.ceil(health) + " / " + Mth.ceil(max));

        // Hunger: bile, draining toward the centre.
        int food = p.getFoodData().getFoodLevel();
        liquid(g, BILE, right, y, food / 20f, time, true, 1f);
        Tint.set(g, 1f, 1f, 1f, 1f);
        g.blit(VIAL, right, y, 0, 0, VIAL_W, VIAL_H, VIAL_W, VIAL_H);
        number(g, mc, right + VIAL_W / 2, y + 3, food + " / 20");

        // Skull medallion between the vials; its eyes burn as you near death.
        g.blit(MEDALLION, w / 2 - 7, y - 1, 0, 0, 15, 15, 15, 15);
        float danger = Mth.clamp((0.5f - health / max) * 2f, 0f, 1f);
        if (danger > 0) {
            float beat = (float) Math.pow(Math.max(0, Math.sin(time * (4 + danger * 5))), 4);
            Tint.set(g, 1f, 1f, 1f, danger * (0.5f + 0.5f * beat));
            g.blit(MEDALLION_EYES, w / 2 - 7, y - 1, 0, 0, 15, 15, 15, 15);
            Tint.reset(g);
        }

        // Armour: a row of shields above the blood vial.
        int armor = p.getArmorValue();
        int extraLeft = 0, extraRight = 0;
        if (armor > 0) {
            for (int i = 0; i < 10; i++) {
                int state = armor >= (i + 1) * 2 ? 0 : armor == i * 2 + 1 ? 1 : 2;
                g.blit(SHIELDS, left + LIQ_X + i * 8, y - 10, state * 9, 0, 9, 9, 27, 9);
            }
            extraLeft = 10;
        }
        // Mana: a smaller vial of souls above the bile.
        if (ClientState.awakened) {
            shownMana = Mth.lerp(0.25f, shownMana, ClientState.mana);
            liquid(g, SOUL, right, y - 14, shownMana / Math.max(1, ClientState.maxMana), time, true, 1f);
            Tint.set(g, 1f, 1f, 1f, 1f);
            g.blit(VIAL, right, y - 14, 0, 0, VIAL_W, VIAL_H, VIAL_W, VIAL_H);
            number(g, mc, right + VIAL_W / 2, y - 11, Mth.floor(ClientState.mana) + " mana");
            extraRight = 14;
        }
        Tint.reset(g);
        RenderSystem.disableBlend();
        mc.gui.leftHeight += 14 + extraLeft;
        mc.gui.rightHeight += 14 + extraRight;
    }

    /** Draws a scrolling liquid strip inside a vial at (x, y) filled to {@code frac}. */
    private static void liquid(GuiGraphics g, ResourceLocation tex, int x, int y, float frac, float time, boolean fromRight, float alpha) {
        int fw = Math.round(LIQ_W * Mth.clamp(frac, 0f, 1f));
        if (fw <= 0) return;
        int x0 = fromRight ? x + LIQ_X + LIQ_W - fw : x + LIQ_X;
        int scroll = (int) (time * 12) % 64;
        Tint.set(g, 1f, 1f, 1f, alpha);
        g.blit(tex, x0, y + LIQ_Y, scroll + (fromRight ? LIQ_W - fw : 0), 0, fw, LIQ_H, 64, 7);
        // a slosh highlight that travels along the surface
        int hx = x0 + (int) ((Math.sin(time * 2.1) * 0.5 + 0.5) * Math.max(0, fw - 6));
        g.fill(hx, y + LIQ_Y, hx + 6, y + LIQ_Y + 1, 0x60ffffff);
    }

    private static void number(GuiGraphics g, Minecraft mc, int cx, int y, String s) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y + 1, 0);
        pose.scale(0.5f, 0.5f, 1f);
        g.drawCenteredString(mc.font, s, 0, 0, 0xffeeeeee);
        pose.popPose();
    }
}
