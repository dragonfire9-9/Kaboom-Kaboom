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
        Identifier disc = ShapeCache.disc(R);
        int D = 2 * R;
        corner(ctx, disc, x0, y0, 0, 0, R, D, color);
        corner(ctx, disc, x1 - R, y0, R, 0, R, D, color);
        corner(ctx, disc, x0, y1 - R, 0, R, R, D, color);
        corner(ctx, disc, x1 - R, y1 - R, R, R, R, D, color);
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
            Identifier ring = ShapeCache.ring(R, T);
            int D = 2 * R;
            corner(ctx, ring, x0, y0, 0, 0, R, D, color);
            corner(ctx, ring, x1 - R, y0, R, 0, R, D, color);
            corner(ctx, ring, x0, y1 - R, 0, R, R, D, color);
            corner(ctx, ring, x1 - R, y1 - R, R, R, R, D, color);
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
        int C = R + S, D = 2 * C;
        Identifier tex = ShapeCache.glow(R, S);
        // corners
        corner(ctx, tex, x0 - S, y0 - S, 0, 0, C, D, color);
        corner(ctx, tex, x1 - R, y0 - S, C, 0, C, D, color);
        corner(ctx, tex, x0 - S, y1 - R, 0, C, C, D, color);
        corner(ctx, tex, x1 - R, y1 - R, C, C, C, D, color);
        // edges: stretch the centre row / column of the glow texture
        if (x1 - x0 > 2 * R) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x0 + R, y0 - S, C - 0.5f, 0, x1 - x0 - 2 * R, C, 1, C, D, D, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x0 + R, y1 - R, C - 0.5f, C, x1 - x0 - 2 * R, C, 1, C, D, D, color);
        }
        if (y1 - y0 > 2 * R) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x0 - S, y0 + R, 0, C - 0.5f, C, y1 - y0 - 2 * R, C, 1, D, D, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x1 - R, y0 + R, C, C - 0.5f, C, y1 - y0 - 2 * R, C, 1, D, D, color);
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
            Identifier disc = ShapeCache.disc(R);
            corner(ctx, disc, x0, y0, 0, 0, R, D, left);
            corner(ctx, disc, x0, y1 - R, 0, R, R, D, left);
            corner(ctx, disc, x1 - R, y0, R, 0, R, D, right);
            corner(ctx, disc, x1 - R, y1 - R, R, R, R, D, right);
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
        Identifier tex = ShapeCache.icon(name, S);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, X, Y, 0, 0, S, S, S, S, S, S, color);
        end(ctx);
    }

    private static void corner(DrawContext ctx, Identifier tex, int x, int y, int u, int v, int size, int texSize, int color) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, size, size, size, size, texSize, texSize, color);
    }

    private static boolean isClear(int color) { return (color >>> 24) == 0; }
}
