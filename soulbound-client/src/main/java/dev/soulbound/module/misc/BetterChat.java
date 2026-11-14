package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Timestamps, anti-spam and a much longer chat history. */
public final class BetterChat extends Module {
    public static BetterChat INSTANCE;
    private final BoolSetting timestamps, antiSpam;
    private final ModeSetting timeFormat;
    private final NumberSetting history, spamWindow;
    private String lastText;
    private long lastAt;

    public BetterChat() {
        super("Better Chat", "Timestamps, anti-spam and longer history.", Category.MISC);
        INSTANCE = this;
        timestamps = add(new BoolSetting("Timestamps", "Show the time before each message", true));
        timeFormat = add(new ModeSetting("Time Format", "Clock style", "24h", "24h", "12h", "With Seconds").visibleWhen(timestamps::isOn));
        antiSpam = add(new BoolSetting("Anti Spam", "Hide a message identical to the one just before it", true));
        spamWindow = add(new NumberSetting("Spam Window", "Only within this long of the first", 10, 1, 120, 1, "s").visibleWhen(antiSpam::isOn));
        history = add(new NumberSetting("History", "Messages kept in chat (vanilla 100)", 1000, 100, 5000, 100));
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }

    public boolean suppress(Text message) {
        if (!antiSpam.isOn()) return false;
        String s = message.getString();
        long now = System.currentTimeMillis();
        boolean dup = s.equals(lastText) && now - lastAt < spamWindow.get() * 1000;
        if (!dup) {
            lastText = s;
            lastAt = now;
        }
        return dup;
    }

    public Text decorate(Text message) {
        if (!timestamps.isOn()) return message;
        String pattern = switch (timeFormat.get()) {
            case "12h" -> "h:mm a";
            case "With Seconds" -> "HH:mm:ss";
            default -> "HH:mm";
        };
        MutableText stamp = Text.literal("[" + LocalTime.now().format(DateTimeFormatter.ofPattern(pattern)) + "] ").formatted(Formatting.DARK_GRAY);
        return stamp.append(message);
    }

    public int history() { return history.getInt(); }
}
