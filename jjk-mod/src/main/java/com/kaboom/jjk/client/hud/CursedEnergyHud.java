package com.kaboom.jjk.client.hud;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.Abilities;
import com.kaboom.jjk.ability.Ability;
import com.kaboom.jjk.ability.DomainHandler;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Top-left jujutsu HUD (clear of chat, hotbar, boss bars and effect icons):
 * <pre>
 *  [icon]  Ability name               Grade
 *          [=====CE bar (glowing)=====] 120/300
 *          [a][b][c][d] ability slots with cooldown sweep
 * </pre>
 */
public class CursedEnergyHud implements LayeredDraw.Layer {
    private static final int BAR_W = 112;
    private static final int BAR_H = 8;
    private static final int SLOT = 14;
    private static final int MAX_SLOTS = 8;

    private static final int CE_LEFT = 0xFF2F6BFF;   // blue
    private static final int CE_RIGHT = 0xFFA43BFF;  // purple

    /** Smoothed CE fraction so the bar glides instead of snapping between syncs. */
    private float displayed = -1;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        CursedEnergyData data = ModAttachments.get(mc.player);
        Font font = mc.font;
        float time = (mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true)) / 20f;

        int x = 6;
        int y = 6;

        // --- technique icon (24x24 framed)
        int iconColor = data.getTechnique().color;
        frame(g, x, y + 2, 24, 24, iconColor, 0.55f + 0.25f * Mth.sin(time * 2.2f));
        ResourceLocation icon = JJKMod.id("textures/gui/technique/" + data.getTechnique().id() + ".png");
        g.blit(icon, x + 4, y + 6, 0, 0, 16, 16, 16, 16);

        int bx = x + 30;

        // --- header: selected ability + grade
        String selected = data.getSelectedAbilityId();
        Ability ability = Abilities.get(selected);
        Component name = ability != null ? ability.displayName() : data.getTechnique().displayName();
        int nameColor = ability != null ? ability.color() | 0xFF000000 : 0xFFFFFFFF;
        g.drawString(font, name, bx, y, nameColor, true);
        Component grade = data.getGrade().displayName();
        g.drawString(font, grade, bx + BAR_W - font.width(grade), y, 0xFFFFFFFF, true);

        // --- CE bar
        int by = y + 11;
        if (data.getRestriction() == HeavenlyRestriction.PHYSICAL) {
            g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, 0xC0000000);
            g.fill(bx, by, bx + BAR_W, by + BAR_H, 0xFF3A3A3A);
            Component hr = Component.translatable("jjk.hud.heavenly_restriction");
            g.drawString(font, hr, bx + (BAR_W - font.width(hr)) / 2, by, 0xFFE8E8E8, false);
        } else {
            double max = data.getMaxCE();
            float frac = max <= 0 ? 0 : (float) Mth.clamp(data.getCurrentCE() / max, 0, 1);
            displayed = displayed < 0 ? frac : Mth.lerp(0.15f, displayed, frac);
            drawCEBar(g, bx, by, displayed, time);
            String ce = String.format("%d/%d", (int) data.getCurrentCE(), (int) max);
            g.drawString(font, ce, bx + BAR_W + 4, by, 0xFFCFC4FF, true);
        }

        // --- ability slots
        int sy = by + BAR_H + 4;
        List<String> unlocked = data.getUnlockedAbilities();
        int sel = data.getSelectedIndex();
        int count = Math.min(unlocked.size(), MAX_SLOTS);
        int start = unlocked.size() <= MAX_SLOTS ? 0 : Mth.clamp(sel - MAX_SLOTS / 2, 0, unlocked.size() - MAX_SLOTS);
        for (int i = 0; i < count; i++) {
            int idx = start + i;
            Ability a = Abilities.get(unlocked.get(idx));
            if (a == null) continue;
            drawSlot(g, font, bx + i * (SLOT + 2), sy, a, data, idx == sel);
        }

        // --- domain cooldown chip
        int domainCd = data.getCooldown(DomainHandler.COOLDOWN_ID);
        if (data.isDomainUnlocked()) {
            int dx = bx + count * (SLOT + 2) + 4;
            String label = domainCd > 0 ? String.format("V %ds", (domainCd + 19) / 20) : "V";
            g.fill(dx, sy, dx + font.width(label) + 6, sy + SLOT, domainCd > 0 ? 0xA0301830 : 0xC0602090);
            g.drawString(font, label, dx + 3, sy + 3, domainCd > 0 ? 0xFF999999 : 0xFFFFE0FF, false);
        }
    }

    private static void drawCEBar(GuiGraphics g, int x, int y, float frac, float time) {
        int filled = Math.round(BAR_W * frac);
        float pulse = 0.5f + 0.5f * Mth.sin(time * 3f);

        // outer glow: stacked translucent rings in purple, breathing with the pulse
        for (int i = 3; i >= 1; i--) {
            int alpha = (int) ((18 + 22 * pulse) / i);
            g.fill(x - i - 1, y - i - 1, x + filled + i + 1, y + BAR_H + i + 1, (alpha << 24) | 0x8A4BFF);
        }
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xE0100820);
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF1A1030);

        // horizontal blue -> purple gradient, one column at a time
        for (int i = 0; i < filled; i++) {
            float t = i / (float) (BAR_W - 1);
            int c = lerpColor(CE_LEFT, CE_RIGHT, t);
            g.fill(x + i, y, x + i + 1, y + BAR_H, c);
        }
        if (filled > 0) {
            // top highlight and a travelling shimmer
            g.fill(x, y, x + filled, y + 2, 0x50FFFFFF);
            int shimmer = (int) ((time * 40) % (BAR_W + 20)) - 10;
            for (int i = 0; i < 6; i++) {
                int sx = x + shimmer + i;
                if (sx >= x && sx < x + filled) g.fill(sx, y, sx + 1, y + BAR_H, (0x60 - i * 0x0C) << 24 | 0xFFFFFF);
            }
        }
        // segment ticks every 10%
        for (int i = 1; i < 10; i++) g.fill(x + BAR_W * i / 10, y + BAR_H - 2, x + BAR_W * i / 10 + 1, y + BAR_H, 0x40000000);
    }

    private static void drawSlot(GuiGraphics g, Font font, int x, int y, Ability a, CursedEnergyData data, boolean selected) {
        int color = a.color() | 0xFF000000;
        g.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, selected ? 0xFFFFFFFF : 0xC0000000);
        g.fill(x, y, x + SLOT, y + SLOT, darken(color, 0.45f));
        g.fill(x + 2, y + 2, x + SLOT - 2, y + SLOT - 2, color);

        String initials = initials(a.displayName().getString());
        int textColor = luminance(color) > 0.6f ? 0xFF101010 : 0xFFFFFFFF;
        g.pose().pushPose();
        g.pose().translate(x + SLOT / 2f, y + SLOT / 2f - 3, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, initials, -font.width(initials) / 2, 0, textColor, false);
        g.pose().popPose();

        int cd = data.getCooldown(a.id());
        if (cd > 0) {
            float frac = Mth.clamp(cd / (float) Math.max(1, a.cooldown()), 0, 1);
            int h = Math.round(SLOT * frac);
            g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, 0xB0000000);
            String secs = String.valueOf((cd + 19) / 20);
            g.pose().pushPose();
            g.pose().translate(x + SLOT / 2f, y + SLOT / 2f - 3, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, secs, -font.width(secs) / 2, 0, 0xFFFF6060, true);
            g.pose().popPose();
        } else if (a.costFor(data) > data.getCurrentCE()) {
            g.fill(x, y, x + SLOT, y + SLOT, 0x80200040);
        }
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color, float glow) {
        int a = (int) (glow * 120);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, (a << 24) | (color & 0xFFFFFF));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xE0140C24);
    }

    private static String initials(String name) {
        String[] parts = name.replace(":", "").split("[\\s-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(p.charAt(0)));
        if (sb.length() == 1 && name.length() > 1) sb.append(Character.toLowerCase(name.charAt(1)));
        return sb.toString();
    }

    static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int darken(int c, float f) {
        return (c & 0xFF000000) | ((int) (((c >> 16) & 0xFF) * f) << 16) | ((int) (((c >> 8) & 0xFF) * f) << 8) | (int) ((c & 0xFF) * f);
    }

    static float luminance(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }
}
