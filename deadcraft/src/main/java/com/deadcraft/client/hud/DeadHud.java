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
 * The reworked HUD: slim health / hunger / armour / mana bars with a trailing "damage ghost",
 * plus the atmosphere layer (vignette, film grain, low-health pulse, bleeding edges).
 */
public final class DeadHud {
    private static final ResourceLocation ICONS = DeadCraft.id("textures/gui/hud_icons.png");
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

    // ---- Bars --------------------------------------------------------------------------------
    public static void renderBars(GuiGraphics g, DeltaTracker delta) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        int w = g.guiWidth(), h = g.guiHeight();
        int left = w / 2 - 91, right = w / 2 + 10, y = h - 39;
        float max = p.getMaxHealth();
        float health = p.getHealth();
        if (shownHealth < 0) shownHealth = ghostHealth = lastHealth = health;
        if (health < lastHealth - 0.01f) hurtFlash = 1f;
        lastHealth = health;
        shownHealth = Mth.lerp(0.3f, shownHealth, health);
        ghostHealth = ghostHealth > shownHealth ? Mth.lerp(0.04f, ghostHealth, shownHealth) : shownHealth;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Health
        icon(g, left - 11, y - 1, 0);
        frame(g, left, y, 81, 7);
        fill(g, left, y, 81, 7, ghostHealth / max, 0xffd8a0a0, 0xffb07070, false);
        fill(g, left, y, 81, 7, shownHealth / max, 0xffd01818, 0xff6a0000, false);
        float absorb = p.getAbsorptionAmount();
        if (absorb > 0) fill(g, left, y, 81, 7, Math.min(1, absorb / max), 0xffffd860, 0xffb08a20, false);
        if (p.hasEffect(MobEffects.POISON) || p.hasEffect(ModEffects.INFESTED)) fill(g, left, y + 5, 81, 2, shownHealth / max, 0xff8ac040, 0xff4a7020, false);
        number(g, mc, left + 41, y, Mth.ceil(health) + " / " + Mth.ceil(max));
        // Hunger
        int food = p.getFoodData().getFoodLevel();
        icon(g, right + 83, y - 1, 1);
        frame(g, right, y, 81, 7);
        fill(g, right, y, 81, 7, food / 20f, 0xffe08a30, 0xff8a4a10, true);
        float sat = p.getFoodData().getSaturationLevel();
        fill(g, right, y + 5, 81, 2, Math.min(1, sat / 20f), 0xfffff0a0, 0xffd0b060, true);
        // Armour
        int armor = p.getArmorValue();
        int extraLeft = 0, extraRight = 0;
        if (armor > 0) {
            frame(g, left, y - 6, 81, 3);
            fill(g, left, y - 6, 81, 3, armor / 20f, 0xffc8ccd8, 0xff6a6e7a, false);
            extraLeft = 6;
        }
        // Mana
        if (ClientState.awakened) {
            shownMana = Mth.lerp(0.25f, shownMana, ClientState.mana);
            icon(g, right + 83, y - 9, 3);
            frame(g, right, y - 8, 81, 5);
            fill(g, right, y - 8, 81, 5, shownMana / Math.max(1, ClientState.maxMana), 0xff60b0ff, 0xff2040b0, true);
            float shimmer = (p.tickCount % 40) / 40f;
            int sx = right + (int) (81 * shimmer);
            if (sx < right + 81 * shownMana / Math.max(1, ClientState.maxMana)) g.fill(sx, y - 8, Math.min(sx + 3, right + 81), y - 3, 0x60ffffff);
            extraRight = 8;
        }
        RenderSystem.disableBlend();
        mc.gui.leftHeight += 10 + extraLeft;
        mc.gui.rightHeight += 10 + extraRight;
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xcc000000);
        g.fill(x, y, x + w, y + h, 0xaa1a1012);
    }

    private static void fill(GuiGraphics g, int x, int y, int w, int h, float frac, int top, int bottom, boolean fromRight) {
        int fw = Math.round(w * Mth.clamp(frac, 0f, 1f));
        if (fw <= 0) return;
        int x0 = fromRight ? x + w - fw : x;
        g.fillGradient(x0, y, x0 + fw, y + h, top, bottom);
        g.fill(x0, y, x0 + fw, y + 1, 0x40ffffff);
    }

    private static void icon(GuiGraphics g, int x, int y, int index) {
        g.blit(ICONS, x, y, index * 9, 0, 9, 9, 36, 9);
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
