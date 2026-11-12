package com.deadcraft.client.sky;

import com.deadcraft.DeadCraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

/**
 * The skies of the four DeadCraft dimensions. Each one replaces the vanilla sky completely:
 * <ul>
 *   <li>Hell's Paradise: no sky at all, a ceiling of rock and thick red fog.</li>
 *   <li>Skyfall Torn: a pale overcast dome with a shattered moon whose pieces drift apart.</li>
 *   <li>Light's Descent: eternal dusk under an enormous blood moon resting on the horizon.</li>
 *   <li>Disposed Skies: black void, swarming stars, and an eye of nothing that slowly turns.</li>
 * </ul>
 */
public final class DeadSkies {
    private static final ResourceLocation BLOOD_MOON = DeadCraft.id("textures/environment/blood_moon.png");
    private static final ResourceLocation HALO = DeadCraft.id("textures/environment/moon_halo.png");
    private static final ResourceLocation SHATTERED_MOON = DeadCraft.id("textures/environment/shattered_moon.png");
    private static final ResourceLocation VOID_EYE = DeadCraft.id("textures/environment/void_eye.png");

    private DeadSkies() {}

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(DeadCraft.id("hells_paradise"), new Hell());
        event.register(DeadCraft.id("skyfall_torn"), new Sky(Kind.SKYFALL));
        event.register(DeadCraft.id("lights_descent"), new Sky(Kind.DESCENT));
        event.register(DeadCraft.id("disposed_skies"), new Sky(Kind.VOID));
    }

    enum Kind { SKYFALL, DESCENT, VOID }

    /** No sky; everything fades into a thick red murk. */
    static final class Hell extends DimensionSpecialEffects {
        Hell() { super(Float.NaN, true, SkyType.NONE, false, true); }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) { return fog; }

        @Override
        public boolean isFoggyAt(int x, int y) { return false; }
    }

    static final class Sky extends DimensionSpecialEffects {
        private final Kind kind;

        Sky(Kind kind) {
            super(kind == Kind.SKYFALL ? 236f : Float.NaN, kind != Kind.SKYFALL, SkyType.NORMAL, false, kind == Kind.VOID);
            this.kind = kind;
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) {
            return switch (kind) {
                case SKYFALL -> fog.multiply(0.6 + brightness * 0.4, 0.6 + brightness * 0.4, 0.62 + brightness * 0.4);
                default -> fog;
            };
        }

        @Override
        public boolean isFoggyAt(int x, int y) { return kind == Kind.VOID; }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTicks) { return null; }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                                 Matrix4f projection, boolean isFoggy, Runnable setupFog) {
            float t = (ticks + partialTick) / 20f;
            PoseStack pose = new PoseStack();
            pose.mulPose(modelView);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            switch (kind) {
                case DESCENT -> {
                    dome(pose, 0x050208, 0x4a0c0c);
                    stars(pose, 1337L, 900, 0.55f, t);
                    // the moon squats on the horizon, enormous, never moving
                    body(pose, HALO, 95f, 205f, 14f, 0.55f, true);
                    body(pose, BLOOD_MOON, 40f, 205f, 14f, 1f, false);
                }
                case SKYFALL -> {
                    dome(pose, 0x4a5670, 0xb0bccc);
                    body(pose, HALO, 90f, 40f, 35f, 0.35f, true);
                    body(pose, SHATTERED_MOON, 34f, 40f, 35f, 1f, false);
                    // shards of the moon tumbling away from it
                    RandomSource r = RandomSource.create(77L);
                    for (int i = 0; i < 7; i++) {
                        float a = r.nextFloat() * 360f + t * (1.5f + r.nextFloat());
                        float d = 6f + r.nextFloat() * 14f + (float) Math.sin(t * 0.1 + i) * 1.5f;
                        body(pose, SHATTERED_MOON, 4f + r.nextFloat() * 6f, 40f + (float) Math.cos(Math.toRadians(a)) * d,
                                35f + (float) Math.sin(Math.toRadians(a)) * d * 0.6f, 0.9f, false, a);
                    }
                }
                case VOID -> {
                    dome(pose, 0x000000, 0x120a26);
                    stars(pose, 4242L, 2600, 1f, t);
                    body(pose, HALO, 80f, 300f, 22f, 0.3f, true);
                    body(pose, VOID_EYE, 38f, 300f, 22f, 1f, false, t * 4f);
                    // the static storm: now and then the whole void flickers
                    float storm = (float) Math.max(0, Math.sin(t * 0.37) * Math.sin(t * 2.9) * Math.sin(t * 7.3));
                    if (storm > 0.55f) dome(pose, 0x205060 | ((int) (storm * 120) << 24), 0x7df9ff | ((int) (storm * 60) << 24), true);
                }
            }
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.depthMask(true);
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack pose, double camX, double camY,
                                    double camZ, Matrix4f modelView, Matrix4f projection) {
            return kind != Kind.SKYFALL; // only Skyfall Torn has clouds
        }
    }

    // ---- drawing helpers -------------------------------------------------------------------
    private static void dome(PoseStack pose, int top, int horizon) {
        dome(pose, 0xff000000 | top, 0xff000000 | horizon, true);
    }

    /** A gradient hemisphere (plus a dark lower half) around the camera. Colours are ARGB. */
    private static void dome(PoseStack pose, int top, int horizon, boolean argb) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = pose.last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 24, rings = 6;
        float R = 100f;
        for (int ring = 0; ring < rings; ring++) {
            float p0 = (float) Math.PI / 2 * ring / rings, p1 = (float) Math.PI / 2 * (ring + 1) / rings;
            int c0 = mix(horizon, top, ring / (float) rings), c1 = mix(horizon, top, (ring + 1) / (float) rings);
            for (int s = 0; s < seg; s++) {
                float a0 = (float) (2 * Math.PI * s / seg), a1 = (float) (2 * Math.PI * (s + 1) / seg);
                vtx(b, m, a0, p0, R, c0);
                vtx(b, m, a0, p1, R, c1);
                vtx(b, m, a1, p1, R, c1);
                vtx(b, m, a1, p0, R, c0);
                // below the horizon: fade the horizon colour to black
                vtx(b, m, a1, -p0, R, mix(horizon, horizon & 0xff000000, ring / (float) rings));
                vtx(b, m, a1, -p1, R, mix(horizon, horizon & 0xff000000, (ring + 1) / (float) rings));
                vtx(b, m, a0, -p1, R, mix(horizon, horizon & 0xff000000, (ring + 1) / (float) rings));
                vtx(b, m, a0, -p0, R, mix(horizon, horizon & 0xff000000, ring / (float) rings));
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void vtx(BufferBuilder b, Matrix4f m, float az, float el, float R, int argb) {
        float x = Mth.cos(el) * Mth.cos(az) * R, y = Mth.sin(el) * R, z = Mth.cos(el) * Mth.sin(az) * R;
        b.addVertex(m, x, y, z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    private static int mix(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 255, cb = (b >>> sh) & 255;
            r |= Math.round(ca + (cb - ca) * t) << sh;
        }
        return r;
    }

    private static void stars(PoseStack pose, long seed, int count, float brightness, float t) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RandomSource r = RandomSource.create(seed);
        Matrix4f m = pose.last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float size = 0.12f + r.nextFloat() * 0.18f;
            float len = x * x + y * y + z * z;
            if (len < 0.01f || len > 1f) continue;
            float inv = 100f / Mth.sqrt(len);
            x *= inv; y *= inv; z *= inv;
            float tw = 0.6f + 0.4f * Mth.sin(t * (1.5f + r.nextFloat() * 3f) + i);
            int a = (int) (255 * brightness * tw);
            int col = r.nextFloat() < 0.15f ? 0xffb0a0 : r.nextFloat() < 0.2f ? 0xb0c8ff : 0xffffff;
            // a little camera-facing square
            Vec3 n = new Vec3(x, y, z).normalize();
            Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : n.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 v = n.cross(u);
            u = u.scale(size); v = v.scale(size);
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (float[] c : corners) {
                b.addVertex(m, (float) (x + u.x * c[0] + v.x * c[1]), (float) (y + u.y * c[0] + v.y * c[1]),
                        (float) (z + u.z * c[0] + v.z * c[1])).setColor((col >> 16) & 255, (col >> 8) & 255, col & 255, a);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void body(PoseStack pose, ResourceLocation tex, float size, float yaw, float elevation, float alpha, boolean additive) {
        body(pose, tex, size, yaw, elevation, alpha, additive, 0f);
    }

    /** A textured disc on the sky at compass bearing {@code yaw}, {@code elevation} degrees above the horizon. */
    private static void body(PoseStack pose, ResourceLocation tex, float size, float yaw, float elevation, float alpha,
                             boolean additive, float spin) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(90f - elevation));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        Matrix4f m = pose.last().pose();
        if (additive) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, -size, 100f, -size).setUv(0, 0);
        b.addVertex(m, size, 100f, -size).setUv(1, 0);
        b.addVertex(m, size, 100f, size).setUv(1, 1);
        b.addVertex(m, -size, 100f, size).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        pose.popPose();
    }
}
