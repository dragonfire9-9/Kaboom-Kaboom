package com.kaboom.jjk.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helpers for sending camera shake, flashes and title pops. */
public final class FxSender {

    public static void send(ServerPlayer player, ClientFxPayload payload) {
        if (player.connection != null && player.connection.hasChannel(ClientFxPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void shakeAround(ServerLevel level, Vec3 pos, double radius, float intensity, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(pos);
            if (d < radius) {
                send(p, new ClientFxPayload(ClientFxPayload.Fx.SHAKE, (float) (intensity * (1 - d / radius * 0.7)), ticks, 0, ""));
            }
        }
    }

    public static void flash(ServerPlayer player, int color, int ticks) {
        send(player, new ClientFxPayload(ClientFxPayload.Fx.FLASH, 1f, ticks, color, ""));
    }

    public static void text(ServerPlayer player, String key, int color, int ticks) {
        send(player, new ClientFxPayload(ClientFxPayload.Fx.TEXT, 1f, ticks, color, key));
    }

    private FxSender() {}
}
