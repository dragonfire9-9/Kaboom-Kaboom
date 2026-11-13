package dev.soulbound.gui;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.bots.BuilderBot;
import dev.soulbound.schem.Schematic;
import dev.soulbound.schem.SchematicLoader;
import dev.soulbound.sound.UiSounds;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The schematic picker for the Schematic Builder bot. Opens out of a white flash with a star
 * floating away, and crumbles apart when you leave it.
 */
public final class SchematicScreen extends SoulScreen {
    private static final float WIN_W = 620, WIN_H = 350, RADIUS = 14, GAP = 5, PANEL_R = 10, SIDE_W = 150, INFO_W = 190, TOP_H = 24;
    private static final Identifier PREVIEW_ID = Identifier.of(SoulBound.MOD_ID, "dynamic/schem_preview");

    private final boolean fromPortal;
    private long openedAt, crumbleAt = -1;
    private final List<Path> files = new ArrayList<>();
    private Path selected;
    private Schematic loaded;
    private String error;
    private String search = "";
    private boolean searchFocused;
    private float listScroll, listScrollTarget, listContentH;
    private float matScroll, matScrollTarget, matContentH;
    private float[] listRect, matRect;
    private int layer = -1; // -1 = top-down view
    private int previewW, previewH;
    private boolean previewDirty = true;
    private float[] slider;

    public SchematicScreen(boolean fromPortal) {
        super(Text.literal("Schematics"));
        this.fromPortal = fromPortal;
    }

    private BuilderBot bot() { return SoulBound.modules().get(BuilderBot.class); }

    @Override
    protected void init() {
        if (openedAt == 0) {
            openedAt = System.currentTimeMillis();
            open.snap(1);
            refresh();
            Schematic current = bot().currentSchematic();
            if (current != null) {
                loaded = current;
                selected = current.file;
            }
        }
    }

    private void refresh() {
        files.clear();
        files.addAll(SchematicLoader.list());
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { crumble(); }

    private void crumble() {
        if (crumbleAt > 0) return;
        crumbleAt = System.currentTimeMillis();
        UiSounds.play(UiSounds.CLOSE, 0.8f, 0.8f);
    }

    @Override
    protected void onClosed() { }

    // =================================================================== render
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client != null && client.world != null && SoulBound.settings().ui.blur.isOn()) ctx.applyBlur();
        Render2D.rectV(ctx, 0, 0, width, height, 0xB005060C, 0xD005060C);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!beginFrame(mouseX, mouseY)) return;
        float t = (System.currentTimeMillis() - openedAt) / 1000f;
        float ww = Math.min(WIN_W, width - 20), wh = Math.min(WIN_H, height - 20);
        float wx = (width - ww) / 2f, wy = (height - wh) / 2f;

        if (crumbleAt > 0) {
            float c = (System.currentTimeMillis() - crumbleAt) / 1000f;
            if (c > 0.66f) {
                client.setScreen(new ClickGuiScreen(true));
                return;
            }
            // the UI breaks into tiles that fall away, top rows a moment before the bottom
            renderTiles(ctx, wx - 30, wy - 30, ww + 60, wh + 60, 12, 8, (col, row, rand) -> {
                float d = (row / 8f) * 0.1f + rand * 0.14f;
                float u = Theme.clamp01((c - d) / 0.42f);
                return new float[]{(rand - 0.5f) * 40 * u, 260 * u * u, 1 - 0.18f * u, 1 - (float) Math.pow(u, 1.4)};
            }, () -> drawWindow(ctx, wx, wy, ww, wh));
            endFrame();
            return;
        }

        float intro = fromPortal ? Anim.easeOutCubic(t / 0.7f) : 1;
        alpha = intro;
        drawWindow(ctx, wx, wy, ww, wh);
        alpha = 1;

        if (fromPortal && t < 1.4f) {
            // the white flash dims, and the star drifts away with a soft glow
            float white = 1 - Anim.easeOutCubic(t / 0.55f);
            if (white > 0.01f) Render2D.rect(ctx, 0, 0, width, height, Theme.alpha(0xFFFFFFFF, white));
            float e = Anim.easeOutCubic(t / 1.3f);
            float sx = width / 2f + 140 * e, sy = height / 2f - 110 * e - 20 * e * e;
            float size = 40 * (1 - 0.75f * e), glow = (1 - e);
            Render2D.glow(ctx, sx - 1, sy - 1, 2, 2, 1, 60 * glow + 10, Theme.alpha(Theme.accent(), 0.45f * glow));
            Render2D.glow(ctx, sx - 1, sy - 1, 2, 2, 1, 16 * glow + 4, Theme.alpha(0xFFFFFFFF, 0.6f * glow));
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(sx, sy);
            m.rotate(t * 1.6f);
            Render2D.icon(ctx, "icon_sparkle", -size / 2f, -size / 2f, size, Theme.alpha(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.7f), glow));
            m.popMatrix();
        }
        endFrame();
    }

    private void panel(DrawContext ctx, float x, float y, float w, float h) { panel(ctx, x, y, w, h, PANEL_R); }

    private void drawWindow(DrawContext ctx, float wx, float wy, float ww, float wh) {
        float g = Theme.glowStrength();
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 30, a(Theme.accent(), 0.22f * g));
        Render2D.glow(ctx, wx, wy, ww, wh, RADIUS, 3, a(0xFF000000, 0.6f));
        Render2D.roundRect(ctx, wx, wy, ww, wh, RADIUS, a(Theme.BG));
        Render2D.outline(ctx, wx, wy, ww, wh, RADIUS, 0, a(Theme.BORDER));

        float sx = wx + GAP, sy = wy + GAP, sh = wh - GAP * 2;
        drawList(ctx, sx, sy, SIDE_W, sh);
        float mx0 = sx + SIDE_W + GAP, mw = wx + ww - GAP - mx0;
        drawTop(ctx, mx0, sy, mw, TOP_H);
        float cy = sy + TOP_H + GAP, ch = wy + wh - GAP - cy;
        float infoX = mx0 + mw - INFO_W;
        drawPreview(ctx, mx0, cy, infoX - GAP - mx0, ch);
        drawInfo(ctx, infoX, cy, INFO_W, ch);
    }

    // ------------------------------------------------------------------ list
    private void drawList(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h);
        Render2D.icon(ctx, "icon_build", x + 10, y + 9, 10, a(Theme.accent()));
        Fonts.LABEL.drawTracked(ctx, "SCHEMATICS", x + 25, y + 14 - Fonts.LABEL.size / 2f, 1.2f, a(Theme.TEXT));
        String cnt = String.valueOf(files.size());
        float cw = Math.max(11, Fonts.LABEL.width(cnt) + 6), bx = x + w - 10 - cw;
        Render2D.roundRect(ctx, bx, y + 8.5f, cw, 11, 5.5f, a(Theme.accent(), 0.16f));
        Fonts.LABEL.drawCentered(ctx, cnt, bx + (cw - Fonts.LABEL.width(cnt)) / 2f, y + 14, a(Theme.accent()));

        // search
        float fx = x + 8, fy = y + 26, fw = w - 16, fh = 16;
        boolean sh = hot("s_search", fx, fy, fw, fh, c -> searchFocused = true);
        float sf = anim("s_search_f").to(searchFocused ? 1 : 0, 14);
        Render2D.roundRect(ctx, fx, fy, fw, fh, 6, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, Math.max(sf, sh ? 0.5f : 0))));
        Render2D.outline(ctx, fx, fy, fw, fh, 6, 0, a(Theme.lerp(Theme.BORDER, Theme.accent(), sf * 0.8f)));
        Render2D.icon(ctx, "icon_search", fx + 6, fy + 4.5f, 7, a(Theme.TEXT_DIM));
        Fonts.BODY.drawCentered(ctx, search.isEmpty() ? "Search…" : Fonts.BODY.fit(search, fw - 24), fx + 17, fy + fh / 2f,
                a(search.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT));

        List<Path> shown = files.stream().filter(p -> search.isBlank() || p.getFileName().toString().toLowerCase(Locale.ROOT)
                .contains(search.toLowerCase(Locale.ROOT))).toList();
        float ly = fy + fh + 6, lh = y + h - ly - 30;
        listRect = new float[]{x, ly, w, lh};
        listScrollTarget = Math.max(0, Math.min(listScrollTarget, Math.max(0, listContentH - lh)));
        listScroll = Anim.approach(listScroll, listScrollTarget, 18);
        pushClip(ctx, x + 1, ly, w - 2, lh);
        if (shown.isEmpty()) {
            String a1 = files.isEmpty() ? "Folder is empty" : "No matches", a2 = files.isEmpty() ? "Add .schem or .litematic files" : "";
            Fonts.BODY_BOLD.draw(ctx, a1, x + (w - Fonts.BODY_BOLD.width(a1)) / 2f, ly + 30, a(Theme.TEXT_MUTED));
            Fonts.BODY.draw(ctx, a2, x + (w - Fonts.BODY.width(a2)) / 2f, ly + 42, a(Theme.TEXT_DIM));
        }
        float ry = ly + 2 - listScroll;
        for (Path f : shown) {
            drawFileRow(ctx, f, x + 6, ry, w - 12, 26);
            ry += 28;
        }
        listContentH = shown.size() * 28 + 4;
        popClip(ctx);

        // footer buttons
        float by = y + h - 24, bw = (w - 20) / 2f;
        button(ctx, "s_open", "Open Folder", "icon_folder", x + 8, by, bw, 16, false, () -> Util.getOperatingSystem().open(SchematicLoader.folder()));
        button(ctx, "s_refresh", "Refresh", "icon_search", x + 12 + bw, by, bw, 16, false, this::refresh);
    }

    private void drawFileRow(DrawContext ctx, Path f, float x, float y, float w, float h) {
        String key = "s_file_" + f.getFileName();
        boolean sel = f.equals(selected);
        boolean hov = hot(key, x, y, w, h, c -> select(f));
        float hv = anim(key).to(hov ? 1 : 0, 18), s = anim(key + "_s").to(sel ? 1 : 0, 14);
        if (s > 0.01f) {
            Render2D.roundRect(ctx, x, y, w, h, 7, a(Theme.SURFACE_SEL, s));
            Render2D.roundRectH(ctx, x, y, w, h, 7, a(Theme.accent(), 0.18f * s), a(Theme.accent2(), 0));
            Render2D.outline(ctx, x, y, w, h, 7, 0, a(Theme.accent(), 0.5f * s));
            Render2D.roundRect(ctx, x + 2, y + h / 2f - 5, 2, 10, 1, a(Theme.accent(), s));
        }
        if (hv * (1 - s) > 0.01f) Render2D.roundRect(ctx, x, y, w, h, 7, a(Theme.SURFACE_HI, hv * (1 - s)));
        drawRipples(ctx, key);
        String fname = f.getFileName().toString();
        String name = fname.contains(".") ? fname.substring(0, fname.lastIndexOf('.')) : fname;
        String ext = fname.contains(".") ? fname.substring(fname.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT) : "";
        Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(name, w - 18), x + 9, y + 4, a(Theme.TEXT));
        long kb = 0;
        try {
            kb = Math.max(1, Files.size(f) / 1024);
        } catch (Exception ignored) { }
        Fonts.LABEL.drawTracked(ctx, ext + "  ·  " + kb + " KB", x + 9, y + 16, 0.8f, a(Theme.lerp(Theme.TEXT_DIM, Theme.accent(), s)));
    }

    private void select(Path f) {
        if (f.equals(selected) && loaded != null) return;
        selected = f;
        error = null;
        layer = -1;
        try {
            loaded = SchematicLoader.load(f);
        } catch (Exception e) {
            loaded = null;
            error = "Couldn't read this file";
            SoulBound.LOGGER.warn("Failed to load schematic {}", f, e);
        }
        previewDirty = true;
        matScrollTarget = 0;
        UiSounds.play(UiSounds.CLICK, 1.05f, 0.8f);
    }

    // ------------------------------------------------------------------ top bar
    private void drawTop(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h);
        Fonts.LABEL.drawTracked(ctx, "SCHEMATIC BUILDER", x + 10, y + h / 2f - Fonts.LABEL.size / 2f, 1.2f, a(Theme.TEXT_DIM));
        float lx = x + 10 + Fonts.LABEL.width("SCHEMATIC BUILDER", 1.2f) + 8;
        Fonts.BODY_BOLD.drawCentered(ctx, "Choose a schematic", lx, y + h / 2f, a(Theme.TEXT));
        BuilderBot bot = bot();
        String st = bot.status().getString();
        float sw = Fonts.BODY.width(st);
        float bx = x + w - 22;
        Fonts.BODY.drawCentered(ctx, st, bx - 10 - sw, y + h / 2f, a(Theme.TEXT_MUTED));
        boolean ch = hot("s_close", bx, y + 5, 14, 14, c -> crumble());
        float hv = anim("s_close").to(ch ? 1 : 0, 18);
        if (hv > 0.01f) Render2D.roundRect(ctx, bx, y + 5, 14, 14, 4, a(Theme.SURFACE_HI, hv));
        Render2D.icon(ctx, "icon_close", bx + 3.5f, y + 8.5f, 7, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFD0D8, hv)));
    }

    // ------------------------------------------------------------------ preview
    private void drawPreview(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h);
        String title = loaded != null ? loaded.name : selected != null ? selected.getFileName().toString() : "No schematic selected";
        Fonts.LABEL.drawTracked(ctx, layer < 0 ? "TOP VIEW" : "LAYER " + (layer + 1), x + 10, y + 9, 1.2f, a(Theme.TEXT_DIM));
        Fonts.BRAND.draw(ctx, Fonts.BRAND.fit(title, w - 20), x + 10, y + 19, a(Theme.TEXT));

        float px = x + 10, py = y + 36, pw = w - 20, ph = h - 36 - 34;
        Render2D.roundRect(ctx, px, py, pw, ph, 8, a(0xFF080910));
        Render2D.outline(ctx, px, py, pw, ph, 8, 0, a(Theme.BORDER));
        // subtle grid
        for (float gx = px + 12; gx < px + pw; gx += 12) Render2D.hairlineV(ctx, gx, py + 1, ph - 2, a(0xFFFFFFFF, 0.025f));
        for (float gy = py + 12; gy < py + ph; gy += 12) Render2D.hairlineH(ctx, px + 1, gy, pw - 2, a(0xFFFFFFFF, 0.025f));

        if (loaded == null) {
            String m = error != null ? error : "Pick a file on the left";
            Fonts.BODY.drawCentered(ctx, m, px + (pw - Fonts.BODY.width(m)) / 2f, py + ph / 2f, a(error != null ? 0xFFFF6B81 : Theme.TEXT_DIM));
        } else {
            if (previewDirty) buildPreview();
            if (previewW > 0) {
                float scale = Math.min((pw - 20) / previewW, (ph - 20) / previewH);
                float dw = previewW * scale, dh = previewH * scale, dx = px + (pw - dw) / 2f, dy = py + (ph - dh) / 2f;
                Render2D.glow(ctx, dx, dy, dw, dh, 2, 10, a(Theme.accent(), 0.18f));
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(dx, dy);
                m.scale(scale, scale);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, 0, 0, 0, 0, previewW, previewH, previewW, previewH, a(0xFFFFFFFF));
                m.popMatrix();
                // a slow scanning glow across the map
                float sweep = (Anim.seconds() * 0.25f) % 1.4f;
                if (sweep < 1) {
                    float sx = dx + dw * sweep;
                    pushClip(ctx, dx, dy, dw, dh);
                    Render2D.rectH(ctx, sx - 30, dy, 30, dh, 0, a(Theme.accent(), 0.12f));
                    Render2D.rectH(ctx, sx, dy, 6, dh, a(Theme.accent(), 0.12f), 0);
                    popClip(ctx);
                }
            }
        }

        // layer slider
        float sy = y + h - 22, sxx = x + 10, sw = w - 20;
        int max = loaded != null ? loaded.sizeY : 1;
        Fonts.LABEL.drawTracked(ctx, "LAYER", sxx, sy - 2, 1f, a(Theme.TEXT_DIM));
        float tx = sxx + 34, tw = sw - 34 - 30;
        slider = new float[]{tx, sy, tw};
        boolean hov = hot("s_layer", tx - 4, sy - 6, tw + 8, 14, c -> dragLayer(c.x()));
        float p = layer < 0 ? 1 : max <= 1 ? 1 : (float) layer / (max - 1);
        Render2D.roundRect(ctx, tx, sy, tw, 2, 1, a(Theme.TRACK_OFF));
        Render2D.roundRectH(ctx, tx, sy, tw * p, 2, 1, a(Theme.accent(), 0.7f), a(Theme.accent2()));
        float ks = hov ? 7.5f : 6;
        Render2D.glow(ctx, tx + tw * p - ks / 2f, sy + 1 - ks / 2f, ks, ks, 2, 5, a(Theme.accent(), 0.5f));
        Render2D.roundRect(ctx, tx + tw * p - ks / 2f, sy + 1 - ks / 2f, ks, ks, 2, a(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.3f)));
        String lv = layer < 0 ? "ALL" : String.valueOf(layer + 1);
        Fonts.LABEL.drawTracked(ctx, lv, x + w - 10 - Fonts.LABEL.width(lv, 1f), sy - 2, 1f, a(Theme.accent()));
    }

    private boolean draggingLayer;

    private void dragLayer(double mouseX) {
        draggingLayer = true;
        if (loaded == null || slider == null) return;
        float p = Theme.clamp01((float) ((mouseX - slider[0]) / slider[2]));
        int newLayer = p >= 0.995f ? -1 : Math.round(p * (loaded.sizeY - 1));
        if (newLayer != layer) {
            layer = newLayer;
            previewDirty = true;
            UiSounds.slide(p);
        }
    }

    /** Renders a shaded top-down map (or one layer) of the schematic into a texture. */
    private void buildPreview() {
        previewDirty = false;
        Schematic s = loaded;
        if (s == null || s.sizeX <= 0 || s.sizeZ <= 0) {
            previewW = previewH = 0;
            return;
        }
        int maxDim = 160;
        float step = Math.max(1, Math.max(s.sizeX, s.sizeZ) / (float) maxDim);
        int w = Math.max(1, (int) Math.ceil(s.sizeX / step)), h = Math.max(1, (int) Math.ceil(s.sizeZ / step));
        int[] topY = new int[w * h];
        int[] color = new int[w * h];
        java.util.Arrays.fill(topY, -1);
        for (Schematic.Entry e : s.blocks()) {
            int y = e.pos().getY();
            if (layer >= 0 && y != layer) continue;
            if (layer >= 0 ? false : false) continue;
            int cx = Math.min(w - 1, (int) (e.pos().getX() / step)), cz = Math.min(h - 1, (int) (e.pos().getZ() / step));
            int i = cz * w + cx;
            if (y >= topY[i]) {
                topY[i] = y;
                color[i] = e.state().getMapColor(client.world, BlockPos.ORIGIN).color;
            }
        }
        NativeImage img = new NativeImage(w, h, true);
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                if (topY[i] < 0) {
                    img.setColorArgb(x, z, 0);
                    continue;
                }
                float shade = layer >= 0 ? 1f : 0.55f + 0.45f * (topY[i] + 1) / Math.max(1f, s.sizeY);
                int north = z > 0 ? topY[i - w] : topY[i];
                if (north < topY[i]) shade *= 1.15f;
                else if (north > topY[i]) shade *= 0.8f;
                int c = color[i] == 0 ? 0x888888 : color[i];
                int r = Math.min(255, (int) (((c >> 16) & 0xFF) * shade)), g = Math.min(255, (int) (((c >> 8) & 0xFF) * shade)),
                        b = Math.min(255, (int) ((c & 0xFF) * shade));
                img.setColorArgb(x, z, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        client.getTextureManager().registerTexture(PREVIEW_ID, new NativeImageBackedTexture(() -> "soulbound schematic preview", img));
        previewW = w;
        previewH = h;
    }

    // ------------------------------------------------------------------ info
    private void drawInfo(DrawContext ctx, float x, float y, float w, float h) {
        panel(ctx, x, y, w, h);
        float px = x + 10, pw = w - 20;
        BuilderBot bot = bot();
        Fonts.LABEL.drawTracked(ctx, "DETAILS", px, y + 9, 1.2f, a(Theme.TEXT_DIM));
        float cy = y + 20;
        if (loaded != null) {
            cy = info(ctx, "Format", loaded.format, px, cy, pw);
            cy = info(ctx, "Size", loaded.sizeX + " × " + loaded.sizeY + " × " + loaded.sizeZ, px, cy, pw);
            cy = info(ctx, "Blocks", String.format(Locale.ROOT, "%,d", loaded.blockCount()), px, cy, pw);
        }
        BlockPos o = bot.getOrigin();
        cy = info(ctx, "Origin", o == null ? "not set" : o.getX() + "  " + o.getY() + "  " + o.getZ(), px, cy, pw);
        // rotation selector, reusing the bot's own setting
        for (var s : bot.getSettings()) {
            if (s instanceof dev.soulbound.setting.ModeSetting ms && ms.getName().equals("Rotation")) {
                String before = ms.get();
                cy = flatMode(ctx, ms, px, cy, pw);
                if (!before.equals(ms.get())) bot.setRotation(ms.get());
            }
        }
        Render2D.hairlineH(ctx, px, cy + 3, pw, a(Theme.HAIRLINE));
        Fonts.LABEL.drawTracked(ctx, "MATERIALS", px, cy + 8, 1.2f, a(Theme.TEXT_DIM));
        float my = cy + 18, mh = y + h - my - 50;
        matRect = new float[]{x, my, w, mh};
        matScrollTarget = Math.max(0, Math.min(matScrollTarget, Math.max(0, matContentH - mh)));
        matScroll = Anim.approach(matScroll, matScrollTarget, 18);
        pushClip(ctx, x + 1, my, w - 2, mh);
        float ry = my - matScroll;
        int rows = 0;
        if (loaded != null) {
            for (Map.Entry<BlockState, Integer> e : loaded.materials()) {
                Item item = e.getKey().getBlock().asItem();
                if (ry > my - 20 && ry < my + mh) drawMaterial(ctx, item, e.getValue(), px, ry, pw);
                ry += 18;
                rows++;
            }
        }
        matContentH = rows * 18 + 4;
        popClip(ctx);
        if (matScroll > 1) Render2D.rectV(ctx, x + 1, my, w - 2, 8, a(Theme.PANEL), 0);
        if (matContentH - matScroll > mh + 1) Render2D.rectV(ctx, x + 1, my + mh - 8, w - 2, 8, 0, a(Theme.PANEL));

        float by = y + h - 44;
        button(ctx, "s_origin", "Set Origin Here", "icon_target", px, by, pw, 16, false, () -> {
            bot.setOriginHere();
            UiSounds.play(UiSounds.TOGGLE_ON, 1.1f, 0.7f);
        });
        button(ctx, "s_build", bot.isEnabled() ? "Building…" : "Build", "icon_build", px, by + 20, pw, 18, true, () -> {
            if (loaded == null) return;
            bot.setSchematic(loaded);
            if (!bot.isEnabled()) bot.setEnabled(true);
            UiSounds.play(UiSounds.TOGGLE_ON, 1f, 0.9f);
            crumble();
        });
    }

    private float info(DrawContext ctx, String k, String v, float x, float y, float w) {
        Fonts.BODY.drawCentered(ctx, k, x, y + 7, a(Theme.TEXT_MUTED));
        Fonts.BODY_BOLD.drawCentered(ctx, v, x + w - Fonts.BODY_BOLD.width(v), y + 7, a(Theme.TEXT));
        return y + 14;
    }

    private void drawMaterial(DrawContext ctx, Item item, int need, float x, float y, float w) {
        int have = 0;
        if (client.player != null) {
            var inv = client.player.getInventory();
            for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(item)) have += inv.getStack(i).getCount();
        }
        if (alpha > 0.5f) {
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(x, y + 1);
            m.scale(0.75f, 0.75f);
            ctx.drawItem(new ItemStack(item), 0, 0);
            m.popMatrix();
        }
        String name = new ItemStack(item).getName().getString();
        Fonts.BODY.drawCentered(ctx, Fonts.BODY.fit(name, w - 70), x + 16, y + 7, a(Theme.TEXT));
        String cnt = have + " / " + need;
        int col = have >= need ? 0xFF4ADE80 : have > 0 ? 0xFFFFD36B : 0xFFFF6B81;
        Fonts.BODY_BOLD.drawCentered(ctx, cnt, x + w - Fonts.BODY_BOLD.width(cnt), y + 7, a(col));
    }

    private void button(DrawContext ctx, String key, String label, String icon, float x, float y, float w, float h, boolean primary, Runnable action) {
        boolean hov = hot(key, x, y, w, h, c -> {
            UiSounds.play(UiSounds.CLICK, 1.05f, 0.8f);
            action.run();
        });
        float hv = anim(key).to(hov ? 1 : 0, 18);
        if (primary) {
            Render2D.glow(ctx, x, y, w, h, 6, 6 + 4 * hv, a(Theme.accent(), 0.3f + 0.3f * hv));
            Render2D.roundRectH(ctx, x, y, w, h, 6, a(Theme.accent()), a(Theme.accent2()));
        } else {
            Render2D.roundRect(ctx, x, y, w, h, 6, a(Theme.lerp(Theme.SURFACE, Theme.SURFACE_HI, hv)));
            Render2D.outline(ctx, x, y, w, h, 6, 0, a(Theme.lerp(Theme.BORDER, Theme.BORDER_HI, hv)));
        }
        drawRipples(ctx, key);
        float lw = Fonts.BODY_BOLD.width(label) + 12;
        float lx = x + (w - lw) / 2f;
        Render2D.icon(ctx, icon, lx, y + h / 2f - 4, 8, a(primary ? 0xFFFFFFFF : Theme.lerp(Theme.TEXT_MUTED, Theme.accent(), hv)));
        Fonts.BODY_BOLD.drawCentered(ctx, label, lx + 12, y + h / 2f, a(primary ? 0xFFFFFFFF : Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv)));
    }

    // =================================================================== input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (crumbleAt > 0) return true;
        if (fromPortal && System.currentTimeMillis() - openedAt < 450) return true;
        Hit h = topHit(click.x(), click.y());
        if (h == null || !h.key().equals("s_search")) searchFocused = false;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (draggingLayer) dragLayer(click.x());
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingLayer = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (listRect != null && inside(mouseX, mouseY, listRect[0], listRect[1], listRect[2], listRect[3])) listScrollTarget -= (float) v * 24;
        else if (matRect != null && inside(mouseX, mouseY, matRect[0], matRect[1], matRect[2], matRect[3])) matScrollTarget -= (float) v * 24;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (handleListening(input)) return true;
        int key = input.key();
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) searchFocused = false;
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) search = search.substring(0, search.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            crumble();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!searchFocused || !input.isValidChar() || search.length() > 30) return false;
        search += input.asString();
        UiSounds.play(UiSounds.TYPE, 1f, 0.5f);
        return true;
    }
}
