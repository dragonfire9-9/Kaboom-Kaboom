package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Moves, scales and tilts the items in your hands (view model). */
public final class HandView extends Module {
    public static HandView INSTANCE;
    private final NumberSetting x, y, z, scale, rotX, rotY, rotZ, offX, offY, offZ, offScale;
    private final BoolSetting separateOffhand;

    public HandView() {
        super("Hand View", "Move, scale and tilt your held items.", Category.VISUAL);
        INSTANCE = this;
        add(new HeaderSetting("MAIN HAND"));
        x = add(new NumberSetting("X", "Left/right", 0, -1, 1, 0.01));
        y = add(new NumberSetting("Y", "Up/down", 0, -1, 1, 0.01));
        z = add(new NumberSetting("Z", "Closer/further", 0, -1, 1, 0.01));
        scale = add(new NumberSetting("Scale", "Item size", 1, 0.3, 1.5, 0.05, "x"));
        rotX = add(new NumberSetting("Tilt X", "Pitch the item", 0, -90, 90, 1, "°"));
        rotY = add(new NumberSetting("Tilt Y", "Yaw the item", 0, -90, 90, 1, "°"));
        rotZ = add(new NumberSetting("Tilt Z", "Roll the item", 0, -90, 90, 1, "°"));
        add(new HeaderSetting("OFFHAND"));
        separateOffhand = add(new BoolSetting("Separate Offhand", "Give the offhand its own position", false));
        offX = add(new NumberSetting("Offhand X", "Left/right", 0, -1, 1, 0.01).visibleWhen(separateOffhand::isOn));
        offY = add(new NumberSetting("Offhand Y", "Up/down", 0, -1, 1, 0.01).visibleWhen(separateOffhand::isOn));
        offZ = add(new NumberSetting("Offhand Z", "Closer/further", 0, -1, 1, 0.01).visibleWhen(separateOffhand::isOn));
        offScale = add(new NumberSetting("Offhand Scale", "Item size", 1, 0.3, 1.5, 0.05, "x").visibleWhen(separateOffhand::isOn));
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }

    public void apply(MatrixStack m, boolean main) {
        boolean own = !main && separateOffhand.isOn();
        // mirror X for the offhand so "outward" is the same for both hands
        float dx = (own ? offX.getFloat() : x.getFloat()) * (main ? 1 : -1);
        m.translate(dx, (own ? offY.getFloat() : y.getFloat()), (own ? offZ.getFloat() : z.getFloat()));
        if (rotX.get() != 0) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX.getFloat()));
        if (rotY.get() != 0) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY.getFloat() * (main ? 1 : -1)));
        if (rotZ.get() != 0) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ.getFloat() * (main ? 1 : -1)));
        float s = (own ? offScale.getFloat() : scale.getFloat());
        m.scale(s, s, s);
    }
}
