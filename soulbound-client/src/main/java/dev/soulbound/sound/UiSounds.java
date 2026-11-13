package dev.soulbound.sound;

import dev.soulbound.SoulBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Interface sound effects. The events are deliberately not added to the sound registry:
 * the sound manager resolves them straight from assets/soulbound/sounds.json, which keeps
 * the client compatible with servers that don't have the mod.
 */
public final class UiSounds {
    public static final SoundEvent HOVER = of("ui.hover");
    public static final SoundEvent CLICK = of("ui.click");
    public static final SoundEvent TOGGLE_ON = of("ui.toggle_on");
    public static final SoundEvent TOGGLE_OFF = of("ui.toggle_off");
    public static final SoundEvent OPEN = of("ui.open");
    public static final SoundEvent CLOSE = of("ui.close");
    public static final SoundEvent SLIDE = of("ui.slide");
    public static final SoundEvent TYPE = of("ui.type");

    private static long lastHover, lastSlide;

    private UiSounds() {}

    private static SoundEvent of(String path) {
        return SoundEvent.of(Identifier.of(SoulBound.MOD_ID, path));
    }

    public static void play(SoundEvent event) { play(event, 1.0f, 1.0f); }

    public static void play(SoundEvent event, float pitch, float volume) {
        var audio = SoulBound.settings().audio;
        if (!audio.sounds.isOn()) return;
        float v = (float) (volume * audio.volume.get());
        if (v <= 0.001f) return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(event, pitch, v));
    }

    /** Hover tick, rate-limited so sweeping the mouse over a list doesn't machine-gun. */
    public static void hover() {
        if (!SoulBound.settings().audio.hoverSounds.isOn()) return;
        long now = System.currentTimeMillis();
        if (now - lastHover < 45) return;
        lastHover = now;
        play(HOVER, 0.95f + (float) Math.random() * 0.1f, 0.55f);
    }

    public static void slide(float progress) {
        long now = System.currentTimeMillis();
        if (now - lastSlide < 40) return;
        lastSlide = now;
        play(SLIDE, 0.8f + progress * 0.6f, 0.5f);
    }
}
