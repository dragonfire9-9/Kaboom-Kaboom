package dev.soulbound.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Shape renderer. Takes GUI coordinates as floats, converts them to real framebuffer pixels
 * and draws there, so rounded corners are anti-aliased at native resolution regardless of
 * the GUI scale.
 */
public final class Render2D {
    private Render2D() {}

    private static float scale() { return MinecraftClient.getInstance().getWindow().getScaleFactor(); }

    private static int px(float v, float s) { return Math.round(v * s); }

    private static float begin(DrawContext ctx) {
        float s = scale();
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(1f / s, 1f / s);
        return s;
    }

    private static void end(DrawContext ctx) { ctx.getMatrices().popMatrix(); }

    // ------------------------------------------------------------------ rectangles
    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        if (isClear(color) || w <= 0 || h <= 0) return;
        float s = begin(ctx);
        ctx.fill(px(x, s), px(y, s), px(x + w, s), px(y + h, s), color);
        end(ctx);
    }

    /** A line exactly one framebuffer pixel thick. */
    public static void hairlineH(DrawContext ctx, float x, float y, float w, int color) {
        float s = begin(ctx);
        int py = px(y, s);
        ctx.fill(px(x, s), py, px(x + w, s), py + 1, color);
        end(ctx);
    }

    public static void hairlineV(DrawContext ctx, float x, float y, float h, int color) {
        float s = begin(ctx);
        int pxx = px(x, s);
        ctx.fill(pxx, px(y, s), pxx + 1, px(y + h, s), color);
        end(ctx);
    }

    public static void roundRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        if (isClear(color) || w <= 0 || h <= 0) return;
        float s = begin(ctx);
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int R = Math.min(px(r, s), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        roundRectPx(ctx, x0, y0, x1, y1, R, color);
        end(ctx);
    }

    private static void roundRectPx(DrawContext ctx, int x0, int y0, int x1, int y1, int R, int color) {
        if (R <= 0) { ctx.fill(x0, y0, x1, y1, color); return; }
        ShapeCache.Tex disc = ShapeCache.disc(R);
        quadrants(ctx, disc.id(), x0, y0, x1, y1, R, disc.size(), color);
        ctx.fill(x0 + R, y0, x1 - R, y1, color);
        ctx.fill(x0, y0 + R, x0 + R, y1 - R, color);
        ctx.fill(x1 - R, y0 + R, x1, y1 - R, color);
    }

    /** Rounded outline of thickness {@code t} GUI units (at least one framebuffer pixel). */
    public static void outline(DrawContext ctx, float x, float y, float w, float h, float r, float t, int color) {
        if (isClear(color) || w <= 0 || h <= 0) return;
        float s = begin(ctx);
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int T = Math.max(1, px(t, s));
        int R = Math.min(px(r, s), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        if (R <= T) {
            ctx.fill(x0, y0, x1, y0 + T, color);
            ctx.fill(x0, y1 - T, x1, y1, color);
            ctx.fill(x0, y0 + T, x0 + T, y1 - T, color);
            ctx.fill(x1 - T, y0 + T, x1, y1 - T, color);
        } else {
            ShapeCache.Tex ring = ShapeCache.ring(R, T);
            quadrants(ctx, ring.id(), x0, y0, x1, y1, R, ring.size(), color);
            ctx.fill(x0 + R, y0, x1 - R, y0 + T, color);
            ctx.fill(x0 + R, y1 - T, x1 - R, y1, color);
            ctx.fill(x0, y0 + R, x0 + T, y1 - R, color);
            ctx.fill(x1 - T, y0 + R, x1, y1 - R, color);
        }
        end(ctx);
    }

    /** Soft glow hugging the outside of a rounded rectangle. Colour alpha = peak intensity. */
    public static void glow(DrawContext ctx, float x, float y, float w, float h, float r, float spread, int color) {
        if (isClear(color) || w <= 0 || h <= 0 || spread <= 0) return;
        float s = begin(ctx);
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int R = Math.min(px(r, s), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        int S = Math.max(1, px(spread, s));
        int C = R + S;
        ShapeCache.Tex g = ShapeCache.glow(R, S);
        Identifier tex = g.id();
        int Ct = g.size(), Dt = 2 * Ct; // texture-space corner size
        // corners (scaled from the texture's corner to the on-screen corner)
        cornerScaled(ctx, tex, x0 - S, y0 - S, C, 0, 0, Ct, Dt, color);
        cornerScaled(ctx, tex, x1 - R, y0 - S, C, Ct, 0, Ct, Dt, color);
        cornerScaled(ctx, tex, x0 - S, y1 - R, C, 0, Ct, Ct, Dt, color);
        cornerScaled(ctx, tex, x1 - R, y1 - R, C, Ct, Ct, Ct, Dt, color);
        // edges: stretch the centre row / column of the glow texture
        if (x1 - x0 > 2 * R) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x0 + R, y0 - S, Ct - 0.5f, 0, x1 - x0 - 2 * R, C, 1, Ct, Dt, Dt, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x0 + R, y1 - R, Ct - 0.5f, Ct, x1 - x0 - 2 * R, C, 1, Ct, Dt, Dt, color);
        }
        if (y1 - y0 > 2 * R) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x0 - S, y0 + R, 0, Ct - 0.5f, C, y1 - y0 - 2 * R, Ct, 1, Dt, Dt, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x1 - R, y0 + R, Ct, Ct - 0.5f, C, y1 - y0 - 2 * R, Ct, 1, Dt, Dt, color);
        }
        end(ctx);
    }

    public static void circle(DrawContext ctx, float cx, float cy, float r, int color) {
        roundRect(ctx, cx - r, cy - r, 2 * r, 2 * r, r, color);
    }

    /** Rounded rectangle filled with a left-to-right gradient. */
    public static void roundRectH(DrawContext ctx, float x, float y, float w, float h, float r, int left, int right) {
        if (w <= 0 || h <= 0) return;
        float s = begin(ctx);
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int R = Math.min(px(r, s), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        if (x1 - x0 <= 2 * R) {
            roundRectPx(ctx, x0, y0, x1, y1, R, Theme.lerp(left, right, 0.5f));
            end(ctx);
            return;
        }
        int D = 2 * R;
        if (R > 0) {
            ShapeCache.Tex dt = ShapeCache.disc(R);
            int t = dt.size(), td = 2 * t;
            cornerScaled(ctx, dt.id(), x0, y0, R, 0, 0, t, td, left);
            cornerScaled(ctx, dt.id(), x0, y1 - R, R, 0, t, t, td, left);
            cornerScaled(ctx, dt.id(), x1 - R, y0, R, t, 0, t, td, right);
            cornerScaled(ctx, dt.id(), x1 - R, y1 - R, R, t, t, t, td, right);
            ctx.fill(x0, y0 + R, x0 + R, y1 - R, left);
            ctx.fill(x1 - R, y0 + R, x1, y1 - R, right);
        }
        gradientH(ctx, x0 + R, y0, x1 - R, y1, left, right);
        end(ctx);
    }

    /** Plain left-to-right gradient rectangle in GUI coordinates. */
    public static void rectH(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        float s = begin(ctx);
        gradientH(ctx, px(x, s), px(y, s), px(x + w, s), px(y + h, s), left, right);
        end(ctx);
    }

    /** Plain top-to-bottom gradient rectangle in GUI coordinates. */
    public static void rectV(DrawContext ctx, float x, float y, float w, float h, int top, int bottom) {
        float s = begin(ctx);
        ctx.fillGradient(px(x, s), px(y, s), px(x + w, s), px(y + h, s), top, bottom);
        end(ctx);
    }

    private static void gradientH(DrawContext ctx, int x0, int y0, int x1, int y1, int left, int right) {
        if (x1 <= x0 || y1 <= y0) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x0, y1);
        m.rotate((float) (-Math.PI / 2));
        ctx.fillGradient(0, 0, y1 - y0, x1 - x0, left, right);
        m.popMatrix();
    }

    /** Draw an icon (white artwork) tinted with {@code color}, size in GUI units. */
    public static void icon(DrawContext ctx, String name, float x, float y, float size, int color) {
        if (isClear(color)) return;
        float s = begin(ctx);
        int X = px(x, s), Y = px(y, s), S = Math.max(1, px(size, s));
        ShapeCache.Tex tex = ShapeCache.icon(name, S);
        int t = tex.size();
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex.id(), X, Y, 0, 0, S, S, t, t, t, t, color);
        end(ctx);
    }

    /** Draws the four quadrants of a disc/ring texture as the corners of a rounded rect of radius R. */
    private static void quadrants(DrawContext ctx, Identifier tex, int x0, int y0, int x1, int y1, int R, int t, int color) {
        int td = 2 * t;
        cornerScaled(ctx, tex, x0, y0, R, 0, 0, t, td, color);
        cornerScaled(ctx, tex, x1 - R, y0, R, t, 0, t, td, color);
        cornerScaled(ctx, tex, x0, y1 - R, R, 0, t, t, td, color);
        cornerScaled(ctx, tex, x1 - R, y1 - R, R, t, t, t, td, color);
    }

    private static void cornerScaled(DrawContext ctx, Identifier tex, int x, int y, int size, int u, int v, int region, int texSize, int color) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, size, size, region, region, texSize, texSize, color);
    }

    private static boolean isClear(int color) { return (color >>> 24) == 0; }
}
