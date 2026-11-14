package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.util.KeyState;

/** Walks forward for you until you turn it off or press back. */
public final class AutoWalk extends Module {
    private final BoolSetting stopOnBack = add(new BoolSetting("Stop On Back", "Turn off when you press the back key", true));
    private final BoolSetting autoJump = add(new BoolSetting("Jump Obstacles", "Hop over blocks in the way", true));
    private final BoolSetting sprintToo = add(new BoolSetting("Sprint", "Sprint while walking", false));
    private final BoolSetting stopAtEdges = add(new BoolSetting("Stop At Drops", "Turn off before walking off a big drop", true));
    private boolean jumping;

    public AutoWalk() {
        super("Auto Walk", "Keeps walking forward hands-free.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        if (stopOnBack.isOn() && KeyState.physical(mc.options.backKey)) {
            setEnabled(false);
            return;
        }
        if (stopAtEdges.isOn() && p.isOnGround()) {
            var ahead = p.getBoundingBox().offset(p.getRotationVector().x * 0.8, -3.1, p.getRotationVector().z * 0.8).withMaxY(p.getY() - 0.01);
            if (mc.world.isSpaceEmpty(p, ahead)) { setEnabled(false); return; }
        }
        mc.options.forwardKey.setPressed(true);
        if (sprintToo.isOn()) mc.options.sprintKey.setPressed(true);
        boolean jump = autoJump.isOn() && p.horizontalCollision && p.isOnGround();
        if (jump || jumping) mc.options.jumpKey.setPressed(jump || KeyState.physical(mc.options.jumpKey));
        jumping = jump;
    }

    @Override
    protected void onDisable() {
        KeyState.release(mc.options.forwardKey);
        KeyState.release(mc.options.sprintKey);
        KeyState.release(mc.options.jumpKey);
        jumping = false;
    }
}
