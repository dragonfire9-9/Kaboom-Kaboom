package com.deadcraft.world;

import com.deadcraft.DeadCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The four DeadCraft dimensions. Their terrain, biomes and sky live in data/deadcraft/dimension*. */
public final class DeadDimensions {
    public static final ResourceKey<Level> HELLS_PARADISE = key("hells_paradise");
    public static final ResourceKey<Level> SKYFALL_TORN = key("skyfall_torn");
    public static final ResourceKey<Level> LIGHTS_DESCENT = key("lights_descent");
    public static final ResourceKey<Level> DISPOSED_SKIES = key("disposed_skies");

    /** Index 0 is the Overworld, 1-4 are DeadCraft dimensions (used by portal blocks and rift keys). */
    public static ResourceKey<Level> byIndex(int index) {
        return switch (index) {
            case 1 -> HELLS_PARADISE;
            case 2 -> SKYFALL_TORN;
            case 3 -> LIGHTS_DESCENT;
            case 4 -> DISPOSED_SKIES;
            default -> Level.OVERWORLD;
        };
    }

    public static boolean isDeadCraft(ResourceKey<Level> key) {
        return key.location().getNamespace().equals(DeadCraft.MODID);
    }

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, DeadCraft.id(name));
    }

    private DeadDimensions() {}
}
