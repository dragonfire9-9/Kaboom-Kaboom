package dev.soulbound.module.visual;

import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.module.Category;
import dev.soulbound.module.HudModule;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/** A card showing whoever you last hit: head, name, health bar, distance and whether you're winning. */
public final class TargetHud extends Module implements HudModule {
    private final ModeSetting position;
    private final NumberSetting timeout, scale;
    private final BoolSetting showDistance, showWinning, showArmor;
    private LivingEntity target;
    private long lastHit;
    private float shownHealth = -1, ghostHealth = -1;

    public TargetHud() {
        super("Target HUD", "Show a card for the player you're fighting.", Category.VISUAL);
        position = add(new ModeSetting("Position", "Where the card sits", "Right Of Crosshair", "Right Of Crosshair", "Below Crosshair", "Top Left", "Top Right"));
        scale = add(new NumberSetting("Scale", "Card size", 1, 0.6, 1.6, 0.05, "x"));
        timeout = add(new NumberSetting("Timeout", "Hide after this long without a hit", 4, 1, 15, 0.5, "s"));
        showDistance = add(new BoolSetting("Distance", "Show the distance", true));
        showWinning = add(new BoolSetting("Winning", "Show whether you have more health", true));
        showArmor = add(new BoolSetting("Armor", "Show their armour points", true));
        dev.soulbound.event.Events.ATTACK.add(this::onAttack);
    }

    private void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            if (le != target) shownHealth = ghostHealth = -1;
            target = le;
            lastHit = System.currentTimeMillis();
        }
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        if (target == null || mc.player == null || mc.options.hudHidden) return;
        long since = System.currentTimeMillis() - lastHit;
        if (target.isRemoved() || since > timeout.get() * 1000 + 300) {
            if (target.isRemoved() || since > timeout.get() * 1000 + 300) target = null;
            return;
        }
        float alpha = since > timeout.get() * 1000 ? 1 - (since - timeout.getFloat() * 1000) / 300f : Math.min(1, since / 120f + 0.4f);
        float s = scale.getFloat(), w = 130, h = 40;
        float sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float x, y;
        switch (position.get()) {
            case "Below Crosshair" -> { x = sw / 2f - w * s / 2; y = sh / 2f + 24; }
            case "Top Left" -> { x = 6; y = 30; }
            case "Top Right" -> { x = sw - w * s - 6; y = 30; }
            default -> { x = sw / 2f + 18; y = sh / 2f - h * s / 2; }
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(s, s);
        float max = target.getMaxHealth() + target.getAbsorptionAmount();
        float hp = target.getHealth() + target.getAbsorptionAmount();
        if (shownHealth < 0) shownHealth = ghostHealth = hp;
        shownHealth += (hp - shownHealth) * 0.3f;
        ghostHealth = ghostHealth > shownHealth ? ghostHealth - Math.max(0.05f, (ghostHealth - shownHealth) * 0.04f) : shownHealth;
        int bg = Theme.alpha(0xF0101220, alpha);
        Render2D.glow(ctx, 0, 0, w, h, 8, 8, Theme.alpha(0xFF000000, 0.5f * alpha));
        Render2D.roundRect(ctx, 0, 0, w, h, 8, bg);
        Render2D.outline(ctx, 0, 0, w, h, 8, 0, Theme.alpha(Theme.BORDER_HI, alpha));
        float hurt = target.hurtTime / 10f;
        if (target instanceof AbstractClientPlayerEntity pl) {
            PlayerSkinDrawer.draw(ctx, pl.getSkin(), 6, 6, 28, Theme.alpha(Theme.lerp(0xFFFFFFFF, 0xFFFF8A9A, hurt), alpha));
        } else {
            Render2D.roundRect(ctx, 6, 6, 28, 28, 6, Theme.alpha(Theme.SURFACE_SEL, alpha));
            Render2D.icon(ctx, "icon_target", 13, 13, 14, Theme.alpha(Theme.TEXT_MUTED, alpha));
        }
        String name = target.getName().getString();
        Fonts.BODY_BOLD.draw(ctx, Fonts.BODY_BOLD.fit(name, 70), 40, 7, Theme.alpha(Theme.TEXT, alpha));
        String right = String.format(Locale.ROOT, "%.1f", hp / 2) + "❤";
        Fonts.BODY_BOLD.draw(ctx, right, w - 7 - Fonts.BODY_BOLD.width(right), 7, Theme.alpha(0xFFFF6B7D, alpha));
        float bx = 40, by = 20, bw = w - 47, bh = 5;
        Render2D.roundRect(ctx, bx, by, bw, bh, 2.5f, Theme.alpha(Theme.TRACK_OFF, alpha));
        float gf = Math.max(0, Math.min(1, ghostHealth / max)), f = Math.max(0, Math.min(1, shownHealth / max));
        Render2D.roundRect(ctx, bx, by, bw * gf, bh, 2.5f, Theme.alpha(0xFFFFFFFF, 0.5f * alpha));
        int hc = Theme.lerp(0xFFFF4D5E, 0xFF4CFF7A, f);
        Render2D.roundRect(ctx, bx, by, Math.max(bh, bw * f), bh, 2.5f, Theme.alpha(hc, alpha));
        StringBuilder info = new StringBuilder();
        if (showDistance.isOn()) info.append(String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(target)));
        if (showArmor.isOn()) info.append(info.isEmpty() ? "" : "  ").append(target.getArmor()).append(" armor");
        Fonts.LABEL.draw(ctx, info.toString(), bx, 30, Theme.alpha(Theme.TEXT_MUTED, alpha));
        if (showWinning.isOn()) {
            float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            String verdict = mine > hp + 1 ? "WINNING" : mine < hp - 1 ? "LOSING" : "EVEN";
            int vc = mine > hp + 1 ? 0xFF4CFF7A : mine < hp - 1 ? 0xFFFF4D5E : 0xFFFFD84D;
            Fonts.LABEL.draw(ctx, verdict, w - 7 - Fonts.LABEL.width(verdict), 30, Theme.alpha(vc, alpha));
        }
        m.popMatrix();
    }
}
