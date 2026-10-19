package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a jujutsu keybind was pressed. The server validates everything. */
public record KeyActionPayload(Action action) implements CustomPacketPayload {
    public enum Action { ACTIVATE, CYCLE_NEXT, CYCLE_PREVIOUS, DOMAIN, REVERSE_CURSED_TECHNIQUE }

    public static final Type<KeyActionPayload> TYPE = new Type<>(JJKMod.id("key_action"));
    public static final StreamCodec<ByteBuf, KeyActionPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> new KeyActionPayload(Action.values()[Math.floorMod(i, Action.values().length)]),
            p -> p.action().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
