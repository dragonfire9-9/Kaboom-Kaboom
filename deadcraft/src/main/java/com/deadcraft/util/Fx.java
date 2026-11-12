package com.deadcraft.util;

import com.deadcraft.network.CameraShakePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helpers for effects that the client renders. */
public final class Fx {
    private Fx() {}

    /** Shake the camera of every player within {@code radius}, fading with distance. */
    public static void shake(ServerLevel level, Vec3 at, double radius, float intensity) {
        for (var player : level.players()) {
            double d = player.position().distanceTo(at);
            if (d > radius) continue;
            float falloff = (float) (1.0 - d / radius);
            PacketDistributor.sendToPlayer(player, new CameraShakePayload(intensity * falloff * falloff));
        }
    }
}
