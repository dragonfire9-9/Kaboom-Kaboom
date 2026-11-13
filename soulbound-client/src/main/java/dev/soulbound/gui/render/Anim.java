package dev.soulbound.gui.render;

import dev.soulbound.SoulBound;

/** Frame-rate independent easing helpers. */
public final class Anim {
    private Anim() {}

    private static float dt = 0.016f;
    private static long last = System.nanoTime();

    /** Call once per frame. */
    public static void frame() {
        long now = System.nanoTime();
        if (now - last < 2_000_000L) return; // already advanced this frame (HUD and menu both call this)
        dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
    }

    public static float dt() { return dt; }

    /** Exponential approach toward target; {@code speed} ~ how many "e-folds" per second. */
    /** While true, animated values hold still (used when the same UI is drawn several times in one frame). */
    public static boolean frozen;

    public static float approach(float current, float target, float speed) {
        if (frozen) return current;
        float s = speed * SoulBound.settings().ui.animationSpeed.getFloat();
        float k = 1f - (float) Math.exp(-s * dt);
        float v = current + (target - current) * k;
        return Math.abs(target - v) < 0.0005f ? target : v;
    }

    public static float easeOutCubic(float t) { t = Theme.clamp01(t); float f = 1 - t; return 1 - f * f * f; }

    public static float easeOutBack(float t) {
        t = Theme.clamp01(t);
        float c1 = 1.4f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static float easeInOut(float t) { t = Theme.clamp01(t); return t * t * (3 - 2 * t); }

    public static float seconds() { return (System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000f; }

    /** A smoothly animated float bound to a UI element. */
    public static final class Value {
        private float v;
        public Value(float start) { v = start; }
        public float get() { return v; }
        public float to(float target, float speed) { return v = approach(v, target, speed); }
        public void snap(float value) { v = value; }
    }
}
