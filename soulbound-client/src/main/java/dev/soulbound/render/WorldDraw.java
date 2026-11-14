package dev.soulbound.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.soulbound.SoulBound;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * World-space boxes and lines for overlays (schematic preview, ore ESP). Everything is batched
 * into four layers (lines and fills, each see-through or depth-tested) and drawn in one go per
 * frame from our own buffer, so hundreds of boxes cost a few draw calls.
 */
public final class WorldDraw {
    private static final RenderPipeline LINES_XRAY = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(SoulBound.MOD_ID, "pipeline/lines_xray"))
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT).withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build();
    private static final RenderPipeline LINES_DEPTH = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(SoulBound.MOD_ID, "pipeline/lines_depth"))
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT).withDepthWrite(false).build();
    private static final RenderPipeline FILL_XRAY = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(SoulBound.MOD_ID, "pipeline/fill_xray")).withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build();
    private static final RenderPipeline FILL_DEPTH = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(SoulBound.MOD_ID, "pipeline/fill_depth")).withCull(false).build();

    public static final RenderLayer LINES_SEE_THROUGH = RenderLayer.of("soulbound_lines_xray", RenderSetup.builder(LINES_XRAY).translucent().build());
    public static final RenderLayer LINES = RenderLayer.of("soulbound_lines", RenderSetup.builder(LINES_DEPTH).translucent().build());
    public static final RenderLayer FILL_SEE_THROUGH = RenderLayer.of("soulbound_fill_xray", RenderSetup.builder(FILL_XRAY).translucent().build());
    public static final RenderLayer FILL = RenderLayer.of("soulbound_fill", RenderSetup.builder(FILL_DEPTH).translucent().build());

    private static final List<Consumer<WorldDraw>> RENDERERS = new ArrayList<>();
    private static VertexConsumerProvider.Immediate buffers;

    private final MatrixStack.Entry entry;
    private final VertexConsumerProvider.Immediate out;
    public final Vec3d cam;

    private WorldDraw(MatrixStack.Entry entry, VertexConsumerProvider.Immediate out, Vec3d cam) {
        this.entry = entry;
        this.out = out;
        this.cam = cam;
    }

    /** Adds something that draws every frame; it gets a {@link WorldDraw} to emit boxes with. */
    public static void register(Consumer<WorldDraw> renderer) { RENDERERS.add(renderer); }

    public static void init() {
        WorldRenderEvents.END_MAIN.register(WorldDraw::frame);
    }

    private static void frame(WorldRenderContext ctx) {
        if (RENDERERS.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || ctx.matrices() == null) return;
        if (buffers == null) buffers = VertexConsumerProvider.immediate(new BufferAllocator(1 << 18));
        WorldDraw d = new WorldDraw(ctx.matrices().peek(), buffers, mc.gameRenderer.getCamera().getCameraPos());
        for (Consumer<WorldDraw> r : RENDERERS) {
            try { r.accept(d); } catch (Exception e) { SoulBound.LOGGER.debug("overlay failed", e); }
        }
        buffers.draw();
    }

    public VertexConsumer lines(boolean seeThrough) { return out.getBuffer(seeThrough ? LINES_SEE_THROUGH : LINES); }

    public VertexConsumer fill(boolean seeThrough) { return out.getBuffer(seeThrough ? FILL_SEE_THROUGH : FILL); }

    /** Outline of an axis-aligned box in world coordinates. */
    public void box(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width) {
        float a = (float) (x0 - cam.x), b = (float) (y0 - cam.y), c = (float) (z0 - cam.z);
        float d = (float) (x1 - cam.x), e = (float) (y1 - cam.y), f = (float) (z1 - cam.z);
        // 4 edges along X, 4 along Y, 4 along Z
        edge(vc, a, b, c, d, b, c, argb, width, 1, 0, 0); edge(vc, a, e, c, d, e, c, argb, width, 1, 0, 0);
        edge(vc, a, b, f, d, b, f, argb, width, 1, 0, 0); edge(vc, a, e, f, d, e, f, argb, width, 1, 0, 0);
        edge(vc, a, b, c, a, e, c, argb, width, 0, 1, 0); edge(vc, d, b, c, d, e, c, argb, width, 0, 1, 0);
        edge(vc, a, b, f, a, e, f, argb, width, 0, 1, 0); edge(vc, d, b, f, d, e, f, argb, width, 0, 1, 0);
        edge(vc, a, b, c, a, b, f, argb, width, 0, 0, 1); edge(vc, d, b, c, d, b, f, argb, width, 0, 0, 1);
        edge(vc, a, e, c, a, e, f, argb, width, 0, 0, 1); edge(vc, d, e, c, d, e, f, argb, width, 0, 0, 1);
    }

    private void edge(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int argb, float w, float nx, float ny, float nz) {
        vc.vertex(entry, x0, y0, z0).color(argb).normal(entry, nx, ny, nz).lineWidth(w);
        vc.vertex(entry, x1, y1, z1).color(argb).normal(entry, nx, ny, nz).lineWidth(w);
    }

    /** Filled box (six quads) in world coordinates. */
    public void filled(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        float a = (float) (x0 - cam.x), b = (float) (y0 - cam.y), c = (float) (z0 - cam.z);
        float d = (float) (x1 - cam.x), e = (float) (y1 - cam.y), f = (float) (z1 - cam.z);
        quad(vc, a, b, c, d, b, c, d, b, f, a, b, f, argb); // bottom
        quad(vc, a, e, c, a, e, f, d, e, f, d, e, c, argb); // top
        quad(vc, a, b, c, a, e, c, d, e, c, d, b, c, argb); // north
        quad(vc, a, b, f, d, b, f, d, e, f, a, e, f, argb); // south
        quad(vc, a, b, c, a, b, f, a, e, f, a, e, c, argb); // west
        quad(vc, d, b, c, d, e, c, d, e, f, d, b, f, argb); // east
    }

    private void quad(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
        vc.vertex(entry, x0, y0, z0).color(argb);
        vc.vertex(entry, x1, y1, z1).color(argb);
        vc.vertex(entry, x2, y2, z2).color(argb);
        vc.vertex(entry, x3, y3, z3).color(argb);
    }

    /** Distance from the camera, for culling. */
    public double distSq(double x, double y, double z) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
