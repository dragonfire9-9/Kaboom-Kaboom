package dev.soulbound.config;

import dev.soulbound.module.Configurable;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.KeybindSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client-wide settings shown under the SETTINGS tab. These are not modules. */
public final class ClientSettings {
    public final Interface ui = new Interface();
    public final Audio audio = new Audio();
    public final Theme theme = new Theme();

    /** Pages under the SETTINGS entry. */
    public List<Configurable> pages() { return List.of(ui, audio); }

    /** Every page that is saved to the config. */
    public List<Configurable> allPages() { return List.of(ui, audio, theme); }

    public static final class Interface extends Configurable {
        public final KeybindSetting guiKey = add(new KeybindSetting("Menu Key", "Opens this menu", GLFW.GLFW_KEY_RIGHT_SHIFT));
        public final NumberSetting animationSpeed = add(new NumberSetting("Animation Speed", "How quickly the interface moves", 1.0, 0.5, 2.0, 0.1, "x"));
        public final BoolSetting blur = add(new BoolSetting("Background Blur", "Blur the world behind the menu", true));

        Interface() { super("Interface", "How the SoulBound menu behaves."); }
    }

    public static final class Theme extends Configurable {
        public final ModeSetting accent = add(new ModeSetting("Accent", "Highlight colour", "Soul", "Soul", "Violet", "Frost", "Ember"));
        public final NumberSetting glow = add(new NumberSetting("Glow", "Strength of the outer glow", 0.7, 0, 1, 0.05));

        Theme() { super("Theme", "Colours and glow of the interface."); }
    }

    public static final class Audio extends Configurable {
        public final BoolSetting sounds = add(new BoolSetting("Interface Sounds", "Hover and click sounds", true));
        public final NumberSetting volume = add(new NumberSetting("Volume", "Interface sound volume", 0.6, 0, 1, 0.05).visibleWhen(sounds::isOn));
        public final BoolSetting hoverSounds = add(new BoolSetting("Hover Ticks", "Play a tick when hovering", true).visibleWhen(sounds::isOn));

        Audio() { super("Audio", "Interface sound effects."); }
    }
}
