package dev.soulbound.hud;

import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * One movable piece of the Custom HUD. Its position is stored as fractions of the free
 * screen space, so an element pushed against an edge stays there at any window size.
 */
public abstract class HudElement {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();
    protected static final float MARGIN = 4;

    public final String name;
    public final BoolSetting enabled;
    public final NumberSetting posX, posY;
    protected float alpha = 1;
    /** Size measured on the last layout pass. */
    public float w, h;

    protected HudElement(String name, boolean on, double defX, double defY) {
        this.name = name;
        String id = name.toLowerCase().replace(' ', '_');
        enabled = new BoolSetting(name, "Show the " + name.toLowerCase() + " element", on);
        posX = new NumberSetting(id + "_x", "", defX, 0, 1, 0.0001).visibleWhen(() -> false);
        posY = new NumberSetting(id + "_y", "", defY, 0, 1, 0.0001).visibleWhen(() -> false);
    }

    /** Measures the element; return false to skip it this frame (nothing to show). */
    public abstract boolean layout(boolean editing);

    /** Draws at the given top-left. {@code right} is true when the element sits on the right half. */
    public abstract void draw(DrawContext ctx, float x, float y, boolean right, boolean editing);

    public float screenX(float sw) { return MARGIN + posX.getFloat() * Math.max(0, sw - w - MARGIN * 2); }

    public float screenY(float sh) { return MARGIN + posY.getFloat() * Math.max(0, sh - h - MARGIN * 2); }

    public void moveTo(float x, float y, float sw, float sh) {
        float fw = Math.max(1, sw - w - MARGIN * 2), fh = Math.max(1, sh - h - MARGIN * 2);
        posX.set((double) Theme.clamp01((x - MARGIN) / fw));
        posY.set((double) Theme.clamp01((y - MARGIN) / fh));
    }

    public void render(DrawContext ctx, float sw, float sh, boolean editing, float alpha) {
        this.alpha = alpha;
        float x = screenX(sw), y = screenY(sh);
        draw(ctx, x, y, x + w / 2f > sw / 2f, editing);
    }

    // ------------------------------------------------------------------ shared look
    protected int a(int color) { return Theme.alpha(color, alpha); }

    protected int a(int color, float mul) { return Theme.alpha(color, alpha * mul); }

    protected void card(DrawContext ctx, float x, float y, float w, float h) {
        float bg = (float) HudRenderer.background();
        Render2D.glow(ctx, x, y, w, h, 6, 6, a(Theme.accent(), 0.12f * Theme.glowStrength()));
        Render2D.roundRect(ctx, x, y, w, h, 6, a(Theme.withAlpha(Theme.BG, Math.round(255 * bg))));
        Render2D.outline(ctx, x, y, w, h, 6, 0, a(Theme.BORDER, 0.5f + 0.5f * bg));
    }

    protected void header(DrawContext ctx, String label, float x, float y) {
        Render2D.circle(ctx, x + 1.5f, y + 3, 1.4f, a(Theme.accent()));
        Fonts.LABEL.drawTracked(ctx, label, x + 6, y + 3 - Fonts.LABEL.size / 2f, 1f, a(Theme.accent()));
    }
}
