package com.deadcraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DeadConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;

    // Client
    public static final ModConfigSpec.BooleanValue CUSTOM_TITLE;
    public static final ModConfigSpec.BooleanValue CUSTOM_HUD;
    public static final ModConfigSpec.DoubleValue CAMERA_SWAY;
    public static final ModConfigSpec.DoubleValue FILM_GRAIN;
    public static final ModConfigSpec.DoubleValue VIEW_MODEL_SWAY;
    public static final ModConfigSpec.BooleanValue MOVEMENT_SOUNDS;
    public static final ModConfigSpec.BooleanValue PLAYER_ANIMATIONS;

    // Server
    public static final ModConfigSpec.BooleanValue VOICE_HEARING;
    public static final ModConfigSpec.DoubleValue VOICE_RANGE;

    static {
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.push("presentation");
        CUSTOM_TITLE = c.comment("Replace the vanilla title screen with the DeadCraft one").define("customTitleScreen", true);
        CUSTOM_HUD = c.comment("Replace hearts/hunger/armour with DeadCraft bars and add the horror overlays").define("customHud", true);
        FILM_GRAIN = c.comment("Strength of the film grain / static overlay (0 = off)").defineInRange("filmGrain", 0.5, 0.0, 1.0);
        c.pop().push("camera");
        CAMERA_SWAY = c.comment("Cinematic camera sway while walking and running (0 = off, 1 = default)").defineInRange("cameraSway", 1.0, 0.0, 2.0);
        VIEW_MODEL_SWAY = c.comment("First-person weapon sway and inertia (0 = off)").defineInRange("viewModelSway", 1.0, 0.0, 2.0);
        PLAYER_ANIMATIONS = c.comment("Cinematic third-person walk and run animations").define("playerAnimations", true);
        c.pop().push("audio");
        MOVEMENT_SOUNDS = c.comment("Gear rustle, breathing and heartbeat layers").define("movementSounds", true);
        c.pop();
        CLIENT_SPEC = c.build();

        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        s.push("hearing");
        VOICE_HEARING = s.comment("Creatures hear players talking through Simple Voice Chat").define("voiceHearing", true);
        VOICE_RANGE = s.comment("Base distance in blocks that normal speech carries to creatures").defineInRange("voiceRange", 24.0, 4.0, 96.0);
        s.pop();
        SERVER_SPEC = s.build();
    }

    private DeadConfig() {}
}
