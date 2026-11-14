package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;

/** Removes screen overlays that get in the way. */
public final class NoOverlays extends Module {
    public enum Kind { VIGNETTE, PORTAL, SPYGLASS, PUMPKIN, WATER, WALL }

    public static NoOverlays INSTANCE;
    private final BoolSetting vignette, portal, spyglass, pumpkin, water, wall;

    public NoOverlays() {
        super("No Overlays", "Hide pumpkin, portal, water and other overlays.", Category.VISUAL);
        INSTANCE = this;
        pumpkin = add(new BoolSetting("Pumpkin & Snow", "Hide the carved pumpkin and powder snow overlays", true));
        portal = add(new BoolSetting("Portal", "Hide the nether portal swirl", true));
        water = add(new BoolSetting("Underwater", "Hide the underwater tint", false));
        wall = add(new BoolSetting("In Wall", "Hide the block texture when your head is inside a block", true));
        vignette = add(new BoolSetting("Vignette", "Hide the darkened screen edges", false));
        spyglass = add(new BoolSetting("Spyglass", "Hide the spyglass scope (keeps the zoom)", false));
    }

    public static boolean hide(Kind k) {
        NoOverlays n = INSTANCE;
        if (n == null || !n.isEnabled()) return false;
        return switch (k) {
            case VIGNETTE -> n.vignette.isOn();
            case PORTAL -> n.portal.isOn();
            case SPYGLASS -> n.spyglass.isOn();
            case PUMPKIN -> n.pumpkin.isOn();
            case WATER -> n.water.isOn();
            case WALL -> n.wall.isOn();
        };
    }
}
