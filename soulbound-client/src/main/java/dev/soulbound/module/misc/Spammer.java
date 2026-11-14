package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.setting.TextSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Sends your messages on a timer (up to three, in order or shuffled). Mind the server rules. */
public final class Spammer extends Module {
    private final TextSetting first, second, third;
    private final NumberSetting interval, jitter;
    private final ModeSetting order;
    private final BoolSetting antiFilter;
    private long next;
    private int index;

    public Spammer() {
        super("Spammer", "Send chat messages on a timer.", Category.MISC);
        first = add(new TextSetting("Message 1", "First message", "Powered by SoulBound", 100));
        second = add(new TextSetting("Message 2", "Second message (leave empty to skip)", "", 100));
        third = add(new TextSetting("Message 3", "Third message (leave empty to skip)", "", 100));
        order = add(new ModeSetting("Order", "Send in order or pick at random", "In Order", "In Order", "Random"));
        interval = add(new NumberSetting("Interval", "Time between messages", 30, 3, 600, 1, "s"));
        jitter = add(new NumberSetting("Jitter", "Random extra wait", 3, 0, 60, 1, "s"));
        antiFilter = add(new BoolSetting("Anti Repeat", "Add a short random tag so the server doesn't block repeats", false));
    }

    @Override
    protected void onEnable() { next = 0; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        long now = System.currentTimeMillis();
        if (now < next) return;
        List<String> msgs = new ArrayList<>();
        for (TextSetting t : new TextSetting[]{first, second, third}) if (!t.get().isBlank()) msgs.add(t.get());
        if (msgs.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String m = order.is("Random") ? msgs.get(r.nextInt(msgs.size())) : msgs.get(index++ % msgs.size());
        if (antiFilter.isOn()) m += " [" + Integer.toString(r.nextInt(46656), 36) + "]";
        mc.getNetworkHandler().sendChatMessage(m);
        next = now + (long) (interval.get() * 1000) + (jitter.get() > 0 ? r.nextLong((long) (jitter.get() * 1000) + 1) : 0);
    }
}
