package dev.soulbound.hud.elements;

import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Active effects with icon, level and time left. */
public final class PotionsElement extends HudElement {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final float ROW = 14;
    private final List<StatusEffectInstance> effects = new ArrayList<>();

    public PotionsElement() { super("Potions", true, 1, 0.55); }

    @Override
    public boolean layout(boolean editing) {
        effects.clear();
        if (mc.player != null) {
            for (StatusEffectInstance e : mc.player.getStatusEffects()) if (e.shouldShowIcon()) effects.add(e);
        }
        if (effects.isEmpty() && editing) {
            effects.add(new StatusEffectInstance(StatusEffects.SPEED, 20 * 95, 1));
            effects.add(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 42, 0));
        }
        effects.sort(Comparator.comparingInt(StatusEffectInstance::getDuration).reversed());
        float mw = 0;
        for (StatusEffectInstance e : effects) mw = Math.max(mw, Fonts.BODY.width(name(e)) + 8 + Fonts.BODY_BOLD.width(time(e)));
        w = 12 + 11 + mw + 4;
        h = 16 + effects.size() * ROW;
        return !effects.isEmpty();
    }

    private static String name(StatusEffectInstance e) {
        int lvl = e.getAmplifier() + 1;
        String n = e.getEffectType().value().getName().getString();
        return lvl > 1 ? n + " " + (lvl < ROMAN.length ? ROMAN[lvl] : String.valueOf(lvl)) : n;
    }

    private static String time(StatusEffectInstance e) {
        if (e.isInfinite()) return "∞";
        int s = e.getDuration() / 20;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        card(ctx, x, y, w, h);
        header(ctx, "EFFECTS", x + 7, y + 5);
        float cy = y + 14;
        for (StatusEffectInstance e : effects) {
            boolean good = e.getEffectType().value().isBeneficial();
            int col = good ? Theme.accent() : 0xFFFF6B81;
            if (alpha > 0.5f) ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(e.getEffectType()),
                    Math.round(x + 7), Math.round(cy + 2), 9, 9);
            Fonts.BODY.drawCentered(ctx, name(e), x + 20, cy + ROW / 2f, a(Theme.TEXT));
            String t = time(e);
            boolean ending = !e.isInfinite() && e.getDuration() < 200;
            float blink = ending ? 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 120.0) : 1;
            Fonts.BODY_BOLD.drawCentered(ctx, t, x + w - 7 - Fonts.BODY_BOLD.width(t), cy + ROW / 2f, a(col, blink));
            cy += ROW;
        }
    }
}
