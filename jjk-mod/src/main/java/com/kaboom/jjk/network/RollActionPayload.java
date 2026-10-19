package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player's choice on the roll screen. */
public record RollActionPayload(Action action) implements CustomPacketPayload {
    public enum Action { ACCEPT, REROLL, DISCARD }

    public static final Type<RollActionPayload> TYPE = new Type<>(JJKMod.id("roll_action"));
    public static final StreamCodec<ByteBuf, RollActionPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> new RollActionPayload(Action.values()[Math.floorMod(i, Action.values().length)]),
            p -> p.action().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
