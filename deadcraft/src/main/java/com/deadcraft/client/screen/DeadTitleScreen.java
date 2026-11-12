package com.deadcraft.client.screen;

import com.deadcraft.DeadCraft;
import com.deadcraft.config.DeadConfig;
import com.deadcraft.client.hud.Tint;
import com.deadcraft.registry.ModSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.ModListScreen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The DeadCraft title screen: a parallax graveyard under a blood moon (sky, castle, fog, crosses,
 * a wall of skeletons), embers and lightning, a bleeding DEADCRAFT logo, blood running down the
 * screen, and a constant flicker of old-TV static.
 */
public class DeadTitleScreen extends Screen {
    public static final ResourceLocation GOTHIC = DeadCraft.id("gothic");
    private static final ResourceLocation LOGO = DeadCraft.id("textures/gui/title/deadcraft_logo.png");
    private static final ResourceLocation BLOOD = DeadCraft.id("textures/gui/title/blood.png");
    private static final ResourceLocation SKY = DeadCraft.id("textures/gui/title/sky.png");
    private static final ResourceLocation FAR = DeadCraft.id("textures/gui/title/far.png");
    private static final ResourceLocation MID = DeadCraft.id("textures/gui/title/mid.png");
    private static final ResourceLocation FOG = DeadCraft.id("textures/gui/title/fog.png");
    private static final ResourceLocation SKELETONS = DeadCraft.id("textures/gui/title/skeletons.png");
    private static final ResourceLocation STATIC = DeadCraft.id("textures/gui/static.png");
    private static final ResourceLocation VIGNETTE = DeadCraft.id("textures/gui/vignette.png");

    private final Random random = new Random();
    private final long openedAt = System.currentTimeMillis();
    private static MenuAmbience ambience;
    private float glitch;
    private float flash, lastFrame, nextStrike = 3f, thunderAt = -1;
    private final List<float[]> embers = new ArrayList<>();

    public DeadTitleScreen() {
        super(Component.literal("DeadCraft"));
    }

    private static Component label(String key) {
        return Component.translatable(key).withStyle(Style.EMPTY.withFont(GOTHIC));
    }

    @Override
    protected void init() {
        int w = 200, h = 20, gap = 25;
        int x = width / 2 - w / 2;
        int y = Math.max(height / 2 - 4, (int) (height * 0.38f));
        Minecraft mc = Minecraft.getInstance();
        addRenderableWidget(new DeadButton(x, y, w, h, label("menu.deadcraft.singleplayer"), () -> mc.setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(new DeadButton(x, y + gap, w, h, label("menu.deadcraft.multiplayer"), () -> mc.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new DeadButton(x, y + gap * 2, w, h, label("menu.deadcraft.mods"), () -> mc.setScreen(new ModListScreen(this))));
        addRenderableWidget(new DeadButton(x, y + gap * 3, w / 2 - 2, h, label("menu.deadcraft.options"), () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        addRenderableWidget(new DeadButton(x + w / 2 + 2, y + gap * 3, w / 2 - 2, h, label("menu.deadcraft.quit"), mc::stop));
        if (ambience == null || !mc.getSoundManager().isActive(ambience)) {
            ambience = new MenuAmbience();
            mc.getSoundManager().play(ambience);
        }
    }

    private float time() {
        return (System.currentTimeMillis() - openedAt) / 1000f;
    }

    /** Draws a layer {@code w} wide that repeats horizontally, its bottom edge at {@code bottom}. */
    private void layer(GuiGraphics g, ResourceLocation tex, int texW, int texH, float scroll, int bottom, int w) {
        int h = w * texH / texW;
        int x0 = (int) (-(scroll % w + w) % w) - (w - width) / 2 - w;
        for (int x = x0; x < width; x += w) g.blit(tex, x, bottom - h, w, h, 0, 0, texW, texH, texW, texH);
    }

    private void tickEffects(float t) {
        float dt = Math.min(0.1f, t - lastFrame);
        lastFrame = t;
        // Lightning: a double strobe every 8-20 s, a far roll of thunder a moment later.
        if (t > nextStrike) {
            flash = 1f;
            nextStrike = t + 8 + random.nextFloat() * 12;
            thunderAt = t + 0.6f + random.nextFloat() * 1.2f;
        }
        if (thunderAt > 0 && t > thunderAt) {
            thunderAt = -1;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.THUNDER_FAR.get(), 0.8f + random.nextFloat() * 0.3f, 0.5f));
        }
        flash = Math.max(0, flash - dt * 2.4f);
        // Embers rising out of the graveyard.
        while (embers.size() < 60) {
            embers.add(new float[]{random.nextFloat() * width, height + random.nextFloat() * 40, (random.nextFloat() - 0.5f) * 10, 8 + random.nextFloat() * 22, random.nextFloat() * 6.28f, 0.4f + random.nextFloat() * 0.6f});
        }
        for (Iterator<float[]> it = embers.iterator(); it.hasNext(); ) {
            float[] e = it.next();
            e[4] += dt * 2;
            e[0] += (e[2] + (float) Math.sin(e[4]) * 6) * dt;
            e[1] -= e[3] * dt;
            if (e[1] < height * 0.25f || e[0] < -4 || e[0] > width + 4) it.remove();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = time();
        tickEffects(t);
        float px = (mouseX - width / 2f) / Math.max(1, width);
        float py = (mouseY - height / 2f) / Math.max(1, height);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Sky: blood moon and clouds, scaled to cover the screen.
        int sw = Math.max(width + 24, (height + 24) * 1024 / 576);
        int sh = sw * 576 / 1024;
        g.blit(SKY, (width - sw) / 2 - (int) (px * 6), (height - sh) / 2 - (int) (py * 4), sw, sh, 0, 0, 1024, 576, 1024, 576);
        // Moon flare, breathing.
        int pulse = (int) (28 + 18 * Math.sin(t * 0.7));
        g.fillGradient(0, 0, width, height / 2, (pulse << 24) | 0x400000, 0x00000000);

        // Lightning lights the whole sky before the land.
        if (flash > 0) {
            float f = flash > 0.75f || (flash > 0.35f && flash < 0.5f) ? flash : flash * 0.35f;
            g.fill(0, 0, width, height, ((int) (f * 150) << 24) | 0xc8d0ff);
        }
        int lw = Math.max(width, 480) + 80;
        // Far mountains and the castle, drifting very slowly.
        layer(g, FAR, 1024, 320, t * 1.5f + px * 14, (int) (height * 0.86f) + (int) (py * 4), (int) (lw * 1.1f));
        // Fog between the castle and the graveyard.
        Tint.set(g, 1f, 1f, 1f, 0.75f);
        layer(g, FOG, 512, 160, t * 9f + px * 20, (int) (height * 0.9f), lw);
        Tint.reset(g);
        // Graveyard hill with crosses and dead trees.
        layer(g, MID, 1024, 300, t * 3f + px * 30, height + 2 + (int) (py * 6), lw);
        // The wall of skeletons, nearest of all.
        int stripH = (int) (height * 0.34f);
        Tint.set(g, 0.5f, 0.42f, 0.4f, 0.95f);
        layer(g, SKELETONS, 1024, 256, px * 48, height + 8, stripH * 4);
        Tint.reset(g);
        // Ground fog rolling over their feet, in two layers moving against each other.
        Tint.set(g, 1f, 0.85f, 0.85f, 0.6f);
        layer(g, FOG, 512, 160, -t * 14f + px * 60, height + 20, lw);
        Tint.reset(g);
        g.fillGradient(0, height - stripH / 3, width, height, 0x00000000, 0xcc050101);

        // Embers.
        for (float[] e : embers) {
            float life = Mth.clamp((e[1] - height * 0.25f) / (height * 0.5f), 0f, 1f);
            int a = (int) (life * e[5] * 255);
            int col = (a << 24) | (random.nextFloat() < 0.1f ? 0xffd070 : 0xff5a1e);
            int ex = (int) (e[0] + px * 70), ey = (int) e[1];
            g.fill(ex, ey, ex + 1, ey + 1, col);
            g.fill(ex - 1, ey, ex + 2, ey + 1, (a / 3 << 24) | 0xff3010);
        }
        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float t = time();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ---- Blood running down the screen ----
        float drip = Mth.clamp(t / 6f, 0f, 1f);
        Tint.set(g, 1f, 1f, 1f, 0.8f);
        int bloodH = (int) (height * (0.6f + 0.4f * drip));
        g.blit(BLOOD, 0, (int) (-height * (1 - drip) * 0.3f), width, bloodH + height / 2, 0, 0, 1024, 576, 1024, 576);
        Tint.reset(g);

        // ---- Logo, with the occasional chromatic glitch ----
        if (random.nextFloat() < 0.008f) glitch = 1f;
        glitch = Math.max(0, glitch - 0.06f);
        int logoW = Math.min((int) (width * 0.8f), 640);
        int logoH = logoW / 4;
        int lx = width / 2 - logoW / 2;
        int ly = Math.max(4, (int) (height * 0.38f) - logoH - 10);
        float fade = Mth.clamp(t / 2.5f, 0f, 1f);
        float flicker = random.nextFloat() < 0.03f ? 0.55f : 1f;
        if (glitch > 0) {
            int j = (int) (glitch * 6);
            Tint.set(g, 1f, 0f, 0f, 0.6f * fade);
            g.blit(LOGO, lx - j, ly, logoW, logoH, 0, 0, 1024, 256, 1024, 256);
            Tint.set(g, 0f, 1f, 1f, 0.4f * fade);
            g.blit(LOGO, lx + j, ly + 1, logoW, logoH, 0, 0, 1024, 256, 1024, 256);
        }
        Tint.set(g, 1f, 1f, 1f, fade * flicker);
        g.blit(LOGO, lx, ly + (int) (Math.sin(t * 0.9) * 2), logoW, logoH, 0, 0, 1024, 256, 1024, 256);
        Tint.reset(g);
        Component tagline = label("menu.deadcraft.tagline");
        int tagAlpha = (int) (Mth.clamp((t - 1.5f) / 2f, 0f, 1f) * 200);
        if (tagAlpha > 4) g.drawCenteredString(font, tagline, width / 2, ly + logoH - 2, (tagAlpha << 24) | 0xb0a090);

        // ---- Static ----
        float grain = DeadConfig.FILM_GRAIN.get().floatValue();
        float burst = random.nextFloat() < 0.01f ? 0.25f : 0f;
        float staticAlpha = (0.07f + burst + 0.03f * (float) Math.sin(t * 13)) * (0.3f + grain);
        if (staticAlpha > 0.005f) {
            Tint.set(g, 1f, 1f, 1f, staticAlpha);
            int ox = random.nextInt(256), oy = random.nextInt(256);
            for (int x = -ox; x < width; x += 256) {
                for (int y = -oy; y < height; y += 256) {
                    g.blit(STATIC, x, y, 256, 256, 0, 0, 256, 256, 256, 256);
                }
            }
            // a rolling scanline band
            int band = (int) ((t * 60) % (height + 40)) - 20;
            g.fill(0, band, width, band + 3, 0x18ffffff);
            Tint.reset(g);
        }
        // ---- Vignette ----
        Tint.set(g, 1f, 1f, 1f, 0.9f);
        g.blit(VIGNETTE, 0, 0, width, height, 0, 0, 256, 256, 256, 256);
        Tint.reset(g);
        RenderSystem.disableBlend();

        g.drawString(font, "DeadCraft 0.1.0  |  Minecraft " + SharedConstants.getCurrentVersion().getName(), 4, height - 10, 0x806a6060, false);
        String copy = "Copyright Mojang AB. Do not distribute!";
        g.drawString(font, copy, width - font.width(copy) - 4, height - 10, 0x806a6060, false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
