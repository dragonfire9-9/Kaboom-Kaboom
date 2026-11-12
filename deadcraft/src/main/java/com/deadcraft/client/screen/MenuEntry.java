package com.deadcraft.client.screen;

import com.deadcraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A text-only menu entry for the title screen: gothic lettering that slides right when hovered,
 * with a blood stroke drawn underneath and a skull marker beside it.
 */
public class MenuEntry extends AbstractButton {
    private final Runnable action;
    private final float scale;
    private float hover;
    private boolean wasHovered;

    public MenuEntry(int x, int y, int w, int h, Component label, float scale, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
        this.scale = scale;
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_CLICK.get(), 1.0f, 0.8f));
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused() && active;
        if (hot && !wasHovered) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_HOVER.get(), 0.9f + (float) Math.random() * 0.2f, 0.5f));
        }
        wasHovered = hot;
        hover = Mth.lerp(0.25f, hover, hot ? 1f : 0f);
        var font = Minecraft.getInstance().font;
        int slide = Math.round(hover * 10);
        // blood stroke behind the text, growing from the left
        int strokeW = Math.round((font.width(getMessage()) * scale + 16) * hover);
        if (strokeW > 1) {
            int y0 = getY() + getHeight() / 2 - 3;
            g.fillGradient(getX() + slide - 6, y0, getX() + slide - 6 + strokeW, y0 + 8, 0xc08a0a10, 0x605a0008);
            g.fill(getX() + slide - 6, y0 + 8, getX() + slide - 6 + strokeW, y0 + 9, 0x80300004);
        }
        // the skull marker
        if (hover > 0.05f) {
            int a = (int) (hover * 255);
            g.drawString(font, "☠", getX() + slide - 14, getY() + (getHeight() - 8) / 2, (a << 24) | 0xd8c8a8, false);
        }
        g.pose().pushPose();
        g.pose().translate(getX() + slide, getY() + (getHeight() - 8 * scale) / 2f, 0);
        g.pose().scale(scale, scale, 1);
        int col = !active ? 0xff5a5050 : hot ? 0xfff0e0d0 : 0xffb0a090;
        g.drawString(font, getMessage(), 0, 0, col, true);
        g.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
