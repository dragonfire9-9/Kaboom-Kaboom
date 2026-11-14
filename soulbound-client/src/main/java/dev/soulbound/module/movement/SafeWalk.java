package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.util.KeyState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Stops you walking off edges: either clamps you like sneaking, or crouches for you at the edge (Eagle). */
public final class SafeWalk extends Module {
    public static SafeWalk INSTANCE;
    private final ModeSetting mode = add(new ModeSetting("Mode", "Safe Walk clamps you to the edge, Eagle crouches at it", "Safe Walk", "Safe Walk", "Eagle"));
    private final NumberSetting margin = add(new NumberSetting("Edge Margin", "How early Eagle crouches", 0.15, 0, 0.4, 0.05, "m").visibleWhen(() -> mode.is("Eagle")));
    private boolean crouching;

    public SafeWalk() {
        super("Safe Walk", "Never fall off the edge of a block.", Category.MOVEMENT);
        INSTANCE = this;
    }

    /** Called from the PlayerEntity#clipAtLedge mixin. */
    public static boolean clip() {
        if (INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.mode.is("Safe Walk")) return true;
        Scaffold sc = dev.soulbound.SoulBound.modules().get(Scaffold.class);
        return sc != null && sc.safeWalk();
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.world == null || !mode.is("Eagle")) {
            if (crouching) { KeyState.release(mc.options.sneakKey); crouching = false; }
            return;
        }
        boolean edge = false;
        if (p.isOnGround() && !p.getAbilities().flying) {
            Vec3d v = p.getVelocity();
            double m = margin.get();
            Box feet = p.getBoundingBox().offset(v.x * 2 + Math.signum(v.x) * m, -0.6, v.z * 2 + Math.signum(v.z) * m).withMaxY(p.getY() - 0.01);
            edge = mc.world.isSpaceEmpty(p, feet.contract(0.05, 0, 0.05));
        }
        if (edge || crouching) mc.options.sneakKey.setPressed(edge || KeyState.physical(mc.options.sneakKey));
        crouching = edge;
    }

    @Override
    protected void onDisable() {
        if (crouching) KeyState.release(mc.options.sneakKey);
        crouching = false;
    }
}
