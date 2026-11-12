package com.deadcraft.client.screen;

import com.deadcraft.DeadCraft;
import com.deadcraft.config.DeadConfig;
import com.deadcraft.client.hud.Tint;
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

import java.util.Random;

/**
 * The DeadCraft title screen: a bleeding DEADCRAFT logo over a wall of skeletons, blood running
 * down the screen, and a constant flicker of old-TV static.
 */
public class DeadTitleScreen extends Screen {
    public static final ResourceLocation GOTHIC = DeadCraft.id("gothic");
    private static final ResourceLocation LOGO = DeadCraft.id("textures/gui/title/deadcraft_logo.png");
    private static final ResourceLocation BLOOD = DeadCraft.id("textures/gui/title/blood.png");
    private static final ResourceLocation SKELETONS = DeadCraft.id("textures/gui/title/skeletons.png");
    private static final ResourceLocation STATIC = DeadCraft.id("textures/gui/static.png");
    private static final ResourceLocation VIGNETTE = DeadCraft.id("textures/gui/vignette.png");

    private final Random random = new Random();
    private final long openedAt = System.currentTimeMillis();
    private static MenuAmbience ambience;
    private float glitch;

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

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = time();
        g.fillGradient(0, 0, width, height, 0xff120506, 0xff000000);
        // a slow red "breath" in the fog
        int pulse = (int) (40 + 30 * Math.sin(t * 0.8));
        g.fillGradient(0, height / 3, width, height, 0x00000000, (pulse << 24) | 0x3a0000);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Wall of skeletons with mouse parallax
        float px = (mouseX - width / 2f) / width;
        int stripH = (int) (height * 0.42f);
        int stripW = stripH * 4;
        int offset = (int) (-px * 24) - stripW / 4;
        Tint.set(g, 0.55f, 0.45f, 0.42f, 0.9f);
        for (int sx = offset; sx < width; sx += stripW) {
            g.blit(SKELETONS, sx, height - stripH + 6, stripW, stripH, 0, 0, 1024, 256, 1024, 256);
        }
        Tint.reset(g);
        // Ground fog over the skeletons' feet
        g.fillGradient(0, height - stripH / 3, width, height, 0x00000000, 0xee050101);
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
        Tint.set(g, 1f, 1f, 1f, 0.92f);
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
