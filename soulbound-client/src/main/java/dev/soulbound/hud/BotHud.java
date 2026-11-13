package dev.soulbound.hud;

import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.bots.BotModule;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/** Status card for the Sword Bot, drawn in the SoulBound style. */
public final class BotHud {
    private static final float W = 184, H = 70, R = 11;

    private final List<BotModule> bots;
    private BotModule bot;
    private final Anim.Value show = new Anim.Value(0);
    private final Anim.Value hasTarget = new Anim.Value(0);
    private final Anim.Value health = new Anim.Value(1);
    private final Anim.Value ghost = new Anim.Value(1);
    private final Anim.Value comboPop = new Anim.Value(0);
    private final Anim.Value stateGlow = new Anim.Value(0);
    private LivingEntity lastTarget;
    private float lastHealthFrac = 1;
    private long ghostHoldUntil;
    private int lastCombo;
    private float alpha;

    public BotHud(List<BotModule> bots) {
        this.bots = bots;
        this.bot = bots.getFirst();
    }

    public void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        Anim.frame();
        // follow whichever bot is running (keep the last one while the card fades out)
        for (BotModule b : bots) if (b.isEnabled() && b.hud.isOn()) { bot = b; break; }
        float s = show.to(bot.isEnabled() && bot.hud.isOn() ? 1 : 0, 10);
        if (s < 0.01f) return;
        alpha = Anim.easeOutCubic(s);

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float x, y;
        switch (bot.hudPosition.get()) {
            case "Top Left" -> { x = 8; y = 8; }
            case "Top Right" -> { x = sw - W - 8; y = 8; }
            case "Bottom Left" -> { x = 8; y = sh - H - 48; }
            default -> { x = (sw - W) / 2f; y = sh / 2f + 22; }
        }
        y += (1 - alpha) * 6;

        LivingEntity t = bot.getTarget();
        float ht = hasTarget.to(t != null ? 1 : 0, 12);
        BotModule.State st = bot.getState();
        boolean fighting = st == BotModule.State.ENGAGING;
        float fg = stateGlow.to(fighting ? 1 : 0, 8);
        float pulse = 0.5f + 0.5f * (float) Math.sin(Anim.seconds() * 5);

        // ----- card
        float g = Theme.glowStrength();
        Render2D.glow(ctx, x, y, W, H, R, 16, a(Theme.accent(), (0.16f + 0.18f * fg * pulse) * g));
        Render2D.glow(ctx, x, y, W, H, R, 3, a(0xFF000000, 0.5f));
        Render2D.roundRect(ctx, x, y, W, H, R, a(Theme.withAlpha(Theme.BG, 0xEE)));
        Render2D.outline(ctx, x, y, W, H, R, 0, a(Theme.lerp(Theme.BORDER, Theme.accent(), 0.25f + 0.35f * fg)));
        float hl = W * 0.5f;
        Render2D.rectH(ctx, x + W / 2f - hl / 2f, y, hl / 2f, 0.5f, 0, a(Theme.accent(), 0.6f * g));
        Render2D.rectH(ctx, x + W / 2f, y, hl / 2f, 0.5f, a(Theme.accent2(), 0.6f * g), 0);

        // ----- header: icon, title, state pill
        float hx = x + 9, hy = y + 9;
        Render2D.icon(ctx, bot.getIcon(), hx, hy - 0.5f, 8, a(Theme.accent()));
        Fonts.LABEL.drawTracked(ctx, bot.getName().toUpperCase(java.util.Locale.ROOT).replace(" PVP", ""), hx + 12, hy + 4 - Fonts.LABEL.size / 2f, 1.1f, a(Theme.TEXT));

        int stColor = switch (st) {
            case ENGAGING -> Theme.accent();
            case CHASING -> 0xFFFFC76B;
            case RETREATING -> 0xFFFF6B81;
            default -> Theme.TEXT_MUTED;
        };
        String label = st.label;
        float lw = Fonts.LABEL.width(label, 0.8f);
        float pw = lw + 16, px = x + W - 9 - pw, py = hy - 2;
        Render2D.roundRect(ctx, px, py, pw, 12, 6, a(stColor, 0.14f));
        Render2D.outline(ctx, px, py, pw, 12, 6, 0, a(stColor, 0.45f));
        float dp = st == BotModule.State.SEARCHING || st == BotModule.State.PAUSED ? 0.35f + 0.65f * pulse : 1f;
        Render2D.glow(ctx, px + 4.5f, py + 4.5f, 3, 3, 1.5f, 3, a(stColor, 0.7f * dp));
        Render2D.circle(ctx, px + 6, py + 6, 1.6f, a(stColor, dp));
        Fonts.LABEL.drawTracked(ctx, label, px + 10, py + 6 - Fonts.LABEL.size / 2f, 0.8f, a(stColor));

        Render2D.hairlineH(ctx, x + 9, y + 21, W - 18, a(Theme.HAIRLINE));

        // ----- target row
        float ty = y + 26;
        float head = 22;
        boolean hurt = t != null && t.hurtTime > 0;
        float hurtF = t != null ? t.hurtTime / 10f : 0;
        if (hurt) Render2D.glow(ctx, hx, ty, head, head, 6, 6, a(0xFFFF4D6A, 0.55f * hurtF));
        Render2D.roundRect(ctx, hx, ty, head, head, 6, a(Theme.SURFACE_SEL));
        Render2D.outline(ctx, hx, ty, head, head, 6, 0, a(Theme.lerp(Theme.BORDER_HI, 0xFFFF4D6A, hurtF)));
        if (t instanceof AbstractClientPlayerEntity pl && ht > 0.5f) {
            PlayerSkinDrawer.draw(ctx, pl.getSkin(), Math.round(hx + 3), Math.round(ty + 3), Math.round(head - 6),
                    a(Theme.lerp(0xFFFFFFFF, 0xFFFF9AA8, hurtF)));
        } else if (t != null) {
            Render2D.icon(ctx, "icon_sword", hx + 6, ty + 6, 10, a(Theme.lerp(Theme.TEXT, 0xFFFF9AA8, hurtF)));
        } else {
            Render2D.icon(ctx, "icon_search", hx + 6, ty + 6 - pulse, 10, a(Theme.TEXT_DIM));
        }

        float nx = hx + head + 7, nw = x + W - 9 - nx;
        if (t != lastTarget) {
            lastTarget = t;
            if (t != null) {
                float f = frac(t);
                health.snap(f);
                ghost.snap(f);
                lastHealthFrac = f;
            }
        }
        if (t != null) {
            String name = t.getName().getString();
            Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(name, nw - 34), nx, ty + 1, a(Theme.TEXT));
            String dist = String.format(Locale.ROOT, "%.1fm", bot.getReach());
            Fonts.BODY.draw(ctx, dist, x + W - 9 - Fonts.BODY.width(dist), ty + 1, a(Theme.TEXT_MUTED));

            float f = frac(t);
            if (f < lastHealthFrac - 0.001f) ghostHoldUntil = System.currentTimeMillis() + 350;
            lastHealthFrac = f;
            float hf = health.to(f, 18);
            float gf = System.currentTimeMillis() < ghostHoldUntil ? ghost.get() : ghost.to(f, 5);
            if (gf < hf) ghost.snap(gf = hf);

            float by = ty + 14, bw = nw, bh = 5;
            Render2D.roundRect(ctx, nx, by, bw, bh, bh / 2f, a(Theme.TRACK_OFF));
            if (gf > hf) Render2D.roundRect(ctx, nx, by, bw * gf, bh, bh / 2f, a(0xFFFFFFFF, 0.45f));
            boolean low = hf < 0.35f;
            int c1 = low ? 0xFFFF4D6A : Theme.accent(), c2 = low ? 0xFFFF9A5A : Theme.accent2();
            if (hf > 0.005f) {
                Render2D.glow(ctx, nx, by, bw * hf, bh, bh / 2f, 4, a(c1, 0.45f));
                Render2D.roundRectH(ctx, nx, by, bw * hf, bh, bh / 2f, a(c1), a(c2));
            }
            float abs = t.getAbsorptionAmount();
            if (abs > 0) {
                float af = Math.min(1, abs / Math.max(1, t.getMaxHealth()));
                Render2D.roundRect(ctx, nx, by + bh + 1.5f, bw * af, 1.5f, 0.75f, a(0xFFFFD36B));
            }
        } else {
            Fonts.BODY_BOLD.draw(ctx, "Scanning…", nx, ty + 1, a(Theme.TEXT_MUTED));
            Fonts.BODY.draw(ctx, "No target in range", nx, ty + 12, a(Theme.TEXT_DIM));
        }

        // ----- stats footer
        int combo = bot.getCombo();
        if (combo > lastCombo) comboPop.snap(1);
        lastCombo = combo;
        float pop = comboPop.to(0, 7);
        float fy = y + H - 13;
        float cx = x + 9;
        cx = stat(ctx, cx, fy, "COMBO", "x" + combo, Theme.lerp(Theme.TEXT, Theme.accent(), Math.min(1, combo / 5f)), pop);
        cx = stat(ctx, cx + 8, fy, bot.statLabels()[0], String.valueOf(bot.getHits()), Theme.TEXT, 0);
        stat(ctx, cx + 8, fy, "CPS", String.format(Locale.ROOT, "%.1f", bot.getCps()), Theme.TEXT, 0);
        String hp = mc.player != null ? String.format(Locale.ROOT, "%.0f", mc.player.getHealth()) : "-";
        float ww = Fonts.LABEL.width("YOU", 0.8f) + 4 + Fonts.BODY_BOLD.width(hp);
        stat(ctx, x + W - 9 - ww, fy, "YOU", hp, mc.player != null && mc.player.getHealth() <= 6 ? 0xFFFF6B81 : Theme.TEXT, 0);
    }

    private float stat(DrawContext ctx, float x, float y, String label, String value, int color, float pop) {
        Fonts.LABEL.drawTracked(ctx, label, x, y + 4 - Fonts.LABEL.size / 2f, 0.8f, a(Theme.TEXT_DIM));
        float vx = x + Fonts.LABEL.width(label, 0.8f) + 4;
        float vw = Fonts.BODY_BOLD.width(value);
        if (pop > 0.01f) {
            Render2D.glow(ctx, vx, y, vw, 8, 3, 5, a(Theme.accent(), 0.6f * pop));
            var m = ctx.getMatrices();
            m.pushMatrix();
            float sc = 1 + 0.35f * pop;
            m.translate(vx + vw / 2f, y + 4);
            m.scale(sc, sc);
            Fonts.BODY_BOLD.drawCentered(ctx, value, -vw / 2f, 0, a(color));
            m.popMatrix();
        } else {
            Fonts.BODY_BOLD.drawCentered(ctx, value, vx, y + 4, a(color));
        }
        return vx + vw;
    }

    private static float frac(LivingEntity t) {
        return Theme.clamp01(t.getHealth() / Math.max(1, t.getMaxHealth()));
    }

    private int a(int color) { return Theme.alpha(color, alpha); }

    private int a(int color, float mul) { return Theme.alpha(color, alpha * mul); }
}
