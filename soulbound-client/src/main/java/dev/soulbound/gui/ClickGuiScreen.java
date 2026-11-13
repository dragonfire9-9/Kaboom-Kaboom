package dev.soulbound.gui;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.SkinPreview;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.Category;
import dev.soulbound.module.Configurable;
import dev.soulbound.module.Module;
import dev.soulbound.module.bots.BotModule;
import dev.soulbound.setting.ActionSetting;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.KeybindSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.setting.Setting;
import dev.soulbound.sound.UiSounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The SoulBound menu: header, category rail, module list and detail cards, footer.
 * The Bots tab swaps the list and detail for a bot strip, a 3D preview of the bot's skin
 * and a flat parameter list.
 */
public final class ClickGuiScreen extends SoulScreen {
    private static final float WIN_W = 540, WIN_H = 340, RADIUS = 14;
    private static final float HEADER_H = 36, FOOTER_H = 20, RAIL_W = 62, LIST_W = 172;
    private static final float GAP = 6, STRIP_W = 28, PREVIEW_W = 150, PANEL_R = 9;
    private static final int TAB_SETTINGS = Category.values().length;
    private static final int TAB_THEME = TAB_SETTINGS + 1;
    /** Tabs from this index on belong to the GENERAL section of the rail. */
    private static final int FIRST_GENERAL = TAB_SETTINGS;
    private static final int TAB_BOTS = Category.BOTS.ordinal();

    private static final String[] TAB_NAMES;
    private static final String[] TAB_ICONS;

    static {
        Category[] cats = Category.values();
        TAB_NAMES = new String[cats.length + 2];
        TAB_ICONS = new String[cats.length + 2];
        for (int i = 0; i < cats.length; i++) {
            TAB_NAMES[i] = cats[i].displayName;
            TAB_ICONS[i] = cats[i].icon;
        }
        TAB_NAMES[cats.length] = "Settings";
        TAB_ICONS[cats.length] = "icon_settings";
        TAB_NAMES[cats.length + 1] = "Theme";
        TAB_ICONS[cats.length + 1] = "icon_theme";
    }

    private static int lastTab = 0;
    private static final Map<Integer, Configurable> lastSelection = new HashMap<>();
    private static final SkinPreview BOT_PREVIEW = new SkinPreview();

    private int tab = lastTab;
    private final Map<Integer, Configurable> selection = new HashMap<>(lastSelection);
    private String search = "";
    private boolean searchFocused, swallowSlash;
    private int sortMode; // 0 = A-Z, 1 = active only
    private float listScroll, listScrollTarget, listContentH;
    private float detailScroll, detailScrollTarget, detailContentH;
    private float[] listRect;
    private ModeSetting dropdown;
    private final Anim.Value dropdownAnim = new Anim.Value(0);
    private float ddX, ddY, ddW;
    private float[] overlay;
    private final Anim.Value content = new Anim.Value(0);
    private Object contentKey;

    // quick-settings sidebar (right-click a module)
    private static final float QUICK_W = 196;
    private Module quick;
    private long quickStart, quickCloseStart;
    private boolean quickClosing, burstPlayed, drawingQuick;
    private float[] quickRect;
    private float quickScroll, quickScrollTarget, quickContentH;
    private float[][] sparks = new float[0][];

    public ClickGuiScreen() {
        super(Text.literal(SoulBound.NAME));
        if (tab > TAB_THEME) tab = 0;
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
        dropdown = null;
        UiSounds.play(UiSounds.CLOSE, 1f, 0.7f);
    }

    @Override
    public void removed() {
        lastTab = tab;
        lastSelection.clear();
        lastSelection.putAll(selection);
        SoulBound.config().save();
    }

    // =================================================================== rendering
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float o = Anim.easeOutCubic(open.get());
        if (SoulBound.settings().ui.blur.isOn() && o > 0.15f && client != null && client.world != null) ctx.applyBlur();
        Render2D.rectV(ctx, 0, 0, width, height, Theme.alpha(0xAA05060C, o), Theme.alpha(0xCC05060C, o));
        Render2D.rectV(ctx, 0, height * 0.55f, width, height * 0.45f, 0, Theme.alpha(Theme.accent(), 0.07f * o));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!beginFrame(mouseX, mouseY)) return;
        listRect = null;
        float o = open.get();
        float scale = closing ? 0.96f + 0.04f * alpha : 0.92f + 0.08f * Anim.easeOutBack(o);
        float ww = Math.min(WIN_W, width - 24), wh = Math.min(WIN_H, height - 24);
        float wx = (width - ww) / 2f, wy = (height - wh) / 2f;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(width / 2f, height / 2f);
        m.scale(scale, scale);
        m.translate(-width / 2f, -height / 2f + (1 - alpha) * 8);
        drawWindow(ctx, wx, wy, ww, wh);
        m.popMatrix();
        endFrame();
    }

    @Override
    protected boolean isBlocked(double x, double y, String key) {
        if (quick != null && !drawingQuick) return true;
        return overlay != null && !key.startsWith("dd_") && inside(x, y, overlay[0], overlay[1], overlay[2], overlay[3]);
    }

    private boolean botsView() { return tab == TAB_BOTS && search.isBlank(); }

    private void drawWindow(DrawContext ctx, float wx, float wy, float ww, float wh) {
        float g = Theme.glowStrength();
        float breathe = 0.85f + 0.15f * pulse(1.6f);
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 34, a(Theme.accent(), 0.22f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 10, a(Theme.accent2(), 0.30f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 3, a(0xFF000000, 0.6f));
        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(Theme.BG));
        Render2D.outline(ctx, wx, wy, ww, wh, RADIUS, 0, a(Theme.BORDER));
        float hlW = ww * 0.5f;
        Render2D.rectH(ctx, wx + ww / 2f - hlW / 2f, wy, hlW / 2f, 0.5f, 0, a(Theme.accent(), 0.55f * g));
        Render2D.rectH(ctx, wx + ww / 2f, wy, hlW / 2f, 0.5f, a(Theme.accent2(), 0.55f * g), 0);

        drawHeader(ctx, wx, wy, ww);
        float bodyY = wy + HEADER_H, bodyH = wh - HEADER_H - FOOTER_H;
        Render2D.hairlineH(ctx, wx, bodyY, ww, a(Theme.HAIRLINE));
        Render2D.hairlineH(ctx, wx, wy + wh - FOOTER_H, ww, a(Theme.HAIRLINE));
        Render2D.hairlineV(ctx, wx + RAIL_W, bodyY, bodyH, a(Theme.HAIRLINE));

        drawRail(ctx, wx, bodyY, bodyH);
        float cx = wx + RAIL_W;
        if (botsView()) {
            drawBots(ctx, cx, bodyY, wx + ww - cx, bodyH);
        } else {
            Render2D.hairlineV(ctx, cx + LIST_W, bodyY, bodyH, a(Theme.HAIRLINE));
            drawList(ctx, cx, bodyY, LIST_W, bodyH);
            float dx = cx + LIST_W;
            drawDetail(ctx, dx, bodyY, wx + ww - dx, bodyH);
        }
        drawFooter(ctx, wx, wy + wh - FOOTER_H, ww);
        drawDropdownOverlay(ctx);
        drawQuick(ctx, wx, wy, ww, wh, bodyY, bodyH);
    }

    // ------------------------------------------------------------------ header
    private void drawHeader(DrawContext ctx, float wx, float wy, float ww) {
        float cy = wy + HEADER_H / 2f;
        float ls = 20, lx = wx + 12, ly = cy - ls / 2f;
        Render2D.glow(ctx, lx + 2, ly + 2, ls - 4, ls - 4, ls / 2f, 9, a(Theme.accent(), 0.45f * (0.7f + 0.3f * pulse(2.2f))));
        Render2D.icon(ctx, "logo", lx, ly, ls, a(0xFFFFFFFF));
        float tx = lx + ls + 9;
        Fonts.BRAND.drawTracked(ctx, "SOULBOUND", tx, cy - Fonts.BRAND.size / 2f - 0.5f, 1.4f, a(Theme.TEXT));
        float bx = tx + Fonts.BRAND.width("SOULBOUND", 1.4f) + 8;
        String badge = "CLIENT";
        float bw = Fonts.LABEL.width(badge, 0.8f) + 10;
        Render2D.roundRect(ctx, bx, cy - 6, bw, 12, 4, a(Theme.accent(), 0.14f));
        Render2D.outline(ctx, bx, cy - 6, bw, 12, 4, 0, a(Theme.accent(), 0.45f));
        Fonts.LABEL.drawTracked(ctx, badge, bx + 5, cy - Fonts.LABEL.size / 2f, 0.8f, a(Theme.accent()));

        float bs = 20, closeX = wx + ww - 12 - bs;
        iconButton(ctx, "close", "icon_close", closeX, cy - bs / 2f, bs, 8, c -> close());
        iconButton(ctx, "gear", "icon_gear", closeX - bs - 4, cy - bs / 2f, bs, 10, c -> selectTab(TAB_SETTINGS));
    }

    private void iconButton(DrawContext ctx, String key, String icon, float x, float y, float s, float iconSize, java.util.function.Consumer<Click> action) {
        boolean hov = hot(key, x, y, s, s, action);
        float h = anim(key).to(hov ? 1 : 0, 18);
        boolean danger = key.equals("close");
        int tint = danger ? 0xFFFF6B81 : Theme.accent();
        if (h > 0.01f) {
            Render2D.glow(ctx, x, y, s, s, 6, 6, a(tint, 0.25f * h));
            Render2D.roundRect(ctx, x, y, s, s, 6, a(Theme.SURFACE_HI, h));
            Render2D.outline(ctx, x, y, s, s, 6, 0, a(Theme.lerp(Theme.BORDER, tint, 0.35f), h));
        }
        drawRipples(ctx, key);
        Render2D.icon(ctx, icon, x + (s - iconSize) / 2f, y + (s - iconSize) / 2f, iconSize,
                a(Theme.lerp(Theme.TEXT_MUTED, danger ? 0xFFFFD0D8 : Theme.TEXT, h)));
    }

    // ------------------------------------------------------------------ rail
    private void drawRail(DrawContext ctx, float x, float y, float h) {
        float tileW = 46, tileH = 26, gap = 2, pad = 3;
        float gx = x + (RAIL_W - tileW - pad * 2) / 2f, gw = tileW + pad * 2, tx = gx + pad;

        // lay out the two groups: MODULES (categories) and GENERAL (settings pages)
        float[] rowY = new float[TAB_NAMES.length];
        float cy = y + 6;
        float[] groupTop = new float[2], groupBottom = new float[2];
        String[] groupLabel = {"MODULES", "GENERAL"};
        for (int gIdx = 0; gIdx < 2; gIdx++) {
            int from = gIdx == 0 ? 0 : FIRST_GENERAL, to = gIdx == 0 ? FIRST_GENERAL : TAB_NAMES.length;
            float lw = Fonts.LABEL.width(groupLabel[gIdx], 0.8f);
            Fonts.LABEL.drawTracked(ctx, groupLabel[gIdx], x + (RAIL_W - lw) / 2f, cy, 0.8f, a(Theme.TEXT_DIM));
            cy += 9;
            groupTop[gIdx] = cy;
            cy += pad;
            for (int i = from; i < to; i++) {
                rowY[i] = cy;
                cy += tileH + (i < to - 1 ? gap : 0);
            }
            cy += pad;
            groupBottom[gIdx] = cy;
            cy += 9;
        }
        for (int gIdx = 0; gIdx < 2; gIdx++) {
            float gh = groupBottom[gIdx] - groupTop[gIdx];
            Render2D.roundRect(ctx, gx, groupTop[gIdx], gw, gh, 11, a(Theme.SURFACE, 0.55f));
            Render2D.outline(ctx, gx, groupTop[gIdx], gw, gh, 11, 0, a(Theme.BORDER, 0.8f));
        }

        Anim.Value ind = anim("rail_indicator");
        if (ind.get() == 0) ind.snap(rowY[tab]);
        float iy = ind.to(rowY[tab], 16);
        Render2D.glow(ctx, tx, iy, tileW, tileH, 8, 8, a(Theme.accent(), 0.16f * Theme.glowStrength()));
        Render2D.roundRect(ctx, tx, iy, tileW, tileH, 8, a(Theme.SURFACE_SEL));
        Render2D.roundRectH(ctx, tx, iy, tileW, tileH, 8, a(Theme.accent(), 0.12f), a(Theme.accent2(), 0.03f));
        Render2D.outline(ctx, tx, iy, tileW, tileH, 8, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.35f)));
        Render2D.glow(ctx, x + 1.5f, iy + tileH / 2f - 7, 2.5f, 14, 1.25f, 6, a(Theme.accent(), 0.8f));
        Render2D.roundRect(ctx, x + 1.5f, iy + tileH / 2f - 7, 2.5f, 14, 1.25f, a(Theme.accent()));

        for (int i = 0; i < TAB_NAMES.length; i++) {
            float ty = rowY[i];
            final int idx = i;
            String key = "tab_" + i;
            boolean hov = hot(key, tx, ty, tileW, tileH, c -> selectTab(idx));
            float hv = anim(key).to(hov && tab != i ? 1 : 0, 18);
            float sel = anim(key + "_sel").to(tab == i ? 1 : 0, 14);
            if (hv > 0.01f) Render2D.roundRect(ctx, tx, ty, tileW, tileH, 8, a(Theme.SURFACE_HI, hv * 0.8f));
            drawRipples(ctx, key);
            int iconColor = Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.accent(), sel);
            float is = 10, lift = -1.2f * hv;
            if (sel > 0.01f) Render2D.glow(ctx, tx + tileW / 2f - 3.5f, ty + 5 + lift, 7, 7, 3.5f, 7, a(Theme.accent(), 0.35f * sel));
            Render2D.icon(ctx, TAB_ICONS[i], tx + (tileW - is) / 2f, ty + 3.5f + lift, is, a(iconColor));
            String label = TAB_NAMES[i].toUpperCase(Locale.ROOT);
            float lw = Fonts.LABEL.width(label, 0.5f);
            int lc = Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.TEXT, sel);
            Fonts.LABEL.drawTracked(ctx, label, tx + (tileW - lw) / 2f, ty + 16.5f, 0.5f, a(lc));
        }
    }

    private void selectTab(int i) {
        if (tab == i) return;
        tab = i;
        listScrollTarget = 0;
        detailScrollTarget = 0;
        dropdown = null;
        listening = null;
        UiSounds.play(UiSounds.CLICK, 1.05f, 0.8f);
    }

    // ------------------------------------------------------------------ list column
    private List<? extends Configurable> tabItems(int t) {
        if (t == TAB_SETTINGS) return SoulBound.settings().pages();
        if (t == TAB_THEME) return List.of(SoulBound.settings().theme);
        return SoulBound.modules().byCategory(Category.values()[t]).stream()
                .sorted(Comparator.comparing(mo -> mo.getName().toLowerCase(Locale.ROOT))).toList();
    }

    private List<? extends Configurable> visibleItems() {
        if (tab >= TAB_SETTINGS && search.isBlank()) return tabItems(tab);
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Module> mods = q.isEmpty()
                ? SoulBound.modules().byCategory(Category.values()[tab])
                : SoulBound.modules().all().stream().filter(mo -> mo.getName().toLowerCase(Locale.ROOT).contains(q)).toList();
        if (sortMode == 1) mods = mods.stream().filter(Module::isEnabled).toList();
        return mods.stream().sorted(Comparator.comparing(mo -> mo.getName().toLowerCase(Locale.ROOT))).toList();
    }

    private void drawSearch(DrawContext ctx, float sx, float sy, float sw, float sh) {
        boolean sHov = hot("search", sx, sy, sw, sh, c -> {
            if (!searchFocused) UiSounds.play(UiSounds.CLICK, 1.2f, 0.6f);
            searchFocused = true;
        });
        float sf = anim("search_focus").to(searchFocused ? 1 : 0, 14);
        float shv = anim("search").to(sHov ? 1 : 0, 18);
        if (sf > 0.01f) Render2D.glow(ctx, sx, sy, sw, sh, 7, 7, a(Theme.accent(), 0.30f * sf));
        Render2D.roundRect(ctx, sx, sy, sw, sh, 7, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, Math.max(shv * 0.6f, sf))));
        Render2D.outline(ctx, sx, sy, sw, sh, 7, 0, a(Theme.lerp(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, shv), Theme.accent(), sf * 0.8f)));
        Render2D.icon(ctx, "icon_search", sx + 7, sy + sh / 2f - 4, 8, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), sf)));
        float textX = sx + 20;
        if (search.isEmpty()) Fonts.BODY.drawCentered(ctx, "Search modules", textX, sy + sh / 2f, a(Theme.TEXT_DIM));
        else Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(search, sw - 40), textX, sy + sh / 2f, a(Theme.TEXT));
        if (searchFocused && (Anim.seconds() % 1.0f) < 0.55f) {
            float cxp = textX + (search.isEmpty() ? 0 : Math.min(Fonts.BODY.width(search), sw - 40)) + 0.5f;
            Render2D.rect(ctx, cxp, sy + 6, 0.75f, sh - 12, a(Theme.accent()));
        }
        float kx = sx + sw - 17;
        Render2D.roundRect(ctx, kx, sy + sh / 2f - 5.5f, 11, 11, 3.5f, a(Theme.SURFACE_SEL));
        Render2D.outline(ctx, kx, sy + sh / 2f - 5.5f, 11, 11, 3.5f, 0, a(Theme.BORDER_HI));
        Fonts.LABEL.drawCentered(ctx, "/", kx + 4, sy + sh / 2f, a(Theme.TEXT_MUTED));
    }

    private void drawList(DrawContext ctx, float x, float y, float w, float h) {
        float pad = 10;
        drawSearch(ctx, x + pad, y + 10, w - pad * 2, 20);

        float ry = y + 38;
        boolean settings = tab >= TAB_SETTINGS && search.isBlank();
        Fonts.LABEL.drawTracked(ctx, settings ? "CLIENT" : "MODULES", x + pad + 1, ry + 7 - Fonts.LABEL.size / 2f, 1f, a(Theme.TEXT_DIM));
        if (!settings) drawSegmented(ctx, x + w - pad - 70, ry, 70, 14);

        float hy = y + 60;
        String head = search.isBlank() ? TAB_NAMES[tab].toUpperCase(Locale.ROOT) : "RESULTS";
        List<? extends Configurable> items = visibleItems();
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

        float listY = y + 74, listH = h - 74 - 6;
        listRect = new float[]{x, listY, w, listH};
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, listContentH - listH)));
        listScroll = Anim.approach(listScroll, listScrollTarget, 18);
        pushClip(ctx, x + 1, listY, w - 1, listH);
        if (items.isEmpty()) {
            drawListEmpty(ctx, x, listY, w, listH);
        } else {
            float rowH = 22, gap = 2, rowY = listY + 2 - listScroll;
            for (Configurable it : items) {
                drawListRow(ctx, it, x + pad - 2, rowY, w - pad * 2 + 4, rowH);
                rowY += rowH + gap;
            }
            listContentH = items.size() * (rowH + gap) + 4;
        }
        popClip(ctx);
        if (listScroll > 1) Render2D.rectV(ctx, x + 1, listY, w - 1, 10, a(Theme.BG), 0);
        if (listContentH - listScroll > listH + 1) Render2D.rectV(ctx, x + 1, listY + listH - 10, w - 1, 10, 0, a(Theme.BG));
    }

    private void drawSegmented(DrawContext ctx, float x, float y, float w, float h) {
        Render2D.roundRect(ctx, x, y, w, h, 5, a(Theme.SURFACE));
        Render2D.outline(ctx, x, y, w, h, 5, 0, a(Theme.BORDER));
        float half = w / 2f;
        float p = anim("seg").to(sortMode, 16);
        float px = x + 1.5f + p * (half - 1.5f);
        Render2D.glow(ctx, px, y + 1.5f, half - 1.5f, h - 3, 4, 5, a(Theme.accent(), 0.18f));
        Render2D.roundRect(ctx, px, y + 1.5f, half - 1.5f, h - 3, 4, a(Theme.SURFACE_SEL));
        Render2D.outline(ctx, px, y + 1.5f, half - 1.5f, h - 3, 4, 0, a(Theme.BORDER_HI));
        String[] labels = {"A-Z", "ACTIVE"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            float sx = x + i * half;
            boolean hov = hot("seg_" + i, sx, y, half, h, c -> {
                if (sortMode != idx) {
                    sortMode = idx;
                    listScrollTarget = 0;
                    UiSounds.play(UiSounds.CLICK, 1.15f, 0.7f);
                }
            });
            float hv = anim("seg_" + i).to(hov ? 1 : 0, 18);
            float on = 1 - Math.abs(p - i);
            int col = Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.TEXT, on);
            float lw = Fonts.LABEL.width(labels[i], 0.6f);
            Fonts.LABEL.drawTracked(ctx, labels[i], sx + (half - lw) / 2f, y + h / 2f - Fonts.LABEL.size / 2f, 0.6f, a(col));
        }
    }

    private void drawListEmpty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 16, r = 15;
        float br = 0.6f + 0.4f * pulse(2f);
        Render2D.glow(ctx, cx - r, cy - r, r * 2, r * 2, r, 12, a(Theme.accent(), 0.18f * br));
        Render2D.circle(ctx, cx, cy, r, a(Theme.SURFACE));
        Render2D.outline(ctx, cx - r, cy - r, r * 2, r * 2, r, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.3f * br)));
        String icon = !search.isBlank() ? "icon_search" : TAB_ICONS[tab];
        Render2D.icon(ctx, icon, cx - 6, cy - 6, 12, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), 0.5f * br)));
        String title = !search.isBlank() ? "No matches" : sortMode == 1 ? "Nothing active" : "No modules yet";
        String sub = !search.isBlank() ? "Try a different search" : sortMode == 1 ? "Enabled modules show here" : "Modules will appear here";
        Fonts.BODY_BOLD.draw(ctx, title, cx - Fonts.BODY_BOLD.width(title) / 2f, cy + r + 8, a(Theme.TEXT_MUTED));
        Fonts.BODY.draw(ctx, sub, cx - Fonts.BODY.width(sub) / 2f, cy + r + 20, a(Theme.TEXT_DIM));
    }

    private void drawListRow(DrawContext ctx, Configurable item, float x, float y, float w, float h) {
        String key = "row_" + item.getName();
        Module module = item instanceof Module mo ? mo : null;
        boolean results = !search.isBlank();
        boolean selected = selection.get(tab) == item;
        boolean hov = hot(key, x, y, w, h, c -> {
            if (module != null && c.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openQuick(module);
                return;
            }
            if (module != null && c.x() >= x + w - 34) {
                toggleModule(module);
                return;
            }
            if (results && module != null && module.getCategory().ordinal() != tab) {
                tab = module.getCategory().ordinal();
                search = "";
                searchFocused = false;
            }
            if (selection.get(tab) != item) {
                selection.put(tab, item);
                detailScrollTarget = 0;
                dropdown = null;
                listening = null;
            }
            UiSounds.play(UiSounds.CLICK, 1f, 0.8f);
        });
        float hv = anim(key).to(hov ? 1 : 0, 18);
        float sel = anim(key + "_sel").to(selected ? 1 : 0, 14);
        if (sel > 0.01f) {
            Render2D.roundRect(ctx, x, y, w, h, 7, a(Theme.SURFACE_SEL, sel));
            Render2D.roundRectH(ctx, x, y, w, h, 7, a(Theme.accent(), 0.16f * sel), a(Theme.accent2(), 0));
            Render2D.outline(ctx, x, y, w, h, 7, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.25f), sel));
            float t = (Anim.seconds() * 0.45f) % 1.6f;
            if (t < 1f) {
                float sx = x + (w + 30) * Anim.easeInOut(t) - 30;
                float sl = Math.max(x + 4, sx), sr = Math.min(x + w - 4, sx + 30);
                if (sr > sl) {
                    float mid = (sl + sr) / 2f;
                    Render2D.rectH(ctx, sl, y + 1, mid - sl, h - 2, 0, a(0xFFFFFFFF, 0.04f * sel));
                    Render2D.rectH(ctx, mid, y + 1, sr - mid, h - 2, a(0xFFFFFFFF, 0.04f * sel), 0);
                }
            }
        }
        if (hv * (1 - sel) > 0.01f) Render2D.roundRect(ctx, x, y, w, h, 7, a(Theme.SURFACE_HI, hv * (1 - sel)));
        drawRipples(ctx, key);
        float mh = 10 * Math.max(sel, hv * 0.5f);
        if (mh > 0.3f) {
            Render2D.glow(ctx, x + 3, y + h / 2f - mh / 2f, 2, mh, 1, 5, a(Theme.accent(), 0.7f * sel));
            Render2D.roundRect(ctx, x + 3, y + h / 2f - mh / 2f, 2, mh, 1, a(Theme.accent(), Math.max(sel, hv * 0.6f)));
        }
        int tc = Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), Theme.TEXT, sel);
        Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(item.getName(), w - 46), x + 10 + 2 * Math.max(sel, hv), y + h / 2f, a(tc));
        if (module != null) {
            float on = anim(key + "_on").to(module.isEnabled() ? 1 : 0, 14);
            drawSwitch(ctx, x + w - 28, y + h / 2f - 5.5f, 21, 11, on, hv);
        } else {
            var mtx = ctx.getMatrices();
            mtx.pushMatrix();
            mtx.translate(x + w - 14, y + h / 2f);
            mtx.rotate((float) (-Math.PI / 2));
            Render2D.icon(ctx, "icon_chevron", -3.5f, -3.5f + hv, 7, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), Math.max(sel, hv))));
            mtx.popMatrix();
        }
    }

    // ------------------------------------------------------------------ detail column (cards)
    private void fadeIn(Object key) {
        if (!key.equals(contentKey)) {
            contentKey = key;
            content.snap(0);
        }
    }

    private void drawDetail(DrawContext ctx, float x, float y, float w, float h) {
        List<? extends Configurable> items = search.isBlank() ? tabItems(tab) : visibleItems();
        Configurable item = selection.get(tab);
        if (item != null && !items.contains(item)) item = null;
        if (item == null && tab >= TAB_SETTINGS && search.isBlank()) item = items.getFirst();
        if (item != null) selection.put(tab, item);

        fadeIn(item == null ? "empty_" + tab : item);
        float c = Anim.easeOutCubic(content.to(1, 9));
        float saved = alpha;
        alpha *= c;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(0, (1 - c) * 6);
        if (item == null) drawDetailEmpty(ctx, x, y, w, h);
        else drawDetailItem(ctx, item, tabItems(item instanceof Module mo ? mo.getCategory().ordinal() : (item == SoulBound.settings().theme ? TAB_THEME : TAB_SETTINGS)), x, y, w, h);
        m.popMatrix();
        alpha = saved;
    }

    private void drawDetailEmpty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 18, s = 38;
        float br = pulse(1.8f);
        Render2D.glow(ctx, cx - s / 2f + 3, cy - s / 2f + 3, s - 6, s - 6, s / 2f, 26, a(Theme.accent(), 0.20f + 0.15f * br));
        Render2D.glow(ctx, cx - s / 2f + 3, cy - s / 2f + 3, s - 6, s - 6, s / 2f, 8, a(Theme.accent2(), 0.25f + 0.1f * br));
        Render2D.icon(ctx, "logo", cx - s / 2f, cy - s / 2f - 2 * br, s, a(0xFFFFFFFF));
        boolean none = tab < TAB_SETTINGS && tabItems(tab).isEmpty();
        String title = none ? "No modules yet" : "Nothing selected";
        String sub = none ? "This is where a module's options will live." : "Pick a module from the list to configure it.";
        Fonts.HEADING.draw(ctx, title, cx - Fonts.HEADING.width(title) / 2f, cy + s / 2f + 10, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, sub, cx - Fonts.BODY.width(sub) / 2f, cy + s / 2f + 24, a(Theme.TEXT_DIM));
    }

    private void drawDetailItem(DrawContext ctx, Configurable item, List<? extends Configurable> siblings, float x, float y, float w, float h) {
        float pad = 16, px = x + pad, pw = w - pad * 2;
        Module module = item instanceof Module mo ? mo : null;
        int idx = siblings.indexOf(item) + 1;
        String section = module != null ? module.getCategory().displayName.toUpperCase(Locale.ROOT) : "SETTINGS";
        String crumb = section + "  ·  " + (module != null ? "MODULE " : "PAGE ") + String.format(Locale.ROOT, "%02d / %02d", idx, siblings.size());
        Fonts.LABEL.drawTracked(ctx, crumb, px, y + 14, 1f, a(Theme.TEXT_DIM));
        Fonts.TITLE.draw(ctx, Fonts.TITLE.fit(item.getName(), pw - 70), px, y + 27, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(item.getDescription(), pw - 70), px, y + 45, a(Theme.TEXT_MUTED));

        if (module != null) {
            float sw = 34, sh = 17, sx = px + pw - sw, sy = y + 26;
            boolean hov = hot("big_toggle", sx - 4, sy - 4, sw + 8, sh + 8, c -> toggleModule(module));
            float hv = anim("big_toggle").to(hov ? 1 : 0, 18);
            float on = anim(module.getName() + "_big_on").to(module.isEnabled() ? 1 : 0, 14);
            String state = module.isEnabled() ? "ENABLED" : "DISABLED";
            float lw = Fonts.LABEL.width(state, 1f);
            Fonts.LABEL.drawTracked(ctx, state, sx + sw - lw, y + 14, 1f, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), on)));
            drawSwitch(ctx, sx, sy, sw, sh, on, hv);
        }
        float lineY = y + 60;
        Render2D.hairlineH(ctx, px, lineY, pw, a(Theme.HAIRLINE));

        float listTop = lineY + 8, listH = y + h - listTop - 6;
        detailScrollTarget = Math.max(0, Math.min(detailScrollTarget, Math.max(0, detailContentH - listH)));
        detailScroll = Anim.approach(detailScroll, detailScrollTarget, 18);
        pushClip(ctx, x + 1, listTop, w - 2, listH);
        float cy = listTop + 2 - detailScroll;
        if (module != null) cy = cardKeybind(ctx, module.getKeybind(), "KEYBIND", "Click to rebind", px, cy, pw, true) + 10;

        List<Setting<?>> visible = item.getSettings().stream().filter(Setting::isVisible).toList();
        Fonts.LABEL.drawTracked(ctx, "PARAMETERS", px + 1, cy, 1.2f, a(Theme.TEXT_DIM));
        String count = String.valueOf(visible.size());
        float cw = Fonts.LABEL.width(count) + 8;
        Render2D.roundRect(ctx, px + pw - cw, cy - 3, cw, 11, 4, a(Theme.SURFACE_SEL));
        Fonts.LABEL.draw(ctx, count, px + pw - cw + 4, cy, a(Theme.TEXT_MUTED));
        cy += 14;
        if (visible.isEmpty()) {
            Render2D.roundRect(ctx, px, cy, pw, 30, 9, a(Theme.SURFACE, 0.6f));
            Render2D.outline(ctx, px, cy, pw, 30, 9, 0, a(Theme.BORDER, 0.7f));
            String t = "This module has no parameters.";
            Fonts.BODY.drawCentered(ctx, t, px + (pw - Fonts.BODY.width(t)) / 2f, cy + 15, a(Theme.TEXT_DIM));
            cy += 34;
        }
        for (Setting<?> s : visible) {
            if (s instanceof BoolSetting b) cy = cardBool(ctx, b, px, cy, pw);
            else if (s instanceof NumberSetting n) cy = cardNumber(ctx, n, px, cy, pw);
            else if (s instanceof ModeSetting mo) cy = cardMode(ctx, mo, px, cy, pw);
            else if (s instanceof KeybindSetting k) cy = cardKeybind(ctx, k, k.getName(), k.getDescription(), px, cy, pw, false);
            else if (s instanceof ActionSetting ac) cy = cardAction(ctx, ac, px, cy, pw);
            cy += 5;
        }
        detailContentH = cy + detailScroll - listTop + 4;
        popClip(ctx);
        if (detailScroll > 1) Render2D.rectV(ctx, x + 1, listTop, w - 2, 10, a(Theme.BG), 0);
        if (detailContentH - detailScroll > listH + 1) Render2D.rectV(ctx, x + 1, listTop + listH - 10, w - 2, 10, 0, a(Theme.BG));
    }

    private float card(DrawContext ctx, String key, float x, float y, float w, float h, boolean hov, float accent) {
        float hv = anim(key).to(hov ? 1 : 0, 18);
        if (accent > 0.01f) Render2D.glow(ctx, x, y, w, h, 9, 7, a(Theme.accent(), 0.18f * accent));
        Render2D.roundRect(ctx, x, y, w, h, 9, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, hv * 0.7f)));
        Render2D.outline(ctx, x, y, w, h, 9, 0, a(Theme.lerp(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, hv), Theme.accent(), accent * 0.6f)));
        drawRipples(ctx, key);
        return hv;
    }

    private float cardBool(DrawContext ctx, BoolSetting s, float x, float y, float w) {
        float h = 26;
        String key = key(s);
        boolean hov = hot(key, x, y, w, h, c -> {
            s.toggle();
            UiSounds.play(s.isOn() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
        });
        float hv = card(ctx, key, x, y, w, h, hov, 0);
        Fonts.BODY.drawCentered(ctx, s.getName(), x + 12, y + h / 2f, a(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.6f + 0.4f * hv)));
        float on = anim(key + "_on").to(s.isOn() ? 1 : 0, 14);
        drawSwitch(ctx, x + w - 12 - 24, y + h / 2f - 6, 24, 12, on, hv);
        return y + h;
    }

    private float cardNumber(DrawContext ctx, NumberSetting s, float x, float y, float w) {
        float h = 36;
        String key = key(s);
        float tx = x + 12, tw = w - 24, ty = y + 25;
        boolean active = dragging == s;
        boolean hov = hot(key, x, y, w, h, c -> startDrag(s, tx, tw));
        float act = anim(key + "_act").to(active ? 1 : 0, 16);
        float hv = card(ctx, key, x, y, w, h, hov || active, act);
        Fonts.BODY.drawCentered(ctx, s.getName(), x + 12, y + 11, a(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.6f + 0.4f * hv)));
        String val = s.display();
        float vw = Fonts.BODY_BOLD.width(val);
        Fonts.BODY_BOLD.drawCentered(ctx, val, x + w - 12 - vw, y + 11, a(Theme.lerp(Theme.TEXT, Theme.accent(), act)));
        float p = anim(key + "_p").to((float) s.progress(), 20);
        float th = 3;
        Render2D.roundRect(ctx, tx, ty - th / 2f, tw, th, th / 2f, a(Theme.TRACK_OFF));
        float fw = tw * p;
        if (fw > 0.5f) {
            Render2D.glow(ctx, tx, ty - th / 2f, fw, th, th / 2f, 4 + 2 * act, a(Theme.accent(), 0.35f + 0.25f * act));
            Render2D.roundRectH(ctx, tx, ty - th / 2f, fw, th, th / 2f, a(Theme.accent()), a(Theme.accentMix(p)));
        }
        float kr = 4f + 0.8f * Math.max(hv, act), kx = tx + fw;
        Render2D.glow(ctx, kx - kr, ty - kr, kr * 2, kr * 2, kr, 4 + 3 * act, a(act > 0 ? Theme.accent() : 0xFF000000, 0.35f + 0.3f * act));
        Render2D.circle(ctx, kx, ty, kr, a(Theme.KNOB));
        Render2D.circle(ctx, kx, ty, kr * 0.38f * act, a(Theme.accent(), act));
        return y + h;
    }

    private float cardMode(DrawContext ctx, ModeSetting s, float x, float y, float w) {
        float h = 26;
        String key = key(s);
        float maxW = 0;
        for (String mode : s.getModes()) maxW = Math.max(maxW, Fonts.BODY.width(mode));
        float bw = Math.max(80, maxW + 30), bh = 16, bx = x + w - 10 - bw, by = y + (h - bh) / 2f;
        boolean openNow = dropdown == s;
        boolean hov = hot(key, x, y, w, h, c -> {
            if (dropdown == s) dropdown = null;
            else {
                dropdown = s;
                dropdownAnim.snap(0);
            }
            UiSounds.play(UiSounds.CLICK, dropdown != null ? 1.15f : 0.95f, 0.75f);
        });
        if (openNow) {
            ddX = bx;
            ddY = by + bh + 3;
            ddW = bw;
        }
        float act = anim(key + "_act").to(openNow ? 1 : 0, 16);
        float hv = card(ctx, key, x, y, w, h, hov, act * 0.6f);
        Fonts.BODY.drawCentered(ctx, s.getName(), x + 12, y + h / 2f, a(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.6f + 0.4f * hv)));
        Render2D.roundRect(ctx, bx, by, bw, bh, 5, a(Theme.lerp(Theme.SURFACE_SEL, Theme.SURFACE_HI, hv)));
        Render2D.outline(ctx, bx, by, bw, bh, 5, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), act * 0.7f)));
        Fonts.BODY.drawCentered(ctx, s.get(), bx + 8, by + bh / 2f, a(Theme.TEXT));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(bx + bw - 10, by + bh / 2f);
        m.rotate((float) Math.PI * Anim.easeInOut(act));
        Render2D.icon(ctx, "icon_chevron", -3, -3, 6, a(Theme.lerp(Theme.TEXT_MUTED, Theme.accent(), act)));
        m.popMatrix();
        return y + h;
    }

    private float cardKeybind(DrawContext ctx, KeybindSetting s, String label, String hint, float x, float y, float w, boolean caps) {
        float h = 26;
        String key = key(s);
        boolean isListening = listening == s;
        boolean hov = hot(key, x, y, w, h, c -> {
            listening = listening == s ? null : s;
            searchFocused = false;
            UiSounds.play(UiSounds.CLICK, listening != null ? 1.2f : 0.9f, 0.8f);
        });
        float act = anim(key + "_act").to(isListening ? 1 : 0, 14);
        float hv = card(ctx, key, x, y, w, h, hov, act * (0.6f + 0.4f * pulse(6f)));
        if (caps) {
            Fonts.LABEL.drawTracked(ctx, label, x + 12, y + h / 2f - Fonts.LABEL.size / 2f, 1.2f, a(Theme.TEXT_MUTED));
            float lw = Fonts.LABEL.width(label, 1.2f);
            Fonts.BODY.drawCentered(ctx, isListening ? "Press a key… (Esc to clear)" : hint, x + 12 + lw + 10, y + h / 2f,
                    a(isListening ? Theme.accent() : Theme.TEXT_DIM));
        } else {
            Fonts.BODY.drawCentered(ctx, label, x + 12, y + h / 2f, a(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.6f + 0.4f * hv)));
        }
        String kn = isListening ? "• • •" : s.display();
        float kw = Math.max(40, Fonts.BODY_BOLD.width(kn) + 16), kh = 16;
        float kx = x + w - 10 - kw, ky = y + (h - kh) / 2f;
        if (act > 0.01f) Render2D.glow(ctx, kx, ky, kw, kh, 5, 6, a(Theme.accent(), 0.4f * act));
        Render2D.roundRect(ctx, kx, ky, kw, kh, 5, a(Theme.lerp(Theme.SURFACE_SEL, Theme.SURFACE_HI, hv)));
        Render2D.outline(ctx, kx, ky, kw, kh, 5, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), act)));
        Fonts.BODY_BOLD.drawCentered(ctx, kn, kx + (kw - Fonts.BODY_BOLD.width(kn)) / 2f, ky + kh / 2f,
                a(isListening ? Theme.accent() : s.isBound() ? Theme.TEXT : Theme.TEXT_MUTED));
        return y + h;
    }

    private float cardAction(DrawContext ctx, ActionSetting s, float x, float y, float w) {
        float h = 26;
        String key = key(s);
        boolean hov = hot(key, x, y, w, h, c -> {
            UiSounds.play(UiSounds.CLICK, 1.1f, 0.8f);
            s.run();
        });
        float hv = card(ctx, key, x, y, w, h, hov, 0);
        Fonts.BODY.drawCentered(ctx, s.getName(), x + 12, y + h / 2f, a(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.6f + 0.4f * hv)));
        String label = s.getButtonLabel();
        float bw = Fonts.BODY_BOLD.width(label) + 18, bh = 16, bx = x + w - 10 - bw, by = y + (h - bh) / 2f;
        Render2D.glow(ctx, bx, by, bw, bh, 5, 6, a(Theme.accent(), 0.25f + 0.3f * hv));
        Render2D.roundRectH(ctx, bx, by, bw, bh, 5, a(Theme.accent()), a(Theme.accent2()));
        Fonts.BODY_BOLD.drawCentered(ctx, label, bx + 9, by + bh / 2f, a(0xFFFFFFFF));
        return y + h;
    }

    private void drawDropdownOverlay(DrawContext ctx) {
        overlay = null;
        if (dropdown == null) return;
        ModeSetting s = dropdown;
        float t = Anim.easeOutCubic(dropdownAnim.to(1, 16));
        float ih = 16, fullH = s.getModes().size() * ih + 6, h = fullH * t;
        float x = ddX, y = ddY, w = ddW;
        overlay = new float[]{x, y, w, fullH};
        float saved = alpha;
        alpha *= t;
        Render2D.glow(ctx, x, y, w, h, 7, 10, a(0xFF000000, 0.7f));
        Render2D.glow(ctx, x, y, w, h, 7, 6, a(Theme.accent(), 0.18f));
        Render2D.roundRect(ctx, x, y, w, h, 7, a(0xFF161828));
        Render2D.outline(ctx, x, y, w, h, 7, 0, a(Theme.BORDER_HI));
        pushClip(ctx, x, y, w, h);
        for (int i = 0; i < s.getModes().size(); i++) {
            String mode = s.getModes().get(i);
            float iy = y + 3 + i * ih;
            String key = "dd_" + mode;
            boolean hov = hot(key, x + 3, iy, w - 6, ih, c -> {
                s.set(mode);
                dropdown = null;
                UiSounds.play(UiSounds.CLICK, 1.1f, 0.8f);
            });
            float hv = anim(key).to(hov ? 1 : 0, 20);
            boolean cur = s.is(mode);
            if (hv > 0.01f) Render2D.roundRect(ctx, x + 3, iy, w - 6, ih, 5, a(Theme.SURFACE_SEL, hv));
            if (cur) {
                Render2D.glow(ctx, x + 9, iy + ih / 2f - 1.5f, 3, 3, 1.5f, 4, a(Theme.accent(), 0.8f));
                Render2D.circle(ctx, x + 10.5f, iy + ih / 2f, 1.6f, a(Theme.accent()));
            }
            Fonts.BODY.drawCentered(ctx, mode, x + 17, iy + ih / 2f, a(cur ? Theme.accent() : Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv)));
        }
        popClip(ctx);
        alpha = saved;
    }

    // ------------------------------------------------------------------ bots view (Corz-style)
    private void drawBots(DrawContext ctx, float x, float y, float w, float h) {
        List<? extends Configurable> bots = tabItems(TAB_BOTS);
        Configurable sel = selection.get(TAB_BOTS);
        if (sel != null && !bots.contains(sel)) sel = null;
        if (sel == null && !bots.isEmpty()) sel = bots.getFirst();
        if (sel != null) selection.put(TAB_BOTS, sel);
        Module bot = sel instanceof Module mo ? mo : null;

        float px = x + GAP, py = y + GAP, ph = h - GAP * 2;
        // strip
        panel(ctx, px, py, STRIP_W, ph, PANEL_R);
        float tile = 20, tx = px + (STRIP_W - tile) / 2f, ty = py + 6;
        for (Configurable c : bots) {
            Module b = (Module) c;
            String key = "bot_" + b.getName();
            boolean isSel = b == bot;
            boolean hov = hot(key, tx, ty, tile, tile, cl -> {
                if (selection.get(TAB_BOTS) != b) {
                    selection.put(TAB_BOTS, b);
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
                Render2D.roundRect(ctx, px + 0.5f, ty + 5, 2, tile - 10, 1, a(Theme.accent(), s));
            }
            if (hv * (1 - s) > 0.01f) Render2D.roundRect(ctx, tx, ty, tile, tile, 6, a(Theme.SURFACE_HI, hv * (1 - s)));
            drawRipples(ctx, key);
            int ic = Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.25f), s);
            Render2D.icon(ctx, b.getIcon(), tx + 5, ty + 5, 10, a(ic));
            if (b.isEnabled()) {
                Render2D.glow(ctx, tx + tile - 4, ty + tile - 4, 3, 3, 1.5f, 3, a(0xFF4ADE80, 0.7f));
                Render2D.circle(ctx, tx + tile - 2.5f, ty + tile - 2.5f, 1.6f, a(0xFF4ADE80));
            }
            ty += tile + 6;
        }

        // preview
        float vx = px + STRIP_W + GAP;
        drawBotPreview(ctx, vx, py, PREVIEW_W, ph, bot);

        // detail (flat)
        float dx = vx + PREVIEW_W + GAP, dw = x + w - GAP - dx;
        panel(ctx, dx, py, dw, ph, PANEL_R);
        if (bot == null) {
            String t = "No bots yet";
            Fonts.HEADING.draw(ctx, t, dx + (dw - Fonts.HEADING.width(t)) / 2f, py + ph / 2f - 5, a(Theme.TEXT_MUTED));
            return;
        }
        fadeIn(bot);
        float c = Anim.easeOutCubic(content.to(1, 9));
        float saved = alpha;
        alpha *= c;
        drawFlatDetail(ctx, bot, dx, py, dw, ph);
        alpha = saved;
    }

    private void drawBotPreview(DrawContext ctx, float x, float y, float w, float h, Module bot) {
        panel(ctx, x, y, w, h, PANEL_R);
        pushClip(ctx, x + 1, y + 1, w - 2, h - 2);
        Render2D.glow(ctx, x + w / 2f - 1, y + h * 0.5f - 1, 2, 2, 1, 70, a(Theme.accent(), 0.14f * Theme.glowStrength()));
        popClip(ctx);

        Fonts.LABEL.drawTracked(ctx, bot instanceof BotModule bm ? bm.getCode() : "BOT", x + 9, y + 9, 1.2f, a(Theme.TEXT_DIM));
        boolean online = bot != null && bot.isEnabled();
        String status = online ? (bot instanceof BotModule bm ? bm.getState().label : "ONLINE") : "OFFLINE";
        int sc = online ? Theme.accent() : Theme.TEXT_DIM;
        float sw = Fonts.LABEL.width(status, 1f), sx = x + w - 9 - sw;
        Fonts.LABEL.drawTracked(ctx, status, sx, y + 9, 1f, a(sc));
        float p = pulse(4f);
        if (online) Render2D.glow(ctx, sx - 7, y + 10.5f, 3, 3, 1.5f, 3, a(sc, 0.5f + 0.4f * p));
        Render2D.circle(ctx, sx - 5.5f, y + 12, 1.6f, a(sc, online ? 0.6f + 0.4f * p : 0.6f));

        float cx = x + w / 2f, feet = y + h - 44;
        pedestal(ctx, cx, feet, Math.min(48, w / 2f - 16), online);
        float top = y + 24;
        if (open.get() > 0.85f && !closing) {
            BOT_PREVIEW.render(ctx, bot instanceof BotModule bm ? bm.getSkin() : SkinPreview.botSkin(), cx, feet + 1, (feet - top) * 0.9f);
            previewArea(BOT_PREVIEW, x, top, w, feet - top + 6);
        }

        float fy = y + h - 24;
        Render2D.hairlineH(ctx, x + 9, fy - 5, w - 18, a(Theme.HAIRLINE));
        String[][] stats = bot instanceof BotModule sb
                ? new String[][]{{sb.statLabels()[0], String.valueOf(sb.getHits())}, {"COMBO", "x" + sb.getCombo()}, {"CPS", String.format(Locale.ROOT, "%.1f", sb.getCps())}}
                : new String[][]{{"STATE", online ? "ON" : "OFF"}};
        float colW = (w - 18) / stats.length;
        for (int i = 0; i < stats.length; i++) {
            float ccx = x + 9 + colW * i + colW / 2f;
            float lw = Fonts.LABEL.width(stats[i][0], 0.8f), vw = Fonts.BODY_BOLD.width(stats[i][1]);
            Fonts.BODY_BOLD.draw(ctx, stats[i][1], ccx - vw / 2f, fy, a(Theme.TEXT));
            Fonts.LABEL.drawTracked(ctx, stats[i][0], ccx - lw / 2f, fy + 11, 0.8f, a(Theme.TEXT_DIM));
        }
    }

    private void drawFlatDetail(DrawContext ctx, Module item, float x, float y, float w, float h) {
        float pad = 11, px = x + pad, pw = w - pad * 2;
        Fonts.LABEL.drawTracked(ctx, item.getTag(), px, y + 9, 1.2f, a(Theme.TEXT_DIM));
        Fonts.BRAND.draw(ctx, Fonts.BRAND.fit(item.getName(), pw - 70), px, y + 19, a(Theme.TEXT));
        float tx = px + Math.min(Fonts.BRAND.width(item.getName()), pw - 70) + 6;
        String badge = item.getBadge();
        if (badge != null) {
            float bw = Fonts.LABEL.width(badge, 0.8f) + 8;
            Render2D.roundRect(ctx, tx, y + 20, bw, 9, 3, a(Theme.accent(), 0.16f));
            Fonts.LABEL.drawTracked(ctx, badge, tx + 4, y + 24.5f - Fonts.LABEL.size / 2f, 0.8f, a(Theme.accent()));
        }
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(item.getDescription(), pw - 10), px, y + 34, a(Theme.TEXT_MUTED));
        float sw = 22, sh = 11, sx = px + pw - sw, sy = y + 10;
        boolean hov = hot("bot_toggle", sx - 4, sy - 4, sw + 8, sh + 8, c -> toggleModule(item));
        float hv = anim("bot_toggle").to(hov ? 1 : 0, 18);
        float on = anim(item.getName() + "_bot_on").to(item.isEnabled() ? 1 : 0, 14);
        drawSwitch(ctx, sx, sy, sw, sh, on, hv);
        Render2D.hairlineH(ctx, px, y + 48, pw, a(Theme.HAIRLINE));

        float listTop = y + 52, listH = y + h - listTop - 4;
        detailScrollTarget = Math.max(0, Math.min(detailScrollTarget, Math.max(0, detailContentH - listH)));
        detailScroll = Anim.approach(detailScroll, detailScrollTarget, 18);
        pushClip(ctx, x + 1, listTop, w - 2, listH);
        float end = flatSettings(ctx, item, px, listTop + 3 - detailScroll, pw);
        detailContentH = end + detailScroll - listTop + 4;
        popClip(ctx);
        if (detailScroll > 1) Render2D.rectV(ctx, x + 1, listTop, w - 2, 10, a(Theme.PANEL), 0);
        if (detailContentH - detailScroll > listH + 1) Render2D.rectV(ctx, x + 1, listTop + listH - 10, w - 2, 10, 0, a(Theme.PANEL));
    }

    // ------------------------------------------------------------------ footer
    private void drawFooter(DrawContext ctx, float x, float y, float w) {
        float cy = y + FOOTER_H / 2f;
        long active = SoulBound.modules().activeCount();
        int total = SoulBound.modules().all().size();
        int dot = active > 0 ? 0xFF4ADE80 : Theme.accent();
        float p = pulse(3f);
        Render2D.glow(ctx, x + 12, cy - 2, 4, 4, 2, 4 + 2 * p, a(dot, 0.5f + 0.3f * p));
        Render2D.circle(ctx, x + 14, cy, 2, a(dot));
        Fonts.LABEL.drawTracked(ctx, active + " OF " + total + " MODULES ACTIVE", x + 22, cy - Fonts.LABEL.size / 2f, 1.1f, a(Theme.TEXT_MUTED));
        String right = SoulBound.settings().ui.guiKey.display() + " TO CLOSE  ·  V" + SoulBound.VERSION;
        float rw = Fonts.LABEL.width(right, 1.1f);
        Fonts.LABEL.drawTracked(ctx, right, x + w - 14 - rw, cy - Fonts.LABEL.size / 2f, 1.1f, a(Theme.TEXT_DIM));
    }

    // =================================================================== quick-settings sidebar
    private float animSpeed() { return SoulBound.settings().ui.animationSpeed.getFloat(); }

    private void openQuick(Module m) {
        if (quick == m && !quickClosing) {
            closeQuick();
            return;
        }
        quick = m;
        quickClosing = false;
        burstPlayed = false;
        quickStart = System.currentTimeMillis();
        quickScroll = quickScrollTarget = 0;
        dropdown = null;
        listening = null;
        // the burst's sparks: direction, speed, size, spin
        java.util.Random r = new java.util.Random(m.getName().hashCode() ^ quickStart);
        sparks = new float[24][];
        for (int i = 0; i < sparks.length; i++) {
            float ang = (float) (i * Math.PI * 2 / sparks.length + r.nextFloat() * 0.4f);
            sparks[i] = new float[]{(float) Math.cos(ang), (float) Math.sin(ang), 45 + r.nextFloat() * 70, 3 + r.nextFloat() * 4, r.nextFloat() * 6};
        }
        UiSounds.play(UiSounds.SHINE, 1f, 0.8f);
    }

    private void closeQuick() {
        if (quick == null || quickClosing) return;
        quickClosing = true;
        quickCloseStart = System.currentTimeMillis();
        listening = null;
        dragging = null;
        UiSounds.play(UiSounds.CLOSE, 1.2f, 0.6f);
    }

    /** Star that shines, fades, then bursts open into the module's settings bar. */
    private void drawQuick(DrawContext ctx, float wx, float wy, float ww, float wh, float bodyY, float bodyH) {
        quickRect = null;
        if (quick == null) return;
        float pw = QUICK_W, ph = bodyH - 16, px = wx + ww - pw - 8, py = bodyY + 8;
        float cx = px + pw / 2f, cy = py + ph / 2f;
        float t = (System.currentTimeMillis() - quickStart) / 1000f * animSpeed();

        // dim the rest of the window while the bar is up
        float dim = quickClosing ? 1 - Theme.clamp01((System.currentTimeMillis() - quickCloseStart) / 1000f * animSpeed() / 0.3f)
                : Theme.clamp01(t / 0.3f);
        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(0xFF04050A, 0.62f * dim));

        float grow, contentA;
        if (quickClosing) {
            float tc = (System.currentTimeMillis() - quickCloseStart) / 1000f * animSpeed();
            if (tc > 0.4f) {
                quick = null;
                return;
            }
            grow = 1 - Anim.easeInOut(tc / 0.22f);
            contentA = Math.max(0, 1 - tc / 0.12f);
            float sc = Theme.clamp01((tc - 0.15f) / 0.25f);
            if (tc > 0.12f) drawStar(ctx, cx, cy, 16 * (1 - sc * 0.6f), 1 - sc, tc * 4, 0.6f * (1 - sc));
        } else {
            // 1. the star shines
            if (t < 0.62f) {
                float shine = Anim.easeOutBack(Theme.clamp01(t / 0.26f));
                float fade = Theme.clamp01((t - 0.42f) / 0.2f);
                float flicker = 0.85f + 0.15f * (float) Math.sin(t * 40);
                drawStar(ctx, cx, cy, 22 * shine * (1 - 0.25f * fade), (1 - 0.7f * fade) * flicker, t * 2.4f, Theme.clamp01(t / 0.35f) * (1 - 0.7f * fade));
            }
            // 2. then bursts
            if (t >= 0.62f) {
                if (!burstPlayed) {
                    burstPlayed = true;
                    UiSounds.play(UiSounds.BURST, 1f, 0.9f);
                }
                drawBurst(ctx, cx, cy, Theme.clamp01((t - 0.62f) / 0.45f));
            }
            grow = t < 0.68f ? 0 : Anim.easeOutBack(Theme.clamp01((t - 0.68f) / 0.36f));
            contentA = Theme.clamp01((t - 0.9f) / 0.3f);
        }
        if (grow <= 0.001f) return;

        // 3. the bar expands out of the burst
        float gw = pw * Math.min(1.04f, grow), gh = ph * Math.min(1.02f, Math.max(0.04f, (float) Math.pow(Math.max(0, grow), 0.7)));
        float gx = cx - gw / 2f, gy = cy - gh / 2f;
        quickRect = new float[]{gx, gy, gw, gh};
        float g = Math.min(1, grow);
        Render2D.glow(ctx, gx, gy, gw, gh, 12, 26, a(Theme.accent(), 0.30f * g));
        Render2D.glow(ctx, gx, gy, gw, gh, 12, 6, a(0xFF000000, 0.7f * g));
        Render2D.roundRect(ctx, gx, gy, gw, gh, 12, a(0xFF0D0F1A, g));
        Render2D.roundRectH(ctx, gx, gy, gw, Math.min(gh, 60), 12, a(Theme.accent(), 0.10f * g), a(Theme.accent2(), 0.02f * g));
        Render2D.outline(ctx, gx, gy, gw, gh, 12, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.6f), g));
        if (contentA <= 0.01f) return;

        float saved = alpha;
        alpha *= contentA;
        drawingQuick = true;
        keyPrefix = "qset_";
        pushClip(ctx, gx + 1, gy + 1, gw - 2, gh - 2);
        float slide = (1 - Anim.easeOutCubic(contentA)) * 10;
        float ix = px + 12 + slide, iw = pw - 24;

        Fonts.LABEL.drawTracked(ctx, "QUICK SETTINGS", ix, py + 11, 1.2f, a(Theme.accent()));
        Fonts.BRAND.draw(ctx, Fonts.BRAND.fit(quick.getName(), iw - 24), ix, py + 21, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(quick.getDescription(), iw), ix, py + 35, a(Theme.TEXT_MUTED));
        // close button
        float bs = 14, bx = px + pw - 10 - bs, by = py + 9;
        boolean ch = hot("q_close", bx, by, bs, bs, c -> closeQuick());
        float chv = anim("q_close").to(ch ? 1 : 0, 18);
        if (chv > 0.01f) Render2D.roundRect(ctx, bx, by, bs, bs, 4, a(Theme.SURFACE_HI, chv));
        Render2D.icon(ctx, "icon_close", bx + 3.5f, by + 3.5f, 7, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFD0D8, chv)));
        // enable row
        float ey = py + 47, eh = 20;
        boolean eHov = hot("q_enable", ix - 4, ey, iw + 8, eh, c -> toggleModule(quick));
        float ehv = anim("q_enable").to(eHov ? 1 : 0, 18);
        float on = anim(quick.getName() + "_q_on").to(quick.isEnabled() ? 1 : 0, 14);
        Render2D.roundRect(ctx, ix - 4, ey, iw + 8, eh, 6, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, ehv)));
        Render2D.outline(ctx, ix - 4, ey, iw + 8, eh, 6, 0, a(Theme.lerp(Theme.BORDER, Theme.accent(), on * 0.6f)));
        Fonts.LABEL.drawTracked(ctx, quick.isEnabled() ? "ENABLED" : "DISABLED", ix + 2, ey + eh / 2f - Fonts.LABEL.size / 2f, 1f,
                a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), on)));
        drawSwitch(ctx, ix + iw - 22, ey + eh / 2f - 5.5f, 22, 11, on, ehv);
        Render2D.hairlineH(ctx, ix, ey + eh + 6, iw, a(Theme.HAIRLINE));

        float listTop = ey + eh + 9, listH = py + ph - listTop - 4;
        quickScrollTarget = Math.max(0, Math.min(quickScrollTarget, Math.max(0, quickContentH - listH)));
        quickScroll = Anim.approach(quickScroll, quickScrollTarget, 18);
        pushClip(ctx, gx + 1, listTop, gw - 2, listH);
        float end = flatSettings(ctx, quick, ix, listTop + 3 - quickScroll, iw);
        quickContentH = end + quickScroll - listTop + 4;
        popClip(ctx);
        if (quickScroll > 1) Render2D.rectV(ctx, gx + 1, listTop, gw - 2, 10, a(0xFF0D0F1A), 0);
        if (quickContentH - quickScroll > listH + 1) Render2D.rectV(ctx, gx + 1, listTop + listH - 10, gw - 2, 10, 0, a(0xFF0D0F1A));
        popClip(ctx);
        keyPrefix = "set_";
        drawingQuick = false;
        alpha = saved;
    }

    /** A four-point star with a glowing core and light rays. */
    private void drawStar(DrawContext ctx, float cx, float cy, float size, float al, float rot, float rays) {
        if (size <= 0.2f || al <= 0.01f) return;
        int c = Theme.accent();
        Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, size * 2.4f, a(c, 0.55f * al));
        Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, size * 0.9f, a(0xFFFFFFFF, 0.35f * al));
        var m = ctx.getMatrices();
        if (rays > 0.01f) {
            float len = size * 3.2f * rays;
            for (int i = 0; i < 2; i++) {
                m.pushMatrix();
                m.translate(cx, cy);
                m.rotate(rot * 0.35f + i * (float) Math.PI / 2);
                Render2D.rectH(ctx, -len, -0.4f, len, 0.8f, 0, a(0xFFFFFFFF, 0.7f * al * rays));
                Render2D.rectH(ctx, 0, -0.4f, len, 0.8f, a(0xFFFFFFFF, 0.7f * al * rays), 0);
                m.popMatrix();
            }
        }
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(rot);
        Render2D.icon(ctx, "icon_sparkle", -size / 2f, -size / 2f, size, a(Theme.lerp(c, 0xFFFFFFFF, 0.65f), al));
        m.popMatrix();
        Render2D.circle(ctx, cx, cy, Math.max(0.8f, size * 0.09f), a(0xFFFFFFFF, al));
    }

    /** Flash, two shockwave rings and sparks flying outward; {@code b} goes 0..1. */
    private void drawBurst(DrawContext ctx, float cx, float cy, float b) {
        if (b >= 1) return;
        float inv = 1 - b;
        float e = Anim.easeOutCubic(b);
        float flash = Theme.clamp01(1 - b * 3.5f);
        if (flash > 0) Render2D.circle(ctx, cx, cy, 3 + 12 * e, a(0xFFFFFFFF, 0.9f * flash * flash));
        Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, 40 + 60 * e, a(Theme.accent(), 0.5f * inv));
        float r1 = 8 + 120 * e;
        Render2D.outline(ctx, cx - r1, cy - r1, r1 * 2, r1 * 2, r1, 0.5f + 1.5f * inv, a(Theme.accent(), inv));
        float b2 = Theme.clamp01((b - 0.12f) / 0.88f);
        if (b2 > 0) {
            float r2 = 6 + 80 * Anim.easeOutCubic(b2);
            Render2D.outline(ctx, cx - r2, cy - r2, r2 * 2, r2 * 2, r2, 0.5f + inv, a(Theme.accent2(), 0.8f * (1 - b2)));
        }
        var m = ctx.getMatrices();
        for (float[] sp : sparks) {
            float d = sp[2] * e;
            float sx = cx + sp[0] * d, sy = cy + sp[1] * d;
            float s = sp[3] * (0.4f + 0.6f * inv);
            Render2D.glow(ctx, sx - 1, sy - 1, 2, 2, 1, s * 1.6f, a(Theme.accent(), 0.5f * inv));
            m.pushMatrix();
            m.translate(sx, sy);
            m.rotate(sp[4] + b * 6);
            Render2D.icon(ctx, "icon_sparkle", -s / 2f, -s / 2f, s, a(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.5f), inv));
            m.popMatrix();
        }
    }

    // =================================================================== input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        double x = click.x(), y = click.y();
        if (quick != null) {
            if (quickClosing) return true;
            if (quickRect == null || !inside(x, y, quickRect[0], quickRect[1], quickRect[2], quickRect[3])) {
                closeQuick();
                return true;
            }
            Hit h = topHit(x, y);
            if (h != null && h.key().startsWith("q")) activate(h, click);
            return true;
        }
        if (dropdown != null && (overlay == null || !inside(x, y, overlay[0], overlay[1], overlay[2], overlay[3]))) {
            Hit h = topHit(x, y);
            if (h == null || !h.key().equals(key(dropdown))) {
                dropdown = null;
                UiSounds.play(UiSounds.CLICK, 0.9f, 0.5f);
                return true;
            }
        }
        Hit h = topHit(x, y);
        if (h == null || !h.key().equals("search")) searchFocused = false;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (quick != null) {
            quickScrollTarget -= (float) vertical * 24;
            return true;
        }
        if (dropdown != null) return true;
        if (listRect != null && inside(mouseX, mouseY, listRect[0], listRect[1], listRect[2], listRect[3])) listScrollTarget -= (float) vertical * 26;
        else detailScrollTarget -= (float) vertical * 26;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (handleListening(input)) return true;
        int key = input.key();
        if (quick != null && key == GLFW.GLFW_KEY_ESCAPE) {
            closeQuick();
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
            return true;
        }
        if (dropdown != null && key == GLFW.GLFW_KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_SLASH) {
            searchFocused = true;
            swallowSlash = true;
            if (tab >= TAB_SETTINGS) selectTab(0);
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
}
