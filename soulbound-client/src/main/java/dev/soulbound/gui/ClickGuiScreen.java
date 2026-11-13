package dev.soulbound.gui;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.Category;
import dev.soulbound.module.Configurable;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.KeybindSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.setting.Setting;
import dev.soulbound.sound.UiSounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The SoulBound menu. Immediate-mode: every frame lays out and draws the whole window and
 * records clickable regions, which the input handlers then resolve against.
 */
public final class ClickGuiScreen extends Screen {
    // ---------------------------------------------------------------- layout constants
    private static final float WIN_W = 520, WIN_H = 340, RADIUS = 14;
    private static final float HEADER_H = 36, FOOTER_H = 20, RAIL_W = 62, LIST_W = 172;
    private static final int TAB_SETTINGS = Category.values().length;

    private static final String[] TAB_NAMES;
    private static final String[] TAB_ICONS;

    static {
        Category[] cats = Category.values();
        TAB_NAMES = new String[cats.length + 1];
        TAB_ICONS = new String[cats.length + 1];
        for (int i = 0; i < cats.length; i++) {
            TAB_NAMES[i] = cats[i].displayName;
            TAB_ICONS[i] = cats[i].icon;
        }
        TAB_NAMES[cats.length] = "Settings";
        TAB_ICONS[cats.length] = "icon_settings";
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
    private int sortMode; // 0 = A-Z, 1 = active only
    private float listScroll, listScrollTarget, listContentH;
    private float detailScroll, detailScrollTarget, detailContentH;
    private KeybindSetting listening;
    private NumberSetting dragging;
    private float dragX, dragW;
    private ModeSetting dropdown;
    private final Anim.Value dropdownAnim = new Anim.Value(0);
    private float ddX, ddY, ddW;
    private final Anim.Value content = new Anim.Value(0);
    private Object contentKey;

    private final Map<Object, Anim.Value> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ripple> ripples = new ArrayList<>();
    private float[] overlay; // x, y, w, h of the open dropdown (blocks hover beneath)
    private float[] clip;    // current clip rect for hit testing
    private String hoverKey, lastHoverKey;
    private int mx, my;
    private float alpha = 1;

    public ClickGuiScreen() {
        super(Text.literal(SoulBound.NAME));
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
        Anim.frame();
        mx = mouseX;
        my = mouseY;
        hits.clear();
        hoverKey = null;
        clip = null;

        float o = open.to(closing ? 0 : 1, closing ? 16 : 9);
        if (closing && o < 0.02f) {
            if (client != null) client.setScreen(null);
            return;
        }
        alpha = Anim.easeOutCubic(o);
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

        if (hoverKey != null && !hoverKey.equals(lastHoverKey) && !closing) UiSounds.hover();
        lastHoverKey = hoverKey;
    }

    private int a(int color) { return Theme.alpha(color, alpha); }

    private int a(int color, float mul) { return Theme.alpha(color, alpha * mul); }

    private Anim.Value anim(Object key) { return anims.computeIfAbsent(key, k -> new Anim.Value(0)); }

    private float pulse(float speed) { return 0.5f + 0.5f * (float) Math.sin(Anim.seconds() * speed); }

    private void drawWindow(DrawContext ctx, float wx, float wy, float ww, float wh) {
        float g = Theme.glowStrength();
        float breathe = 0.85f + 0.15f * pulse(1.6f);
        // soft outer glow, two layers for a smooth falloff
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 34, a(Theme.accent(), 0.22f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 10, a(Theme.accent2(), 0.30f * g * breathe));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 3, a(0xFF000000, 0.6f));

        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(Theme.BG));
        Render2D.outline(ctx, wx, wy, ww, wh, RADIUS, 0, a(Theme.BORDER));
        // glowing top edge highlight
        float hlW = ww * 0.5f;
        Render2D.rectH(ctx, wx + ww / 2f - hlW / 2f, wy, hlW / 2f, 0.5f, 0, a(Theme.accent(), 0.55f * g));
        Render2D.rectH(ctx, wx + ww / 2f, wy, hlW / 2f, 0.5f, a(Theme.accent2(), 0.55f * g), 0);

        drawHeader(ctx, wx, wy, ww);
        float bodyY = wy + HEADER_H, bodyH = wh - HEADER_H - FOOTER_H;
        Render2D.hairlineH(ctx, wx, bodyY, ww, a(Theme.HAIRLINE));
        Render2D.hairlineH(ctx, wx, wy + wh - FOOTER_H, ww, a(Theme.HAIRLINE));
        Render2D.hairlineV(ctx, wx + RAIL_W, bodyY, bodyH, a(Theme.HAIRLINE));
        Render2D.hairlineV(ctx, wx + RAIL_W + LIST_W, bodyY, bodyH, a(Theme.HAIRLINE));

        drawRail(ctx, wx, bodyY, bodyH);
        drawList(ctx, wx + RAIL_W, bodyY, LIST_W, bodyH);
        float dx = wx + RAIL_W + LIST_W;
        drawDetail(ctx, dx, bodyY, wx + ww - dx, bodyH);
        drawFooter(ctx, wx, wy + wh - FOOTER_H, ww);
        drawDropdownOverlay(ctx);
    }

    // ------------------------------------------------------------------ header
    private void drawHeader(DrawContext ctx, float wx, float wy, float ww) {
        float cy = wy + HEADER_H / 2f;
        float ls = 20;
        float lx = wx + 12, ly = cy - ls / 2f;
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

        // right-hand buttons
        float bs = 20;
        float closeX = wx + ww - 12 - bs;
        iconButton(ctx, "close", "icon_close", closeX, cy - bs / 2f, bs, 8, c -> close());
        iconButton(ctx, "gear", "icon_gear", closeX - bs - 4, cy - bs / 2f, bs, 10, c -> selectTab(TAB_SETTINGS));
    }

    private void iconButton(DrawContext ctx, String key, String icon, float x, float y, float s, float iconSize, Consumer<Click> action) {
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

    // ------------------------------------------------------------------ category rail
    private void drawRail(DrawContext ctx, float x, float y, float h) {
        float tileW = 48, tileH = 38, gap = 3;
        float tx = x + (RAIL_W - tileW) / 2f;
        float startY = y + 8;

        Anim.Value ind = anim("rail_indicator");
        float targetY = startY + tab * (tileH + gap);
        if (ind.get() == 0) ind.snap(targetY);
        float iy = ind.to(targetY, 16);

        // sliding selection tile
        Render2D.glow(ctx, tx, iy, tileW, tileH, 10, 8, a(Theme.accent(), 0.16f * Theme.glowStrength()));
        Render2D.roundRect(ctx, tx, iy, tileW, tileH, 10, a(Theme.SURFACE_SEL));
        Render2D.roundRectH(ctx, tx, iy, tileW, tileH, 10, a(Theme.accent(), 0.10f), a(Theme.accent2(), 0.03f));
        Render2D.outline(ctx, tx, iy, tileW, tileH, 10, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.35f)));
        // glowing edge marker
        Render2D.glow(ctx, x + 1.5f, iy + tileH / 2f - 8, 2.5f, 16, 1.25f, 6, a(Theme.accent(), 0.8f));
        Render2D.roundRect(ctx, x + 1.5f, iy + tileH / 2f - 8, 2.5f, 16, 1.25f, a(Theme.accent()));

        for (int i = 0; i < TAB_NAMES.length; i++) {
            float ty = startY + i * (tileH + gap);
            final int idx = i;
            String key = "tab_" + i;
            boolean hov = hot(key, tx, ty, tileW, tileH, c -> selectTab(idx));
            float hv = anim(key).to(hov && tab != i ? 1 : 0, 18);
            float sel = anim(key + "_sel").to(tab == i ? 1 : 0, 14);
            if (hv > 0.01f) Render2D.roundRect(ctx, tx, ty, tileW, tileH, 10, a(Theme.SURFACE_HI, hv * 0.8f));
            drawRipples(ctx, key);

            int iconColor = Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.accent(), sel);
            float is = 12;
            float lift = -1.5f * hv;
            if (sel > 0.01f) Render2D.glow(ctx, tx + tileW / 2f - 4, ty + 8 + lift + 2, 8, 8, 4, 8, a(Theme.accent(), 0.35f * sel));
            Render2D.icon(ctx, TAB_ICONS[i], tx + (tileW - is) / 2f, ty + 7 + lift, is, a(iconColor));
            String label = TAB_NAMES[i].toUpperCase(Locale.ROOT);
            float lw = Fonts.LABEL.width(label, 0.6f);
            int lc = Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.TEXT, sel);
            Fonts.LABEL.drawTracked(ctx, label, tx + (tileW - lw) / 2f, ty + 24, 0.6f, a(lc));
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
    private List<? extends Configurable> visibleItems() {
        if (tab == TAB_SETTINGS) return SoulBound.settings().pages();
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Module> mods = q.isEmpty()
                ? SoulBound.modules().byCategory(Category.values()[tab])
                : SoulBound.modules().all().stream().filter(mo -> mo.getName().toLowerCase(Locale.ROOT).contains(q)).toList();
        if (sortMode == 1) mods = mods.stream().filter(Module::isEnabled).toList();
        return mods.stream().sorted(Comparator.comparing(mo -> mo.getName().toLowerCase(Locale.ROOT))).toList();
    }

    private void drawList(DrawContext ctx, float x, float y, float w, float h) {
        float pad = 10;
        // search box
        float sx = x + pad, sy = y + 10, sw = w - pad * 2, sh = 20;
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
        if (search.isEmpty()) {
            Fonts.BODY.drawCentered(ctx, "Search modules", textX, sy + sh / 2f, a(Theme.TEXT_DIM));
        } else {
            Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(search, sw - 40), textX, sy + sh / 2f, a(Theme.TEXT));
        }
        if (searchFocused && (Anim.seconds() % 1.0f) < 0.55f) {
            float cxp = textX + (search.isEmpty() ? 0 : Math.min(Fonts.BODY.width(search), sw - 40)) + 0.5f;
            Render2D.rect(ctx, cxp, sy + 6, 0.75f, sh - 12, a(Theme.accent()));
        }
        // "/" shortcut chip
        float kx = sx + sw - 17;
        Render2D.roundRect(ctx, kx, sy + 4.5f, 11, 11, 3.5f, a(Theme.SURFACE_SEL));
        Render2D.outline(ctx, kx, sy + 4.5f, 11, 11, 3.5f, 0, a(Theme.BORDER_HI));
        Fonts.LABEL.drawCentered(ctx, "/", kx + 4, sy + sh / 2f, a(Theme.TEXT_MUTED));

        // section label + segmented sort
        float ry = y + 38;
        String section = tab == TAB_SETTINGS ? "CLIENT" : "MODULES";
        Fonts.LABEL.drawTracked(ctx, section, x + pad + 1, ry + 7 - Fonts.LABEL.size / 2f, 1f, a(Theme.TEXT_DIM));
        if (tab != TAB_SETTINGS) drawSegmented(ctx, x + w - pad - 70, ry, 70, 14);

        // category header
        float hy = y + 60;
        String head = search.isBlank() || tab == TAB_SETTINGS ? TAB_NAMES[tab].toUpperCase(Locale.ROOT) : "RESULTS";
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

        // rows
        float listY = y + 74, listH = h - 74 - 6;
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, listContentH - listH)));
        listScroll = Anim.approach(listScroll, listScrollTarget, 18);
        pushClip(ctx, x + 1, listY, w - 1, listH);
        if (items.isEmpty()) {
            drawListEmpty(ctx, x, listY, w, listH);
        } else {
            float rowH = 22, gap = 2;
            float ry2 = listY + 2 - listScroll;
            for (int i = 0; i < items.size(); i++) {
                drawListRow(ctx, items.get(i), i, x + pad - 2, ry2, w - pad * 2 + 4, rowH);
                ry2 += rowH + gap;
            }
            listContentH = items.size() * (rowH + gap) + 4;
        }
        popClip(ctx);
        // fade edges when scrolled
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
        float cx = x + w / 2f, cy = y + h / 2f - 16;
        float r = 15;
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

    private void drawListRow(DrawContext ctx, Configurable item, int index, float x, float y, float w, float h) {
        String key = "row_" + item.getName();
        Module module = item instanceof Module mo ? mo : null;
        boolean selected = selection.get(tab) == item;
        boolean hov = hot(key, x, y, w, h, c -> {
            if (module != null && (c.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || c.x() >= x + w - 34)) {
                toggleModule(module);
                return;
            }
            if (selection.get(tab) != item) {
                selection.put(tab, item);
                detailScrollTarget = 0;
                dropdown = null;
                listening = null;
                UiSounds.play(UiSounds.CLICK, 1f, 0.8f);
            }
        });
        float hv = anim(key).to(hov ? 1 : 0, 18);
        float sel = anim(key + "_sel").to(selected ? 1 : 0, 14);

        if (sel > 0.01f) {
            Render2D.roundRect(ctx, x, y, w, h, 7, a(Theme.SURFACE_SEL, sel));
            Render2D.roundRectH(ctx, x, y, w, h, 7, a(Theme.accent(), 0.16f * sel), a(Theme.accent2(), 0));
            Render2D.outline(ctx, x, y, w, h, 7, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), 0.25f), sel));
            // shimmer sweep across the selected row
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

        // left accent marker
        float mh = 10 * Math.max(sel, hv * 0.5f);
        if (mh > 0.3f) {
            Render2D.glow(ctx, x + 3, y + h / 2f - mh / 2f, 2, mh, 1, 5, a(Theme.accent(), 0.7f * sel));
            Render2D.roundRect(ctx, x + 3, y + h / 2f - mh / 2f, 2, mh, 1, a(Theme.accent(), Math.max(sel, hv * 0.6f)));
        }

        int tc = Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), Theme.TEXT, sel);
        float tx = x + 10 + 2 * Math.max(sel, hv);
        Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(item.getName(), w - 46), tx, y + h / 2f, a(tc));

        if (module != null) {
            float on = anim(key + "_on").to(module.isEnabled() ? 1 : 0, 14);
            drawSwitch(ctx, x + w - 28, y + h / 2f - 5.5f, 21, 11, on, hv);
        } else {
            var mtx = ctx.getMatrices();
            mtx.pushMatrix();
            float ix = x + w - 14, iy = y + h / 2f;
            mtx.translate(ix, iy);
            mtx.rotate((float) (-Math.PI / 2));
            Render2D.icon(ctx, "icon_chevron", -3.5f, -3.5f + hv, 7, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), Math.max(sel, hv))));
            mtx.popMatrix();
        }
    }

    private void toggleModule(Module module) {
        module.toggle();
        UiSounds.play(module.isEnabled() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
    }

    /** Animated pill switch. {@code on} 0..1, {@code hover} 0..1. */
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

    // ------------------------------------------------------------------ detail column
    private void drawDetail(DrawContext ctx, float x, float y, float w, float h) {
        Configurable item = selection.get(tab);
        List<? extends Configurable> items = tab == TAB_SETTINGS ? SoulBound.settings().pages()
                : SoulBound.modules().byCategory(Category.values()[tab]);
        if (item != null && !items.contains(item)) item = null;
        if (item == null && tab == TAB_SETTINGS) item = SoulBound.settings().pages().getFirst();
        if (item != null) selection.put(tab, item);

        Object key = item == null ? "empty_" + tab : item;
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
        else drawDetailItem(ctx, item, items, x, y, w, h);
        m.popMatrix();
        alpha = saved;
    }

    private void drawDetailEmpty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 18;
        float s = 38;
        float br = pulse(1.8f);
        Render2D.glow(ctx, cx - s / 2f + 3, cy - s / 2f + 3, s - 6, s - 6, s / 2f, 26, a(Theme.accent(), 0.20f + 0.15f * br));
        Render2D.glow(ctx, cx - s / 2f + 3, cy - s / 2f + 3, s - 6, s - 6, s / 2f, 8, a(Theme.accent2(), 0.25f + 0.1f * br));
        Render2D.icon(ctx, "logo", cx - s / 2f, cy - s / 2f - 2 * br, s, a(0xFFFFFFFF));
        boolean none = SoulBound.modules().all().isEmpty();
        String title = none ? "No modules yet" : "Nothing selected";
        String sub = none ? "This is where a module's options will live." : "Pick a module from the list to configure it.";
        Fonts.HEADING.draw(ctx, title, cx - Fonts.HEADING.width(title) / 2f, cy + s / 2f + 10, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, sub, cx - Fonts.BODY.width(sub) / 2f, cy + s / 2f + 24, a(Theme.TEXT_DIM));
    }

    private void drawDetailItem(DrawContext ctx, Configurable item, List<? extends Configurable> siblings, float x, float y, float w, float h) {
        float pad = 16;
        float px = x + pad, pw = w - pad * 2;
        Module module = item instanceof Module mo ? mo : null;

        int idx = siblings.indexOf(item) + 1;
        String crumb = (tab == TAB_SETTINGS ? "SETTINGS" : TAB_NAMES[tab].toUpperCase(Locale.ROOT)) + "  ·  "
                + (module != null ? "MODULE " : "PAGE ") + String.format(Locale.ROOT, "%02d / %02d", idx, siblings.size());
        Fonts.LABEL.drawTracked(ctx, crumb, px, y + 14, 1f, a(Theme.TEXT_DIM));
        Fonts.TITLE.draw(ctx, Fonts.TITLE.fit(item.getName(), pw - 70), px, y + 27, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(item.getDescription(), pw - 70), px, y + 45, a(Theme.TEXT_MUTED));

        if (module != null) {
            float sw = 34, sh = 17;
            float sx = px + pw - sw, sy = y + 26;
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

        if (module != null) {
            cy = drawKeybindRow(ctx, module.getKeybind(), "KEYBIND", "Click to rebind", px, cy, pw, true) + 10;
        }

        List<Setting<?>> visible = item.getSettings().stream().filter(Setting::isVisible).toList();
        Fonts.LABEL.drawTracked(ctx, "PARAMETERS", px + 1, cy, 1.2f, a(Theme.TEXT_DIM));
        String count = visible.size() + "/" + item.getSettings().size();
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
            if (s instanceof BoolSetting b) cy = drawBoolRow(ctx, b, px, cy, pw);
            else if (s instanceof NumberSetting n) cy = drawNumberRow(ctx, n, px, cy, pw);
            else if (s instanceof ModeSetting mo) cy = drawModeRow(ctx, mo, px, cy, pw);
            else if (s instanceof KeybindSetting k) cy = drawKeybindRow(ctx, k, k.getName(), k.getDescription(), px, cy, pw, false);
            cy += 5;
        }
        detailContentH = cy + detailScroll - listTop + 4;
        popClip(ctx);
        if (detailScroll > 1) Render2D.rectV(ctx, x + 1, listTop, w - 2, 10, a(Theme.BG), 0);
        if (detailContentH - detailScroll > listH + 1) Render2D.rectV(ctx, x + 1, listTop + listH - 10, w - 2, 10, 0, a(Theme.BG));
    }

    /** Card background shared by every parameter row; returns hover amount. */
    private float card(DrawContext ctx, String key, float x, float y, float w, float h, boolean hov, float accent) {
        float hv = anim(key).to(hov ? 1 : 0, 18);
        if (accent > 0.01f) Render2D.glow(ctx, x, y, w, h, 9, 7, a(Theme.accent(), 0.18f * accent));
        Render2D.roundRect(ctx, x, y, w, h, 9, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, hv * 0.7f)));
        Render2D.outline(ctx, x, y, w, h, 9, 0, a(Theme.lerp(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, hv), Theme.accent(), accent * 0.6f)));
        drawRipples(ctx, key);
        return hv;
    }

    private float drawBoolRow(DrawContext ctx, BoolSetting s, float x, float y, float w) {
        float h = 26;
        String key = "set_" + System.identityHashCode(s);
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

    private float drawNumberRow(DrawContext ctx, NumberSetting s, float x, float y, float w) {
        float h = 36;
        String key = "set_" + System.identityHashCode(s);
        float tx = x + 12, tw = w - 24, ty = y + 25;
        boolean active = dragging == s;
        boolean hov = hot(key, x, y, w, h, c -> {
            dragging = s;
            dragX = tx;
            dragW = tw;
            updateDrag();
            UiSounds.play(UiSounds.CLICK, 1.1f, 0.6f);
        });
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
        float kr = 4f + 0.8f * Math.max(hv, act);
        float kx = tx + fw;
        Render2D.glow(ctx, kx - kr, ty - kr, kr * 2, kr * 2, kr, 4 + 3 * act, a(act > 0 ? Theme.accent() : 0xFF000000, 0.35f + 0.3f * act));
        Render2D.circle(ctx, kx, ty, kr, a(Theme.KNOB));
        Render2D.circle(ctx, kx, ty, kr * 0.38f * act, a(Theme.accent(), act));
        return y + h;
    }

    private float drawModeRow(DrawContext ctx, ModeSetting s, float x, float y, float w) {
        float h = 26;
        String key = "set_" + System.identityHashCode(s);
        float bw = Math.max(80, maxModeWidth(s) + 30), bh = 16;
        float bx = x + w - 10 - bw, by = y + (h - bh) / 2f;
        boolean openNow = dropdown == s;
        boolean hov = hot(key, x, y, w, h, c -> {
            if (dropdown == s) {
                dropdown = null;
            } else {
                dropdown = s;
                dropdownAnim.snap(0);
                ddX = bx;
                ddY = by + bh + 3;
                ddW = bw;
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

    private float maxModeWidth(ModeSetting s) {
        float mw = 0;
        for (String mode : s.getModes()) mw = Math.max(mw, Fonts.BODY.width(mode));
        return mw;
    }

    private float drawKeybindRow(DrawContext ctx, KeybindSetting s, String label, String hint, float x, float y, float w, boolean caps) {
        float h = 26;
        String key = "set_" + System.identityHashCode(s);
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

    private void drawDropdownOverlay(DrawContext ctx) {
        overlay = null;
        if (dropdown == null) return;
        ModeSetting s = dropdown;
        float t = Anim.easeOutCubic(dropdownAnim.to(1, 16));
        float ih = 16;
        float fullH = s.getModes().size() * ih + 6;
        float h = fullH * t;
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
            }, true);
            float hv = anim(key).to(hov ? 1 : 0, 20);
            boolean cur = s.is(mode);
            if (hv > 0.01f) Render2D.roundRect(ctx, x + 3, iy, w - 6, ih, 5, a(Theme.SURFACE_SEL, hv));
            if (cur) {
                Render2D.glow(ctx, x + 9, iy + ih / 2f - 1.5f, 3, 3, 1.5f, 4, a(Theme.accent(), 0.8f));
                Render2D.circle(ctx, x + 10.5f, iy + ih / 2f, 1.6f, a(Theme.accent()));
            }
            Fonts.BODY.drawCentered(ctx, mode, x + 17, iy + ih / 2f,
                    a(cur ? Theme.accent() : Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv)));
        }
        popClip(ctx);
        alpha = saved;
    }

    // ------------------------------------------------------------------ footer
    private void drawFooter(DrawContext ctx, float x, float y, float w) {
        float cy = y + FOOTER_H / 2f;
        long active = SoulBound.modules().activeCount();
        int total = SoulBound.modules().all().size();
        int dot = active > 0 ? 0xFF4ADE80 : Theme.accent();
        float p = pulse(3f);
        Render2D.glow(ctx, x + 14 - 2, cy - 2, 4, 4, 2, 4 + 2 * p, a(dot, 0.5f + 0.3f * p));
        Render2D.circle(ctx, x + 14, cy, 2, a(dot));
        String text = active + " OF " + total + " MODULES ACTIVE";
        Fonts.LABEL.drawTracked(ctx, text, x + 22, cy - Fonts.LABEL.size / 2f, 1.1f, a(Theme.TEXT_MUTED));

        String right = SoulBound.settings().ui.guiKey.display() + " TO CLOSE  ·  V" + SoulBound.VERSION;
        float rw = Fonts.LABEL.width(right, 1.1f);
        Fonts.LABEL.drawTracked(ctx, right, x + w - 14 - rw, cy - Fonts.LABEL.size / 2f, 1.1f, a(Theme.TEXT_DIM));
    }

    // =================================================================== hit testing
    private record Hit(String key, float x, float y, float w, float h, Consumer<Click> action, boolean overlay) {}

    private record Ripple(String key, float x, float y, float maxR, long start) {}

    private boolean hot(String key, float x, float y, float w, float h, Consumer<Click> action) {
        return hot(key, x, y, w, h, action, false);
    }

    /** Registers a clickable region and returns whether the mouse is over it this frame. */
    private boolean hot(String key, float x, float y, float w, float h, Consumer<Click> action, boolean isOverlay) {
        float cx = x, cy = y, cw = w, ch = h;
        if (clip != null) {
            float x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
            float x1 = Math.min(x + w, clip[0] + clip[2]), y1 = Math.min(y + h, clip[1] + clip[3]);
            cx = x0; cy = y0; cw = x1 - x0; ch = y1 - y0;
            if (cw <= 0 || ch <= 0) return false;
        }
        hits.add(new Hit(key, cx, cy, cw, ch, action, isOverlay));
        if (closing || open.get() < 0.6f) return false;
        if (!isOverlay && overlay != null && inside(mx, my, overlay[0], overlay[1], overlay[2], overlay[3])) return false;
        boolean over = inside(mx, my, cx, cy, cw, ch);
        if (over) hoverKey = key;
        return over;
    }

    private static boolean inside(double px, double py, float x, float y, float w, float h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    private void pushClip(DrawContext ctx, float x, float y, float w, float h) {
        clip = new float[]{x, y, w, h};
        ctx.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    private void popClip(DrawContext ctx) {
        clip = null;
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
            boolean hadClip = clip != null;
            if (!hadClip) ctx.enableScissor((int) Math.floor(hit.x), (int) Math.floor(hit.y), (int) Math.ceil(hit.x + hit.w), (int) Math.ceil(hit.y + hit.h));
            Render2D.circle(ctx, r.x, r.y, rad, a(Theme.accent(), 0.22f * (1 - t)));
            if (!hadClip) ctx.disableScissor();
        }
    }

    // =================================================================== input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        double x = click.x(), y = click.y();
        if (dropdown != null && (overlay == null || !inside(x, y, overlay[0], overlay[1], overlay[2], overlay[3]))) {
            // clicking outside an open dropdown just closes it (unless it's the same row, handled by its action)
            Hit h = topHit(x, y, false);
            if (h == null || !h.key.equals("set_" + System.identityHashCode(dropdown))) {
                dropdown = null;
                UiSounds.play(UiSounds.CLICK, 0.9f, 0.5f);
                return true;
            }
        }
        Hit h = topHit(x, y, dropdown != null);
        if (h == null) {
            searchFocused = false;
            if (listening != null) listening = null;
            return true;
        }
        if (!h.key.equals("search")) searchFocused = false;
        if (listening != null && !h.key.equals("set_" + System.identityHashCode(listening))) listening = null;
        ripples.removeIf(r -> System.currentTimeMillis() - r.start > 600);
        ripples.add(new Ripple(h.key, (float) x, (float) y, Math.max(h.w, h.h) * 1.1f, System.currentTimeMillis()));
        h.action.accept(click);
        return true;
    }

    private Hit topHit(double x, double y, boolean overlayFirst) {
        if (overlayFirst) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.overlay && inside(x, y, h.x, h.y, h.w, h.h)) return h;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.overlay && inside(x, y, h.x, h.y, h.w, h.h)) return h;
        }
        return null;
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
        if (dropdown != null) return true;
        float ww = Math.min(WIN_W, width - 24);
        float wx = (width - ww) / 2f;
        if (mouseX < wx + RAIL_W + LIST_W) listScrollTarget -= (float) vertical * 26;
        else detailScrollTarget -= (float) vertical * 26;
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
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = input.hasCtrlOrCmd() ? "" : search.substring(0, search.length() - 1);
                    listScrollTarget = 0;
                    UiSounds.play(UiSounds.TYPE, 0.85f, 0.6f);
                }
                return true;
            }
            if (input.isSelectAll()) return true;
            return true; // swallow everything else while typing (chars arrive via charTyped)
        }
        if (dropdown != null && key == GLFW.GLFW_KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_SLASH) {
            searchFocused = true;
            swallowSlash = true;
            if (tab == TAB_SETTINGS) selectTab(0);
            UiSounds.play(UiSounds.CLICK, 1.2f, 0.6f);
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
        // drop finished ripples
        long now = System.currentTimeMillis();
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) if (now - it.next().start > 600) it.remove();
    }
}
