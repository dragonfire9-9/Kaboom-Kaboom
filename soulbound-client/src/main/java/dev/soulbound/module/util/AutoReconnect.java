package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Reconnects to the last server after you get disconnected. */
public final class AutoReconnect extends Module {
    private final NumberSetting delay, attempts;
    private final BoolSetting backoff;
    private ServerInfo last;
    private long disconnectedAt = -1;
    private int tries;

    public AutoReconnect() {
        super("Auto Reconnect", "Rejoin the server after a disconnect.", Category.UTIL);
        delay = add(new NumberSetting("Delay", "Wait before reconnecting", 5, 1, 60, 1, "s"));
        attempts = add(new NumberSetting("Attempts", "Give up after this many tries (0 = never)", 5, 0, 50, 1));
        backoff = add(new BoolSetting("Back Off", "Wait a little longer after each failed try", true));
    }

    @Override
    public void onTick() {
        if (mc.getCurrentServerEntry() != null && mc.world != null) {
            last = mc.getCurrentServerEntry();
            tries = 0;
        }
    }

    /** Called every client tick, even without a world (see SoulBound). */
    public void screenTick() {
        if (!isEnabled() || last == null || !(mc.currentScreen instanceof DisconnectedScreen)) {
            disconnectedAt = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (disconnectedAt < 0) disconnectedAt = now;
        if (attempts.getInt() > 0 && tries >= attempts.getInt()) return;
        double wait = delay.get() * (backoff.isOn() ? 1 + tries * 0.5 : 1);
        if (now - disconnectedAt < wait * 1000) return;
        tries++;
        disconnectedAt = -1;
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc, ServerAddress.parse(last.address), last, false, null);
    }

    public String countdown() {
        if (disconnectedAt < 0 || last == null) return null;
        double wait = delay.get() * (backoff.isOn() ? 1 + tries * 0.5 : 1);
        return String.format(java.util.Locale.ROOT, "Reconnecting in %.0fs", Math.max(0, wait - (System.currentTimeMillis() - disconnectedAt) / 1000.0));
    }
}
