package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.DeathScreen;

/** Respawns for you a moment after you die. */
public final class AutoRespawn extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Wait this long before respawning", 0.5, 0, 5, 0.1, "s"));
    private final NumberSetting randomDelay = add(new NumberSetting("Random Delay", "Up to this much extra, so it isn't instant every time", 0.3, 0, 3, 0.1, "s"));
    private final dev.soulbound.setting.BoolSetting sayMessage = add(new dev.soulbound.setting.BoolSetting("Chat After", "Say something after respawning", false));
    private final dev.soulbound.setting.TextSetting message = add(new dev.soulbound.setting.TextSetting("Message", "What to say", "back!").visibleWhen(sayMessage::isOn));
    private long extra;
    private int sayIn = -1;
    private long diedAt = -1;

    public AutoRespawn() {
        super("Auto Respawn", "Respawns automatically when you die.", Category.UTIL);
    }

    @Override
    public void onTick() {
        if (sayIn >= 0 && --sayIn == 0 && mc.getNetworkHandler() != null && !message.get().isBlank()) mc.getNetworkHandler().sendChatMessage(message.get());
        if (!(mc.currentScreen instanceof DeathScreen) || mc.player == null) {
            diedAt = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (diedAt < 0) {
            diedAt = now;
            extra = randomDelay.get() > 0 ? java.util.concurrent.ThreadLocalRandom.current().nextLong((long) (randomDelay.get() * 1000) + 1) : 0;
        }
        if (now - diedAt >= delay.get() * 1000 + extra) {
            if (sayMessage.isOn()) sayIn = 40;
            mc.player.requestRespawn();
            mc.setScreen(null);
            diedAt = -1;
        }
    }
}
