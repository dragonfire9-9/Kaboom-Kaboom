package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.util.KeyState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/** Resets your sprint after each hit so the next one deals full knockback (W-tap, S-tap or sprint-tap). */
public final class SprintReset extends Module {
    private final ModeSetting mode;
    private final NumberSetting length, delay, chance;
    private final BoolSetting onlyLiving;
    private int startIn = -1, ticks;

    public SprintReset() {
        super("Sprint Reset", "Auto W-tap after hits for maximum knockback.", Category.COMBAT);
        mode = add(new ModeSetting("Mode", "How to reset sprint", "W-Tap", "W-Tap", "S-Tap", "Sprint Tap"));
        length = add(new NumberSetting("Length", "Ticks to hold the reset", 2, 1, 6, 1, "t"));
        delay = add(new NumberSetting("Delay", "Ticks after the hit before resetting", 0, 0, 4, 1, "t"));
        chance = add(new NumberSetting("Chance", "Chance to reset after a hit", 100, 0, 100, 1, "%"));
        onlyLiving = add(new BoolSetting("Only Living", "Only after hitting mobs and players", true));
        dev.soulbound.event.Events.ATTACK.add(this::onAttack);
    }

    private void onAttack(Entity target) {
        if (!isEnabled() || mc.player == null || !mc.player.isSprinting()) return;
        if (onlyLiving.isOn() && !(target instanceof LivingEntity)) return;
        if (ThreadLocalRandom.current().nextDouble(100) >= chance.get()) return;
        startIn = delay.getInt();
    }

    @Override
    public void onTick() {
        if (startIn >= 0 && startIn-- == 0) ticks = length.getInt();
        if (ticks <= 0) return;
        ticks--;
        var o = mc.options;
        switch (mode.get()) {
            case "S-Tap" -> {
                o.forwardKey.setPressed(false);
                o.backKey.setPressed(true);
            }
            case "Sprint Tap" -> {
                o.sprintKey.setPressed(false);
                mc.player.setSprinting(false);
            }
            default -> o.forwardKey.setPressed(false);
        }
        if (ticks == 0) {
            KeyState.release(o.forwardKey);
            KeyState.release(o.backKey);
            KeyState.release(o.sprintKey);
        }
    }
}
