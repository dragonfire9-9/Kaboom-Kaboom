package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.DeathScreen;

/** Respawns for you a moment after you die. */
public final class AutoRespawn extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Wait this long before respawning", 0.5, 0, 5, 0.1, "s"));
    private long diedAt = -1;

    public AutoRespawn() {
        super("Auto Respawn", "Respawns automatically when you die.", Category.UTIL);
    }

    @Override
    public void onTick() {
        if (!(mc.currentScreen instanceof DeathScreen) || mc.player == null) {
            diedAt = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (diedAt < 0) diedAt = now;
        if (now - diedAt >= delay.get() * 1000) {
            mc.player.requestRespawn();
            mc.setScreen(null);
            diedAt = -1;
        }
    }
}
