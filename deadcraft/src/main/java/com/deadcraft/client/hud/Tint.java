package com.deadcraft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sets the blit colour and (re-)enables alpha blending. GuiGraphics.setColor and text drawing
 * flush batched geometry, and that flush switches blending off again, so every translucent
 * blit has to go through here.
 */
public final class Tint {
    private Tint() {}

    public static void set(GuiGraphics g, float r, float gr, float b, float a) {
        g.setColor(r, gr, b, a);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void reset(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
    }
}
