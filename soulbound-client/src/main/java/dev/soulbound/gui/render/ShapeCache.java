package dev.soulbound.gui.render;

import dev.soulbound.SoulBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Procedurally generated, pixel-exact textures: anti-aliased discs, rings and soft glows,
 * plus icons resampled to the exact on-screen size. Everything is cached by size, and
 * every size is measured in real framebuffer pixels so edges are perfectly round and crisp.
 */
public final class ShapeCache {
    private ShapeCache() {}

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<String, NativeImage> SOURCES = new HashMap<>();

    /** Disc of radius r (texture is 2r x 2r). */
    public static Identifier disc(int r) {
        return CACHE.computeIfAbsent("disc_" + r, k -> upload(k, gen(2 * r, (x, y) -> {
            double d = Math.hypot(x + 0.5 - r, y + 0.5 - r);
            return clamp(r - d + 0.5);
        })));
    }

    /** Ring of outer radius r and thickness t (texture is 2r x 2r). */
    public static Identifier ring(int r, int t) {
        return CACHE.computeIfAbsent("ring_" + r + "_" + t, k -> upload(k, gen(2 * r, (x, y) -> {
            double d = Math.hypot(x + 0.5 - r, y + 0.5 - r);
            return clamp(r - d + 0.5) - clamp((r - t) - d + 0.5);
        })));
    }

    /**
     * Glow around a disc of radius r with a soft falloff of {@code spread} pixels.
     * Texture is 2(r+spread) square; the inside of the disc is transparent so the glow
     * never tints translucent panels drawn on top.
     */
    public static Identifier glow(int r, int spread) {
        int c = r + spread;
        return CACHE.computeIfAbsent("glow_" + r + "_" + spread, k -> upload(k, gen(2 * c, (x, y) -> {
            double d = Math.hypot(x + 0.5 - c, y + 0.5 - c) - r;
            if (d < 0) return clamp(1 + d / 1.5);
            double t = d / spread;
            if (t >= 1) return 0;
            double f = 1 - t;
            return Math.exp(-3.2 * t) * f * f;
        })));
    }

    /** Icon from textures/gui/&lt;name&gt;.png area-resampled to size x size pixels. */
    public static Identifier icon(String name, int size) {
        return CACHE.computeIfAbsent("icon_" + name + "_" + size, k -> {
            NativeImage src = SOURCES.computeIfAbsent(name, ShapeCache::readSource);
            return upload(k, resample(src, size));
        });
    }

    // ------------------------------------------------------------------ internals
    private interface Coverage { double at(int x, int y); }

    private static double clamp(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static NativeImage gen(int size, Coverage cov) {
        size = Math.max(1, size);
        NativeImage img = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int a = (int) Math.round(clamp(cov.at(x, y)) * 255);
                img.setColorArgb(x, y, (a << 24) | 0xFFFFFF);
            }
        }
        return img;
    }

    private static NativeImage readSource(String name) {
        Identifier id = Identifier.of(SoulBound.MOD_ID, "textures/gui/" + name + ".png");
        try (InputStream in = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(id).getInputStream()) {
            return NativeImage.read(in);
        } catch (Exception e) {
            SoulBound.LOGGER.error("Missing GUI texture {}", id, e);
            return new NativeImage(1, 1, false);
        }
    }

    /** Box-filter downscale with premultiplied alpha. */
    private static NativeImage resample(NativeImage src, int size) {
        size = Math.max(1, size);
        int sw = src.getWidth(), sh = src.getHeight();
        NativeImage out = new NativeImage(size, size, false);
        double fx = (double) sw / size, fy = (double) sh / size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double x0 = x * fx, x1 = (x + 1) * fx, y0 = y * fy, y1 = (y + 1) * fy;
                double a = 0, r = 0, g = 0, b = 0, wsum = 0;
                for (int sy = (int) y0; sy < Math.min(sh, Math.ceil(y1)); sy++) {
                    double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) x0; sx < Math.min(sw, Math.ceil(x1)); sx++) {
                        double w = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        int c = src.getColorArgb(sx, sy);
                        double ca = ((c >>> 24) & 0xFF) / 255.0;
                        a += ca * w;
                        r += ((c >> 16) & 0xFF) * ca * w;
                        g += ((c >> 8) & 0xFF) * ca * w;
                        b += (c & 0xFF) * ca * w;
                        wsum += w;
                    }
                }
                if (wsum <= 0 || a <= 0) { out.setColorArgb(x, y, 0); continue; }
                int oa = (int) Math.round(a / wsum * 255);
                int or = (int) Math.round(r / a), og = (int) Math.round(g / a), ob = (int) Math.round(b / a);
                out.setColorArgb(x, y, (oa << 24) | (Math.min(255, or) << 16) | (Math.min(255, og) << 8) | Math.min(255, ob));
            }
        }
        return out;
    }

    private static Identifier upload(String key, NativeImage img) {
        Identifier id = Identifier.of(SoulBound.MOD_ID, "dynamic/" + key);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new LinearTexture(id.toString(), img));
        return id;
    }
}
