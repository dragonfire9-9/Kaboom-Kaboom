package dev.soulbound.hud.elements;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import dev.soulbound.module.Module;
import dev.soulbound.module.visual.CustomHud;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Active modules, longest first, sliding in and out as they toggle. */
public final class ModuleListElement extends HudElement {
    private static final float ROW = 12;
    private final Map<Module, Anim.Value> slide = new HashMap<>();
    private final List<Module> shown = new ArrayList<>();

    public ModuleListElement() { super("Module List", true, 1, 0); }

    @Override
    public boolean layout(boolean editing) {
        shown.clear();
        for (Module m : SoulBound.modules().all()) {
            if (m instanceof CustomHud) continue;
            Anim.Value v = slide.computeIfAbsent(m, k -> new Anim.Value(0));
            float t = v.to(m.isEnabled() ? 1 : 0, 10);
            if (t > 0.01f) shown.add(m);
        }
        shown.sort(Comparator.comparingDouble((Module m) -> -Fonts.BODY.width(m.getName())));
        float mw = 0, hh = 0;
        for (Module m : shown) {
            mw = Math.max(mw, Fonts.BODY.width(m.getName()) + 14);
            hh += ROW * Anim.easeOutCubic(slide.get(m).get());
        }
        if (shown.isEmpty() && editing) {
            mw = Fonts.BODY.width("Active modules") + 14;
            hh = ROW;
        }
        w = Math.max(40, mw);
        h = Math.max(ROW, hh);
        return !shown.isEmpty() || editing;
    }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        if (shown.isEmpty()) {
            Fonts.BODY.drawCentered(ctx, "Active modules", right ? x + w - 7 - Fonts.BODY.width("Active modules") : x + 7, y + ROW / 2f, a(Theme.TEXT_DIM));
            return;
        }
        float cy = y;
        int i = 0;
        for (Module m : shown) {
            float t = Anim.easeOutCubic(slide.get(m).get());
            String n = m.getName();
            float lw = Fonts.BODY.width(n) + 14;
            float off = (1 - t) * (lw + 6) * (right ? 1 : -1);
            float lx = (right ? x + w - lw : x) + off;
            float rh = ROW * t;
            int col = Theme.accentMix(shown.size() <= 1 ? 0 : (float) i / (shown.size() - 1));
            float saved = alpha;
            alpha *= t;
            float bg = (float) dev.soulbound.hud.HudRenderer.background();
            Render2D.rect(ctx, lx, cy, lw, rh, a(Theme.withAlpha(Theme.BG, Math.round(230 * bg))));
            float barX = right ? lx + lw - 1.5f : lx;
            Render2D.glow(ctx, barX, cy, 1.5f, rh, 0.5f, 4, a(col, 0.6f));
            Render2D.rect(ctx, barX, cy, 1.5f, rh, a(col));
            Fonts.BODY.drawCentered(ctx, n, lx + 7, cy + rh / 2f, a(Theme.lerp(Theme.TEXT, col, 0.35f)));
            alpha = saved;
            cy += rh;
            i++;
        }
    }
}
