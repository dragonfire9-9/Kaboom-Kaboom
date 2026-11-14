package dev.soulbound.sound;

import dev.soulbound.SoulBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Interface sounds, built from Minecraft's own recorded sounds (paper, wood, a lever,
 * amethyst) so they sound natural and familiar. Callers pass pitch and volume as
 * multipliers around 1.0; each sound has its own tuned base pitch and level.
 */
public final class UiSounds {
    /** A sound and the base pitch/volume that make it sit well as an interface sound. */
    public record Sfx(SoundEvent event, float pitch, float volume, float pitchJitter) {}

    public static final Sfx HOVER = new Sfx(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.9f, 0.07f, 0.08f);
    public static final Sfx CLICK = new Sfx(SoundEvents.UI_BUTTON_CLICK.value(), 1.15f, 0.22f, 0.04f);
    public static final Sfx TOGGLE_ON = new Sfx(SoundEvents.BLOCK_LEVER_CLICK, 0.78f, 0.28f, 0.03f);
    public static final Sfx TOGGLE_OFF = new Sfx(SoundEvents.BLOCK_LEVER_CLICK, 0.6f, 0.25f, 0.03f);
    public static final Sfx OPEN = new Sfx(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f, 0.55f, 0.06f);
    public static final Sfx CLOSE = new Sfx(SoundEvents.ITEM_BOOK_PUT, 1.1f, 0.4f, 0.05f);
    public static final Sfx SLIDE = new Sfx(SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1.0f, 0.18f, 0.0f);
    public static final Sfx TYPE = new Sfx(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 2.0f, 0.06f, 0.12f);
    public static final Sfx SHINE = new Sfx(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f, 0.9f, 0.05f);
    public static final Sfx BURST = new Sfx(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.55f, 0.04f);

    private static long lastHover, lastSlide;

    private UiSounds() {}

    public static void play(Sfx s) { play(s, 1.0f, 1.0f); }

    public static void play(Sfx s, float pitch, float volume) {
        var audio = SoulBound.settings().audio;
        if (!audio.sounds.isOn()) return;
        float v = (float) (s.volume() * volume * audio.volume.get() * 1.6);
        if (v <= 0.001f) return;
        float p = s.pitch() * pitch * (1 + (float) (Math.random() * 2 - 1) * s.pitchJitter());
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(s.event(), Math.max(0.5f, Math.min(2f, p)), v));
        if (s == BURST) play(SHINE, 1.25f, 0.5f); // a brighter chime layered on the shatter
    }

    /** Hover tick, rate-limited so sweeping the mouse over a list doesn't machine-gun. */
    public static void hover() {
        if (!SoulBound.settings().audio.hoverSounds.isOn()) return;
        long now = System.currentTimeMillis();
        if (now - lastHover < 55) return;
        lastHover = now;
        play(HOVER);
    }

    public static void slide(float progress) {
        long now = System.currentTimeMillis();
        if (now - lastSlide < 60) return;
        lastSlide = now;
        play(SLIDE, 0.85f + progress * 0.3f, 1f);
    }
}
