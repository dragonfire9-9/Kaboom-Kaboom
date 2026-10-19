package com.kaboom.jjk.client;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.network.ClientFxPayload;
import com.kaboom.jjk.network.DomainStatePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;

/**
 * Client-side cinematics: camera shake, screen flashes, title pops (BLACK FLASH, domain names),
 * an FOV punch on domain activation, and fog + tint while the camera is inside a Domain Expansion.
 */
@EventBusSubscriber(modid = JJKMod.MODID, value = Dist.CLIENT)
public final class ClientFx {
    private static final RandomSource RANDOM = RandomSource.create();

    private static float shakeIntensity;
    private static int shakeTicks, shakeDuration;

    private static int flashColor;
    private static int flashTicks, flashDuration;

    private static Component popText;
    private static Component popSubtitle;
    private static int popColor;
    private static int popTicks, popDuration;
    private static boolean blackFlashStyle;

    private static int fovPunchTicks;

    private static List<DomainStatePayload.DomainInfo> domains = List.of();

    public static void handle(ClientFxPayload p) {
        switch (p.fx()) {
            case SHAKE -> shake(p.intensity(), p.duration());
            case FLASH -> flash(p.color(), p.duration());
            case TEXT -> pop(Component.translatable(p.text()), null, p.color(), p.duration(), false);
            case BLACK_FLASH -> {
                shake(1.6f, 14);
                flash(0xFF000000, 5);
                pop(Component.translatable("jjk.fx.black_flash"), null, 0xFFE0102A, 30, true);
            }
            case DOMAIN_OPEN -> {
                flash(p.color(), 14);
                fovPunchTicks = 24;
                pop(Component.translatable(p.text()), Component.translatable("jjk.fx.domain_expansion"), p.color(), p.duration(), false);
            }
        }
    }

    public static void setDomains(List<DomainStatePayload.DomainInfo> list) {
        domains = List.copyOf(list);
    }

    public static void shake(float intensity, int ticks) {
        if (intensity >= shakeIntensity * (shakeTicks / (float) Math.max(1, shakeDuration))) {
            shakeIntensity = intensity;
            shakeTicks = shakeDuration = ticks;
        }
    }

    public static void flash(int color, int ticks) {
        flashColor = color;
        flashTicks = flashDuration = ticks;
    }

    private static void pop(Component text, Component subtitle, int color, int ticks, boolean blackFlash) {
        popText = text;
        popSubtitle = subtitle;
        popColor = color;
        popTicks = popDuration = ticks;
        blackFlashStyle = blackFlash;
    }

    /** The domain the camera is currently inside, if any. */
    public static DomainStatePayload.DomainInfo currentDomain() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || domains.isEmpty()) return null;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (DomainStatePayload.DomainInfo d : domains) if (d.contains(cam.x, cam.y, cam.z)) return d;
        return null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (shakeTicks > 0) shakeTicks--;
        if (flashTicks > 0) flashTicks--;
        if (popTicks > 0) popTicks--;
        if (fovPunchTicks > 0) fovPunchTicks--;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        domains = List.of();
        shakeTicks = flashTicks = popTicks = fovPunchTicks = 0;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0) return;
        float s = shakeIntensity * (shakeTicks - (float) event.getPartialTick()) / Math.max(1, shakeDuration);
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5f) * s * 3f);
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5f) * s * 3f);
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5f) * s * 4f);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (fovPunchTicks <= 0) return;
        float t = (fovPunchTicks - (float) event.getPartialTick()) / 24f;
        // pull in hard, then ease back: a "slow-motion" zoom
        event.setFOV(event.getFOV() * (1 - 0.25 * Math.sin(t * Math.PI)));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        DomainStatePayload.DomainInfo d = currentDomain();
        if (d == null) return;
        int c = d.color();
        event.setRed(((c >> 16) & 0xFF) / 255f * 0.35f);
        event.setGreen(((c >> 8) & 0xFF) / 255f * 0.35f);
        event.setBlue((c & 0xFF) / 255f * 0.35f);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (currentDomain() == null) return;
        event.setNearPlaneDistance(2f);
        event.setFarPlaneDistance(28f);
        event.setCanceled(true);
    }

    /** Full-screen overlay layer (registered above all vanilla layers). */
    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth(), h = g.guiHeight();

        // Domain tint + vignette ("post-processing" without a shader).
        DomainStatePayload.DomainInfo d = currentDomain();
        if (d != null) {
            int rgb = d.color() & 0xFFFFFF;
            g.fill(0, 0, w, h, 0x28 << 24 | rgb);
            for (int i = 0; i < 12; i++) {
                int a = (int) (90 * (1 - i / 12f));
                int inset = i * 3;
                g.fill(0, inset, w, inset + 3, a << 24);
                g.fill(0, h - inset - 3, w, h - inset, a << 24);
                g.fill(inset, 0, inset + 3, h, a << 24);
                g.fill(w - inset - 3, 0, w - inset, h, a << 24);
            }
        }

        if (flashTicks > 0) {
            float t = (flashTicks - partial) / Math.max(1, flashDuration);
            int a = (int) (Mth.clamp(t, 0, 1) * 200);
            g.fill(0, 0, w, h, a << 24 | (flashColor & 0xFFFFFF));
        }

        if (popTicks > 0 && popText != null) renderPop(g, mc.font, w, h, partial);
    }

    private static void renderPop(GuiGraphics g, Font font, int w, int h, float partial) {
        float age = popDuration - popTicks + partial;
        float in = Mth.clamp(age / 4f, 0, 1);
        float out = Mth.clamp((popTicks - partial) / 8f, 0, 1);
        float alpha = Math.min(in, out);
        float scale = (blackFlashStyle ? 4.2f : 3f) * (1.4f - 0.4f * in);
        int a = (int) (alpha * 255) << 24;

        g.pose().pushPose();
        g.pose().translate(w / 2f, h * 0.33f, 0);
        if (blackFlashStyle) g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6));
        g.pose().scale(scale, scale, 1);
        String s = popText.getString();
        int tw = font.width(s);
        if (blackFlashStyle) {
            // black outline + red core + white glint: a manga-style impact title
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawString(font, s, -tw / 2 + dx, dy, a, false);
            }
            g.drawString(font, s, -tw / 2, 0, a | (popColor & 0xFFFFFF), false);
            g.fill(-tw / 2 - 4, 10, tw / 2 + 4, 11, a | 0xE0102A);
        } else {
            g.drawString(font, s, -tw / 2 + 1, 1, (a >>> 1) & 0xFF000000, false);
            g.drawString(font, s, -tw / 2, 0, a | (popColor & 0xFFFFFF) | 0x202020, false);
        }
        g.pose().popPose();

        if (popSubtitle != null) {
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * 0.33f - 16, 0);
            g.pose().scale(1.4f, 1.4f, 1);
            String sub = popSubtitle.getString();
            g.drawString(font, sub, -font.width(sub) / 2, 0, a | 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    private ClientFx() {}
}
