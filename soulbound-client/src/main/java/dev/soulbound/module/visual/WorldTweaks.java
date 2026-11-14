package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;

/** Client-side time and weather: lock the sky to your favourite time, stop rain, or make it storm. */
public final class WorldTweaks extends Module {
    public static WorldTweaks INSTANCE;
    private final BoolSetting changeTime, cycle;
    private final NumberSetting time, cycleSpeed;
    private final ModeSetting weather;
    private long started = System.currentTimeMillis();

    public WorldTweaks() {
        super("World Tweaks", "Change the time of day and weather, just for you.", Category.VISUAL);
        INSTANCE = this;
        add(new HeaderSetting("TIME"));
        changeTime = add(new BoolSetting("Change Time", "Override the time of day", true));
        time = add(new NumberSetting("Time", "Time of day (0 dawn, 6000 noon, 13000 dusk, 18000 midnight)", 6000, 0, 23999, 250).visibleWhen(changeTime::isOn));
        cycle = add(new BoolSetting("Cycle", "Let the overridden time keep moving", false).visibleWhen(changeTime::isOn));
        cycleSpeed = add(new NumberSetting("Cycle Speed", "How fast it moves compared to normal", 1, 0.1, 20, 0.1, "x").visibleWhen(() -> changeTime.isOn() && cycle.isOn()));
        add(new HeaderSetting("WEATHER"));
        weather = add(new ModeSetting("Weather", "Weather you see", "Clear", "Server", "Clear", "Rain", "Thunder"));
    }

    @Override
    protected void onEnable() { started = System.currentTimeMillis(); }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled(); }

    public Long time() {
        if (!changeTime.isOn()) return null;
        long t = (long) time.getInt();
        if (cycle.isOn()) t += (long) ((System.currentTimeMillis() - started) / 50.0 * cycleSpeed.get());
        return t % 24000;
    }

    public Float rain() {
        return switch (weather.get()) {
            case "Clear" -> 0f;
            case "Rain", "Thunder" -> 1f;
            default -> null;
        };
    }

    public Float thunder() {
        return switch (weather.get()) {
            case "Clear", "Rain" -> 0f;
            case "Thunder" -> 1f;
            default -> null;
        };
    }
}
