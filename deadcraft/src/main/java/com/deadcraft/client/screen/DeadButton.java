package com.deadcraft.client.screen;

import com.deadcraft.DeadCraft;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.client.hud.Tint;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A cracked, blood-lit button that shivers when hovered. */
public class DeadButton extends AbstractButton {
    private static final ResourceLocation NORMAL = DeadCraft.id("textures/gui/title/button_normal.png");
    private static final ResourceLocation HOVER = DeadCraft.id("textures/gui/title/button_hover.png");
    private static final ResourceLocation DISABLED = DeadCraft.id("textures/gui/title/button_disabled.png");

    private final Runnable action;
    private float hover;
    private boolean wasHovered;

    public DeadButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused() && active;
        if (hot && !wasHovered) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_HOVER.get(), 0.9f + (float) Math.random() * 0.2f, 0.6f));
        }
        wasHovered = hot;
        hover = Mth.lerp(0.25f, hover, hot ? 1f : 0f);
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        int shake = hot && Math.sin(time * 40) > 0.6 ? 1 : 0;
        int x = getX() + shake, y = getY();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (hover > 0.02f) {
            // red glow bleeding out from behind the button
            int a = (int) (90 * hover);
            g.fill(x - 3, y - 2, x + width + 3, y + height + 2, (a << 24) | 0x8b0000);
        }
        ResourceLocation tex = !active ? DISABLED : hover > 0.5f ? HOVER : NORMAL;
        Tint.set(g, 1f, 1f, 1f, 1f);
        g.blit(tex, x, y, width, height, 0, 0, 200, 20, 200, 20);
        int base = active ? lerpColor(0xd8cfc0, 0xff3030, hover) : 0x6a6060;
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + (height - 8) / 2, base | 0xff000000);
        RenderSystem.disableBlend();
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bb = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (gg << 8) | bb;
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        sounds.play(SimpleSoundInstance.forUI(ModSounds.UI_CLICK.get(), 1.0f, 0.8f));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
