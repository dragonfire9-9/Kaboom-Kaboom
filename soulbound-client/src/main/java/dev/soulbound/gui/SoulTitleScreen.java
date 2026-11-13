package dev.soulbound.gui;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.SkinPreview;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.sound.UiSounds;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** SoulBound's main menu, replacing Minecraft's title screen. */
public final class SoulTitleScreen extends SoulScreen {
    private static final float WIN_W = 620, WIN_H = 350, RADIUS = 14, GAP = 5, PANEL_R = 10;
    private static final float SIDE_W = 118, TOP_H = 24, PREVIEW_W = 190;

    /** Set when the player picks the classic menu; lasts for this session. */
    public static boolean useVanilla;

    private static final SkinPreview PREVIEW = new SkinPreview();

    private record Entry(String section, String name, String icon, Runnable action) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<ServerInfo> servers = new ArrayList<>();
    private Supplier<SkinTextures> skin;
    private float serverScroll, serverScrollTarget, serverContentH;
    private float[] serverRect;

    public SoulTitleScreen() {
        super(Text.translatable("narrator.screen.title"));
    }

    @Override
    protected void init() {
        entries.clear();
        entries.add(new Entry("PLAY", "Singleplayer", "icon_play", () -> client.setScreen(new SelectWorldScreen(this))));
        entries.add(new Entry("PLAY", "Multiplayer", "icon_globe", () -> client.setScreen(
                client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this))));
        entries.add(new Entry("PLAY", "Realms", "icon_realms", () -> client.setScreen(new RealmsMainScreen(this))));
        entries.add(new Entry("GENERAL", "SoulBound", "logo", () -> client.setScreen(new ClickGuiScreen())));
        entries.add(new Entry("GENERAL", "Options", "icon_gear", () -> client.setScreen(new OptionsScreen(this, client.options))));
        entries.add(new Entry("GENERAL", "Quit Game", "icon_power", () -> client.scheduleStop()));

        servers.clear();
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            for (int i = 0; i < list.size(); i++) servers.add(list.get(i));
        } catch (Exception e) {
            SoulBound.LOGGER.warn("Could not read the server list", e);
        }
        skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
        if (open.get() == 0) UiSounds.play(UiSounds.OPEN, 1f, 0.6f);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderPanoramaBackground(ctx, delta);
        applyBlur(ctx);
        float o = Anim.easeOutCubic(open.get());
        Render2D.rectV(ctx, 0, 0, width, height, Theme.alpha(0x8805060C, o), Theme.alpha(0xBB05060C, o));
        Render2D.rectV(ctx, 0, height * 0.5f, width, height * 0.5f, 0, Theme.alpha(Theme.accent(), 0.08f * o));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!beginFrame(mouseX, mouseY)) return;
        serverRect = null;
        float scale = 0.94f + 0.06f * Anim.easeOutBack(open.get());
        float ww = Math.min(WIN_W, width - 20), wh = Math.min(WIN_H, height - 20);
        float wx = (width - ww) / 2f, wy = (height - wh) / 2f;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(width / 2f, height / 2f);
        m.scale(scale, scale);
        m.translate(-width / 2f, -height / 2f + (1 - alpha) * 10);
        drawWindow(ctx, wx, wy, ww, wh);
        m.popMatrix();
        endFrame();
    }

    private void drawWindow(DrawContext ctx, float wx, float wy, float ww, float wh) {
        float g = Theme.glowStrength(), breathe = 0.85f + 0.15f * pulse(1.6f);
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 34, a(Theme.accent(), 0.22f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 10, a(Theme.accent2(), 0.30f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 3, a(0xFF000000, 0.6f));
        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(Theme.BG));
        Render2D.outline(ctx, wx, wy, ww, wh, RADIUS, 0, a(Theme.BORDER));

        float sx = wx + GAP, sy = wy + GAP, sh = wh - GAP * 2;
        drawSidebar(ctx, sx, sy, SIDE_W, sh);
        float mx0 = sx + SIDE_W + GAP, mw = wx + ww - GAP - mx0;
        drawTopBar(ctx, mx0, sy, mw, TOP_H);
        float ly = sy + TOP_H + 3;
        Render2D.glow(ctx, mx0 + 10, ly, mw - 20, 0.75f, 0.3f, 4, a(Theme.accent(), 0.35f * g));
        Render2D.rectH(ctx, mx0 + 10, ly, (mw - 20) / 2f, 0.75f, a(Theme.accent(), 0.2f), a(Theme.accent()));
        Render2D.rectH(ctx, mx0 + 10 + (mw - 20) / 2f, ly, (mw - 20) / 2f, 0.75f, a(Theme.accent2()), a(Theme.accent2(), 0.2f));

        float cy = ly + 4, ch = wy + wh - GAP - cy;
        float pw = Math.min(PREVIEW_W, mw * 0.42f);
        drawPreview(ctx, mx0, cy, pw, ch);
        float rx = mx0 + pw + GAP;
        drawServers(ctx, rx, cy, mx0 + mw - rx, ch);
    }

    // ------------------------------------------------------------------ sidebar
    private void drawSidebar(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h, PANEL_R);
        pushClip(ctx, x + 1, y + 1, w - 2, h - 2);
        Render2D.glow(ctx, x + w / 2f - 1, y + h - 30, 2, 2, 1, 80, a(Theme.accent(), 0.16f * Theme.glowStrength()));
        popClip(ctx);

        float lx = x + 10, ly = y + 10, ls = 20;
        Render2D.glow(ctx, lx + 2, ly + 2, ls - 4, ls - 4, ls / 2f, 8, a(Theme.accent(), 0.45f * (0.7f + 0.3f * pulse(2.2f))));
        Render2D.icon(ctx, "logo", lx, ly, ls, a(0xFFFFFFFF));
        Fonts.BRAND.drawTracked(ctx, "SOULBOUND", lx + ls + 6, ly + 1, 0.9f, a(Theme.TEXT));
        Fonts.LABEL.draw(ctx, "v" + SoulBound.VERSION, lx + ls + 6, ly + 14, a(Theme.TEXT_DIM));
        Render2D.hairlineH(ctx, x + 8, y + 38, w - 16, a(Theme.HAIRLINE));

        float rowH = 16, rx = x + 4, rw = w - 8, cy = y + 46;
        String section = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!e.section().equals(section)) {
                if (section != null) cy += 6;
                section = e.section();
                Fonts.LABEL.drawTracked(ctx, section, x + 10, cy, 1f, a(Theme.TEXT_DIM));
                cy += 10;
            }
            String key = "nav_" + i;
            boolean quit = e.name().startsWith("Quit");
            boolean hov = hot(key, rx, cy, rw, rowH, c -> {
                UiSounds.play(UiSounds.CLICK, 1f, 0.8f);
                e.action().run();
            });
            float hv = anim(key).to(hov ? 1 : 0, 18);
            int tint = quit ? 0xFFFF6B81 : Theme.accent();
            if (hv > 0.01f) {
                Render2D.roundRectH(ctx, rx, cy, rw, rowH, 5, a(tint, 0.22f * hv), a(tint, 0));
                Render2D.glow(ctx, x + 0.5f, cy + 3, 2, rowH - 6, 1, 5, a(tint, 0.8f * hv));
                Render2D.roundRect(ctx, x + 0.5f, cy + 3, 2, rowH - 6, 1, a(tint, hv));
            }
            drawRipples(ctx, key);
            int col = Theme.lerp(Theme.TEXT_MUTED, Theme.lerp(tint, 0xFFFFFFFF, 0.3f), hv);
            if (e.icon().equals("logo")) Render2D.icon(ctx, "logo", rx + 6, cy + rowH / 2f - 5, 10, a(0xFFFFFFFF, 0.7f + 0.3f * hv));
            else Render2D.icon(ctx, e.icon(), rx + 7, cy + rowH / 2f - 3.5f, 8, a(Theme.lerp(Theme.TEXT_DIM, tint, hv)));
            Fonts.BODY.drawCentered(ctx, e.name(), rx + 21 + 2 * hv, cy + rowH / 2f, a(col));
            cy += rowH + 1;
        }

        // user card
        float cx = x + 6, cyy = y + h - 34, cw = w - 12, chh = 28;
        Render2D.roundRect(ctx, cx, cyy, cw, chh, 8, a(Theme.SURFACE));
        Render2D.outline(ctx, cx, cyy, cw, chh, 8, 0, a(Theme.BORDER));
        float hs = 16, hx = cx + 6, hy = cyy + (chh - hs) / 2f;
        if (alpha > 0.5f) PlayerSkinDrawer.draw(ctx, skin.get(), Math.round(hx), Math.round(hy), (int) hs, a(0xFFFFFFFF));
        Render2D.glow(ctx, hx + hs - 3, hy + hs - 3, 4, 4, 2, 3, a(0xFF4ADE80, 0.6f));
        Render2D.circle(ctx, hx + hs - 1, hy + hs - 1, 2.2f, a(Theme.PANEL));
        Render2D.circle(ctx, hx + hs - 1, hy + hs - 1, 1.5f, a(0xFF4ADE80));
        float tx = hx + hs + 6;
        Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(client.getSession().getUsername(), cw - (tx - cx) - 4), tx, cyy + 6, a(Theme.TEXT));
        Fonts.LABEL.drawTracked(ctx, "SOULBOUND", tx, cyy + 17, 0.6f, a(Theme.TEXT_DIM));
    }

    // ------------------------------------------------------------------ top bar
    private void drawTopBar(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h, PANEL_R);
        float cy = y + h / 2f;
        String greet = greeting() + ", ";
        Fonts.BODY.drawCentered(ctx, greet, x + 10, cy, a(Theme.TEXT_MUTED));
        Fonts.BODY_BOLD.drawCentered(ctx, client.getSession().getUsername(), x + 10 + Fonts.BODY.width(greet), cy, a(Theme.TEXT));

        LocalTime now = LocalTime.now();
        String time = String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
        float tw = Fonts.BODY_BOLD.width(time);
        Fonts.BODY_BOLD.drawCentered(ctx, time, x + w * 0.42f - tw / 2f, cy, a(Theme.accent()));

        // classic menu button
        String label = "Classic Menu";
        float bw = Fonts.BODY.width(label) + 16, bh = 15, bx = x + w - bw - 5, by = y + (h - bh) / 2f;
        boolean hov = hot("classic", bx, by, bw, bh, c -> {
            UiSounds.play(UiSounds.CLICK, 0.95f, 0.8f);
            useVanilla = true;
            client.setScreen(new TitleScreen());
        });
        float hv = anim("classic").to(hov ? 1 : 0, 18);
        Render2D.roundRect(ctx, bx, by, bw, bh, 5, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_SEL, hv)));
        Render2D.outline(ctx, bx, by, bw, bh, 5, 0, a(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, hv)));
        Fonts.BODY.drawCentered(ctx, label, bx + 8, by + bh / 2f, a(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hv)));

        String ver = "MINECRAFT " + SharedConstants.getGameVersion().name();
        float vw = Fonts.LABEL.width(ver, 1f);
        Fonts.LABEL.drawTracked(ctx, ver, bx - 10 - vw, cy - Fonts.LABEL.size / 2f, 1f, a(Theme.TEXT_DIM));
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    // ------------------------------------------------------------------ preview
    private void drawPreview(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h, PANEL_R);
        pushClip(ctx, x + 1, y + 1, w - 2, h - 2);
        Render2D.glow(ctx, x + w / 2f - 1, y + h * 0.5f - 1, 2, 2, 1, 80, a(Theme.accent(), 0.15f * Theme.glowStrength()));
        popClip(ctx);
        String name = client.getSession().getUsername().toUpperCase(Locale.ROOT);
        Fonts.LABEL.drawTracked(ctx, Fonts.LABEL.fit(name, w - 70), x + 9, y + 9, 1.2f, a(Theme.TEXT_DIM));
        String st = "READY";
        float sw = Fonts.LABEL.width(st, 1f), sx = x + w - 9 - sw;
        Fonts.LABEL.drawTracked(ctx, st, sx, y + 9, 1f, a(0xFF4ADE80));
        Render2D.glow(ctx, sx - 7, y + 10.5f, 3, 3, 1.5f, 3, a(0xFF4ADE80, 0.4f + 0.4f * pulse(4f)));
        Render2D.circle(ctx, sx - 5.5f, y + 12, 1.6f, a(0xFF4ADE80));

        float cx = x + w / 2f, feet = y + h - 30;
        pedestal(ctx, cx, feet, Math.min(56, w / 2f - 18), true);
        float top = y + 26;
        if (open.get() > 0.85f) {
            PREVIEW.render(ctx, skin.get(), cx, feet + 1, (feet - top) * 0.88f);
            previewArea(PREVIEW, x, top, w, feet - top + 6);
        }
    }

    // ------------------------------------------------------------------ servers
    private void drawServers(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h, PANEL_R);
        float px = x + 11, pw = w - 22;
        Fonts.LABEL.drawTracked(ctx, "QUICK JOIN", px, y + 9, 1.2f, a(Theme.TEXT_DIM));
        Fonts.BRAND.draw(ctx, "Servers", px, y + 19, a(Theme.TEXT));
        String cnt = String.valueOf(servers.size());
        float cw = Math.max(11, Fonts.LABEL.width(cnt) + 6), bx = px + Fonts.BRAND.width("Servers") + 6;
        Render2D.roundRect(ctx, bx, y + 20, cw, 10, 5, a(Theme.accent(), 0.16f));
        Fonts.LABEL.drawCentered(ctx, cnt, bx + (cw - Fonts.LABEL.width(cnt)) / 2f, y + 25, a(Theme.accent()));
        Fonts.BODY.draw(ctx, "Jump straight into a saved server.", px, y + 34, a(Theme.TEXT_MUTED));

        // manage button
        String label = "Manage";
        float mbw = Fonts.BODY_BOLD.width(label) + 16, mbh = 14, mbx = px + pw - mbw, mby = y + 13;
        boolean mh = hot("manage", mbx, mby, mbw, mbh, c -> {
            UiSounds.play(UiSounds.CLICK, 1.05f, 0.8f);
            client.setScreen(client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this));
        });
        float mhv = anim("manage").to(mh ? 1 : 0, 18);
        Render2D.glow(ctx, mbx, mby, mbw, mbh, 5, 5, a(Theme.accent(), 0.2f + 0.3f * mhv));
        Render2D.roundRectH(ctx, mbx, mby, mbw, mbh, 5, a(Theme.accent()), a(Theme.accent2()));
        Fonts.BODY_BOLD.drawCentered(ctx, label, mbx + 8, mby + mbh / 2f, a(0xFFFFFFFF));
        Render2D.hairlineH(ctx, px, y + 48, pw, a(Theme.HAIRLINE));

        float listTop = y + 52, listH = y + h - listTop - 4;
        serverRect = new float[]{x, listTop, w, listH};
        serverScrollTarget = Math.max(0, Math.min(serverScrollTarget, Math.max(0, serverContentH - listH)));
        serverScroll = Anim.approach(serverScroll, serverScrollTarget, 18);
        pushClip(ctx, x + 1, listTop, w - 2, listH);
        if (servers.isEmpty()) {
            float ccx = x + w / 2f, ccy = listTop + listH / 2f - 14, r = 14;
            Render2D.glow(ctx, ccx - r, ccy - r, r * 2, r * 2, r, 12, a(Theme.accent(), 0.15f + 0.1f * pulse(2f)));
            Render2D.circle(ctx, ccx, ccy, r, a(Theme.SURFACE));
            Render2D.outline(ctx, ccx - r, ccy - r, r * 2, r * 2, r, 0, a(Theme.BORDER_HI));
            Render2D.icon(ctx, "icon_globe", ccx - 5.5f, ccy - 5.5f, 11, a(Theme.TEXT_MUTED));
            String t1 = "No saved servers", t2 = "Add one from Manage";
            Fonts.BODY_BOLD.draw(ctx, t1, ccx - Fonts.BODY_BOLD.width(t1) / 2f, ccy + r + 8, a(Theme.TEXT_MUTED));
            Fonts.BODY.draw(ctx, t2, ccx - Fonts.BODY.width(t2) / 2f, ccy + r + 20, a(Theme.TEXT_DIM));
        } else {
            float rowH = 26, cy = listTop + 3 - serverScroll;
            for (int i = 0; i < servers.size(); i++) {
                drawServerRow(ctx, servers.get(i), i, px, cy, pw, rowH);
                cy += rowH + 3;
            }
            serverContentH = servers.size() * (rowH + 3) + 6;
        }
        popClip(ctx);
        if (serverScroll > 1) Render2D.rectV(ctx, x + 1, listTop, w - 2, 10, a(Theme.PANEL), 0);
        if (serverContentH - serverScroll > listH + 1) Render2D.rectV(ctx, x + 1, listTop + listH - 10, w - 2, 10, 0, a(Theme.PANEL));
    }

    private void drawServerRow(DrawContext ctx, ServerInfo info, int i, float x, float y, float w, float h) {
        String key = "srv_" + i;
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            UiSounds.play(UiSounds.TOGGLE_ON, 1f, 0.8f);
            ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
        });
        float hv = anim(key).to(hov ? 1 : 0, 18);
        Render2D.roundRect(ctx, x - 4, y, w + 8, h, 7, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, hv)));
        Render2D.outline(ctx, x - 4, y, w + 8, h, 7, 0, a(Theme.lerp(Theme.BORDER, Theme.accent(), 0.5f * hv)));
        drawRipples(ctx, key);
        float is = 16, ix = x + 1, iy = y + (h - is) / 2f;
        Render2D.roundRect(ctx, ix, iy, is, is, 5, a(Theme.lerp(Theme.SURFACE_SEL, Theme.withAlpha(Theme.accent(), 0x40), hv)));
        String letter = info.name.isEmpty() ? "?" : info.name.substring(0, 1).toUpperCase(Locale.ROOT);
        Fonts.BODY_BOLD.drawCentered(ctx, letter, ix + (is - Fonts.BODY_BOLD.width(letter)) / 2f, iy + is / 2f, a(Theme.lerp(Theme.TEXT_MUTED, Theme.accent(), hv)));
        float tx = ix + is + 7;
        Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(info.name, w - 70), tx, y + 5, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(info.address, w - 70), tx, y + 15, a(Theme.TEXT_DIM));
        // join pill
        String j = "JOIN";
        float jw = Fonts.LABEL.width(j, 1f) + 14, jh = 13, jx = x + w - jw, jy = y + (h - jh) / 2f;
        if (hv > 0.01f) Render2D.glow(ctx, jx, jy, jw, jh, 5, 5, a(Theme.accent(), 0.4f * hv));
        Render2D.roundRect(ctx, jx, jy, jw, jh, 5, a(Theme.lerp(Theme.SURFACE_SEL, Theme.accent(), hv)));
        Fonts.LABEL.drawTracked(ctx, j, jx + 7, jy + jh / 2f - Fonts.LABEL.size / 2f, 1f, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, hv)));
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (serverRect != null && inside(mouseX, mouseY, serverRect[0], serverRect[1], serverRect[2], serverRect[3])) {
            serverScrollTarget -= (float) vertical * 24;
        }
        return true;
    }

    @Override
    protected boolean onEmptyClick(Click click) { return true; }

    /** Swap Minecraft's title screen for ours (called from the MinecraftClient mixin). */
    public static Screen replace(Screen screen) {
        if (screen instanceof TitleScreen && !useVanilla && SoulBound.settings() != null && SoulBound.settings().ui.titleScreen.isOn()) {
            return new SoulTitleScreen();
        }
        return screen;
    }
}
