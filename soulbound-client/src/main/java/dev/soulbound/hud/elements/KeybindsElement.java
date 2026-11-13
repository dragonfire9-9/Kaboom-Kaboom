package dev.soulbound.hud.elements;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import dev.soulbound.module.Module;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Every module with a keybind, its key, and whether it's on. */
public final class KeybindsElement extends HudElement {
    private static final float ROW = 13;
    private final List<Module> bound = new ArrayList<>();

    public KeybindsElement() { super("Keybinds", true, 0, 0.34); }

    @Override
    public boolean layout(boolean editing) {
        bound.clear();
        for (Module m : SoulBound.modules().all()) if (m.getKeybind().isBound()) bound.add(m);
        float mw = Fonts.LABEL.width("KEYBINDS", 1f) + 10;
        for (Module m : bound) mw = Math.max(mw, 10 + Fonts.BODY.width(m.getName()) + 12 + chipW(m));
        if (bound.isEmpty()) mw = Math.max(mw, Fonts.BODY.width("Bind a module to see it") + 4);
        w = mw + 14;
        h = 16 + Math.max(1, bound.size()) * ROW;
        return !bound.isEmpty() || editing;
    }

    private static float chipW(Module m) { return Math.max(18, Fonts.LABEL.width(m.getKeybind().display(), 0.6f) + 8); }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        card(ctx, x, y, w, h);
        header(ctx, "KEYBINDS", x + 7, y + 5);
        float cy = y + 14;
        if (bound.isEmpty()) {
            Fonts.BODY.drawCentered(ctx, "Bind a module to see it", x + 7, cy + ROW / 2f, a(Theme.TEXT_DIM));
            return;
        }
        for (Module m : bound) {
            boolean on = m.isEnabled();
            int dot = on ? 0xFF4ADE80 : Theme.TEXT_DIM;
            if (on) Render2D.glow(ctx, x + 7, cy + ROW / 2f - 1.5f, 3, 3, 1.5f, 3, a(dot, 0.6f));
            Render2D.circle(ctx, x + 8.5f, cy + ROW / 2f, 1.6f, a(dot));
            Fonts.BODY.drawCentered(ctx, m.getName(), x + 15, cy + ROW / 2f, a(on ? Theme.TEXT : Theme.TEXT_MUTED));
            String k = m.getKeybind().display();
            float cw = chipW(m), cx = x + w - 7 - cw;
            Render2D.roundRect(ctx, cx, cy + 2, cw, ROW - 4, 3, a(on ? Theme.withAlpha(Theme.accent(), 0x40) : Theme.SURFACE_SEL));
            Fonts.LABEL.drawTracked(ctx, k, cx + (cw - Fonts.LABEL.width(k, 0.6f)) / 2f, cy + ROW / 2f - Fonts.LABEL.size / 2f, 0.6f,
                    a(on ? Theme.accent() : Theme.TEXT_MUTED));
            cy += ROW;
        }
    }
}
