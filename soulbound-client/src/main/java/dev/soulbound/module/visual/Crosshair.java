package dev.soulbound.module.visual;

import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.Category;
import dev.soulbound.module.HudModule;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

/** A custom crosshair: cross, dot, circle or a T, with gap, size, outline and colour, plus a hit marker. */
public final class Crosshair extends Module implements HudModule {
    public static Crosshair INSTANCE;
    private final ModeSetting style, color;
    private final NumberSetting size, gap, thickness, opacity;
    private final BoolSetting dot, outline, dynamic, targetColor, hitMarker, thirdPerson;
    private long lastHit;

    public Crosshair() {
        super("Crosshair", "Replace the crosshair with your own.", Category.VISUAL);
        INSTANCE = this;
        add(new HeaderSetting("SHAPE"));
        style = add(new ModeSetting("Style", "Crosshair shape", "Cross", "Cross", "T", "Circle", "Dot", "Chevron"));
        size = add(new NumberSetting("Size", "Arm length", 4, 1, 12, 0.5, "px"));
        gap = add(new NumberSetting("Gap", "Space in the middle", 2, 0, 8, 0.5, "px"));
        thickness = add(new NumberSetting("Thickness", "Line width", 1, 0.5, 4, 0.5, "px"));
        dot = add(new BoolSetting("Centre Dot", "Draw a dot in the middle", false));
        dynamic = add(new BoolSetting("Dynamic Gap", "Open up while moving or charging an attack", true));
        add(new HeaderSetting("LOOK"));
        color = add(new ModeSetting("Color", "Crosshair colour", "White", "White", "Theme", "Green", "Cyan", "Red", "Yellow"));
        opacity = add(new NumberSetting("Opacity", "How solid it is", 100, 20, 100, 5, "%"));
        outline = add(new BoolSetting("Outline", "Thin dark outline so it shows on bright blocks", true));
        targetColor = add(new BoolSetting("Target Color", "Turn red over an entity", true));
        hitMarker = add(new BoolSetting("Hit Marker", "Flash an X when you land a hit", true));
        thirdPerson = add(new BoolSetting("Third Person", "Also show it in third person", false));
        dev.soulbound.event.Events.ATTACK.add(e -> lastHit = System.currentTimeMillis());
    }

    public static boolean replacing() { return INSTANCE != null && INSTANCE.isEnabled(); }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.options.hudHidden) return;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON && !thirdPerson.isOn()) return;
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        boolean onEntity = mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity;
        int c = switch (color.get()) {
            case "Theme" -> Theme.accent();
            case "Green" -> 0xFF4CFF7A;
            case "Cyan" -> 0xFF4FE3FF;
            case "Red" -> 0xFFFF4D5E;
            case "Yellow" -> 0xFFFFD84D;
            default -> 0xFFFFFFFF;
        };
        if (targetColor.isOn() && onEntity) c = 0xFFFF4D5E;
        c = Theme.alpha(c, opacity.getFloat() / 100f);
        float g = gap.getFloat(), s = size.getFloat(), t = thickness.getFloat();
        if (dynamic.isOn()) {
            double speed = mc.player.getVelocity().horizontalLength();
            g += (float) Math.min(4, speed * 12) + (1 - mc.player.getAttackCooldownProgress(tickDelta)) * 2;
        }
        int o = 0xC0000000;
        switch (style.get()) {
            case "Dot" -> {
                if (outline.isOn()) Render2D.circle(ctx, cx, cy, t + 1.5f, o);
                Render2D.circle(ctx, cx, cy, t + 0.8f, c);
            }
            case "Circle" -> {
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI * 2 / 32;
                    float px = cx + (float) Math.cos(a) * (g + s / 2), py = cy + (float) Math.sin(a) * (g + s / 2);
                    if (outline.isOn()) Render2D.circle(ctx, px, py, t * 0.9f + 0.6f, o);
                    Render2D.circle(ctx, px, py, t * 0.6f + 0.2f, c);
                }
            }
            case "Chevron" -> {
                for (int i = 0; i <= (int) s; i++) {
                    if (outline.isOn()) Render2D.rect(ctx, cx - i - t / 2 - 0.5f, cy + g + i - 0.5f, t + 1, t + 1, o);
                    Render2D.rect(ctx, cx - i - t / 2, cy + g + i, t, t, c);
                    if (outline.isOn()) Render2D.rect(ctx, cx + i - t / 2 - 0.5f, cy + g + i - 0.5f, t + 1, t + 1, o);
                    Render2D.rect(ctx, cx + i - t / 2, cy + g + i, t, t, c);
                }
            }
            default -> {
                boolean top = !style.is("T");
                arm(ctx, cx - g - s, cy - t / 2, s, t, c, o);
                arm(ctx, cx + g, cy - t / 2, s, t, c, o);
                if (top) arm(ctx, cx - t / 2, cy - g - s, t, s, c, o);
                arm(ctx, cx - t / 2, cy + g, t, s, c, o);
            }
        }
        if (dot.isOn() && !style.is("Dot")) Render2D.rect(ctx, cx - t / 2, cy - t / 2, t, t, c);
        long since = System.currentTimeMillis() - lastHit;
        if (hitMarker.isOn() && since < 260) {
            float a = 1 - since / 260f;
            int hc = Theme.alpha(0xFFFFFFFF, a);
            for (int i = 0; i < 4; i++) {
                float d = g + 3 + i;
                Render2D.rect(ctx, cx - d - 0.5f, cy - d - 0.5f, 1, 1, hc);
                Render2D.rect(ctx, cx + d - 0.5f, cy - d - 0.5f, 1, 1, hc);
                Render2D.rect(ctx, cx - d - 0.5f, cy + d - 0.5f, 1, 1, hc);
                Render2D.rect(ctx, cx + d - 0.5f, cy + d - 0.5f, 1, 1, hc);
            }
        }
    }

    private void arm(DrawContext ctx, float x, float y, float w, float h, int c, int o) {
        if (outline.isOn()) Render2D.rect(ctx, x - 0.5f, y - 0.5f, w + 1, h + 1, o);
        Render2D.rect(ctx, x, y, w, h, c);
    }
}
