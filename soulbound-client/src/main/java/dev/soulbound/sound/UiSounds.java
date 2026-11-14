package dev.soulbound.sound;

import dev.soulbound.SoulBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Interface sounds. Each one is a physically modelled recording of a small object (a key
 * switch, a latching toggle, a sliding panel, a struck glass) rendered by
 * tools/gen_ui_sounds.py in three slightly different takes; Minecraft picks a take at random
 * and we add a touch of pitch drift, so repeated clicks never sound mechanical.
 *
 * <p>The events are not added to the sound registry: the sound manager resolves them straight
 * from assets/soulbound/sounds.json, so the client still works on servers without the mod.
 * Callers pass pitch and volume as multipliers around 1.0.
 */
public final class UiSounds {
    /** A sound and the base pitch/volume that make it sit well in the interface. */
    public record Sfx(SoundEvent event, float pitch, float volume, float pitchJitter) {}

    public static final Sfx HOVER = of("hover", 1.0f, 0.22f, 0.04f);
    public static final Sfx CLICK = of("click", 1.0f, 0.5f, 0.025f);
    public static final Sfx TOGGLE_ON = of("toggle_on", 1.0f, 0.55f, 0.02f);
    public static final Sfx TOGGLE_OFF = of("toggle_off", 1.0f, 0.5f, 0.02f);
    public static final Sfx OPEN = of("open", 1.0f, 0.45f, 0.03f);
    public static final Sfx CLOSE = of("close", 1.0f, 0.42f, 0.03f);
    public static final Sfx SLIDE = of("slide", 1.0f, 0.3f, 0.0f);
    public static final Sfx TYPE = of("type", 1.0f, 0.3f, 0.05f);
    public static final Sfx SHINE = of("shine", 1.0f, 0.5f, 0.015f);
    public static final Sfx BURST = of("burst", 1.0f, 0.6f, 0.02f);

    private static long lastHover, lastSlide;

    private UiSounds() {}

    private static Sfx of(String name, float pitch, float volume, float jitter) {
        return new Sfx(SoundEvent.of(Identifier.of(SoulBound.MOD_ID, "ui." + name)), pitch, volume, jitter);
    }

    public static void play(Sfx s) { play(s, 1.0f, 1.0f); }

    public static void play(Sfx s, float pitch, float volume) {
        var audio = SoulBound.settings().audio;
        if (!audio.sounds.isOn()) return;
        float v = (float) (s.volume() * volume * audio.volume.get());
        if (v <= 0.001f) return;
        // keep callers' pitch changes subtle: big shifts make modelled sounds read as synthetic
        float callerPitch = 1 + (pitch - 1) * 0.5f;
        float p = s.pitch() * callerPitch * (1 + (float) (Math.random() * 2 - 1) * s.pitchJitter());
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(s.event(), Math.max(0.5f, Math.min(2f, p)), Math.min(1f, v)));
    }

    /** Hover tick, rate-limited so sweeping the mouse over a list doesn't machine-gun. */
    public static void hover() {
        if (!SoulBound.settings().audio.hoverSounds.isOn()) return;
        long now = System.currentTimeMillis();
        if (now - lastHover < 55) return;
        lastHover = now;
        play(HOVER);
    }

    /** Slider detent: one notch per step, pitched very slightly with the value. */
    public static void slide(float progress) {
        long now = System.currentTimeMillis();
        if (now - lastSlide < 45) return;
        lastSlide = now;
        play(SLIDE, 0.92f + progress * 0.16f, 1f);
    }
}
