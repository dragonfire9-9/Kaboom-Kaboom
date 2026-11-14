package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.NumberSetting;

/** Removes or softens the camera shake when you take damage. */
public final class NoHurtCam extends Module {
    public static NoHurtCam INSTANCE;
    public final NumberSetting strength = add(new NumberSetting("Strength", "How much shake is left (0 = none)", 0, 0, 100, 5, "%"));
    private final dev.soulbound.setting.BoolSetting noBob = add(new dev.soulbound.setting.BoolSetting("No View Bobbing", "Turn off the camera bob while walking", false));
    private Boolean savedBob;

    @Override
    public void onTick() {
        if (noBob.isOn()) {
            if (savedBob == null) savedBob = mc.options.getBobView().getValue();
            mc.options.getBobView().setValue(false);
        } else if (savedBob != null) {
            mc.options.getBobView().setValue(savedBob);
            savedBob = null;
        }
    }

    @Override
    protected void onDisable() {
        if (savedBob != null) mc.options.getBobView().setValue(savedBob);
        savedBob = null;
    }

    public NoHurtCam() {
        super("No Hurt Cam", "Stops the screen shaking when you get hit.", Category.VISUAL);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }
}
