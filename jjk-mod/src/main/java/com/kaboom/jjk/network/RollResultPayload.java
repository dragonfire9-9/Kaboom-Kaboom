package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.roll.RollResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: a fresh roll result. Opens the reel screen (or re-spins it on a reroll). */
public record RollResultPayload(RollResult result, int scrollsRemaining) implements CustomPacketPayload {
    public static final Type<RollResultPayload> TYPE = new Type<>(JJKMod.id("roll_result"));
    public static final StreamCodec<ByteBuf, RollResultPayload> STREAM_CODEC = StreamCodec.composite(
            RollResult.STREAM_CODEC, RollResultPayload::result,
            ByteBufCodecs.VAR_INT, RollResultPayload::scrollsRemaining,
            RollResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
