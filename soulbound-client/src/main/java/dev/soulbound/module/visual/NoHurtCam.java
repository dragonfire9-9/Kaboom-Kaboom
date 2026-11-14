package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.NumberSetting;

/** Removes or softens the camera shake when you take damage. */
public final class NoHurtCam extends Module {
    public static NoHurtCam INSTANCE;
    public final NumberSetting strength = add(new NumberSetting("Strength", "How much shake is left (0 = none)", 0, 0, 100, 5, "%"));

    public NoHurtCam() {
        super("No Hurt Cam", "Stops the screen shaking when you get hit.", Category.VISUAL);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }
}
