package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients tracking a player: show or hide Sukuna's markings on that player. */
public record MarkingsPayload(int entityId, boolean marked) implements CustomPacketPayload {
    public static final Type<MarkingsPayload> TYPE = new Type<>(JJKMod.id("markings"));
    public static final StreamCodec<ByteBuf, MarkingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MarkingsPayload::entityId,
            ByteBufCodecs.BOOL, MarkingsPayload::marked,
            MarkingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
