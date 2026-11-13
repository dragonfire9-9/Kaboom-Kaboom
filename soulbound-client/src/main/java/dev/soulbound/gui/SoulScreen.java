package dev.soulbound.gui;

import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.SkinPreview;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.Configurable;
import dev.soulbound.module.Module;
import dev.soulbound.setting.ActionSetting;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.KeybindSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.setting.Setting;
import dev.soulbound.sound.UiSounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared base for SoulBound's immediate-mode screens: open/close animation, hit regions,
 * hover sounds, click ripples, clipping, and the flat parameter rows (checkbox, slider,
 * arrow selector, keybind chip, button) used by the Bots view and the title screen.
 */
public abstract class SoulScreen extends Screen {
    protected final Anim.Value open = new Anim.Value(0);
    protected boolean closing;
    protected float alpha = 1;
    protected int mx, my;

    protected final Map<Object, Anim.Value> anims = new HashMap<>();
    protected final List<Hit> hits = new ArrayList<>();
    private final List<Ripple> ripples = new ArrayList<>();
    protected float[] clip;
    private final List<float[]> clipStack = new ArrayList<>();
    protected String hoverKey;
    private String lastHoverKey;

    protected KeybindSetting listening;
    protected NumberSetting dragging;
    protected float dragX, dragW;
    /** Model preview being dragged, and its current on-screen rect. */
    protected SkinPreview previewDrag;
    private final Map<SkinPreview, float[]> previewRects = new HashMap<>();

    protected SoulScreen(Text title) { super(title); }

    // =================================================================== frame
    /** Called by subclasses at the start of {@code render}; returns false if the screen just finished closing. */
    protected boolean beginFrame(int mouseX, int mouseY) {
        Anim.frame();
        mx = mouseX;
        my = mouseY;
        hits.clear();
        previewRects.clear();
        hoverKey = null;
        clip = null;
        clipStack.clear();
        float o = open.to(closing ? 0 : 1, closing ? 16 : 9);
        if (closing && o < 0.02f) {
            onClosed();
            return false;
        }
        alpha = Anim.easeOutCubic(o);
        return true;
    }

    protected void endFrame() {
        if (hoverKey != null && !hoverKey.equals(lastHoverKey) && !closing) UiSounds.hover();
        lastHoverKey = hoverKey;
    }

    /** What happens when the close animation finishes. */
    protected void onClosed() {
        if (client != null) client.setScreen(null);
    }

    protected int a(int color) { return Theme.alpha(color, alpha); }

    protected int a(int color, float mul) { return Theme.alpha(color, alpha * mul); }

    protected Anim.Value anim(Object key) { return anims.computeIfAbsent(key, k -> new Anim.Value(0)); }

    protected float pulse(float speed) { return 0.5f + 0.5f * (float) Math.sin(Anim.seconds() * speed); }

    // =================================================================== shared drawing
    protected void panel(DrawContext ctx, float x, float y, float w, float h, float r) {
        Render2D.roundRect(ctx, x, y, w, h, r, a(Theme.PANEL));
        Render2D.outline(ctx, x, y, w, h, r, 0, a(Theme.BORDER));
    }

    protected void drawSwitch(DrawContext ctx, float x, float y, float w, float h, float on, float hover) {
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

    protected void toggleModule(Module module) {
        module.toggle();
        UiSounds.play(module.isEnabled() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
    }

    /** Glowing pedestal ellipse with an orbiting spark; {@code lit} brightens it. */
    protected void pedestal(DrawContext ctx, float cx, float cy, float radius, boolean lit) {
        float p = pulse(4f);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(1f, 0.3f);
        float pr = radius;
        Render2D.glow(ctx, -pr * 0.75f, -pr * 0.75f, pr * 1.5f, pr * 1.5f, pr * 0.75f, 18, a(Theme.accent(), (lit ? 0.35f : 0.18f) * (0.8f + 0.2f * p)));
        Render2D.outline(ctx, -pr, -pr, pr * 2, pr * 2, pr, 0.75f, a(Theme.accent(), lit ? 0.55f : 0.3f));
        Render2D.outline(ctx, -pr * 0.68f, -pr * 0.68f, pr * 1.36f, pr * 1.36f, pr * 0.68f, 0.5f, a(Theme.accent2(), 0.25f));
        float spin = Anim.seconds() * 0.8f;
        float ox = (float) Math.cos(spin) * pr, oy = (float) Math.sin(spin) * pr;
        Render2D.glow(ctx, ox - 2, oy - 2, 4, 4, 2, 8, a(Theme.accent(), lit ? 0.9f : 0.4f));
        Render2D.circle(ctx, ox, oy, 2, a(0xFFFFFFFF, lit ? 0.9f : 0.5f));
        m.popMatrix();
    }

    /** Registers a model preview's rect so dragging inside it rotates the model. */
    protected void previewArea(SkinPreview preview, float x, float y, float w, float h) {
        previewRects.put(preview, new float[]{x, y, w, h});
    }

    // =================================================================== flat parameter rows
    /** Draws every visible setting of {@code item} as flat rows from {@code y}; returns the y after the last row. */
    protected float flatSettings(DrawContext ctx, Configurable item, float x, float y, float w) {
        if (item instanceof Module m) y = flatKeybind(ctx, m.getKeybind(), "Keybind", x, y, w);
        for (Setting<?> s : item.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof BoolSetting b) y = flatBool(ctx, b, x, y, w);
            else if (s instanceof NumberSetting n) y = flatNumber(ctx, n, x, y, w);
            else if (s instanceof ModeSetting mo) y = flatMode(ctx, mo, x, y, w);
            else if (s instanceof KeybindSetting k) y = flatKeybind(ctx, k, k.getName(), x, y, w);
            else if (s instanceof ActionSetting ac) y = flatAction(ctx, ac, x, y, w);
        }
        return y;
    }

    protected String key(Setting<?> s) { return "set_" + System.identityHashCode(s); }

    private float flatRow(DrawContext ctx, String key, float x, float y, float w, float h, boolean hov) {
        float hv = anim(key).to(hov ? 1 : 0, 18);
        if (hv > 0.01f) Render2D.roundRect(ctx, x - 4, y, w + 8, h, 5, a(Theme.SURFACE_HI, 0.55f * hv));
        drawRipples(ctx, key);
        return hv;
    }

    protected int labelColor(float hv) { return Theme.lerp(Theme.lerp(Theme.TEXT_MUTED, Theme.TEXT, 0.7f), Theme.TEXT, hv); }

    protected float flatBool(DrawContext ctx, BoolSetting s, float x, float y, float w) {
        float h = 19;
        String key = key(s);
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            s.toggle();
            UiSounds.play(s.isOn() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
        });
        float hv = flatRow(ctx, key, x, y, w, h, hov);
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

    protected float flatNumber(DrawContext ctx, NumberSetting s, float x, float y, float w) {
        float h = 28;
        String key = key(s);
        float ty = y + 20;
        boolean active = dragging == s;
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> startDrag(s, x, w));
        float act = anim(key + "_act").to(active ? 1 : 0, 16);
        float hv = flatRow(ctx, key, x, y, w, h, hov || active);
        Fonts.BODY.drawCentered(ctx, s.getName(), x, y + 8, a(labelColor(hv)));
        String val = s.display();
        float vw = Fonts.BODY_BOLD.width(val);
        Fonts.BODY_BOLD.drawCentered(ctx, val, x + w - vw, y + 8, a(Theme.lerp(Theme.TEXT, Theme.accent(), act)));
        float p = anim(key + "_p").to((float) s.progress(), 20);
        float th = 2;
        Render2D.roundRect(ctx, x, ty - th / 2f, w, th, th / 2f, a(Theme.TRACK_OFF));
        float fw = w * p;
        if (fw > 0.5f) {
            Render2D.glow(ctx, x, ty - th / 2f, fw, th, th / 2f, 4 + 2 * act, a(Theme.accent(), 0.35f + 0.25f * act));
            Render2D.roundRectH(ctx, x, ty - th / 2f, fw, th, th / 2f, a(Theme.accent(), 0.7f), a(Theme.accentMix(p)));
        }
        float ks = 6 + 1.5f * Math.max(hv, act);
        float kx = x + fw - ks / 2f;
        Render2D.glow(ctx, kx, ty - ks / 2f, ks, ks, 2, 4 + 3 * act, a(Theme.accent(), 0.45f + 0.35f * act));
        Render2D.roundRect(ctx, kx, ty - ks / 2f, ks, ks, 2, a(Theme.lerp(Theme.accent(), 0xFFFFFFFF, 0.15f + 0.3f * act)));
        return y + h;
    }

    protected float flatMode(DrawContext ctx, ModeSetting s, float x, float y, float w) {
        float h = 21;
        String key = key(s);
        float maxW = 0;
        for (String mode : s.getModes()) maxW = Math.max(maxW, Fonts.BODY_BOLD.width(mode));
        float cw = Math.max(74, maxW + 30), ch = 15, cx = x + w - cw, cy = y + (h - ch) / 2f;
        Anim.Value slide = anim(key + "_slide");
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            int n = s.getModes().size();
            boolean back = c.x() < cx + cw / 2f && c.x() >= cx;
            s.set(s.getModes().get((s.index() + (back ? n - 1 : 1)) % n));
            slide.snap(back ? -1 : 1);
            UiSounds.play(UiSounds.CLICK, back ? 0.95f : 1.1f, 0.75f);
        });
        float hv = flatRow(ctx, key, x, y, w, h, hov);
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

    protected float flatKeybind(DrawContext ctx, KeybindSetting s, String label, float x, float y, float w) {
        float h = 19;
        String key = key(s);
        boolean isListening = listening == s;
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            listening = listening == s ? null : s;
            UiSounds.play(UiSounds.CLICK, listening != null ? 1.2f : 0.9f, 0.8f);
        });
        float act = anim(key + "_act").to(isListening ? 1 : 0, 14);
        float hv = flatRow(ctx, key, x, y, w, h, hov);
        Fonts.BODY.drawCentered(ctx, label, x, y + h / 2f, a(labelColor(hv)));
        float kh = 12, ky = y + (h - kh) / 2f;
        if (isListening || s.isBound()) {
            String kn = isListening ? "PRESS KEY" : s.display();
            float kw = Math.max(22, (isListening ? Fonts.LABEL.width(kn, 0.8f) : Fonts.BODY_BOLD.width(kn)) + 10);
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

    protected float flatAction(DrawContext ctx, ActionSetting s, float x, float y, float w) {
        float h = 21;
        String key = key(s);
        String label = s.getButtonLabel();
        float bw = Fonts.BODY_BOLD.width(label) + 16, bh = 14, bx = x + w - bw, by = y + (h - bh) / 2f;
        boolean hov = hot(key, x - 4, y, w + 8, h, c -> {
            UiSounds.play(UiSounds.CLICK, 1.1f, 0.8f);
            s.run();
        });
        float hv = flatRow(ctx, key, x, y, w, h, hov);
        Fonts.BODY.drawCentered(ctx, s.getName(), x, y + h / 2f, a(labelColor(hv)));
        Render2D.glow(ctx, bx, by, bw, bh, 5, 5, a(Theme.accent(), 0.25f + 0.3f * hv));
        Render2D.roundRectH(ctx, bx, by, bw, bh, 5, a(Theme.accent()), a(Theme.accent2()));
        Fonts.BODY_BOLD.drawCentered(ctx, label, bx + 8, by + bh / 2f, a(0xFFFFFFFF));
        return y + h;
    }

    protected void startDrag(NumberSetting s, float trackX, float trackW) {
        dragging = s;
        dragX = trackX;
        dragW = trackW;
        updateDrag(mx);
        UiSounds.play(UiSounds.CLICK, 1.1f, 0.6f);
    }

    private void updateDrag(double mouseX) {
        if (dragging == null) return;
        double before = dragging.get();
        dragging.setProgress((mouseX - dragX) / dragW);
        if (dragging.get() != before) UiSounds.slide((float) dragging.progress());
    }

    // =================================================================== hit testing
    protected record Hit(String key, float x, float y, float w, float h, Consumer<Click> action) {}

    private record Ripple(String key, float x, float y, float maxR, long start) {}

    /** Registers a clickable region and returns whether the mouse is over it this frame. */
    protected boolean hot(String key, float x, float y, float w, float h, Consumer<Click> action) {
        float cx = x, cy = y, cw = w, ch = h;
        if (clip != null) {
            float x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
            float x1 = Math.min(x + w, clip[0] + clip[2]), y1 = Math.min(y + h, clip[1] + clip[3]);
            cx = x0; cy = y0; cw = x1 - x0; ch = y1 - y0;
            if (cw <= 0 || ch <= 0) return false;
        }
        hits.add(new Hit(key, cx, cy, cw, ch, action));
        if (closing || open.get() < 0.6f || isBlocked(mx, my, key)) return false;
        boolean over = inside(mx, my, cx, cy, cw, ch);
        if (over) hoverKey = key;
        return over;
    }

    /** Subclasses can block hover beneath overlays. */
    protected boolean isBlocked(double x, double y, String key) { return false; }

    protected static boolean inside(double px, double py, float x, float y, float w, float h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    protected void pushClip(DrawContext ctx, float x, float y, float w, float h) {
        if (clip != null) {
            clipStack.add(clip);
            float x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
            float x1 = Math.min(x + w, clip[0] + clip[2]), y1 = Math.min(y + h, clip[1] + clip[3]);
            x = x0; y = y0; w = Math.max(0, x1 - x0); h = Math.max(0, y1 - y0);
        }
        clip = new float[]{x, y, w, h};
        ctx.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    protected void popClip(DrawContext ctx) {
        clip = clipStack.isEmpty() ? null : clipStack.removeLast();
        ctx.disableScissor();
    }

    protected void drawRipples(DrawContext ctx, String key) {
        long now = System.currentTimeMillis();
        for (Ripple r : ripples) {
            if (!r.key.equals(key)) continue;
            float t = (now - r.start) / 520f;
            if (t >= 1) continue;
            Hit hit = null;
            for (Hit hh : hits) if (hh.key.equals(key)) hit = hh;
            if (hit == null) continue;
            pushClip(ctx, hit.x, hit.y, hit.w, hit.h);
            Render2D.circle(ctx, r.x, r.y, r.maxR * Anim.easeOutCubic(t), a(Theme.accent(), 0.22f * (1 - t)));
            popClip(ctx);
        }
    }

    protected Hit topHit(double x, double y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (inside(x, y, h.x, h.y, h.w, h.h)) return h;
        }
        return null;
    }

    /** Runs a hit's action with a ripple; subclasses call this from mouseClicked. */
    protected void activate(Hit h, Click click) {
        long now = System.currentTimeMillis();
        ripples.removeIf(r -> now - r.start > 600);
        ripples.add(new Ripple(h.key, (float) click.x(), (float) click.y(), Math.max(h.w, h.h) * 1.1f, now));
        h.action.accept(click);
    }

    // =================================================================== input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        Hit h = topHit(click.x(), click.y());
        if (listening != null && (h == null || !h.key.equals(key(listening)))) listening = null;
        if (h != null) {
            activate(h, click);
            return true;
        }
        for (var e : previewRects.entrySet()) {
            float[] r = e.getValue();
            if (inside(click.x(), click.y(), r[0], r[1], r[2], r[3])) {
                previewDrag = e.getKey();
                return true;
            }
        }
        return onEmptyClick(click);
    }

    protected boolean onEmptyClick(Click click) { return true; }

    @Override
    public boolean mouseReleased(Click click) {
        previewDrag = null;
        if (dragging != null) {
            dragging = null;
            UiSounds.play(UiSounds.CLICK, 0.9f, 0.4f);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (previewDrag != null) previewDrag.drag(offsetX, offsetY);
        if (dragging != null) {
            mx = (int) click.x();
            updateDrag(click.x());
        }
        return true;
    }

    /** Handles keybind capture; returns true if the key was consumed. */
    protected boolean handleListening(KeyInput input) {
        if (listening == null) return false;
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) listening.set(KeybindSetting.NONE);
        else listening.set(key);
        listening = null;
        UiSounds.play(UiSounds.TOGGLE_ON, 1.1f, 0.7f);
        return true;
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        ripples.removeIf(r -> now - r.start > 600);
    }
}
