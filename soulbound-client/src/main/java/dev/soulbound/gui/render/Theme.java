package dev.soulbound.gui.render;

import dev.soulbound.SoulBound;

/** Colour palette. All colours are ARGB. */
public final class Theme {
    private Theme() {}

    public static final int BG = 0xFF0B0C14;          // window body
    public static final int BG_HEADER = 0xFF0E0F19;
    public static final int SURFACE = 0xFF12131F;     // cards
    public static final int SURFACE_HI = 0xFF181A2A;  // hovered card
    public static final int SURFACE_SEL = 0xFF1C1E32; // selected
    public static final int BORDER = 0xFF1F2236;
    public static final int BORDER_HI = 0xFF2C3050;
    public static final int HAIRLINE = 0xFF191B2B;
    public static final int TEXT = 0xFFE9EBF8;
    public static final int TEXT_MUTED = 0xFF8A8EAD;
    public static final int TEXT_DIM = 0xFF565B7A;
    public static final int KNOB = 0xFFF4F5FF;
    public static final int TRACK_OFF = 0xFF23263B;

    private static final int[][] ACCENTS = {
            {0xFF7FA7FF, 0xFFA57CFF}, // Soul
            {0xFF8B5CF6, 0xFFC084FC}, // Violet
            {0xFF5EEAD4, 0xFF60A5FA}, // Frost
            {0xFFFF7A9A, 0xFFFFB86B}, // Ember
    };

    private static int[] accents() { return ACCENTS[SoulBound.settings().ui.accent.index()]; }

    public static int accent() { return accents()[0]; }
    public static int accent2() { return accents()[1]; }
    public static int accentMix(float t) { return lerp(accent(), accent2(), t); }

    public static float glowStrength() { return SoulBound.settings().ui.glow.getFloat(); }

    // ------------------------------------------------------------ colour math
    public static int alpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 0xFF) * clamp01(a));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int withAlpha(int color, int a) { return (a << 24) | (color & 0xFFFFFF); }

    public static int lerp(int c1, int c2, float t) {
        t = clamp01(t);
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
