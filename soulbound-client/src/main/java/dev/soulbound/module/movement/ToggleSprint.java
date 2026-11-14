package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.util.KeyState;

/** Keeps you sprinting whenever you walk forward. */
public final class ToggleSprint extends Module {
    private final BoolSetting omni = add(new BoolSetting("Any Direction", "Sprint sideways and backwards too (where the server allows)", false));
    private final BoolSetting inWater = add(new BoolSetting("Swim Sprint", "Keep sprinting in water", true));

    public ToggleSprint() {
        super("Toggle Sprint", "Always sprint while moving forward.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        boolean moving = omni.isOn() ? p.input.getMovementInput().lengthSquared() > 0 : mc.options.forwardKey.isPressed();
        boolean ok = moving && !p.isSneaking() && (inWater.isOn() || !p.isTouchingWater());
        mc.options.sprintKey.setPressed(ok || KeyState.physical(mc.options.sprintKey));
        if (ok && omni.isOn() && p.getHungerManager().getFoodLevel() > 6) p.setSprinting(true);
    }

    @Override
    protected void onDisable() { KeyState.release(mc.options.sprintKey); }
}
