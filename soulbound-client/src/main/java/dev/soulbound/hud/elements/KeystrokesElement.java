package dev.soulbound.hud.elements;

import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import dev.soulbound.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/** WASD, mouse buttons with CPS, and the space bar. */
public final class KeystrokesElement extends HudElement {
    private static final float K = 18, G = 2;
    private final Anim.Value[] press = new Anim.Value[7];

    public KeystrokesElement() {
        super("Keystrokes", true, 1, 0.8);
        for (int i = 0; i < press.length; i++) press[i] = new Anim.Value(0);
    }

    @Override
    public boolean layout(boolean editing) {
        w = K * 3 + G * 2;
        h = K * 3 + G * 3 + 8;
        return true;
    }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        var o = mc.options;
        key(ctx, 0, "W", o.forwardKey, x + K + G, y, K, K);
        key(ctx, 1, "A", o.leftKey, x, y + K + G, K, K);
        key(ctx, 2, "S", o.backKey, x + K + G, y + K + G, K, K);
        key(ctx, 3, "D", o.rightKey, x + (K + G) * 2, y + K + G, K, K);
        float mw = (w - G) / 2f, my = y + (K + G) * 2;
        mouse(ctx, 4, "LMB", HudRenderer.leftCps(), HudRenderer.leftDown(), x, my, mw, K);
        mouse(ctx, 5, "RMB", HudRenderer.rightCps(), HudRenderer.rightDown(), x + mw + G, my, mw, K);
        float sy = my + K + G;
        boolean sp = o.jumpKey.isPressed();
        float t = press[6].to(sp ? 1 : 0, 20);
        box(ctx, x, sy, w, 8, t);
        Render2D.roundRect(ctx, x + w / 2f - 9, sy + 3.5f, 18, 1, 0.5f, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t)));
    }

    private void box(DrawContext ctx, float x, float y, float w, float h, float t) {
        float bg = (float) HudRenderer.background();
        if (t > 0.01f) Render2D.glow(ctx, x, y, w, h, 4, 5, a(Theme.accent(), 0.45f * t));
        Render2D.roundRect(ctx, x, y, w, h, 4, a(Theme.withAlpha(Theme.BG, Math.round(255 * bg))));
        if (t > 0.01f) Render2D.roundRectH(ctx, x, y, w, h, 4, a(Theme.accent(), 0.85f * t), a(Theme.accent2(), 0.85f * t));
        Render2D.outline(ctx, x, y, w, h, 4, 0, a(Theme.lerp(Theme.BORDER, Theme.accent(), t)));
    }

    private void key(DrawContext ctx, int i, String label, KeyBinding kb, float x, float y, float w, float h) {
        float t = press[i].to(kb.isPressed() ? 1 : 0, 20);
        box(ctx, x, y, w, h, t);
        Fonts.BODY_BOLD.drawCentered(ctx, label, x + (w - Fonts.BODY_BOLD.width(label)) / 2f, y + h / 2f, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t)));
    }

    private void mouse(DrawContext ctx, int i, String label, int cps, boolean down, float x, float y, float w, float h) {
        float t = press[i].to(down ? 1 : 0, 20);
        box(ctx, x, y, w, h, t);
        int c = Theme.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t);
        Fonts.LABEL.drawTracked(ctx, label, x + (w - Fonts.LABEL.width(label, 0.8f)) / 2f, y + 3.5f, 0.8f, a(c));
        String cs = cps + " CPS";
        Fonts.LABEL.draw(ctx, cs, x + (w - Fonts.LABEL.width(cs)) / 2f, y + 10.5f, a(Theme.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, t)));
    }
}
