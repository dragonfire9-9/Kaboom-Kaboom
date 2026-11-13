package dev.soulbound.hud.elements;

import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Logo, brand, FPS, ping, server and time in one bar. */
public final class WatermarkElement extends HudElement {
    private final List<String[]> parts = new ArrayList<>();

    public WatermarkElement() { super("Watermark", true, 0, 0); }

    @Override
    public boolean layout(boolean editing) {
        parts.clear();
        parts.add(new String[]{String.valueOf(mc.getCurrentFps()), "FPS"});
        int ping = 0;
        if (mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        parts.add(new String[]{String.valueOf(ping), "MS"});
        LocalTime t = LocalTime.now();
        parts.add(new String[]{String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute()), ""});
        float width = 8 + 11 + 5 + Fonts.LABEL.width("SOULBOUND", 1.2f) + 6;
        for (String[] p : parts) width += 9 + Fonts.BODY_BOLD.width(p[0]) + (p[1].isEmpty() ? 0 : 2 + Fonts.LABEL.width(p[1], 0.6f));
        w = width + 4;
        h = 17;
        return true;
    }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        card(ctx, x, y, w, h);
        Render2D.rectH(ctx, x + 6, y, (w - 12) / 2f, 0.5f, a(Theme.accent(), 0), a(Theme.accent()));
        Render2D.rectH(ctx, x + w / 2f, y, (w - 12) / 2f, 0.5f, a(Theme.accent2()), a(Theme.accent2(), 0));
        float cx = x + 6, cy = y + h / 2f;
        Render2D.glow(ctx, cx + 2, cy - 3.5f, 7, 7, 3.5f, 5, a(Theme.accent(), 0.5f));
        Render2D.icon(ctx, "logo", cx, cy - 5.5f, 11, a(0xFFFFFFFF));
        cx += 15;
        Fonts.LABEL.drawTracked(ctx, "SOULBOUND", cx, cy - Fonts.LABEL.size / 2f, 1.2f, a(Theme.TEXT));
        cx += Fonts.LABEL.width("SOULBOUND", 1.2f) + 6;
        for (String[] p : parts) {
            Render2D.rect(ctx, cx, cy - 3.5f, 0.5f, 7, a(Theme.BORDER_HI));
            cx += 5;
            Fonts.BODY_BOLD.drawCentered(ctx, p[0], cx, cy, a(Theme.TEXT));
            cx += Fonts.BODY_BOLD.width(p[0]);
            if (!p[1].isEmpty()) {
                cx += 2;
                Fonts.LABEL.drawTracked(ctx, p[1], cx, cy - Fonts.LABEL.size / 2f + 0.5f, 0.6f, a(Theme.accent()));
                cx += Fonts.LABEL.width(p[1], 0.6f);
            }
            cx += 4;
        }
    }
}
