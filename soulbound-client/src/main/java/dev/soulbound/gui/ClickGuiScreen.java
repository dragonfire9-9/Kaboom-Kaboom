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
import dev.soulbound.setting.HeaderSetting;
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

    private Object[] railTooltip; // name, x, y of the hovered compact rail tile

    // quick-settings sidebar (right-click a module)
    private static final float QUICK_W = 196;
    private Module quick;
    private long quickStart, quickCloseStart;
    private boolean quickClosing, burstPlayed, drawingQuick;
    private float[] quickRect;
    private float quickScroll, quickScrollTarget, quickContentH;
    private float[][] sparks = new float[0][];

    // transitions
    private long portalStart = -1, rebuildStart = -1;
    private final float[][] trail = new float[24][3];

    public ClickGuiScreen() { this(false); }

    /** @param rebuild assemble the window from flying tiles (when coming back from the schematic screen) */
    public ClickGuiScreen(boolean rebuild) {
        super(Text.literal(SoulBound.NAME));
        if (tab > TAB_THEME) tab = 0;
        if (rebuild) {
            rebuildStart = System.currentTimeMillis();
            open.snap(1);
        }
    }

    /** Star swirls around the window, shines until everything turns white, then opens the schematic screen. */
    public void beginPortal() {
        if (portalStart > 0) return;
        portalStart = System.currentTimeMillis();
        closeQuick();
        dropdown = null;
        listening = null;
        UiSounds.play(UiSounds.SHINE, 0.8f, 0.9f);
    }

    @Override
    protected void init() {
        if (open.get() == 0 && rebuildStart < 0) UiSounds.play(UiSounds.OPEN, 1f, 0.8f);
        if (rebuildStart > 0 && System.currentTimeMillis() - rebuildStart < 100) UiSounds.play(UiSounds.OPEN, 0.85f, 0.7f);
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
        long now = System.currentTimeMillis();

        // coming back from the schematic screen: tiles fly up and lock into place, bottom rows first
        if (rebuildStart > 0) {
            float t = (now - rebuildStart) / 1000f;
            if (t < 0.75f) {
                renderTiles(ctx, wx - 30, wy - 30, ww + 60, wh + 60, 12, 8, (col, row, rand) -> {
                    float d = ((7 - row) / 8f) * 0.18f + rand * 0.08f;
                    float lin = Theme.clamp01((t - d) / 0.42f);
                    float u = Anim.easeOutBack(lin);
                    return new float[]{(1 - u) * (rand - 0.5f) * 50, (1 - u) * 140, 0.82f + 0.18f * u, Theme.clamp01(lin * 2.2f)};
                }, () -> drawWindow(ctx, wx, wy, ww, wh));
                endFrame();
                return;
            }
            rebuildStart = -1;
        }

        float portal = portalStart > 0 ? (now - portalStart) / 1000f : -1;
        if (portal >= 1.4f) {
            Render2D.rect(ctx, 0, 0, width, height, 0xFFFFFFFF); // hold the white for the hand-off frame
            client.setScreen(new SchematicScreen(true));
            return;
        }
        float shift = portal < 0 ? 0 : (float) Math.sin(Math.PI * Theme.clamp01(portal / 1.4f));
        float[] star = portal < 0 ? null : portalStar(portal, wx, wy, ww, wh);

        if (star != null && star[2] < 0) drawPortalStar(ctx, star, portal); // behind the window
        m.pushMatrix();
        m.translate(width / 2f, height / 2f);
        m.scale(scale * (1 - 0.035f * shift), scale * (1 - 0.035f * shift));
        m.translate(-width / 2f + (star != null ? -(star[0] - width / 2f) * 0.02f : 0), -height / 2f + (1 - alpha) * 8 - 5 * shift);
        drawWindow(ctx, wx, wy, ww, wh);
        m.popMatrix();
        if (star != null && star[2] >= 0) drawPortalStar(ctx, star, portal); // in front of the window
        if (portal >= 0) {
            float white = Anim.easeInOut((portal - 1.0f) / 0.35f);
            if (white > 0) Render2D.rect(ctx, 0, 0, width, height, Theme.alpha(0xFFFFFFFF, white));
        }
        endFrame();
    }

    /** Star position on a tilted 3D orbit around the window: {x, y, depth (-1 behind .. 1 front), size}. */
    private float[] portalStar(float t, float wx, float wy, float ww, float wh) {
        float cx = wx + ww / 2f, cy = wy + wh / 2f;
        if (t < 1.0f) {
            float theta = (float) (Math.PI * 2 * (0.15 + 2.2 * Math.pow(t, 1.55)));
            float shrink = 1 - 0.3f * t;
            float rx = (ww / 2f + 34) * shrink, ry = (wh / 2f) * 0.42f * shrink;
            float x = cx + rx * (float) Math.cos(theta), z = (float) Math.sin(theta);
            float y = cy + ry * z - (wh * 0.1f) * (float) Math.cos(theta);
            return new float[]{x, y, z, 15 + 11 * (z + 1) / 2f + 10 * t};
        }
        // pull into the centre and swell
        float u = Anim.easeInOut((t - 1.0f) / 0.3f);
        float[] last = portalStar(0.999f, wx, wy, ww, wh);
        return new float[]{last[0] + (cx - last[0]) * u, last[1] + (cy - last[1]) * u, 1, last[3] + 60 * u};
    }

    private void drawPortalStar(DrawContext ctx, float[] s, float t) {
        // afterimage trail
        for (int i = trail.length - 1; i > 0; i--) trail[i] = trail[i - 1];
        trail[0] = new float[]{s[0], s[1], s[3]};
        for (int i = trail.length - 1; i >= 1; i--) {
            float[] tr = trail[i];
            if (tr[2] <= 0) continue;
            float f = 1 - i / (float) trail.length;
            float al = f * f * 0.7f;
            Render2D.glow(ctx, tr[0] - 1, tr[1] - 1, 2, 2, 1, tr[2] * 0.7f * f + 2, Theme.alpha(Theme.accent(), al * 0.55f));
            Render2D.circle(ctx, tr[0], tr[1], Math.max(0.6f, tr[2] * 0.09f * f), Theme.alpha(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.6f), al));
        }
        float depth = (s[2] + 1) / 2f;
        float bright = 0.55f + 0.45f * depth + Theme.clamp01((t - 1.0f) / 0.3f);
        Render2D.glow(ctx, s[0] - 1, s[1] - 1, 2, 2, 1, s[3] * 2.6f, Theme.alpha(Theme.accent(), 0.4f * Math.min(1, bright)));
        Render2D.glow(ctx, s[0] - 1, s[1] - 1, 2, 2, 1, s[3] * 0.9f, Theme.alpha(0xFFFFFFFF, 0.5f * Math.min(1, bright)));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(s[0], s[1]);
        m.rotate(t * 5f);
        Render2D.icon(ctx, "icon_sparkle", -s[3] / 2f, -s[3] / 2f, s[3], Theme.alpha(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.7f), Math.min(1, bright)));
        m.popMatrix();
        float len = s[3] * 3.5f * Math.min(1, bright);
        Render2D.rectH(ctx, s[0] - len, s[1] - 0.3f, len, 0.6f, 0, Theme.alpha(0xFFFFFFFF, 0.6f * Math.min(1, bright)));
        Render2D.rectH(ctx, s[0], s[1] - 0.3f, len, 0.6f, Theme.alpha(0xFFFFFFFF, 0.6f * Math.min(1, bright)), 0);
    }

    @Override
    protected boolean isBlocked(double x, double y, String key) {
        if (portalStart > 0 || rebuildStart > 0) return true;
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

        railTooltip = null;
        pushClip(ctx, wx, bodyY, RAIL_W, bodyH);
        drawRail(ctx, wx, bodyY, bodyH);
        popClip(ctx);
        float cx = wx + RAIL_W;
        if (botsView()) {
            drawBots(ctx, cx, bodyY, wx + ww - cx, bodyH);
        } else if (tab < TAB_SETTINGS || !search.isBlank()) {
            drawModuleGrid(ctx, cx, bodyY, wx + ww - cx, bodyH);
        } else {
            Render2D.hairlineV(ctx, cx + LIST_W, bodyY, bodyH, a(Theme.HAIRLINE));
            drawList(ctx, cx, bodyY, LIST_W, bodyH);
            float dx = cx + LIST_W;
            drawDetail(ctx, dx, bodyY, wx + ww - dx, bodyH);
        }
        drawFooter(ctx, wx, wy + wh - FOOTER_H, ww);
        drawDropdownOverlay(ctx);
        if (railTooltip != null) {
            String t = (String) railTooltip[0];
            float tx = (float) railTooltip[1], ty = (float) railTooltip[2], tw = Fonts.BODY_BOLD.width(t) + 12;
            Render2D.glow(ctx, tx, ty - 7, tw, 14, 5, 6, a(0xFF000000, 0.6f));
            Render2D.roundRect(ctx, tx, ty - 7, tw, 14, 5, a(0xFF181A2C));
            Render2D.outline(ctx, tx, ty - 7, tw, 14, 5, 0, a(Theme.accent(), 0.5f));
            Fonts.BODY_BOLD.drawCentered(ctx, t, tx + 6, ty, a(Theme.TEXT));
        }
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
        float tileW = 46, gap = 2, pad = 3;
        // size the tiles to the space available so both groups always fit inside the window
        int n = TAB_NAMES.length;
        float overhead = 6 + 2 * 9 + 4 * pad + 9 + (n - 2) * gap + 6;
        float tileH = Math.max(14, Math.min(30, (h - overhead) / n));
        boolean compact = tileH < 23;
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
            float is = compact ? Math.min(10, tileH - 6) : 10, lift = -1.2f * hv;
            float iy0 = compact ? ty + (tileH - is) / 2f : ty + tileH * 0.5f - 8.5f;
            if (sel > 0.01f) Render2D.glow(ctx, tx + tileW / 2f - 3.5f, iy0 + is / 2f - 3.5f + lift, 7, 7, 3.5f, 7, a(Theme.accent(), 0.35f * sel));
            Render2D.icon(ctx, TAB_ICONS[i], tx + (tileW - is) / 2f, iy0 + lift, is, a(iconColor));
            if (compact) {
                if (hov) railTooltip = new Object[]{TAB_NAMES[i], tx + tileW + 6, ty + tileH / 2f};
                continue;
            }
            String label = TAB_NAMES[i].toUpperCase(Locale.ROOT);
            float lw = Fonts.LABEL.width(label, 0.5f);
            int lc = Theme.lerp(Theme.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv), Theme.TEXT, sel);
            Fonts.LABEL.drawTracked(ctx, label, tx + (tileW - lw) / 2f, iy0 + is + 3, 0.5f, a(lc));
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
                .sorted(Comparator.comparing((Module mo) -> mo instanceof BotModule bm && bm.kind() == BotModule.Kind.UTILITY ? 1 : 0)
                        .thenComparing(mo -> mo.getName().toLowerCase(Locale.ROOT))).toList();
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

    // ------------------------------------------------------------------ module grid
    private void drawModuleGrid(DrawContext ctx, float x, float y, float w, float h) {
        float pad = 12;
        // toolbar: search, filter, hint
        float sw = Math.min(200, w * 0.42f);
        drawSearch(ctx, x + pad, y + 10, sw, 20);
        drawSegmented(ctx, x + pad + sw + 8, y + 13, 70, 14);
        String hint = "CLICK TO TOGGLE  ·  RIGHT-CLICK FOR SETTINGS";
        float hw = Fonts.LABEL.width(hint, 0.8f);
        if (x + pad + sw + 86 + hw < x + w - pad) Fonts.LABEL.drawTracked(ctx, hint, x + w - pad - hw, y + 20 - Fonts.LABEL.size / 2f, 0.8f, a(Theme.TEXT_DIM));

        List<? extends Configurable> items = visibleItems();
        String head = search.isBlank() ? TAB_NAMES[tab].toUpperCase(Locale.ROOT) : "RESULTS";
        float hy = y + 40;
        Render2D.glow(ctx, x + pad + 1, hy + 2.5f, 3, 3, 1.5f, 4, a(Theme.accent(), 0.8f));
        Render2D.circle(ctx, x + pad + 2.5f, hy + 4, 1.6f, a(Theme.accent()));
        Fonts.LABEL.drawTracked(ctx, head, x + pad + 8, hy + 4 - Fonts.LABEL.size / 2f, 1.2f, a(Theme.accent()));
        float hx = x + pad + 8 + Fonts.LABEL.width(head, 1.2f) + 5;
        String cnt = String.valueOf(items.size());
        float cw = Math.max(11, Fonts.LABEL.width(cnt) + 6);
        Render2D.roundRect(ctx, hx, hy - 1.5f, cw, 11, 5.5f, a(Theme.accent(), 0.16f));
        Fonts.LABEL.drawCentered(ctx, cnt, hx + (cw - Fonts.LABEL.width(cnt)) / 2f, hy + 4, a(Theme.accent()));
        Render2D.rectH(ctx, hx + cw + 5, hy + 4, x + w - pad - (hx + cw + 5), 0.5f, a(Theme.accent(), 0.35f), 0);

        float gy = y + 54, gh = h - 54 - 6;
        listRect = new float[]{x, gy, w, gh};
        int cols = w > 420 ? 3 : 2;
        float gap = 6, cardW = (w - pad * 2 - gap * (cols - 1)) / cols, cardH = 46;
        int rows = (items.size() + cols - 1) / cols;
        listContentH = rows * (cardH + gap) + 4;
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, listContentH - gh)));
        listScroll = Anim.approach(listScroll, listScrollTarget, 18);
        pushClip(ctx, x + 1, gy, w - 2, gh);
        if (items.isEmpty()) drawListEmpty(ctx, x, gy, w, gh);
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Module m)) continue;
            float cx = x + pad + (i % cols) * (cardW + gap), cy = gy + 2 + (i / cols) * (cardH + gap) - listScroll;
            if (cy + cardH < gy - 2 || cy > gy + gh + 2) continue;
            drawModuleCard(ctx, m, i, cx, cy, cardW, cardH);
        }
        popClip(ctx);
        if (listScroll > 1) Render2D.rectV(ctx, x + 1, gy, w - 2, 10, a(Theme.BG), 0);
        if (listContentH - listScroll > gh + 1) Render2D.rectV(ctx, x + 1, gy + gh - 10, w - 2, 10, 0, a(Theme.BG));
    }

    private void drawModuleCard(DrawContext ctx, Module m, int index, float x, float y, float w, float h) {
        String key = "card_" + m.getName();
        boolean hov = hot(key, x, y, w, h, c -> {
            if (c.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openQuick(m);
            else toggleModule(m);
        });
        float hv = anim(key).to(hov ? 1 : 0, 16);
        float on = anim(key + "_on").to(m.isEnabled() ? 1 : 0, 12);
        float open = anim(key + "_q").to(quick == m && !quickClosing ? 1 : 0, 14);
        float lift = -1.2f * hv;
        y += lift;
        if (on > 0.01f || hv > 0.01f) Render2D.glow(ctx, x, y, w, h, 10, 8 + 4 * hv, a(Theme.accent(), 0.10f * hv + 0.16f * on));
        Render2D.roundRect(ctx, x, y, w, h, 10, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, hv * 0.8f)));
        if (on > 0.01f) Render2D.roundRectH(ctx, x, y, w, h, 10, a(Theme.accent(), 0.14f * on), a(Theme.accent2(), 0.02f * on));
        Render2D.outline(ctx, x, y, w, h, 10, 0, a(Theme.lerp(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, hv), Theme.accent(), Math.max(on * 0.55f, open))));
        drawRipples(ctx, key);
        // accent bar on the left edge when enabled
        if (on > 0.01f) {
            float bh = 18 * on;
            Render2D.glow(ctx, x + 1, y + h / 2f - bh / 2f, 2, bh, 1, 5, a(Theme.accent(), 0.7f * on));
            Render2D.roundRect(ctx, x + 1, y + h / 2f - bh / 2f, 2, bh, 1, a(Theme.accent(), on));
        }
        float tx = x + 12;
        Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(m.getName(), w - 54), tx, y + 9, a(Theme.lerp(Theme.TEXT, 0xFFFFFFFF, on)));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(m.getDescription(), w - 24), tx, y + 22, a(Theme.TEXT_DIM));
        String meta = !search.isBlank() ? m.getCategory().displayName.toUpperCase(Locale.ROOT) : settingsLabel(m.getSettings().stream().filter(st -> !(st instanceof HeaderSetting) && st.isVisible()).count());
        if (m.getKeybind().isBound()) meta += "  ·  " + m.getKeybind().display();
        Fonts.LABEL.drawTracked(ctx, meta, tx, y + 35, 0.8f, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), on * 0.7f)));
        drawSwitch(ctx, x + w - 32, y + 9, 22, 11, on, hv);
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
        long shownCount = visible.stream().filter(st -> !(st instanceof HeaderSetting)).count();
        Fonts.LABEL.drawTracked(ctx, "PARAMETERS", px + 1, cy, 1.2f, a(Theme.TEXT_DIM));
        String count = String.valueOf(shownCount);
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
            else if (s instanceof HeaderSetting hs) cy = flatHeader(ctx, hs.getName(), px + 1, cy, pw - 2);
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
        float tile = 20, tx = px + (STRIP_W - tile) / 2f, ty = py + 4;
        BotModule.Kind group = null;
        for (Configurable c : bots) {
            Module b = (Module) c;
            BotModule.Kind k = b instanceof BotModule bm0 ? bm0.kind() : BotModule.Kind.PVP;
            if (k != group) {
                // group heading: PVP / UTIL with a hairline
                if (group != null) ty += 2;
                String label = k == BotModule.Kind.PVP ? "PVP" : "UTIL";
                float lw = Fonts.LABEL.width(label, 0.3f);
                Fonts.LABEL.drawTracked(ctx, label, px + (STRIP_W - lw) / 2f, ty + 1, 0.3f, a(Theme.TEXT_DIM));
                Render2D.hairlineH(ctx, px + 6, ty + 9, STRIP_W - 12, a(Theme.HAIRLINE));
                ty += 12;
                group = k;
            }
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
                ? new String[][]{{sb.statLabels()[0], String.valueOf(sb.getHits())},
                        {sb.statLabels()[1], (sb.statLabels()[1].equals("COMBO") ? "x" : "") + sb.getCombo()},
                        {sb.statLabels()[2], String.format(Locale.ROOT, "%.1f", sb.getCps())}}
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
        sparks = new float[16][];
        for (int i = 0; i < sparks.length; i++) {
            float ang = (float) (i * Math.PI * 2 / sparks.length + (r.nextFloat() - 0.5f) * 0.3f);
            sparks[i] = new float[]{(float) Math.cos(ang), (float) Math.sin(ang), 34 + r.nextFloat() * 46, 0.6f + r.nextFloat() * 0.4f};
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
    private static float clamp01(float v) { return Theme.clamp01(v); }

    private static float easeOutQuart(float t) {
        t = clamp01(t);
        float f = 1 - t;
        return 1 - f * f * f * f;
    }

    private static float easeOutExpo(float t) {
        t = clamp01(t);
        return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * Ignite → dim → collapse and bloom → the bar morphs out of the burst → rows reveal.
     * Times are in seconds and scale with the animation-speed setting.
     */
    private void drawQuick(DrawContext ctx, float wx, float wy, float ww, float wh, float bodyY, float bodyH) {
        quickRect = null;
        if (quick == null) return;
        float pw = QUICK_W, ph = bodyH - 16, px = wx + ww - pw - 8, py = bodyY + 8;
        float cx = px + pw / 2f, cy = py + ph / 2f;
        long now = System.currentTimeMillis();
        float t = (now - quickStart) / 1000f * animSpeed();
        float tc = quickClosing ? (now - quickCloseStart) / 1000f * animSpeed() : 0;

        // backdrop
        float dim = quickClosing ? 1 - clamp01(tc / 0.3f) : Anim.easeInOut(t / 0.35f);
        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(0xFF04050A, 0.62f * dim));

        float panelW, panelH, radius, panelA, contentA, edge;
        if (quickClosing) {
            if (tc > 0.36f) {
                quick = null;
                return;
            }
            contentA = 1 - clamp01(tc / 0.1f);
            float hm = Anim.easeInOut((tc - 0.04f) / 0.16f), wm = Anim.easeInOut((tc - 0.1f) / 0.16f);
            panelW = lerp(pw, 14, wm);
            panelH = lerp(ph, 14, hm);
            radius = lerp(12, Math.min(panelW, panelH) / 2f, hm);
            panelA = 1 - clamp01((tc - 0.22f) / 0.06f);
            edge = 0.55f + 0.4f * hm;
            // the bar folds back into a fading point of light
            float pt = clamp01((tc - 0.2f) / 0.16f);
            if (pt > 0) drawStar(ctx, cx, cy, 1 - pt * 0.4f, 1 - pt, 0, 0.5f * (1 - pt));
        } else {
            // 1. ignite: the star settles from 45° with a lens streak
            if (t < 0.52f) {
                float ign = easeOutExpo(t / 0.3f);
                float fade = Anim.easeInOut((t - 0.3f) / 0.16f);              // 2. dims
                float collapse = Anim.easeInOut((t - 0.46f) / 0.06f);         // 3a. pulls into a point
                float scale = ign * (1 - 0.18f * fade) * (1 - 0.8f * collapse);
                float bright = ign * (1 - 0.55f * fade) + collapse * 0.9f;
                float rot = (1 - ign) * (float) Math.PI / 4;
                drawStar(ctx, cx, cy, scale, Math.min(1, bright), rot, ign * (1 - fade) * (1 - collapse));
            }
            // 3b. bloom: shockwave and trailed particles
            if (t >= 0.5f) {
                if (!burstPlayed) {
                    burstPlayed = true;
                    UiSounds.play(UiSounds.BURST, 1f, 0.85f);
                }
                drawBloom(ctx, cx, cy, clamp01((t - 0.5f) / 0.5f));
            }
            // 4. morph: circle → pill → panel
            float m = (t - 0.55f) / 0.42f;
            if (m <= 0) return;
            float wm = easeOutQuart(m / 0.75f), hm = easeOutQuart((m - 0.15f) / 0.85f);
            panelW = lerp(16, pw, wm);
            panelH = lerp(16, ph, hm);
            radius = lerp(Math.min(panelW, panelH) / 2f, 12, Anim.easeInOut(m));
            panelA = clamp01(m * 4);
            edge = lerp(1f, 0.55f, clamp01(m));
            contentA = Anim.easeOutCubic((t - 0.8f) / 0.25f);
            revealT = t - 0.86f;
        }

        float gx = cx - panelW / 2f, gy = cy - panelH / 2f;
        quickRect = new float[]{gx, gy, panelW, panelH};
        if (panelA > 0.01f) {
            float sa = alpha;
            alpha *= panelA;
            Render2D.glow(ctx, gx, gy, panelW, panelH, radius, 28, a(Theme.accent(), 0.10f + 0.2f * edge));
            Render2D.glow(ctx, gx, gy, panelW, panelH, radius, 6, a(0xFF000000, 0.7f));
            Render2D.roundRect(ctx, gx, gy, panelW, panelH, radius, a(0xFF0C0E18));
            float band = clamp01((panelH - ph * 0.7f) / (ph * 0.3f)); // the header tint only once the bar is nearly open
            if (band > 0) Render2D.roundRectH(ctx, gx, gy, panelW, Math.min(panelH, 70), radius,
                    a(Theme.accent(), 0.09f * band), a(Theme.accent2(), 0.02f * band));
            Render2D.outline(ctx, gx, gy, panelW, panelH, radius, 0, a(Theme.lerp(Theme.BORDER_HI, Theme.accent(), edge)));
            // a fine highlight along the top edge
            float hlw = panelW * 0.6f;
            if (panelH > 30) {
                Render2D.rectH(ctx, cx - hlw / 2f, gy + 0.5f, hlw / 2f, 0.5f, 0, a(0xFFFFFFFF, 0.35f));
                Render2D.rectH(ctx, cx, gy + 0.5f, hlw / 2f, 0.5f, a(0xFFFFFFFF, 0.35f), 0);
            }
            alpha = sa;
        }
        if (contentA > 0.01f) drawQuickContent(ctx, px, py, pw, ph, gx, gy, panelW, panelH, contentA,
                quickClosing ? -1 : clamp01((t - 0.95f) / 0.5f));
        revealT = -1;
    }

    private void drawQuickContent(DrawContext ctx, float px, float py, float pw, float ph,
                                  float gx, float gy, float gw, float gh, float contentA, float sheen) {
        float saved = alpha;
        alpha *= contentA;
        drawingQuick = true;
        keyPrefix = "qset_";
        pushClip(ctx, gx + 1, gy + 1, gw - 2, gh - 2);
        float rise = (1 - contentA) * 6;
        float ix = px + 12, iw = pw - 24;

        Fonts.LABEL.drawTracked(ctx, "QUICK SETTINGS", ix, py + 11 + rise, 1.4f, a(Theme.accent()));
        Fonts.BRAND.draw(ctx, Fonts.BRAND.fit(quick.getName(), iw - 24), ix, py + 21 + rise, a(Theme.TEXT));
        Fonts.BODY.draw(ctx, Fonts.BODY.fit(quick.getDescription(), iw), ix, py + 35 + rise, a(Theme.TEXT_MUTED));
        float bs = 14, bx = px + pw - 10 - bs, by = py + 9;
        boolean ch = hot("q_close", bx, by, bs, bs, c -> closeQuick());
        float chv = anim("q_close").to(ch ? 1 : 0, 18);
        if (chv > 0.01f) Render2D.roundRect(ctx, bx, by, bs, bs, 4, a(Theme.SURFACE_HI, chv));
        Render2D.icon(ctx, "icon_close", bx + 3.5f, by + 3.5f, 7, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFD0D8, chv)));

        float ey = py + 47 + rise, eh = 20;
        boolean eHov = hot("q_enable", ix - 4, ey, iw + 8, eh, c -> toggleModule(quick));
        float ehv = anim("q_enable").to(eHov ? 1 : 0, 18);
        float on = anim(quick.getName() + "_q_on").to(quick.isEnabled() ? 1 : 0, 14);
        if (on > 0.01f) Render2D.glow(ctx, ix - 4, ey, iw + 8, eh, 6, 6, a(Theme.accent(), 0.18f * on));
        Render2D.roundRect(ctx, ix - 4, ey, iw + 8, eh, 6, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, ehv)));
        Render2D.roundRectH(ctx, ix - 4, ey, iw + 8, eh, 6, a(Theme.accent(), 0.12f * on), a(Theme.accent2(), 0));
        Render2D.outline(ctx, ix - 4, ey, iw + 8, eh, 6, 0, a(Theme.lerp(Theme.BORDER, Theme.accent(), on * 0.6f)));
        Fonts.LABEL.drawTracked(ctx, quick.isEnabled() ? "ENABLED" : "DISABLED", ix + 2, ey + eh / 2f - Fonts.LABEL.size / 2f, 1.2f,
                a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), on)));
        drawSwitch(ctx, ix + iw - 22, ey + eh / 2f - 5.5f, 22, 11, on, ehv);
        Render2D.hairlineH(ctx, ix, py + 47 + eh + 6, iw, a(Theme.HAIRLINE));

        float listTop = py + 47 + eh + 9, listH = py + ph - listTop - 4;
        quickScrollTarget = Math.max(0, Math.min(quickScrollTarget, Math.max(0, quickContentH - listH)));
        quickScroll = Anim.approach(quickScroll, quickScrollTarget, 18);
        pushClip(ctx, gx + 1, listTop, gw - 2, listH);
        float end = flatSettings(ctx, quick, ix, listTop + 3 - quickScroll, iw);
        quickContentH = end + quickScroll - listTop + 4;
        popClip(ctx);
        if (quickScroll > 1) Render2D.rectV(ctx, gx + 1, listTop, gw - 2, 10, a(0xFF0C0E18), 0);
        if (quickContentH - quickScroll > listH + 1) Render2D.rectV(ctx, gx + 1, listTop + listH - 10, gw - 2, 10, 0, a(0xFF0C0E18));

        // one diagonal glass sheen sweeping across once the bar has settled
        if (sheen > 0 && sheen < 1) {
            float sx = gx - 60 + (gw + 120) * Anim.easeInOut(sheen);
            var mtx = ctx.getMatrices();
            mtx.pushMatrix();
            mtx.translate(sx, gy + gh / 2f);
            mtx.rotate(0.35f);
            float band = 26, al = 0.07f * (float) Math.sin(Math.PI * sheen);
            Render2D.rectH(ctx, -band, -gh, band, gh * 2, 0, a(0xFFFFFFFF, al));
            Render2D.rectH(ctx, 0, -gh, band, gh * 2, a(0xFFFFFFFF, al), 0);
            mtx.popMatrix();
        }
        popClip(ctx);
        keyPrefix = "set_";
        drawingQuick = false;
        alpha = saved;
    }

    /** A crisp four-point star: bloom, an 8-point glint, a hot core and an anamorphic lens streak. */
    private void drawStar(DrawContext ctx, float cx, float cy, float scale, float bright, float rot, float streak) {
        if (scale <= 0.01f || bright <= 0.01f) return;
        int acc = Theme.accent();
        Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, 26 * scale, a(acc, 0.32f * bright));
        Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, 9 * scale, a(0xFFFFFFFF, 0.38f * bright));
        var m = ctx.getMatrices();
        if (streak > 0.01f) {
            float len = 80 * scale * streak;
            Render2D.rectH(ctx, cx - len, cy - 0.3f, len, 0.6f, 0, a(0xFFFFFFFF, 0.85f * streak * bright));
            Render2D.rectH(ctx, cx, cy - 0.3f, len, 0.6f, a(0xFFFFFFFF, 0.85f * streak * bright), 0);
            float wide = len * 0.6f;
            Render2D.rectH(ctx, cx - wide, cy - 1.2f, wide, 2.4f, 0, a(acc, 0.22f * streak * bright));
            Render2D.rectH(ctx, cx, cy - 1.2f, wide, 2.4f, a(acc, 0.22f * streak * bright), 0);
        }
        float s = 18 * scale;
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(rot + (float) Math.PI / 4);
        Render2D.icon(ctx, "icon_sparkle", -s * 0.28f, -s * 0.28f, s * 0.56f, a(Theme.lerp(acc, 0xFFFFFFFF, 0.4f), 0.55f * bright));
        m.rotate((float) -Math.PI / 4);
        Render2D.icon(ctx, "icon_sparkle", -s / 2f, -s / 2f, s, a(Theme.lerp(acc, 0xFFFFFFFF, 0.75f), bright));
        m.popMatrix();
        Render2D.circle(ctx, cx, cy, Math.max(0.6f, 1.3f * scale), a(0xFFFFFFFF, bright));
    }

    /** Soft bloom, a fine shockwave ring and particles with motion trails; {@code b} goes 0..1. */
    private void drawBloom(DrawContext ctx, float cx, float cy, float b) {
        if (b >= 1) return;
        float inv = 1 - b;
        float e = easeOutQuart(b);
        Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, 24 + 50 * e, a(Theme.accent(), 0.45f * inv * inv));
        float flash = clamp01(1 - b * 5);
        if (flash > 0) Render2D.glow(ctx, cx - 1, cy - 1, 2, 2, 1, 10, a(0xFFFFFFFF, 0.8f * flash));

        float r1 = 5 + 78 * e;
        Render2D.outline(ctx, cx - r1, cy - r1, r1 * 2, r1 * 2, r1, 0.4f + 1.1f * inv,
                a(Theme.lerp(0xFFFFFFFF, Theme.accent(), b), (float) Math.pow(inv, 1.5)));
        float b2 = clamp01((b - 0.08f) / 0.92f);
        if (b2 > 0) {
            float r2 = 4 + 52 * easeOutQuart(b2);
            Render2D.outline(ctx, cx - r2, cy - r2, r2 * 2, r2 * 2, r2, 0.4f, a(Theme.accent2(), 0.6f * (1 - b2)));
        }
        var m = ctx.getMatrices();
        for (float[] sp : sparks) {
            float dist = sp[2] * e, tail = Math.max(2, sp[2] * 0.22f * inv);
            float ang = (float) Math.atan2(sp[1], sp[0]);
            m.pushMatrix();
            m.translate(cx + sp[0] * dist, cy + sp[1] * dist);
            m.rotate(ang);
            float al = inv * sp[3];
            Render2D.rectH(ctx, -tail, -0.35f, tail, 0.7f, 0, a(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.5f), al));
            m.popMatrix();
            float hx = cx + sp[0] * dist, hy = cy + sp[1] * dist;
            Render2D.glow(ctx, hx - 0.5f, hy - 0.5f, 1, 1, 0.5f, 3, a(Theme.accent(), 0.6f * al));
            Render2D.circle(ctx, hx, hy, 0.7f, a(0xFFFFFFFF, al));
        }
    }

    // =================================================================== input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing || portalStart > 0 || rebuildStart > 0) return true;
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
        if (portalStart > 0 || rebuildStart > 0) return true;
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

    private static String settingsLabel(long n) {
        return n == 0 ? "NO SETTINGS" : n == 1 ? "1 SETTING" : n + " SETTINGS";
    }
}
