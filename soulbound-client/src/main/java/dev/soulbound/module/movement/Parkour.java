package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.util.math.Box;

/** Jumps for you at the very edge of a block, for longer jumps. */
public final class Parkour extends Module {
    private final NumberSetting edge;
    private final BoolSetting onlySprinting, notSneaking;
    private int jumpTicks;

    public Parkour() {
        super("Parkour", "Jump automatically at the edge of blocks.", Category.MOVEMENT);
        edge = add(new NumberSetting("Edge Distance", "How close to the edge before jumping", 0.05, 0.0, 0.3, 0.01, "m"));
        onlySprinting = add(new BoolSetting("Only Sprinting", "Only while sprinting", false));
        notSneaking = add(new BoolSetting("Not Sneaking", "Never while sneaking", true));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        if (jumpTicks > 0 && --jumpTicks == 0) dev.soulbound.util.KeyState.release(mc.options.jumpKey);
        if (!p.isOnGround() || (notSneaking.isOn() && p.isSneaking()) || (onlySprinting.isOn() && !p.isSprinting())) return;
        var v = p.getVelocity();
        if (v.horizontalLengthSquared() < 1e-4) return;
        double e = edge.get();
        Box next = p.getBoundingBox().offset(v.x * (1 + e * 10), -0.5, v.z * (1 + e * 10)).withMaxY(p.getY() - 0.01);
        if (mc.world.isSpaceEmpty(p, next.contract(0.001, 0, 0.001))) {
            mc.options.jumpKey.setPressed(true);
            jumpTicks = 2;
        }
    }
}
