package dev.soulbound.module;

import net.minecraft.client.gui.DrawContext;

/** A module that draws something on the in-game HUD while it's enabled. */
public interface HudModule {
    void renderHud(DrawContext ctx, float tickDelta);
}
