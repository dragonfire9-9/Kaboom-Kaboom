package dev.soulbound.gui;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.Category;
import dev.soulbound.module.Configurable;
import dev.soulbound.module.Module;
import dev.soulbound.module.bots.SwordBot;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.KeybindSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.setting.Setting;
import dev.soulbound.sound.UiSounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The SoulBound menu. Immediate-mode: every frame lays out and draws the whole window and
 * records clickable regions, which the input handlers then resolve against.
 *
 * <p>Layout: a sidebar (brand, MODULES and GENERAL sections, user card), a top bar
 * (greeting, clock, search) and a content area that depends on the selected entry:
 * module list + detail, or for bots an icon strip + model preview + detail.
 */
public final class ClickGuiScreen extends Screen {
    // ---------------------------------------------------------------- layout constants
    private static final float WIN_W = 620, WIN_H = 350, RADIUS = 14, GAP = 5, PANEL_R = 10;
    private static final float SIDE_W = 118, TOP_H = 24, LIST_W = 150, STRIP_W = 28, PREVIEW_W = 150;

    private enum Kind { MODULES, BOTS, PAGES }

    private record Tab(String name, String icon, Category category, Supplier<List<? extends Configurable>> pages) {
        Kind kind() {
            if (category == Category.BOTS) return Kind.BOTS;
            return category != null ? Kind.MODULES : Kind.PAGES;
        }

        List<? extends Configurable> items() {
            if (category != null) {
                return SoulBound.modules().byCategory(category).stream()
                        .sorted(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT))).toList();
            }
            return pages.get();
        }
    }

    private static final List<Tab> TABS = new ArrayList<>();
    private static final int FIRST_GENERAL;

    static {
        for (Category c : Category.values()) TABS.add(new Tab(c.displayName, c.icon, c, null));
        FIRST_GENERAL = TABS.size();
        TABS.add(new Tab("Settings", "icon_settings", null, () -> SoulBound.settings().pages()));
        TABS.add(new Tab("Theme", "icon_theme", null, () -> List.of(SoulBound.settings().theme)));
    }

    /** Remembered between openings. */
    private static int lastTab = 0;
    private static final Map<Integer, Configurable> lastSelection = new HashMap<>();

    // ---------------------------------------------------------------- state
    private final Anim.Value open = new Anim.Value(0);
    private boolean closing;
    private int tab = lastTab;
    private final Map<Integer, Configurable> selection = new HashMap<>(lastSelection);
    private String search = "";
    private boolean searchFocused, swallowSlash;
    private float listScroll, listScrollTarget, listContentH;
    private float detailScroll, detailScrollTarget, detailContentH;
    private float[] listRect;
    private KeybindSetting listening;
    private NumberSetting dragging;
    private float dragX, dragW;
    private final Anim.Value content = new Anim.Value(0);
    private Object contentKey;

    private final Map<Object, Anim.Value> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ripple> ripples = new ArrayList<>();
    private float[] clip;
    private String hoverKey, lastHoverKey;
    private int mx, my;
    private float alpha = 1;

    public ClickGuiScreen() {
        super(Text.literal(SoulBound.NAME));
        if (tab >= TABS.size()) tab = 0;
    }

    @Override
    protected void init() {
        if (open.get() == 0) UiSounds.play(UiSounds.OPEN, 1f, 0.8f);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        searchFocused = false;
        listening = null;
        UiSounds.play(UiSounds.CLOSE, 1f, 0.7f);
    }

    @Override
    public void removed() {
        lastTab = tab;
        lastSelection.clear();
        lastSelection.putAll(selection);
        SoulBound.config().save();
    }

    // =================================================================== frame
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float o = Anim.easeOutCubic(open.get());
        if (SoulBound.settings().ui.blur.isOn() && o > 0.15f && client != null && client.world != null) ctx.applyBlur();
        Render2D.rectV(ctx, 0, 0, width, height, Theme.alpha(0xAA05060C, o), Theme.alpha(0xCC05060C, o));
        Render2D.rectV(ctx, 0, height * 0.55f, width, height * 0.45f, 0, Theme.alpha(Theme.accent(), 0.07f * o));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Anim.frame();
        mx = mouseX;
        my = mouseY;
        hits.clear();
        hoverKey = null;
        clip = null;
        listRect = null;

        float o = open.to(closing ? 0 : 1, closing ? 16 : 9);
        if (closing && o < 0.02f) {
            if (client != null) client.setScreen(null);
            return;
        }
        alpha = Anim.easeOutCubic(o);
        float scale = closing ? 0.96f + 0.04f * alpha : 0.92f + 0.08f * Anim.easeOutBack(o);

        float ww = Math.min(WIN_W, width - 20), wh = Math.min(WIN_H, height - 20);
        float wx = (width - ww) / 2f, wy = (height - wh) / 2f;

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(width / 2f, height / 2f);
        m.scale(scale, scale);
        m.translate(-width / 2f, -height / 2f + (1 - alpha) * 8);
        drawWindow(ctx, wx, wy, ww, wh);
        m.popMatrix();

        if (hoverKey != null && !hoverKey.equals(lastHoverKey) && !closing) UiSounds.hover();
        lastHoverKey = hoverKey;
    }

    private int a(int color) { return Theme.alpha(color, alpha); }

    private int a(int color, float mul) { return Theme.alpha(color, alpha * mul); }

    private Anim.Value anim(Object key) { return anims.computeIfAbsent(key, k -> new Anim.Value(0)); }

    private float pulse(float speed) { return 0.5f + 0.5f * (float) Math.sin(Anim.seconds() * speed); }

    private void panel(DrawContext ctx, float x, float y, float w, float h) {
        Render2D.roundRect(ctx, x, y, w, h, PANEL_R, a(Theme.PANEL));
        Render2D.outline(ctx, x, y, w, h, PANEL_R, 0, a(Theme.BORDER));
    }

    private void drawWindow(DrawContext ctx, float wx, float wy, float ww, float wh) {
        float g = Theme.glowStrength();
        float breathe = 0.85f + 0.15f * pulse(1.6f);
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 34, a(Theme.accent(), 0.22f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 10, a(Theme.accent2(), 0.30f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 3, a(0xFF000000, 0.6f));
        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(Theme.BG));
        Render2D.outline(ctx, wx, wy, ww, wh, RADIUS, 0, a(Theme.BORDER));

        // sidebar
        float sx = wx + GAP, sy = wy + GAP, sh = wh - GAP * 2;
        drawSidebar(ctx, sx, sy, SIDE_W, sh);

        // top bar
        float mx0 = sx + SIDE_W + GAP, mw = wx + ww - GAP - mx0;
        drawTopBar(ctx, mx0, sy, mw, TOP_H);

        // accent line under the top bar
        float ly = sy + TOP_H + 3;
        Render2D.glow(ctx, mx0 + 10, ly, mw - 20, 0.75f, 0.3f, 4, a(Theme.accent(), 0.35f * g));
        Render2D.rectH(ctx, mx0 + 10, ly, (mw - 20) / 2f, 0.75f, a(Theme.accent(), 0.2f), a(Theme.accent()));
        Render2D.rectH(ctx, mx0 + 10 + (mw - 20) / 2f, ly, (mw - 20) / 2f, 0.75f, a(Theme.accent2()), a(Theme.accent2(), 0.2f));

        float cy = ly + 4, ch = wy + wh - GAP - cy;
        if (!search.isBlank()) {
            drawListDetail(ctx, mx0, cy, mw, ch, searchResults(), "RESULTS", true);
            return;
        }
        Tab t = TABS.get(tab);
        switch (t.kind()) {
            case BOTS -> drawBots(ctx, mx0, cy, mw, ch, t);
            default -> drawListDetail(ctx, mx0, cy, mw, ch, t.items(), t.name().toUpperCase(Locale.ROOT), false);
        }
    }

    // =================================================================== sidebar
    private void drawSidebar(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h);
        // soft ambient glow pooled at the bottom of the sidebar
        pushClip(ctx, x + 1, y + 1, w - 2, h - 2);
        Render2D.glow(ctx, x + w / 2f - 1, y + h - 30, 2, 2, 1, 80, a(Theme.accent(), 0.16f * Theme.glowStrength()));
        popClip(ctx);

        // brand
        float lx = x + 10, ly = y + 10, ls = 20;
        Render2D.glow(ctx, lx + 2, ly + 2, ls - 4, ls - 4, ls / 2f, 8, a(Theme.accent(), 0.45f * (0.7f + 0.3f * pulse(2.2f))));
        Render2D.icon(ctx, "logo", lx, ly, ls, a(0xFFFFFFFF));
        Fonts.BRAND.drawTracked(ctx, "SOULBOUND", lx + ls + 6, ly + 1, 0.9f, a(Theme.TEXT));
        Fonts.LABEL.draw(ctx, "v" + SoulBound.VERSION, lx + ls + 6, ly + 14, a(Theme.TEXT_DIM));
        Render2D.hairlineH(ctx, x + 8, y + 38, w - 16, a(Theme.HAIRLINE));

        // rows
        float rowH = 15, rx = x + 4, rw = w - 8;
        float[] rowY = new float[TABS.size()];
        float cy = y + 46;
        Fonts.LABEL.drawTracked(ctx, "MODULES", x + 10, cy, 1f, a(Theme.TEXT_DIM));
        cy += 10;
        for (int i = 0; i < TABS.size(); i++) {
            if (i == FIRST_GENERAL) {
                cy += 6;
                Fonts.LABEL.drawTracked(ctx, "GENERAL", x + 10, cy, 1f, a(Theme.TEXT_DIM));
                cy += 10;
            }
            rowY[i] = cy;
            cy += rowH + 1;
        }

        // sliding selection
        Anim.Value ind = anim("side_ind");
        if (ind.get() == 0) ind.snap(rowY[tab]);
        float iy = ind.to(rowY[tab], 16);
        boolean showSel = search.isBlank();
        float sel = anim("side_show").to(showSel ? 1 : 0, 14);
        if (sel > 0.01f) {
            Render2D.roundRectH(ctx, rx, iy, rw, rowH, 5, a(Theme.accent(), 0.24f * sel), a(Theme.accent(), 0));
            Render2D.glow(ctx, x + 0.5f, iy + 3, 2, rowH - 6, 1, 5, a(Theme.accent(), 0.8f * sel));
            Render2D.roundRect(ctx, x + 0.5f, iy + 3, 2, rowH - 6, 1, a(Theme.accent(), sel));
        }

        for (int i = 0; i < TABS.size(); i++) {
            Tab t = TABS.get(i);
            final int idx = i;
            String key = "side_" + i;
            float ry = rowY[i];
            boolean hov = hot(key, rx, ry, rw, rowH, c -> selectTab(idx));
            float hv = anim(key).to(hov ? 1 : 0, 18);
            float on = anim(key + "_sel").to(tab == i && showSel ? 1 : 0, 14);
            if (hv * (1 - on) > 0.01f) Render2D.roundRect(ctx, rx, ry, rw, rowH, 5, a(Theme.SURFACE_HI, 0.7f * hv * (1 - on)));
            drawRipples(ctx, key);

            int selCol = Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.2f);
            int col = Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), selCol, on);
            Render2D.icon(ctx, t.icon(), rx + 7, ry + rowH / 2f - 3.5f, 8, a(Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.accent(), on)));
            Fonts.BODY.drawCentered(ctx, t.name(), rx + 21 + hv * 1.5f, ry + rowH / 2f, a(col));

            if (t.category() != null) {
                int n = SoulBound.modules().byCategory(t.category()).size();
                if (n > 0) {
                    String cnt = String.valueOf(n);
                    float cw = Math.max(10, Fonts.LABEL.width(cnt) + 6);
                    float bx = rx + rw - cw - 4, by = ry + (rowH - 10) / 2f;
                    Render2D.roundRect(ctx, bx, by, cw, 10, 5, a(Theme.lerp(Theme.SURFACE_SEL, Theme.withAlpha(Theme.accent(), 0x40), on)));
                    Fonts.LABEL.drawCentered(ctx, cnt, bx + (cw - Fonts.LABEL.width(cnt)) / 2f, by + 5, a(Theme.lerp(Theme.TEXT_MUTED, selCol, on)));
                }
            }
        }

        drawUserCard(ctx, x + 6, y + h - 34, w - 12, 28);
    }

    private void drawUserCard(DrawContext ctx, float x, float y, float w, float h) {
        Render2D.roundRect(ctx, x, y, w, h, 8, a(Theme.SURFACE));
        Render2D.outline(ctx, x, y, w, h, 8, 0, a(Theme.BORDER));
        float hs = 16, hx = x + 6, hy = y + (h - hs) / 2f;
        SkinTextures skin;
        if (client != null && client.player != null) skin = client.player.getSkin();
        else {
            UUID id = client != null ? client.getSession().getUuidOrNull() : null;
            skin = id != null ? DefaultSkinHelper.getSkinTextures(id) : DefaultSkinHelper.getSteve();
        }
        if (alpha > 0.5f) PlayerSkinDrawer.draw(ctx, skin, Math.round(hx), Math.round(hy), (int) hs, a(0xFFFFFFFF));
        Render2D.glow(ctx, hx + hs - 3, hy + hs - 3, 4, 4, 2, 3, a(0xFF4ADE80, 0.6f));
        Render2D.circle(ctx, hx + hs - 1, hy + hs - 1, 2.2f, a(Theme.PANEL));
        Render2D.circle(ctx, hx + hs - 1, hy + hs - 1, 1.5f, a(0xFF4ADE80));

        String name = client != null ? client.getSession().getUsername() : "Player";
        float tx = hx + hs + 6;
        Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(name, w - (tx - x) - 4), tx, y + 6, a(Theme.TEXT));
        Fonts.LABEL.drawTracked(ctx, Fonts.LABEL.fit(where(), w - (tx - x) - 4), tx, y + 17, 0.6f, a(Theme.TEXT_DIM));
    }

    private String where() {
        if (client == null || client.world == null) return "MAIN MENU";
        if (client.isInSingleplayer()) return "SINGLEPLAYER";
        var server = client.getCurrentServerEntry();
        return server != null ? server.address.toUpperCase(Locale.ROOT) : "MULTIPLAYER";
    }

    private void selectTab(int i) {
        boolean changed = tab != i || !search.isBlank();
        tab = i;
        search = "";
        searchFocused = false;
        listScrollTarget = 0;
        detailScrollTarget = 0;
        listening = null;
        if (changed) UiSounds.play(UiSounds.CLICK, 1.05f, 0.8f);
    }

    // =================================================================== top bar
    private void drawTopBar(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h);
        float cy = y + h / 2f;
        String hello = "Hello, ";
        String name = client != null ? client.getSession().getUsername() : "Player";
        Fonts.BODY.drawCentered(ctx, hello, x + 10, cy, a(Theme.TEXT_MUTED));
        Fonts.BODY_BOLD.drawCentered(ctx, name, x + 10 + Fonts.BODY.width(hello), cy, a(Theme.TEXT));

        LocalTime now = LocalTime.now();
        String time = String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
        float tw = Fonts.BODY_BOLD.width(time);
        float tcx = x + w * 0.42f;
        Fonts.BODY_BOLD.drawCentered(ctx, time, tcx - tw / 2f, cy, a(Theme.accent()));

        // search
        float sw = 136, sh = 16, sx = x + w - sw - 4, sy = y + (h - sh) / 2f;
        boolean sHov = hot("search", sx, sy, sw, sh, c -> {
            if (!searchFocused) UiSounds.play(UiSounds.CLICK, 1.2f, 0.6f);
            searchFocused = true;
        });
        float sf = anim("search_focus").to(searchFocused ? 1 : 0, 14);
        float shv = anim("search").to(sHov ? 1 : 0, 18);
        if (sf > 0.01f) Render2D.glow(ctx, sx, sy, sw, sh, 6, 6, a(Theme.accent(), 0.3f * sf));
        Render2D.roundRect(ctx, sx, sy, sw, sh, 6, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, Math.max(shv * 0.6f, sf))));
        Render2D.outline(ctx, sx, sy, sw, sh, 6, 0, a(Theme.lerp(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, shv), Theme.accent(), sf * 0.8f)));
        Render2D.icon(ctx, "icon_search", sx + 6, sy + sh / 2f - 3.5f, 7, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), sf)));
        float textX = sx + 17;
        if (search.isEmpty()) Fonts.BODY.drawCentered(ctx, "Search modules…", textX, sy + sh / 2f, a(Theme.TEXT_DIM));
        else Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(search, sw - 36), textX, sy + sh / 2f, a(Theme.TEXT));
        if (searchFocused && (Anim.seconds() % 1.0f) < 0.55f) {
            float cxp = textX + (search.isEmpty() ? 0 : Math.min(Fonts.BODY.width(search), sw - 36)) + 0.5f;
            Render2D.rect(ctx, cxp, sy + 4, 0.75f, sh - 8, a(Theme.accent()));
        }
        float kx = sx + sw - 15;
        Render2D.roundRect(ctx, kx, sy + 3, 11, 10, 3, a(Theme.SURFACE_SEL));
        Render2D.outline(ctx, kx, sy + 3, 11, 10, 3, 0, a(Theme.BORDER_HI));
        Fonts.LABEL.drawCentered(ctx, "/", kx + 4, sy + sh / 2f, a(Theme.TEXT_MUTED));
    }

    private List<Module> searchResults() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        return SoulBound.modules().all().stream().filter(m -> m.getName().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT))).toList();
    }

    // =================================================================== list + detail view
    private void drawListDetail(DrawContext ctx, float x, float y, float w, float h, List<? extends Configurable> items, String head, boolean results) {
        drawList(ctx, x, y, LIST_W, h, items, head, results);
        float dx = x + LIST_W + GAP;
        Configurable item = results ? null : currentSelection(items);
        if (results && !items.isEmpty()) item = items.getFirst();
        drawDetailPanel(ctx, dx, y, x + w - dx, h, item, items);
    }

    private Configurable currentSelection(List<? extends Configurable> items) {
        Configurable item = selection.get(tab);
        if (item != null && !items.contains(item)) item = null;
        if (item == null && TABS.get(tab).kind() != Kind.MODULES && !items.isEmpty()) item = items.getFirst();
        if (item != null) selection.put(tab, item);
        return item;
    }

    private void drawList(DrawContext ctx, float x, float y, float w, float h, List<? extends Configurable> items, String head, boolean results) {
        panel(ctx, x, y, w, h);
        float pad = 8;
        float hy = y + 10;
        Render2D.glow(ctx, x + pad + 1, hy + 2.5f, 3, 3, 1.5f, 4, a(Theme.accent(), 0.8f));
        Render2D.circle(ctx, x + pad + 2.5f, hy + 4, 1.6f, a(Theme.accent()));
        Fonts.LABEL.drawTracked(ctx, head, x + pad + 8, hy + 4 - Fonts.LABEL.size / 2f, 1.2f, a(Theme.accent()));
        float hx = x + pad + 8 + Fonts.LABEL.width(head, 1.2f) + 5;
        String cnt = String.valueOf(items.size());
        float cw = Math.max(11, Fonts.LABEL.width(cnt) + 6);
        Render2D.roundRect(ctx, hx, hy - 1.5f, cw, 11, 5.5f, a(Theme.accent(), 0.16f));
        Fonts.LABEL.drawCentered(ctx, cnt, hx + (cw - Fonts.LABEL.width(cnt)) / 2f, hy + 4, a(Theme.accent()));
        float lineX = hx + cw + 5;
        Render2D.rectH(ctx, lineX, hy + 4, x + w - pad - lineX, 0.5f, a(Theme.accent(), 0.35f), 0);

        float listY = y + 24, listH = h - 24 - 4;
        listRect = new float[]{x, listY, w, listH};
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, listContentH - listH)));
        listScroll = Anim.approach(listScroll, listScrollTarget, 18);
        pushClip(ctx, x + 1, listY, w - 2, listH);
        if (items.isEmpty()) {
            drawListEmpty(ctx, x, listY, w, listH, results);
        } else {
            float rowH = 20, gap = 2;
            float ry = listY + 2 - listScroll;
            for (Configurable it : items) {
                drawListRow(ctx, it, x + 5, ry, w - 10, rowH, results);
                ry += rowH + gap;
            }
            listContentH = items.size() * (rowH + gap) + 4;
        }
        popClip(ctx);
        if (listScroll > 1) Render2D.rectV(ctx, x + 1, listY, w - 2, 10, a(Theme.PANEL), 0);
        if (listContentH - listScroll > listH + 1) Render2D.rectV(ctx, x + 1, listY + listH - 10, w - 2, 10, 0, a(Theme.PANEL));
    }

    private void drawListEmpty(DrawContext ctx, float x, float y, float w, float h, boolean results) {
        float cx = x + w / 2f, cy = y + h / 2f - 16;
        float r = 14;
        float br = 0.6f + 0.4f * pulse(2f);
        Render2D.glow(ctx, cx - r, cy - r, r * 2, r * 2, r, 12, a(Theme.accent(), 0.18f * br));
        Render2D.circle(ctx, cx, cy, r, a(Theme.SURFACE));
        Render2D.outline(ctx, cx - r, cy - r, r * 2, r * 2, r, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.3f * br)));
        String icon = results ? "icon_search" : TABS.get(tab).icon();
        Render2D.icon(ctx, icon, cx - 5.5f, cy - 5.5f, 11, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), 0.5f * br)));
        String title = results ? "No matches" : "No modules yet";
        String sub = results ? "Try a different search" : "Modules will appear here";
        Fonts.BODY_BOLD.draw(ctx, title, cx - Fonts.BODY_BOLD.width(title) / 2f, cy + r + 8, a(Theme.TEXT_MUTED));
        Fonts.BODY.draw(ctx, sub, cx - Fonts.BODY.width(sub) / 2f, cy + r + 20, a(Theme.TEXT_DIM));
    }

    private void drawListRow(DrawContext ctx, Configurable item, float x, float y, float w, float h, boolean results) {
        String key = "row_" + item.getName();
        Module module = item instanceof Module mo ? mo : null;
        boolean selected = !results && selection.get(tab) == item;
        boolean hov = hot(key, x, y, w, h, c -> {
            if (module != null && (c.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || c.x() >= x + w - 30)) {
                toggleModule(module);
                return;
            }
            if (results && module != null) {
                // jump to the module's own page
                for (int i = 0; i < TABS.size(); i++) if (TABS.get(i).category() == module.getCategory()) tab = i;
                search = "";
                searchFocused = false;
                selection.put(tab, module);
                UiSounds.play(UiSounds.CLICK, 1f, 0.8f);
                return;
            }
            if (selection.get(tab) != item) {
                selection.put(tab, item);
                detailScrollTarget = 0;
                listening = null;
                UiSounds.play(UiSounds.CLICK, 1f, 0.8f);
            }
        });
        float hv = anim(key).to(hov ? 1 : 0, 18);
        float sel = anim(key + "_sel").to(selected ? 1 : 0, 14);
        if (sel > 0.01f) {
            Render2D.roundRect(ctx, x, y, w, h, 6, a(Theme.SURFACE_SEL, sel));
            Render2D.roundRectH(ctx, x, y, w, h, 6, a(Theme.accent(), 0.18f * sel), a(Theme.accent2(), 0));
            Render2D.outline(ctx, x, y, w, h, 6, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.25f), sel));
        }
        if (hv * (1 - sel) > 0.01f) Render2D.roundRect(ctx, x, y, w, h, 6, a(Theme.SURFACE_HI, hv * (1 - sel)));
        drawRipples(ctx, key);
        float mh = 9 * Math.max(sel, hv * 0.5f);
        if (mh > 0.3f) {
            Render2D.glow(ctx, x + 3, y + h / 2f - mh / 2f, 2, mh, 1, 5, a(Theme.accent(), 0.7f * sel));
            Render2D.roundRect(ctx, x + 3, y + h / 2f - mh / 2f, 2, mh, 1, a(Theme.accent(), Math.max(sel, hv * 0.6f)));
        }
        int tc = Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), Theme.TEXT, sel);
        Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(item.getName(), w - 40), x + 9 + 2 * Math.max(sel, hv), y + h / 2f, a(tc));
        if (module != null) {
            float on = anim(key + "_on").to(module.isEnabled() ? 1 : 0, 14);
            drawSwitch(ctx, x + w - 25, y + h / 2f - 5, 19, 10, on, hv);
        } else {
            var mtx = ctx.getMatrices();
            mtx.pushMatrix();
            mtx.translate(x + w - 12, y + h / 2f);
            mtx.rotate((float) (-Math.PI / 2));
            Render2D.icon(ctx, "icon_chevron", -3f, -3f + hv, 6, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), Math.max(sel, hv))));
            mtx.popMatrix();
        }
    }

    private void toggleModule(Module module) {
        module.toggle();
        UiSounds.play(module.isEnabled() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
    }

    private void drawSwitch(DrawContext ctx, float x, float y, float w, float h, float on, float hover) {
        float r = h / 2f;
        if (on > 0.01f) Render2D.glow(ctx, x, y, w, h, r, h * 0.55f, a(Theme.accent(), 0.45f * on * (0.8f + 0.2f * hover)));
        Render2D.roundRect(ctx, x, y, w, h, r, a(Theme.lerp(Theme.TRACK_OFF, Theme.BORDER_HI, hover * (1 - on))));
        if (on > 0.01f) Render2D.roundRectH(ctx, x, y, w, h, r, a(Theme.accent(), on), a(Theme.accent2(), on));
        Render2D.outline(ctx, x, y, w, h, r, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), on), 0.6f));
        float kr = r - 1.5f + 0.4f * hover;
        float kx = x + r + (w - 2 * r) * Anim.easeInOut(on);
        Render2D.glow(ctx, kx - kr, y + r - kr, kr * 2, kr * 2, kr, 2.5f, a(0xFF000000, 0.45f));
        Render2D.circle(ctx, kx, y + r, kr, a(Theme.lerp(0xFFB9BCD6, Theme.KNOB, Math.max(on, hover))));
    }

    // =================================================================== bots view
    private void drawBots(DrawContext ctx, float x, float y, float w, float h, Tab t) {
        List<? extends Configurable> bots = t.items();
        Configurable sel = currentSelection(bots);
        if (sel == null && !bots.isEmpty()) {
            sel = bots.getFirst();
            selection.put(tab, sel);
        }

        // icon strip
        panel(ctx, x, y, STRIP_W, h);
        float tile = 20, tx = x + (STRIP_W - tile) / 2f, ty = y + 6;
        for (Configurable c : bots) {
            Module bot = (Module) c;
            String key = "bot_" + bot.getName();
            boolean isSel = bot == sel;
            boolean hov = hot(key, tx, ty, tile, tile, cl -> {
                if (selection.get(tab) != bot) {
                    selection.put(tab, bot);
                    detailScrollTarget = 0;
                    UiSounds.play(UiSounds.CLICK, 1f, 0.8f);
                }
            });
            float hv = anim(key).to(hov ? 1 : 0, 18);
            float s = anim(key + "_sel").to(isSel ? 1 : 0, 14);
            if (s > 0.01f) {
                Render2D.glow(ctx, tx, ty, tile, tile, 6, 6, a(Theme.accent(), 0.35f * s));
                Render2D.roundRect(ctx, tx, ty, tile, tile, 6, a(Theme.withAlpha(Theme.accent(), 0x38), s));
                Render2D.outline(ctx, tx, ty, tile, tile, 6, 0, a(Theme.accent(), 0.6f * s));
                Render2D.roundRect(ctx, x + 0.5f, ty + 5, 2, tile - 10, 1, a(Theme.accent(), s));
            }
            if (hv * (1 - s) > 0.01f) Render2D.roundRect(ctx, tx, ty, tile, tile, 6, a(Theme.SURFACE_HI, hv * (1 - s)));
            drawRipples(ctx, key);
            int ic = Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.25f), s);
            Render2D.icon(ctx, bot.getIcon(), tx + 5, ty + 5, 10, a(ic));
            if (bot.isEnabled()) {
                Render2D.glow(ctx, tx + tile - 4, ty + tile - 4, 3, 3, 1.5f, 3, a(0xFF4ADE80, 0.7f));
                Render2D.circle(ctx, tx + tile - 2.5f, ty + tile - 2.5f, 1.6f, a(0xFF4ADE80));
            }
            ty += tile + 6;
        }
        if (!bots.isEmpty()) Render2D.hairlineH(ctx, x + 6, ty - 3, STRIP_W - 12, a(Theme.HAIRLINE));

        // model preview
        float px = x + STRIP_W + GAP;
        drawPreview(ctx, px, y, PREVIEW_W, h, sel instanceof Module mo ? mo : null);

        // detail
        float dx = px + PREVIEW_W + GAP;
        drawDetailPanel(ctx, dx, y, x + w - dx, h, sel, bots);
    }

    private void drawPreview(DrawContext ctx, float x, float y, float w, float h, Module bot) {
        panel(ctx, x, y, w, h);
        pushClip(ctx, x + 1, y + 1, w - 2, h - 2);
        Render2D.glow(ctx, x + w / 2f - 1, y + h * 0.5f - 1, 2, 2, 1, 70, a(Theme.accent(), 0.14f * Theme.glowStrength()));
        popClip(ctx);

        int idx = 1;
        String code = String.format(Locale.ROOT, "SB-%02d", idx);
        Fonts.LABEL.drawTracked(ctx, code, x + 9, y + 9, 1.2f, a(Theme.TEXT_DIM));
        boolean online = bot != null && bot.isEnabled();
        String status = "OFFLINE";
        int sc = Theme.TEXT_DIM;
        if (online) {
            status = bot instanceof SwordBot sb ? sb.getState().label : "ONLINE";
            sc = Theme.accent();
        }
        float sw = Fonts.LABEL.width(status, 1f);
        float sx = x + w - 9 - sw;
        Fonts.LABEL.drawTracked(ctx, status, sx, y + 9, 1f, a(sc));
        float p = pulse(4f);
        if (online) Render2D.glow(ctx, sx - 7, y + 10.5f, 3, 3, 1.5f, 3, a(sc, 0.5f + 0.4f * p));
        Render2D.circle(ctx, sx - 5.5f, y + 12, 1.6f, a(sc, online ? 0.6f + 0.4f * p : 0.6f));

        // pedestal
        float cx = x + w / 2f, feet = y + h - 46;
        float spin = Anim.seconds() * 0.8f;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, feet);
        m.scale(1f, 0.3f);
        float pr = Math.min(52, w / 2f - 14);
        Render2D.glow(ctx, -pr * 0.75f, -pr * 0.75f, pr * 1.5f, pr * 1.5f, pr * 0.75f, 18, a(Theme.accent(), (online ? 0.35f : 0.18f) * (0.8f + 0.2f * p)));
        Render2D.outline(ctx, -pr, -pr, pr * 2, pr * 2, pr, 0.75f, a(Theme.accent(), online ? 0.55f : 0.3f));
        Render2D.outline(ctx, -pr * 0.68f, -pr * 0.68f, pr * 1.36f, pr * 1.36f, pr * 0.68f, 0.5f, a(Theme.accent2(), 0.25f));
        // orbiting spark
        float ox = (float) Math.cos(spin) * pr, oy = (float) Math.sin(spin) * pr;
        Render2D.glow(ctx, ox - 2, oy - 2, 4, 4, 2, 8, a(Theme.accent(), online ? 0.9f : 0.4f));
        Render2D.circle(ctx, ox, oy, 2, a(0xFFFFFFFF, online ? 0.9f : 0.5f));
        m.popMatrix();

        // model
        float top = y + 22, bottom = feet + 4;
        if (client != null && client.player != null && open.get() > 0.9f && !closing) {
            // drawEntity centres the model in its box, so size the box to put the feet on the pedestal
            float eh = client.player.getHeight();
            int size = (int) Math.min((feet - top - 4) * 0.85f / (eh + 0.1f), (w - 20) / 1.1f);
            float centre = feet + 1 - eh * size / 2f;
            int y1 = (int) (centre - (centre - top)), y2 = (int) (centre + (centre - top));
            InventoryScreen.drawEntity(ctx, (int) (x + 6), y1, (int) (x + w - 6), y2, size, 0.0625f, mx, my, client.player);
        } else if (client == null || client.player == null) {
            float ls = 54;
            Render2D.glow(ctx, cx - ls / 2f + 6, feet - ls - 16, ls - 12, ls - 12, ls / 2f, 20, a(Theme.accent(), 0.3f));
            Render2D.icon(ctx, "logo", cx - ls / 2f, feet - ls - 22 - 2 * p, ls, a(0xFFFFFFFF));
        }

        // stats
        float fy = y + h - 26;
        Render2D.hairlineH(ctx, x + 9, fy - 6, w - 18, a(Theme.HAIRLINE));
        String[][] stats;
        if (bot instanceof SwordBot sb) {
            stats = new String[][]{{"HITS", String.valueOf(sb.getHits())}, {"COMBO", "x" + sb.getCombo()},
                    {"CPS", String.format(Locale.ROOT, "%.1f", sb.getCps())}};
        } else {
            stats = new String[][]{{"STATE", online ? "ON" : "OFF"}};
        }
        float colW = (w - 18) / stats.length;
        for (int i = 0; i < stats.length; i++) {
            float ccx = x + 9 + colW * i + colW / 2f;
            float lw = Fonts.LABEL.width(stats[i][0], 0.8f), vw = Fonts.BODY_BOLD.width(stats[i][1]);
            Fonts.BODY_BOLD.draw(ctx, stats[i][1], ccx - vw / 2f, fy, a(Theme.TEXT));
            Fonts.LABEL.drawTracked(ctx, stats[i][0], ccx - lw / 2f, fy + 11, 0.8f, a(Theme.TEXT_DIM));
        }
    }

    // =================================================================== detail panel
    private void drawDetailPanel(DrawContext ctx, float x, float y, float w, float h, Configurable item, List<? extends Configurable> siblings) {
        panel(ctx, x, y, w, h);
        Object key = item == null ? "empty_" + tab + search.isBlank() : item;
        if (!key.equals(contentKey)) {
            contentKey = key;
            content.snap(0);
        }
        float c = Anim.easeOutCubic(content.to(1, 9));
        float saved = alpha;
        alpha *= c;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(0, (1 - c) * 6);
        if (item == null) drawDetailEmpty(ctx, x, y, w, h);
        else drawDetail(ctx, item, x, y, w, h);
        m.popMatrix();
        alpha = saved;
    }

    private void drawDetailEmpty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 18;
        float s = 36;
        float br = pulse(1.8f);
        Render2D.glow(ctx, cx - s / 2f + 3, cy - s / 2f + 3, s - 6, s - 6, s / 2f, 24, a(Theme.accent(), 0.2f + 0.15f * br));
        Render2D.icon(ctx, "logo", cx - s / 2f, cy - s / 2f - 2 * br, s, a(0xFFFFFFFF));
        boolean none = SoulBound.modules().all().isEmpty() || (TABS.get(tab).category() != null && TABS.get(tab).items().isEmpty());
        String title = none ? "No modules yet" : "Nothing selected";
        String sub = none ? "This is where a module's options will live." : "Pick a module from the list.";
        Fonts.HEADING.draw(ctx, title, cx - Fonts.HEADING.width(title) / 2f, cy + s / 2f + 10, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, sub, cx - Fonts.BODY.width(sub) / 2f, cy + s / 2f + 24, a(Theme.TEXT_DIM));
    }

    private void drawDetail(DrawContext ctx, Configurable item, float x, float y, float w, float h) {
        float pad = 11, px = x + pad, pw = w - pad * 2;
        Module module = item instanceof Module mo ? mo : null;

        String tag = module != null ? module.getTag() : "SETTINGS";
        Fonts.LABEL.drawTracked(ctx, tag, px, y + 9, 1.2f, a(Theme.TEXT_DIM));
        Fonts.BRAND.draw(ctx, Fonts.BRAND.fit(item.getName(), pw - 70), px, y + 19, a(Theme.TEXT));
        float tx = px + Math.min(Fonts.BRAND.width(item.getName()), pw - 70) + 6;
        String badge = module != null ? module.getBadge() : null;
        if (badge != null) {
            float bw = Fonts.LABEL.width(badge, 0.8f) + 8;
            Render2D.roundRect(ctx, tx, y + 20, bw, 9, 3, a(Theme.accent(), 0.16f));
            Fonts.LABEL.drawTracked(ctx, badge, tx + 4, y + 24.5f - Fonts.LABEL.size / 2f, 0.8f, a(Theme.accent()));
            tx += bw + 5;
        }
        // info dot with the description as a hover hint
        boolean info = hot("info_" + item.getName(), tx, y + 20, 9, 9, cl -> { });
        float ih = anim("info_" + item.getName()).to(info ? 1 : 0, 18);
        Render2D.circle(ctx, tx + 4.5f, y + 24.5f, 4.5f, a(Theme.lerp(Theme.TEXT_MUTED, Theme.accent(), ih)));
        Fonts.LABEL.drawCentered(ctx, "i", tx + 3.4f, y + 24.5f, a(Theme.PANEL));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(item.getDescription(), pw - 40), px, y + 34, a(Theme.TEXT_MUTED));

        if (module != null) {
            float sw = 22, sh = 11, sx = px + pw - sw, sy = y + 10;
            boolean hov = hot("big_toggle", sx - 4, sy - 4, sw + 8, sh + 8, cl -> toggleModule(module));
            float hv = anim("big_toggle").to(hov ? 1 : 0, 18);
            float on = anim(module.getName() + "_big_on").to(module.isEnabled() ? 1 : 0, 14);
            drawSwitch(ctx, sx, sy, sw, sh, on, hv);
        }
        Render2D.hairlineH(ctx, px, y + 48, pw, a(Theme.HAIRLINE));

        float listTop = y + 52, listH = y + h - listTop - 4;
        detailScrollTarget = Math.max(0, Math.min(detailScrollTarget, Math.max(0, detailContentH - listH)));
        detailScroll = Anim.approach(detailScroll, detailScrollTarget, 18);
        pushClip(ctx, x + 1, listTop, w - 2, listH);
        float cy = listTop + 3 - detailScroll;
        if (module != null) cy = keybindRow(ctx, module.getKeybind(), "Keybind", px, cy, pw);
        for (Setting<?> s : item.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof BoolSetting b) cy = boolRow(ctx, b, px, cy, pw);
            else if (s instanceof NumberSetting n) cy = numberRow(ctx, n, px, cy, pw);
            else if (s instanceof ModeSetting mo) cy = modeRow(ctx, mo, px, cy, pw);
            else if (s instanceof KeybindSetting k) cy = keybindRow(ctx, k, k.getName(), px, cy, pw);
        }
        detailContentH = cy + detailScroll - listTop + 4;
        popClip(ctx);
        if (detailScroll > 1) Render2D.rectV(ctx, x + 1, listTop, w - 2, 10, a(Theme.PANEL), 0);
        if (detailContentH - detailScroll > listH + 1) Render2D.rectV(ctx, x + 1, listTop + listH - 10, w - 2, 10, 0, a(Theme.PANEL));

        // tooltip for the info dot
        if (ih > 0.01f) {
            String d = item.getDescription();
            float tw = Fonts.BODY.width(d) + 12;
            float ttx = Math.min(tx - 4, x + w - tw - 4), tty = y + 32;
            float saved = alpha;
            alpha *= ih;
            Render2D.glow(ctx, ttx, tty, tw, 15, 5, 8, a(0xFF000000, 0.7f));
            Render2D.roundRect(ctx, ttx, tty, tw, 15, 5, a(0xFF181A2C));
            Render2D.outline(ctx, ttx, tty, tw, 15, 5, 0, a(Theme.accent(), 0.5f));
            Fonts.BODY.drawCentered(ctx, d, ttx + 6, tty + 7.5f, a(Theme.TEXT));
            alpha = saved;
        }
    }

    /** Flat setting row background; returns the hover amount. */
    private float row(DrawContext ctx, String key, float x, float y, float w, float h, boolean hov) {
        float hv = anim(key).to(hov ? 1 : 0, 18);
        if (hv > 0.01f) Render2D.roundRect(ctx, x - 4, y, w + 8, h, 5, a(Theme.SURFACE_HI, 0.55f * hv));
        drawRipples(ctx, key);
        return hv;
    }

    private int labelColor(float hv) { return Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.7f), Theme.TEXT, hv); }

    private float boolRow(DrawContext ctx, BoolSetting s, float x, float y, float w) {
        float h = 19;
        String key = "set_" + System.identityHashCode(s);
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            s.toggle();
            UiSounds.play(s.isOn() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
        });
        float hv = row(ctx, key, x, y, w, h, hov);
        Fonts.BODY.drawCentered(ctx, s.getName(), x, y + h / 2f, a(labelColor(hv)));
        float on = anim(key + "_on").to(s.isOn() ? 1 : 0, 16);
        float b = 10, bx = x + w - b, by = y + (h - b) / 2f;
        if (on > 0.01f) Render2D.glow(ctx, bx, by, b, b, 3, 5, a(Theme.accent(), 0.5f * on));
        Render2D.roundRect(ctx, bx, by, b, b, 3, a(Theme.lerp(Theme.SURFACE_SEL, Theme.accent(), on)));
        Render2D.outline(ctx, bx, by, b, b, 3, 0, a(Theme.lerp(Theme.lerp(Theme.BORDER_HI, Theme.TEXT_DIM, hv), Theme.accent(), on)));
        if (on > 0.01f) {
            float cs = 7 * (0.6f + 0.4f * Anim.easeOutBack(on));
            Render2D.icon(ctx, "icon_check", bx + (b - cs) / 2f, by + (b - cs) / 2f, cs, a(0xFFFFFFFF, on));
        }
        return y + h;
    }

    private float numberRow(DrawContext ctx, NumberSetting s, float x, float y, float w) {
        float h = 28;
        String key = "set_" + System.identityHashCode(s);
        float tx = x, tw = w, ty = y + 20;
        boolean active = dragging == s;
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            dragging = s;
            dragX = tx;
            dragW = tw;
            updateDrag();
            UiSounds.play(UiSounds.CLICK, 1.1f, 0.6f);
        });
        float act = anim(key + "_act").to(active ? 1 : 0, 16);
        float hv = row(ctx, key, x, y, w, h, hov || active);
        Fonts.BODY.drawCentered(ctx, s.getName(), x, y + 8, a(labelColor(hv)));
        String val = s.display();
        float vw = Fonts.BODY_BOLD.width(val);
        Fonts.BODY_BOLD.drawCentered(ctx, val, x + w - vw, y + 8, a(Theme.lerp(Theme.TEXT, Theme.accent(), act)));

        float p = anim(key + "_p").to((float) s.progress(), 20);
        float th = 2;
        Render2D.roundRect(ctx, tx, ty - th / 2f, tw, th, th / 2f, a(Theme.TRACK_OFF));
        float fw = tw * p;
        if (fw > 0.5f) {
            Render2D.glow(ctx, tx, ty - th / 2f, fw, th, th / 2f, 4 + 2 * act, a(Theme.accent(), 0.35f + 0.25f * act));
            Render2D.roundRectH(ctx, tx, ty - th / 2f, fw, th, th / 2f, a(Theme.accent(), 0.7f), a(Theme.accentMix(p)));
        }
        float ks = 6 + 1.5f * Math.max(hv, act);
        float kx = tx + fw - ks / 2f;
        Render2D.glow(ctx, kx, ty - ks / 2f, ks, ks, 2, 4 + 3 * act, a(Theme.accent(), 0.45f + 0.35f * act));
        Render2D.roundRect(ctx, kx, ty - ks / 2f, ks, ks, 2, a(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.15f + 0.3f * act)));
        return y + h;
    }

    private float modeRow(DrawContext ctx, ModeSetting s, float x, float y, float w) {
        float h = 21;
        String key = "set_" + System.identityHashCode(s);
        float maxW = 0;
        for (String mode : s.getModes()) maxW = Math.max(maxW, Fonts.BODY_BOLD.width(mode));
        float cw = Math.max(74, maxW + 30), ch = 15, cx = x + w - cw, cy = y + (h - ch) / 2f;
        Anim.Value slide = anim(key + "_slide");
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            int n = s.getModes().size();
            boolean back = c.x() < cx + cw / 2f && c.x() >= cx;
            int next = (s.index() + (back ? n - 1 : 1)) % n;
            s.set(s.getModes().get(next));
            slide.snap(back ? -1 : 1);
            UiSounds.play(UiSounds.CLICK, back ? 0.95f : 1.1f, 0.75f);
        });
        float hv = row(ctx, key, x, y, w, h, hov);
        Fonts.BODY.drawCentered(ctx, s.getName(), x, y + h / 2f, a(labelColor(hv)));

        boolean overLeft = hov && mx < cx + cw / 2f && mx >= cx, overRight = hov && mx >= cx + cw / 2f;
        Render2D.roundRect(ctx, cx, cy, cw, ch, 5, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_SEL, hv)));
        Render2D.outline(ctx, cx, cy, cw, ch, 5, 0, a(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, hv)));
        var m = ctx.getMatrices();
        for (int side = -1; side <= 1; side += 2) {
            boolean lit = side < 0 ? overLeft : overRight;
            m.pushMatrix();
            m.translate(side < 0 ? cx + 7 : cx + cw - 7, cy + ch / 2f);
            m.rotate((float) (side < 0 ? Math.PI / 2 : -Math.PI / 2));
            Render2D.icon(ctx, "icon_chevron", -2.5f, -2.5f + (lit ? 0.6f : 0), 5, a(lit ? Theme.accent() : Theme.TEXT_DIM));
            m.popMatrix();
        }
        float sv = slide.to(0, 14);
        String val = s.get();
        float vw = Fonts.BODY_BOLD.width(val);
        pushClip(ctx, cx + 12, cy, cw - 24, ch);
        Fonts.BODY_BOLD.drawCentered(ctx, val, cx + (cw - vw) / 2f + sv * 10, cy + ch / 2f, a(Theme.TEXT, 1 - Math.abs(sv)));
        popClip(ctx);
        return y + h;
    }

    private float keybindRow(DrawContext ctx, KeybindSetting s, String label, float x, float y, float w) {
        float h = 19;
        String key = "set_" + System.identityHashCode(s);
        boolean isListening = listening == s;
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            listening = listening == s ? null : s;
            searchFocused = false;
            UiSounds.play(UiSounds.CLICK, listening != null ? 1.2f : 0.9f, 0.8f);
        });
        float act = anim(key + "_act").to(isListening ? 1 : 0, 14);
        float hv = row(ctx, key, x, y, w, h, hov);
        Fonts.BODY.drawCentered(ctx, label, x, y + h / 2f, a(labelColor(hv)));
        float kh = 12, ky = y + (h - kh) / 2f;
        if (isListening || s.isBound()) {
            String kn = isListening ? "PRESS KEY" : s.display();
            Fonts f = isListening ? Fonts.LABEL : Fonts.BODY_BOLD;
            float kw = Math.max(22, (isListening ? f.width(kn, 0.8f) : f.width(kn)) + 10);
            float kx = x + w - kw;
            if (act > 0.01f) Render2D.glow(ctx, kx, ky, kw, kh, 4, 6, a(Theme.accent(), 0.45f * act * (0.6f + 0.4f * pulse(6f))));
            Render2D.roundRect(ctx, kx, ky, kw, kh, 4, a(Theme.lerp(Theme.SURFACE_SEL, Theme.SURFACE_HI, hv)));
            Render2D.outline(ctx, kx, ky, kw, kh, 4, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), act)));
            if (isListening) Fonts.LABEL.drawTracked(ctx, kn, kx + 5, ky + kh / 2f - Fonts.LABEL.size / 2f, 0.8f, a(Theme.accent()));
            else Fonts.BODY_BOLD.drawCentered(ctx, kn, kx + (kw - Fonts.BODY_BOLD.width(kn)) / 2f, ky + kh / 2f, a(Theme.TEXT));
        } else {
            float kx = x + w - 12;
            Render2D.roundRect(ctx, kx, ky, 12, kh, 3.5f, a(Theme.lerp(Theme.SURFACE_SEL, Theme.SURFACE_HI, hv)));
            Render2D.outline(ctx, kx, ky, 12, kh, 3.5f, 0, a(Theme.BORDER_HI));
            Render2D.icon(ctx, "icon_keyboard", kx + 2.5f, ky + 2.5f, 7, a(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv)));
        }
        return y + h;
    }

    // =================================================================== hit testing
    private record Hit(String key, float x, float y, float w, float h, Consumer<Click> action) {}

    private record Ripple(String key, float x, float y, float maxR, long start) {}

    /** Registers a clickable region and returns whether the mouse is over it this frame. */
    private boolean hot(String key, float x, float y, float w, float h, Consumer<Click> action) {
        float cx = x, cy = y, cw = w, ch = h;
        if (clip != null) {
            float x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
            float x1 = Math.min(x + w, clip[0] + clip[2]), y1 = Math.min(y + h, clip[1] + clip[3]);
            cx = x0; cy = y0; cw = x1 - x0; ch = y1 - y0;
            if (cw <= 0 || ch <= 0) return false;
        }
        hits.add(new Hit(key, cx, cy, cw, ch, action));
        if (closing || open.get() < 0.6f) return false;
        boolean over = inside(mx, my, cx, cy, cw, ch);
        if (over) hoverKey = key;
        return over;
    }

    private static boolean inside(double px, double py, float x, float y, float w, float h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    private final List<float[]> clipStack = new ArrayList<>();

    private void pushClip(DrawContext ctx, float x, float y, float w, float h) {
        if (clip != null) {
            clipStack.add(clip);
            float x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
            float x1 = Math.min(x + w, clip[0] + clip[2]), y1 = Math.min(y + h, clip[1] + clip[3]);
            x = x0; y = y0; w = Math.max(0, x1 - x0); h = Math.max(0, y1 - y0);
        }
        clip = new float[]{x, y, w, h};
        ctx.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    private void popClip(DrawContext ctx) {
        clip = clipStack.isEmpty() ? null : clipStack.removeLast();
        ctx.disableScissor();
    }

    private void drawRipples(DrawContext ctx, String key) {
        long now = System.currentTimeMillis();
        for (Ripple r : ripples) {
            if (!r.key.equals(key)) continue;
            float t = (now - r.start) / 520f;
            if (t >= 1) continue;
            Hit hit = null;
            for (Hit hh : hits) if (hh.key.equals(key)) hit = hh;
            if (hit == null) continue;
            float rad = r.maxR * Anim.easeOutCubic(t);
            pushClip(ctx, hit.x, hit.y, hit.w, hit.h);
            Render2D.circle(ctx, r.x, r.y, rad, a(Theme.accent(), 0.22f * (1 - t)));
            popClip(ctx);
        }
    }

    // =================================================================== input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        double x = click.x(), y = click.y();
        Hit h = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (inside(x, y, hh.x, hh.y, hh.w, hh.h)) { h = hh; break; }
        }
        if (h == null) {
            searchFocused = false;
            listening = null;
            return true;
        }
        if (!h.key.equals("search")) searchFocused = false;
        if (listening != null && !h.key.equals("set_" + System.identityHashCode(listening))) listening = null;
        long now = System.currentTimeMillis();
        ripples.removeIf(r -> now - r.start > 600);
        ripples.add(new Ripple(h.key, (float) x, (float) y, Math.max(h.w, h.h) * 1.1f, now));
        h.action.accept(click);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            UiSounds.play(UiSounds.CLICK, 0.9f, 0.4f);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            mx = (int) click.x();
            updateDrag(click.x());
        }
        return true;
    }

    private void updateDrag() { updateDrag(mx); }

    private void updateDrag(double mouseX) {
        if (dragging == null) return;
        double before = dragging.get();
        dragging.setProgress((mouseX - dragX) / dragW);
        if (dragging.get() != before) UiSounds.slide((float) dragging.progress());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (listRect != null && inside(mouseX, mouseY, listRect[0], listRect[1], listRect[2], listRect[3])) listScrollTarget -= (float) vertical * 24;
        else detailScrollTarget -= (float) vertical * 24;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) listening.set(KeybindSetting.NONE);
            else listening.set(key);
            listening = null;
            UiSounds.play(UiSounds.TOGGLE_ON, 1.1f, 0.7f);
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = input.hasCtrlOrCmd() ? "" : search.substring(0, search.length() - 1);
                listScrollTarget = 0;
                UiSounds.play(UiSounds.TYPE, 0.85f, 0.6f);
            }
            return true; // characters arrive via charTyped
        }
        if (key == GLFW.GLFW_KEY_SLASH) {
            searchFocused = true;
            swallowSlash = true;
            UiSounds.play(UiSounds.CLICK, 1.2f, 0.6f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || (key == SoulBound.settings().ui.guiKey.get() && open.get() > 0.5f)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (swallowSlash) {
            swallowSlash = false;
            if (input.asString().equals("/")) return true;
        }
        if (!searchFocused || !input.isValidChar()) return false;
        if (search.length() >= 32) return true;
        search += input.asString();
        listScrollTarget = 0;
        UiSounds.play(UiSounds.TYPE, 0.95f + (float) Math.random() * 0.15f, 0.6f);
        return true;
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        ripples.removeIf(r -> now - r.start > 600);
    }
}
