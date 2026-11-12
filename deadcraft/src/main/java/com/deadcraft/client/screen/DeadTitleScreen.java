package com.deadcraft.client.screen;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.hud.Tint;
import com.deadcraft.client.model.DeadRigModel;
import com.deadcraft.client.render.DeadLayers;
import com.deadcraft.config.DeadConfig;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.gui.ModListScreen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The DeadCraft title screen, third edition: a candle-lit crypt. The menu is a column of gothic
 * words on the left; on the altar at the right stands a live, animated 3D model of one of the
 * seven bosses, turning slowly in red light, with its name and title beneath it. Every few
 * seconds static swallows the altar and another boss is standing there when it clears.
 */
public class DeadTitleScreen extends Screen {
    public static final ResourceLocation GOTHIC = DeadCraft.id("gothic");
    private static final ResourceLocation CRYPT = DeadCraft.id("textures/gui/title/crypt.png");
    private static final ResourceLocation LOGO = DeadCraft.id("textures/gui/title/deadcraft_logo.png");
    private static final ResourceLocation STATIC = DeadCraft.id("textures/gui/static.png");
    private static final ResourceLocation VIGNETTE = DeadCraft.id("textures/gui/vignette.png");
    private static final ResourceLocation FOG = DeadCraft.id("textures/gui/title/fog.png");
    private static final int CRYPT_W = 1024, CRYPT_H = 576;
    private static final float ALTAR_U = 0.70f, ALTAR_V = 0.68f;
    private static final float SHOW_SECONDS = 9f, SWAP_SECONDS = 0.6f;

    /** The bosses on the altar: id, title, model height in pixels (for fitting), whether it walks in place. */
    private record Boss(String id, String title, float px) {}

    private static final Boss[] BOSSES = {
            new Boss("skeleton", "The Ossuary Colossus", 49),
            new Boss("fracture", "The Shattered Titan", 50),
            new Boss("chronos", "Keeper of the Last Hour", 46),
            new Boss("dim_light_demon", "Lord of the Dimming", 51),
            new Boss("petrified", "The Gorgon in Stone", 38),
            new Boss("hive_mother", "Queen of the Brood", 46),
            new Boss("wailing_bride", "She Who Was Left at the Altar", 48),
    };

    private final Random random = new Random();
    private final long openedAt = System.currentTimeMillis();
    private static MenuAmbience ambience;
    private final List<DeadRigModel<LivingEntity>> models = new ArrayList<>();
    private final List<float[]> embers = new ArrayList<>();
    private float lastFrame;
    private int shown = -1;

    public DeadTitleScreen() {
        super(Component.literal("DeadCraft"));
    }

    private static Component label(String key) {
        return Component.translatable(key).withStyle(Style.EMPTY.withFont(GOTHIC));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (models.isEmpty()) {
            for (Boss b : BOSSES) {
                try {
                    models.add(new DeadRigModel<>(mc.getEntityModels().bakeLayer(DeadLayers.of(b.id)), b.id, 1.6f));
                } catch (RuntimeException e) {
                    models.add(null);
                }
            }
        }
        if (shown < 0) shown = random.nextInt(BOSSES.length);
        int x = Math.max(18, (int) (width * 0.07f));
        int y = Math.max(height / 2 - 30, 92);
        int gap = 22;
        float scale = height > 300 ? 1.6f : 1.3f;
        int w = 170;
        addRenderableWidget(new MenuEntry(x, y, w, 20, label("menu.deadcraft.singleplayer"), scale, () -> mc.setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(new MenuEntry(x, y + gap, w, 20, label("menu.deadcraft.multiplayer"), scale, () -> mc.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new MenuEntry(x, y + gap * 2, w, 20, label("menu.deadcraft.mods"), scale, () -> mc.setScreen(new ModListScreen(this))));
        addRenderableWidget(new MenuEntry(x, y + gap * 3, w, 20, label("menu.deadcraft.options"), scale, () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        addRenderableWidget(new MenuEntry(x, y + gap * 4 + 8, w, 20, label("menu.deadcraft.quit"), scale, mc::stop));
        if (ambience == null || !mc.getSoundManager().isActive(ambience)) {
            ambience = new MenuAmbience();
            mc.getSoundManager().play(ambience);
        }
    }

    private float time() {
        return (System.currentTimeMillis() - openedAt) / 1000f;
    }

    // ---- layout helpers: the crypt image is drawn to cover the screen ---------------------------
    private float coverScale() {
        return Math.max(width / (float) CRYPT_W, height / (float) CRYPT_H) * 1.04f;
    }

    private float cryptX(float u, float px) {
        float s = coverScale();
        return (width - CRYPT_W * s) / 2f + u * CRYPT_W * s - px * 10;
    }

    private float cryptY(float v, float py) {
        float s = coverScale();
        return (height - CRYPT_H * s) / 2f + v * CRYPT_H * s - py * 6;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = time();
        float px = (mouseX - width / 2f) / Math.max(1, width), py = (mouseY - height / 2f) / Math.max(1, height);
        float s = coverScale();
        int cw = (int) (CRYPT_W * s), ch = (int) (CRYPT_H * s);
        g.fill(0, 0, width, height, 0xff000000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(CRYPT, (int) cryptX(0, px), (int) cryptY(0, py), cw, ch, 0, 0, CRYPT_W, CRYPT_H, CRYPT_W, CRYPT_H);
        // candlelight flicker over the whole room
        float flick = 0.06f + 0.05f * (float) Math.sin(t * 7.3) * (float) Math.sin(t * 3.1 + 1);
        g.fill(0, 0, width, height, ((int) (Math.max(0, flick) * 255) << 24) | 0x301000);
        // low fog drifting across the floor
        Tint.set(g, 1f, 0.8f, 0.8f, 0.45f);
        int fw = width + 200, fh = fw * 160 / 512;
        int fx = (int) (-(t * 12 % fw)) - 100;
        for (int x = fx; x < width; x += fw) g.blit(FOG, x, height - fh / 2, fw, fh, 0, 0, 512, 160, 512, 160);
        Tint.reset(g);
        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float t = time();
        float px = (mouseX - width / 2f) / Math.max(1, width), py = (mouseY - height / 2f) / Math.max(1, height);

        // ---- the boss on the altar ----
        float cycle = SHOW_SECONDS + SWAP_SECONDS;
        int index = (shown + (int) (t / cycle)) % BOSSES.length;
        float into = t % cycle;
        float swap = into > SHOW_SECONDS ? (into - SHOW_SECONDS) / SWAP_SECONDS : into < SWAP_SECONDS && t > cycle ? 1f - into / SWAP_SECONDS : 0f;
        Boss boss = BOSSES[index];
        DeadRigModel<LivingEntity> model = models.size() > index ? models.get(index) : null;
        float ax = cryptX(ALTAR_U, px), ay = cryptY(ALTAR_V, py);
        if (model != null) {
            float target = height * 0.46f;
            float scale = target / (boss.px / 16f);
            renderBoss(g, model, boss.id, ax, ay, scale, t, mouseX, mouseY);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // static swallowing the altar during a swap
        if (swap > 0.01f) {
            Tint.set(g, 1f, 0.9f, 0.9f, Math.min(1f, swap * 1.3f));
            int bw = (int) (height * 0.42f), bh = (int) (height * 0.6f);
            int ox = random.nextInt(256), oy = random.nextInt(256);
            g.blit(STATIC, (int) ax - bw / 2, (int) ay - bh, bw, bh, ox, oy, 256, 256, 256, 256);
            Tint.reset(g);
        }
        // name plate
        float plateA = 1f - Mth.clamp(swap * 2f, 0f, 1f);
        if (plateA > 0.05f) {
            Component name = Component.translatable("entity.deadcraft." + boss.id).withStyle(Style.EMPTY.withFont(GOTHIC));
            Component title = Component.literal(boss.title).withStyle(Style.EMPTY.withFont(GOTHIC));
            int a = (int) (plateA * 255);
            g.pose().pushPose();
            g.pose().translate(ax, ay + 30, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            g.drawCenteredString(font, name, 0, 0, (a << 24) | 0xe8d8c8);
            g.pose().popPose();
            g.drawCenteredString(font, title, (int) ax, (int) ay + 46, ((int) (plateA * 200) << 24) | 0xb04040);
        }
        tickEmbers(t, ax, ay);
        for (float[] e : embers) {
            float life = Mth.clamp(e[4], 0f, 1f);
            int a = (int) (life * 220);
            g.fill((int) e[0], (int) e[1], (int) e[0] + 1, (int) e[1] + 1, (a << 24) | (e[5] > 0.5f ? 0xff5a1e : 0xffc060));
        }

        // ---- logo, top left ----
        int logoW = Math.min((int) (width * 0.42f), 420);
        int logoH = logoW / 4;
        int lx = Math.max(10, (int) (width * 0.05f)), ly = Math.max(8, (int) (height * 0.08f));
        float fade = Mth.clamp(t / 2f, 0f, 1f);
        Tint.set(g, 1f, 1f, 1f, fade * (random.nextFloat() < 0.02f ? 0.6f : 1f));
        g.blit(LOGO, lx, ly + (int) (Math.sin(t * 0.8) * 1.5), logoW, logoH, 0, 0, 1024, 256, 1024, 256);
        Tint.reset(g);
        Component tagline = label("menu.deadcraft.tagline");
        int tagAlpha = (int) (Mth.clamp((t - 1.2f) / 2f, 0f, 1f) * 200);
        if (tagAlpha > 4) g.drawString(font, tagline, lx + 8, ly + logoH + 2, (tagAlpha << 24) | 0xb0a090, true);

        // ---- static and vignette ----
        float grain = DeadConfig.FILM_GRAIN.get().floatValue();
        float staticAlpha = (0.05f + 0.02f * (float) Math.sin(t * 13)) * (0.3f + grain);
        if (staticAlpha > 0.005f) {
            Tint.set(g, 1f, 1f, 1f, staticAlpha);
            int ox = random.nextInt(256), oy = random.nextInt(256);
            for (int x = -ox; x < width; x += 256)
                for (int y = -oy; y < height; y += 256) g.blit(STATIC, x, y, 256, 256, 0, 0, 256, 256, 256, 256);
            Tint.reset(g);
        }
        Tint.set(g, 1f, 1f, 1f, 0.85f);
        g.blit(VIGNETTE, 0, 0, width, height, 0, 0, 256, 256, 256, 256);
        Tint.reset(g);
        RenderSystem.disableBlend();
        g.drawString(font, "DeadCraft 0.1.0  |  Minecraft " + SharedConstants.getCurrentVersion().getName(), 4, height - 10, 0x806a6060, false);
        String copy = "Copyright Mojang AB. Do not distribute!";
        g.drawString(font, copy, width - font.width(copy) - 4, height - 10, 0x806a6060, false);
    }

    /** Draws a boss model standing on the altar: slow turn, idle animation, glowing eyes. */
    private void renderBoss(GuiGraphics g, DeadRigModel<LivingEntity> model, String id, float x, float y, float scale,
                            float t, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        model.poseForMenu(t * 20f, false);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y - 1.5f * scale, 150);
        pose.scale(scale, scale, -scale);
        float yaw = 25f + (float) Math.sin(t * 0.35) * 35f + (mouseX - x) / Math.max(1, width) * 40f;
        pose.mulPose(Axis.XP.rotationDegrees(-8f));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        ResourceLocation tex = DeadCraft.id("textures/entity/" + id + ".png");
        model.renderToBuffer(pose, buf.getBuffer(RenderType.entityCutoutNoCull(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        ResourceLocation eyes = DeadCraft.id("textures/entity/" + id + "_eyes.png");
        if (mc.getResourceManager().getResource(eyes).isPresent()) {
            model.renderToBuffer(pose, buf.getBuffer(RenderType.eyes(eyes)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        buf.endBatch();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    private void tickEmbers(float t, float ax, float ay) {
        float dt = Math.min(0.1f, t - lastFrame);
        lastFrame = t;
        while (embers.size() < 45) {
            embers.add(new float[]{ax + (random.nextFloat() - 0.5f) * height * 0.5f, ay - random.nextFloat() * 10,
                    (random.nextFloat() - 0.5f) * 8, 12 + random.nextFloat() * 25, 1f, random.nextFloat()});
        }
        for (Iterator<float[]> it = embers.iterator(); it.hasNext(); ) {
            float[] e = it.next();
            e[0] += (e[2] + (float) Math.sin(t * 2 + e[5] * 6) * 5) * dt;
            e[1] -= e[3] * dt;
            e[4] -= dt * 0.35f;
            if (e[4] <= 0) it.remove();
        }
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
