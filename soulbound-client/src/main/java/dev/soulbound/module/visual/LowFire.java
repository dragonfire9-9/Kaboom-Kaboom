package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;

/** Lowers or hides the fire overlay so you can see while burning. */
public final class LowFire extends Module {
    public static LowFire INSTANCE;
    public final ModeSetting mode = add(new ModeSetting("Mode", "Lower the flames or hide them", "Lower", "Lower", "Hide"));
    public final NumberSetting height = add(new NumberSetting("Offset", "How far down to push the flames", 0.3, 0.05, 0.6, 0.05).visibleWhen(() -> mode.is("Lower")));

    public LowFire() {
        super("Low Fire", "Moves the fire overlay out of your view.", Category.VISUAL);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }
}
