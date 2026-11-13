package dev.soulbound.hud;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.sound.UiSounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Drag HUD elements around; they snap to the screen edges and centre lines. Right-click hides/shows one. */
public final class HudEditorScreen extends Screen {
    private static final float SNAP = 6;
    private final Screen parent;
    private HudElement dragging, hovered;
    private float grabX, grabY;
    private boolean snapX, snapY;
    private final Anim.Value fade = new Anim.Value(0);

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD Layout"));
        this.parent = parent;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float f = fade.get();
        Render2D.rect(ctx, 0, 0, width, height, Theme.alpha(0x7005060C, f));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Anim.frame();
        float f = Anim.easeOutCubic(fade.to(1, 8));
        // grid
        for (int x = 0; x < width; x += 20) Render2D.hairlineV(ctx, x, 0, height, Theme.alpha(0xFFFFFFFF, 0.025f * f));
        for (int y = 0; y < height; y += 20) Render2D.hairlineH(ctx, 0, y, width, Theme.alpha(0xFFFFFFFF, 0.025f * f));
        // centre guides
        int guide = Theme.alpha(Theme.accent(), 0.7f * f);
        if (dragging != null && snapX) Render2D.hairlineV(ctx, width / 2f, 0, height, guide);
        if (dragging != null && snapY) Render2D.hairlineH(ctx, 0, height / 2f, width, guide);

        HudRenderer.renderAll(ctx, width, height, true, f);

        hovered = null;
        for (HudElement e : HudRenderer.hud().elements) {
            if (!e.layout(true)) continue;
            float x = e.screenX(width), y = e.screenY(height);
            boolean over = mouseX >= x - 2 && mouseY >= y - 2 && mouseX < x + e.w + 2 && mouseY < y + e.h + 2;
            if (over && dragging == null) hovered = e;
            boolean active = e == dragging || e == hovered;
            int col = active ? Theme.accent() : Theme.alpha(Theme.TEXT_MUTED, 0.4f);
            if (active) Render2D.glow(ctx, x - 2, y - 2, e.w + 4, e.h + 4, 7, 6, Theme.alpha(Theme.accent(), 0.35f * f));
            Render2D.outline(ctx, x - 2, y - 2, e.w + 4, e.h + 4, 7, 0, Theme.alpha(col, f));
            if (active) {
                String label = e.name + (e.enabled.isOn() ? "" : "  (hidden)");
                float lw = Fonts.LABEL.width(label.toUpperCase(), 0.8f) + 8;
                float ly = y - 14 < 2 ? y + e.h + 4 : y - 14;
                Render2D.roundRect(ctx, x - 2, ly, lw, 10, 3, Theme.alpha(Theme.accent(), f));
                Fonts.LABEL.drawTracked(ctx, label.toUpperCase(), x + 2, ly + 5 - Fonts.LABEL.size / 2f, 0.8f, Theme.alpha(0xFFFFFFFF, f));
            }
        }

        // hint
        String hint = "DRAG TO MOVE  ·  RIGHT-CLICK TO HIDE / SHOW  ·  ESC TO SAVE";
        float hw = Fonts.LABEL.width(hint, 1f) + 20, hx = (width - hw) / 2f, hy = height / 2f - 8;
        if (dragging == null) {
            Render2D.glow(ctx, hx, hy, hw, 16, 8, 10, Theme.alpha(Theme.accent(), 0.25f * f));
            Render2D.roundRect(ctx, hx, hy, hw, 16, 8, Theme.alpha(0xEE0B0C14, f));
            Render2D.outline(ctx, hx, hy, hw, 16, 8, 0, Theme.alpha(Theme.accent(), 0.6f * f));
            Fonts.LABEL.drawTracked(ctx, hint, hx + 10, hy + 8 - Fonts.LABEL.size / 2f, 1f, Theme.alpha(Theme.TEXT, f));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (hovered == null) return true;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            hovered.enabled.toggle();
            UiSounds.play(hovered.enabled.isOn() ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF);
            return true;
        }
        dragging = hovered;
        grabX = (float) click.x() - dragging.screenX(width);
        grabY = (float) click.y() - dragging.screenY(height);
        UiSounds.play(UiSounds.CLICK, 1.1f, 0.6f);
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging == null) return true;
        float x = (float) click.x() - grabX, y = (float) click.y() - grabY;
        float m = 4;
        // snap to edges and centre
        if (Math.abs(x - m) < SNAP) x = m;
        if (Math.abs(x + dragging.w - (width - m)) < SNAP) x = width - m - dragging.w;
        snapX = Math.abs(x + dragging.w / 2f - width / 2f) < SNAP;
        if (snapX) x = width / 2f - dragging.w / 2f;
        if (Math.abs(y - m) < SNAP) y = m;
        if (Math.abs(y + dragging.h - (height - m)) < SNAP) y = height - m - dragging.h;
        snapY = Math.abs(y + dragging.h / 2f - height / 2f) < SNAP;
        if (snapY) y = height / 2f - dragging.h / 2f;
        dragging.moveTo(x, y, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) UiSounds.play(UiSounds.CLICK, 0.9f, 0.5f);
        dragging = null;
        snapX = snapY = false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        SoulBound.config().save();
        UiSounds.play(UiSounds.CLOSE, 1f, 0.6f);
        if (client != null) client.setScreen(parent instanceof dev.soulbound.gui.ClickGuiScreen ? new dev.soulbound.gui.ClickGuiScreen() : parent);
    }
}
