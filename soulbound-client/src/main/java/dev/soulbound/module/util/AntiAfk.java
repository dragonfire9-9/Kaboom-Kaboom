package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/** Keeps you from being kicked for idling with small, random actions. */
public final class AntiAfk extends Module {
    private final NumberSetting interval = add(new NumberSetting("Interval", "Average time between actions", 20, 5, 120, 5, "s"));
    private final BoolSetting jump = add(new BoolSetting("Jump", "Hop now and then", true));
    private final BoolSetting look = add(new BoolSetting("Look Around", "Turn your head a little", true));
    private final BoolSetting swing = add(new BoolSetting("Swing", "Swing your arm", false));
    private long next;
    private int jumpTicks;

    public AntiAfk() {
        super("Anti AFK", "Stops servers kicking you for being idle.", Category.UTIL);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (jumpTicks > 0 && --jumpTicks == 0) mc.options.jumpKey.setPressed(false);
        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (next == 0) next = now + (long) (interval.get() * 1000);
        if (now < next) return;
        next = now + (long) (interval.get() * 1000 * r.nextDouble(0.6, 1.4));
        if (jump.isOn() && mc.player.isOnGround()) { mc.options.jumpKey.setPressed(true); jumpTicks = 2; }
        if (look.isOn()) mc.player.changeLookDirection(r.nextDouble(-60, 60), r.nextDouble(-15, 15));
        if (swing.isOn()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    protected void onDisable() {
        if (jumpTicks > 0) mc.options.jumpKey.setPressed(false);
        jumpTicks = 0;
        next = 0;
    }
}
