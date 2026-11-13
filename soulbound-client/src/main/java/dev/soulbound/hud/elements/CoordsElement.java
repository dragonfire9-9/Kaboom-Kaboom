package dev.soulbound.hud.elements;

import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Locale;

/** Position, facing and the matching Nether/Overworld coordinates. */
public final class CoordsElement extends HudElement {
    private static final String[] DIRS = {"South", "+Z", "West", "-X", "North", "-Z", "East", "+X"};
    private String xyz = "", facing = "", other = "", otherLabel = "";

    public CoordsElement() { super("Coordinates", true, 0, 0.16); }

    @Override
    public boolean layout(boolean editing) {
        if (mc.player == null) return false;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        xyz = String.format(Locale.ROOT, "%.0f  %.0f  %.0f", x, y, z);
        int d = MathHelper.floor(mc.player.getYaw() * 4 / 360 + 0.5) & 3;
        facing = DIRS[d * 2] + "  " + DIRS[d * 2 + 1];
        boolean nether = mc.world != null && mc.world.getRegistryKey() == World.NETHER;
        otherLabel = nether ? "OVERWORLD" : "NETHER";
        other = nether ? String.format(Locale.ROOT, "%.0f  %.0f", x * 8, z * 8) : String.format(Locale.ROOT, "%.0f  %.0f", x / 8, z / 8);
        w = 14 + Math.max(Fonts.LABEL.width("XYZ", 1f) + 6 + Fonts.BODY_BOLD.width(xyz),
                Math.max(Fonts.LABEL.width("FACING", 1f) + 6 + Fonts.BODY.width(facing), Fonts.LABEL.width(otherLabel, 1f) + 6 + Fonts.BODY.width(other)));
        h = 40;
        return true;
    }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        card(ctx, x, y, w, h);
        row(ctx, "XYZ", xyz, x + 7, y + 8, true);
        row(ctx, "FACING", facing, x + 7, y + 20, false);
        row(ctx, otherLabel, other, x + 7, y + 32, false);
    }

    private void row(DrawContext ctx, String label, String value, float x, float cy, boolean bold) {
        Fonts.LABEL.drawTracked(ctx, label, x, cy - Fonts.LABEL.size / 2f, 1f, a(Theme.accent()));
        float vx = x + Fonts.LABEL.width(label, 1f) + 6;
        (bold ? Fonts.BODY_BOLD : Fonts.BODY).drawCentered(ctx, value, vx, cy, a(bold ? Theme.TEXT : Theme.TEXT_MUTED));
    }
}
