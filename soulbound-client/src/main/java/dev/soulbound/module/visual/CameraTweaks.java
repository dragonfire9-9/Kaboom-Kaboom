package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;

/** Third-person camera: set the distance and let it pass through walls. */
public final class CameraTweaks extends Module {
    public static CameraTweaks INSTANCE;
    private final NumberSetting distance;
    private final BoolSetting noClip;

    public CameraTweaks() {
        super("Camera Tweaks", "Third-person distance and camera clipping.", Category.VISUAL);
        INSTANCE = this;
        distance = add(new NumberSetting("Distance", "Third-person camera distance (vanilla is 4)", 4, 1, 20, 0.5, "m"));
        noClip = add(new BoolSetting("No Clip", "Camera goes through walls instead of bumping in", true));
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }

    public float distance() { return distance.getFloat(); }

    public boolean noClip() { return noClip.isOn(); }
}
