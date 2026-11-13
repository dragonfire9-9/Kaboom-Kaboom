package dev.soulbound.gui;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.SkinPreview;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.sound.UiSounds;
import net.minecraft.SharedConstants;
import net.minecraft.client.gl.RenderPipelines;
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
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

/**
 * SoulBound's main menu: a full-screen scene with drifting silk, floating soul sparks,
 * the brand, a centred text menu and the player's skin standing on the right.
 */
public final class SoulTitleScreen extends SoulScreen {
    private static final Identifier SILK = Identifier.of(SoulBound.MOD_ID, "textures/gui/title_silk.png");
    private static final int SILK_W = 1600, SILK_H = 900;

    /** Set when the player picks the classic menu; lasts for this session. */
    public static boolean useVanilla;

    private static final SkinPreview PREVIEW = new SkinPreview();

    private record Item(String label, Runnable action, boolean danger, boolean gapBefore) {}

    private static final class Particle {
        float x, y, vy, size, phase, spin, depth;
        boolean sparkle;
    }

    private final List<Item> items = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();
    private Supplier<SkinTextures> skin;
    private ServerInfo lastServer;
    private long openedAt;
    private int focus = -1;
    private float px, py; // smoothed mouse parallax, -1..1

    public SoulTitleScreen() {
        super(Text.translatable("narrator.screen.title"));
    }

    @Override
    protected void init() {
        items.clear();
        items.add(new Item("Singleplayer", () -> client.setScreen(new SelectWorldScreen(this)), false, false));
        items.add(new Item("Multiplayer", () -> client.setScreen(
                client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this)), false, false));
        items.add(new Item("Realms", () -> client.setScreen(new RealmsMainScreen(this)), false, false));
        items.add(new Item("Minecraft Settings", () -> client.setScreen(new OptionsScreen(this, client.options)), false, true));
        items.add(new Item("Client", () -> client.setScreen(new ClickGuiScreen()), false, false));
        items.add(new Item("Quit Game", () -> client.scheduleStop(), true, true));

        lastServer = null;
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            if (list.size() > 0) lastServer = list.get(0);
        } catch (Exception e) {
            SoulBound.LOGGER.warn("Could not read the server list", e);
        }
        skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);

        if (particles.isEmpty()) {
            for (int i = 0; i < 34; i++) {
                Particle p = new Particle();
                respawn(p, true);
                particles.add(p);
            }
        }
        if (openedAt == 0) {
            openedAt = System.currentTimeMillis();
            UiSounds.play(UiSounds.OPEN, 0.9f, 0.6f);
        }
    }

    private void respawn(Particle p, boolean anywhere) {
        p.depth = 0.3f + rnd.nextFloat() * 0.7f;
        p.x = rnd.nextFloat();
        p.y = anywhere ? rnd.nextFloat() : 1.05f + rnd.nextFloat() * 0.1f;
        p.vy = (0.008f + rnd.nextFloat() * 0.018f) * p.depth;
        p.size = 2 + rnd.nextFloat() * 4 * p.depth;
        p.phase = rnd.nextFloat() * 6.28f;
        p.spin = (rnd.nextFloat() - 0.5f) * 0.8f;
        p.sparkle = rnd.nextFloat() < 0.45f;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    /** Seconds since the menu first appeared, for the staggered intro. */
    private float intro() { return (System.currentTimeMillis() - openedAt) / 1000f; }

    private static float stage(float t, float start, float dur) { return Anim.easeOutCubic((t - start) / dur); }

    // =================================================================== render
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Render2D.rect(ctx, 0, 0, width, height, 0xFF07080D);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!beginFrame(mouseX, mouseY)) return;
        float t = intro();
        float dt = Anim.dt();
        px += ((mouseX / (float) width - 0.5f) * 2 - px) * Math.min(1, dt * 3);
        py += ((mouseY / (float) height - 0.5f) * 2 - py) * Math.min(1, dt * 3);

        drawScene(ctx, t);
        drawParticles(ctx, dt, t);
        drawModel(ctx, t);
        drawBrand(ctx, t);
        drawMenu(ctx, t);
        drawCorners(ctx, t);
        endFrame();
    }

    private void drawScene(DrawContext ctx, float t) {
        float sec = Anim.seconds();
        float fade = stage(t, 0, 1.2f);
        int tint = Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.25f);
        // two silk layers drifting at different speeds for depth
        silk(ctx, sec * 0.11f, -px * 18, -py * 10, 1.08f, Theme.alpha(tint, 0.40f * fade), false);
        silk(ctx, sec * 0.07f + 2, -px * 8, -py * 5, 1.18f, Theme.alpha(Theme.accent2(), 0.14f * fade), true);

        // soft light pools: behind the brand and behind the model
        float cx = width * 0.46f;
        Render2D.glow(ctx, cx - 1, height * 0.2f - 1, 2, 2, 1, Math.min(width, height) * 0.35f, Theme.alpha(Theme.accent(), 0.10f * fade));
        Render2D.glow(ctx, width * 0.79f - 1, height * 0.55f - 1, 2, 2, 1, height * 0.5f, Theme.alpha(Theme.accent2(), 0.10f * fade));
        // vignette
        Render2D.rectV(ctx, 0, 0, width, height * 0.25f, 0x99000000, 0);
        Render2D.rectV(ctx, 0, height * 0.7f, width, height * 0.3f, 0, 0xB0000000);
    }

    private void silk(DrawContext ctx, float time, float ox, float oy, float scale, int color, boolean flip) {
        float w = width * scale, h = height * scale;
        float x = (width - w) / 2f + ox + (float) Math.sin(time) * width * 0.015f;
        float y = (height - h) / 2f + oy + (float) Math.cos(time * 0.8f) * height * 0.012f;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(w / 1000f, h / 1000f);
        if (flip) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, SILK, 0, 0, SILK_W, 0, 1000, 1000, -SILK_W, SILK_H, SILK_W, SILK_H, color);
        } else {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, SILK, 0, 0, 0, 0, 1000, 1000, SILK_W, SILK_H, SILK_W, SILK_H, color);
        }
        m.popMatrix();
    }

    private void drawParticles(DrawContext ctx, float dt, float t) {
        float fade = stage(t, 0.3f, 1.5f);
        float sec = Anim.seconds();
        for (Particle p : particles) {
            p.y -= p.vy * dt;
            if (p.y < -0.05f) respawn(p, false);
            float x = p.x * width + (float) Math.sin(sec * 0.4f + p.phase) * 14 * p.depth - px * 20 * p.depth;
            float y = p.y * height - py * 12 * p.depth;
            float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 1.6f + p.phase * 3));
            float edge = Math.min(1, Math.min(p.y, 1 - p.y) * 8);
            float al = fade * tw * edge * (0.35f + 0.65f * p.depth);
            if (al < 0.02f) continue;
            if (p.sparkle) {
                float s = p.size * 2.2f;
                Render2D.glow(ctx, x - 1, y - 1, 2, 2, 1, s * 1.4f, Theme.alpha(Theme.accent(), 0.35f * al));
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(x, y);
                m.rotate(sec * p.spin + p.phase);
                Render2D.icon(ctx, "icon_sparkle", -s / 2f, -s / 2f, s, Theme.alpha(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.55f), al));
                m.popMatrix();
            } else {
                float r = p.size * 0.35f;
                Render2D.glow(ctx, x - r, y - r, r * 2, r * 2, r, r * 4, Theme.alpha(Theme.accent(), 0.4f * al));
                Render2D.circle(ctx, x, y, r, Theme.alpha(0xFFE6EEFF, 0.8f * al));
            }
        }
    }

    private void drawModel(DrawContext ctx, float t) {
        float f = stage(t, 0.35f, 0.9f);
        float cx = width * 0.79f + px * -6, feet = height * 0.88f;
        float mh = height * 0.66f;
        // ground shadow and rim light
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, feet);
        m.scale(1f, 0.22f);
        float sr = mh * 0.24f;
        Render2D.glow(ctx, -sr * 0.6f, -sr * 0.6f, sr * 1.2f, sr * 1.2f, sr * 0.6f, sr * 0.7f, Theme.alpha(0xFF000000, 0.9f * f));
        Render2D.glow(ctx, -sr * 0.5f, -sr * 0.5f, sr, sr, sr * 0.5f, sr * 0.9f, Theme.alpha(Theme.accent(), 0.22f * f));
        Render2D.outline(ctx, -sr, -sr, sr * 2, sr * 2, sr, 0.75f, Theme.alpha(Theme.accent(), 0.35f * f));
        m.popMatrix();

        PREVIEW.look((mx - cx) / (width * 0.4f), (my - (feet - mh * 0.85f)) / (height * 0.6f));
        if (f > 0.6f) {
            PREVIEW.render(ctx, skin.get(), cx, feet + (1 - f) * 12, mh);
            previewArea(PREVIEW, cx - mh * 0.3f, feet - mh, mh * 0.6f, mh);
        }
    }

    private void drawBrand(DrawContext ctx, float t) {
        float f = stage(t, 0.1f, 0.8f);
        float cx = width * 0.46f, cy = height * 0.19f + (1 - f) * -8;
        String name = "SoulBound";
        float ls = 34, gap = 10;
        float nw = Fonts.DISPLAY.width(name);
        float total = ls + gap + nw, x0 = cx - total / 2f;
        float br = 0.75f + 0.25f * pulse(1.8f);
        Render2D.glow(ctx, x0 + 6, cy - ls / 2f + 6, ls - 12, ls - 12, ls / 2f, 18, Theme.alpha(Theme.accent(), 0.45f * br * f));
        Render2D.icon(ctx, "logo", x0, cy - ls / 2f, ls, Theme.alpha(0xFFFFFFFF, f));
        // the display font's glyphs sit high in their box, so nudge them onto the logo's centre line
        Fonts.DISPLAY.drawCentered(ctx, name, x0 + ls + gap, cy + 13, Theme.alpha(Theme.TEXT, f));

        float f2 = stage(t, 0.3f, 0.8f);
        String sub = "CLIENT  ·  " + SharedConstants.getGameVersion().name();
        float sw = Fonts.LABEL.width(sub, 2f), sy = cy + ls / 2f + 8;
        Fonts.LABEL.drawTracked(ctx, sub, cx - sw / 2f, sy, 2f, Theme.alpha(Theme.TEXT_MUTED, f2));
        float lw = 40 * f2;
        Render2D.rectH(ctx, cx - sw / 2f - 10 - lw, sy + 3, lw, 0.5f, 0, Theme.alpha(Theme.accent(), 0.8f * f2));
        Render2D.rectH(ctx, cx + sw / 2f + 10, sy + 3, lw, 0.5f, Theme.alpha(Theme.accent(), 0.8f * f2), 0);
    }

    private void drawMenu(DrawContext ctx, float t) {
        float cx = width * 0.46f;
        float row = 22, groupGap = 10;
        float total = items.size() * row;
        for (Item it : items) if (it.gapBefore()) total += groupGap;
        float y = height * 0.52f - total / 2f + 6;
        int hovered = -1;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.gapBefore()) {
                float f = stage(t, 0.45f + i * 0.06f, 0.5f);
                Render2D.rectH(ctx, cx - 30, y + groupGap / 2f - 1, 30, 0.5f, 0, Theme.alpha(Theme.BORDER_HI, f));
                Render2D.rectH(ctx, cx, y + groupGap / 2f - 1, 30, 0.5f, Theme.alpha(Theme.BORDER_HI, f), 0);
                y += groupGap;
            }
            if (drawItem(ctx, it, i, cx, y, row, stage(t, 0.4f + i * 0.06f, 0.5f))) hovered = i;
            y += row;
        }
        if (hovered >= 0) focus = hovered;

        // quick join
        if (lastServer != null) {
            float f = stage(t, 0.9f, 0.6f);
            String label = "Quick Join  ·  " + lastServer.name;
            float lw = Fonts.BODY.width(label) + 26, lh = 16, lx = cx - lw / 2f, ly = y + 12 + (1 - f) * 6;
            boolean hov = hot("quick", lx, ly, lw, lh, c -> {
                UiSounds.play(UiSounds.TOGGLE_ON, 1f, 0.8f);
                ConnectScreen.connect(this, client, ServerAddress.parse(lastServer.address), lastServer, false, null);
            });
            float hv = anim("quick").to(hov ? 1 : 0, 18);
            float sa = alpha;
            alpha = f;
            Render2D.glow(ctx, lx, ly, lw, lh, 8, 6 + 4 * hv, a(Theme.accent(), 0.2f + 0.3f * hv));
            Render2D.roundRect(ctx, lx, ly, lw, lh, 8, a(Theme.lerp(0xCC0E1020, Theme.withAlpha(Theme.accent(), 0x50), hv)));
            Render2D.outline(ctx, lx, ly, lw, lh, 8, 0, a(Theme.accent(), 0.6f + 0.4f * hv));
            Render2D.circle(ctx, lx + 9, ly + lh / 2f, 2, a(0xFF4ADE80));
            Fonts.BODY.drawCentered(ctx, label, lx + 16, ly + lh / 2f, a(Theme.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, hv)));
            alpha = sa;
        }
    }

    /** Draws one menu entry; returns whether the mouse is over it. */
    private boolean drawItem(DrawContext ctx, Item it, int i, float cx, float y, float row, float f) {
        String key = "item_" + i;
        float tw0 = Fonts.MENU.width(it.label());
        boolean hov = hot(key, cx - tw0 / 2f - 24, y, tw0 + 48, row, c -> activate(it));
        boolean on = hov || (focus == i && hoverKey == null);
        float hv = anim(key).to(on ? 1 : 0, 14);
        int tint = it.danger() ? 0xFFFF6B81 : Theme.accent();
        float tracking = 0.6f * hv;
        float tw = Fonts.MENU.width(it.label(), tracking);
        float x = cx - tw / 2f, cy = y + row / 2f + (1 - f) * 8;
        float sa = alpha;
        alpha = f;

        if (hv > 0.01f) {
            float pw = tw + 40, ph = row - 4;
            Render2D.glow(ctx, cx - pw / 2f, cy - ph / 2f, pw, ph, ph / 2f, 12, a(tint, 0.16f * hv));
            Render2D.roundRectH(ctx, cx - pw / 2f, cy - ph / 2f, pw / 2f, ph, ph / 2f, a(tint, 0), a(tint, 0.10f * hv));
            Render2D.roundRectH(ctx, cx, cy - ph / 2f, pw / 2f, ph, ph / 2f, a(tint, 0.10f * hv), a(tint, 0));
            // underline growing from the centre
            float uw = tw * Anim.easeOutCubic(hv);
            Render2D.glow(ctx, cx - uw / 2f, cy + 7, uw, 0.75f, 0.3f, 4, a(tint, 0.7f * hv));
            Render2D.rectH(ctx, cx - uw / 2f, cy + 7, uw / 2f, 0.75f, a(tint, 0), a(tint));
            Render2D.rectH(ctx, cx, cy + 7, uw / 2f, 0.75f, a(tint), a(tint, 0));
            // chevrons sliding in from both sides
            float off = 16 - 6 * hv;
            var m = ctx.getMatrices();
            for (int side = -1; side <= 1; side += 2) {
                m.pushMatrix();
                m.translate(side < 0 ? x - off : x + tw + off, cy);
                m.rotate((float) (side < 0 ? -Math.PI / 2 : Math.PI / 2));
                Render2D.icon(ctx, "icon_chevron", -3, -3, 6, a(tint, hv));
                m.popMatrix();
            }
        }
        drawRipples(ctx, key);
        int col = Theme.lerp(0xFFC9CCDE, 0xFFFFFFFF, hv);
        if (it.danger()) col = Theme.lerp(0xFFC9CCDE, 0xFFFFC2CC, hv);
        Fonts.MENU.drawTracked(ctx, it.label(), x, cy - Fonts.MENU.size / 2f, tracking, a(col));
        alpha = sa;
        return hov;
    }

    private void activate(Item it) {
        UiSounds.play(it.danger() ? UiSounds.CLOSE : UiSounds.CLICK, 1f, 0.85f);
        it.action().run();
    }

    private void drawCorners(DrawContext ctx, float t) {
        float f = stage(t, 0.8f, 0.8f);
        float sa = alpha;
        alpha = f;
        // top right: clock and date
        LocalTime now = LocalTime.now();
        String time = String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
        LocalDate d = LocalDate.now();
        String date = (d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", "
                + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth()).toUpperCase(Locale.ROOT);
        float tw = Fonts.TITLE.width(time);
        Fonts.TITLE.draw(ctx, time, width - 14 - tw, 12, a(Theme.TEXT));
        float dw = Fonts.LABEL.width(date, 1f);
        Fonts.LABEL.drawTracked(ctx, date, width - 14 - dw, 29, 1f, a(Theme.TEXT_DIM));

        // bottom left: account chip
        float bx = 12, bh = 24, by = height - 12 - bh;
        String name = client.getSession().getUsername();
        float bw = 30 + Math.max(Fonts.BODY_BOLD.width(name), Fonts.LABEL.width("SIGNED IN", 0.8f)) + 12;
        Render2D.roundRect(ctx, bx, by, bw, bh, 8, a(0xB00E1020));
        Render2D.outline(ctx, bx, by, bw, bh, 8, 0, a(Theme.BORDER_HI));
        if (f > 0.5f) PlayerSkinDrawer.draw(ctx, skin.get(), Math.round(bx + 5), Math.round(by + 4), 16, a(0xFFFFFFFF));
        Render2D.circle(ctx, bx + 20, by + 19, 2.2f, a(0xFF0E1020));
        Render2D.circle(ctx, bx + 20, by + 19, 1.5f, a(0xFF4ADE80));
        Fonts.BODY_BOLD.draw(ctx, name, bx + 27, by + 4, a(Theme.TEXT));
        Fonts.LABEL.drawTracked(ctx, "SIGNED IN", bx + 27, by + 15, 0.8f, a(Theme.TEXT_DIM));

        // bottom right: version and classic menu link
        String ver = "SOULBOUND " + SoulBound.VERSION + "  ·  MINECRAFT " + SharedConstants.getGameVersion().name();
        float vw = Fonts.LABEL.width(ver, 1f);
        Fonts.LABEL.drawTracked(ctx, ver, width - 14 - vw, height - 18, 1f, a(Theme.TEXT_DIM));
        String cl = "Classic Menu";
        float cw = Fonts.BODY.width(cl), cxp = width - 14 - cw, cyp = height - 34;
        boolean hov = hot("classic", cxp - 4, cyp - 2, cw + 8, 12, c -> {
            UiSounds.play(UiSounds.CLICK, 0.95f, 0.8f);
            useVanilla = true;
            client.setScreen(new TitleScreen());
        });
        float hv = anim("classic").to(hov ? 1 : 0, 18);
        Fonts.BODY.draw(ctx, cl, cxp, cyp, a(Theme.lerp(Theme.TEXT_MUTED, Theme.accent(), hv)));
        Render2D.rect(ctx, cxp + cw * (1 - hv) / 2f, cyp + 10, cw * hv, 0.5f, a(Theme.accent()));
        alpha = sa;
    }

    // =================================================================== input
    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_TAB && !input.hasShift()) {
            focus = (focus + 1 + items.size()) % items.size();
            UiSounds.hover();
            return true;
        }
        if (k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_TAB) {
            focus = (focus - 1 + items.size()) % items.size();
            UiSounds.hover();
            return true;
        }
        if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER || k == GLFW.GLFW_KEY_SPACE) && focus >= 0) {
            activate(items.get(focus));
            return true;
        }
        return super.keyPressed(input);
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
