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

    /** A cached texture and the logical size (in texels) it was generated at. */
    public record Tex(Identifier id, int size, int extra) {}

    /**
     * Textures are kept while in use; once there are many, ones unused for a while are released.
     * (Never evict something drawn recently: the GUI renders after the frame is recorded, so a
     * texture freed mid-frame would show as the missing-texture checkerboard.)
     */
    private static final int SOFT_LIMIT = 320;
    private static final long STALE_MS = 15_000;
    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static final Map<String, Long> LAST_USED = new HashMap<>();
    private static long lastSweep;

    private static Tex cached(String key, java.util.function.Function<String, Tex> make) {
        long now = System.currentTimeMillis();
        LAST_USED.put(key, now);
        Tex t = CACHE.get(key);
        if (t == null) {
            t = make.apply(key);
            CACHE.put(key, t);
            if (CACHE.size() > SOFT_LIMIT && now - lastSweep > 2000) sweep(now);
        }
        return t;
    }

    private static void sweep(long now) {
        lastSweep = now;
        var it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now - LAST_USED.getOrDefault(e.getKey(), 0L) > STALE_MS) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(e.getValue().id());
                LAST_USED.remove(e.getKey());
                it.remove();
            }
        }
    }
    private static final Map<String, NativeImage> SOURCES = new HashMap<>();

    /**
     * Sizes snap to a small set of buckets so animated shapes reuse a handful of textures
     * (drawn slightly scaled with linear filtering) instead of generating one per frame.
     */
    public static int bucket(int v) {
        if (v <= 32) return Math.max(1, v);
        if (v <= 64) return (v + 2) / 4 * 4;
        if (v <= 128) return (v + 4) / 8 * 8;
        if (v <= 256) return (v + 8) / 16 * 16;
        return (v + 16) / 32 * 32;
    }

    /** Disc of radius r (texture is 2r x 2r); big discs are generated smaller and scaled up. */
    public static Tex disc(int radius) {
        int r = Math.min(bucket(radius), 128);
        return cached("disc_" + r, k -> new Tex(upload(k, gen(2 * r, (x, y) -> {
            double d = Math.hypot(x + 0.5 - r, y + 0.5 - r);
            return clamp(r - d + 0.5);
        })), r, 0));
    }

    /** Ring of outer radius r and thickness t, stored at a bucketed radius with proportional thickness. */
    public static Tex ring(int radius, int thickness) {
        int r = Math.min(bucket(radius), 128);
        int t = Math.max(1, Math.round(thickness * (float) r / Math.max(1, radius)));
        return cached("ring_" + r + "_" + t, k -> new Tex(upload(k, gen(2 * r, (x, y) -> {
            double d = Math.hypot(x + 0.5 - r, y + 0.5 - r);
            return clamp(r - d + 0.5) - clamp((r - t) - d + 0.5);
        })), r, t));
    }

    /**
     * Glow around a disc of radius r with a soft falloff of {@code spread}. Large glows are generated
     * at a reduced resolution (they're smooth, so upscaling is invisible).
     */
    public static Tex glow(int radius, int spread) {
        int rb = bucket(radius), sb = bucket(spread);
        int c = rb + sb;
        float k = c > 160 ? 160f / c : 1f;
        int r = Math.max(0, Math.round(rb * k)), s = Math.max(1, Math.round(sb * k)), cc = r + s;
        return cached("glow_" + rb + "_" + sb, key -> new Tex(upload(key, gen(2 * cc, (x, y) -> {
            double d = Math.hypot(x + 0.5 - cc, y + 0.5 - cc) - r;
            if (d < 0) return clamp(1 + d / 1.5);
            double t = d / s;
            if (t >= 1) return 0;
            double f = 1 - t;
            return Math.exp(-3.2 * t) * f * f;
        })), cc, r));
    }

    /** Icon from textures/gui/&lt;name&gt;.png area-resampled to a bucketed size. */
    public static Tex icon(String name, int size) {
        int s = Math.min(bucket(size), 128);
        return cached("icon_" + name + "_" + s, k -> {
            NativeImage src = SOURCES.computeIfAbsent(name, ShapeCache::readSource);
            return new Tex(upload(k, resample(src, s)), s, 0);
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
