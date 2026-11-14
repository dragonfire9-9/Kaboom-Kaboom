package dev.soulbound.aim;

import dev.soulbound.module.Configurable;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;

/** A reusable block of aim settings that any module can add to its parameter list. */
public final class AimSettings {
    public final ModeSetting mode;
    public final NumberSetting speed, smoothness, jitter, overshoot, reaction, vertical;
    public final BoolSetting gcdFix;

    public AimSettings(Configurable owner, double defaultSpeed, double defaultSmooth) {
        this(owner, defaultSpeed, defaultSmooth, 80);
    }

    public AimSettings(Configurable owner, double defaultSpeed, double defaultSmooth, double defaultReaction) {
        owner.add(new HeaderSetting("AIM"));
        mode = owner.add(new ModeSetting("Turning", "How the view turns: Natural eases like a hand, Normal follows springily, Linear moves at a steady speed",
                "Natural", "Linear", "Normal", "Natural"));
        speed = owner.add(new NumberSetting("Aim Speed", "Top turning speed", defaultSpeed, 30, 1440, 10, "°/s"));
        smoothness = owner.add(new NumberSetting("Smoothness", "Higher eases in more gently", defaultSmooth, 0, 1, 0.05));
        vertical = owner.add(new NumberSetting("Vertical Speed", "Pitch speed relative to yaw", 0.7, 0.1, 1, 0.05, "x"));
        jitter = owner.add(new NumberSetting("Jitter", "Small natural hand movement", 0.25, 0, 1, 0.05));
        overshoot = owner.add(new NumberSetting("Overshoot", "Slightly pass big flicks, then settle", 0.3, 0, 1, 0.05));
        reaction = owner.add(new NumberSetting("Reaction Time", "Delay before following movement", defaultReaction, 0, 400, 10, "ms"));
        gcdFix = owner.add(new BoolSetting("Sensitivity Fix", "Move in steps your mouse sensitivity allows", true));
    }
}
