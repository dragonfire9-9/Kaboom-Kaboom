package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a cinematic effect for this player's camera/HUD.
 * {@code text} is a translation key (used by TEXT), {@code color} is ARGB.
 */
public record ClientFxPayload(Fx fx, float intensity, int duration, int color, String text) implements CustomPacketPayload {
    public enum Fx { SHAKE, FLASH, TEXT, BLACK_FLASH, DOMAIN_OPEN }

    public static final Type<ClientFxPayload> TYPE = new Type<>(JJKMod.id("client_fx"));
    public static final StreamCodec<ByteBuf, ClientFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.fx().ordinal(),
            ByteBufCodecs.FLOAT, ClientFxPayload::intensity,
            ByteBufCodecs.VAR_INT, ClientFxPayload::duration,
            ByteBufCodecs.INT, ClientFxPayload::color,
            ByteBufCodecs.STRING_UTF8, ClientFxPayload::text,
            (fx, i, d, c, t) -> new ClientFxPayload(Fx.values()[Math.floorMod(fx, Fx.values().length)], i, d, c, t));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
