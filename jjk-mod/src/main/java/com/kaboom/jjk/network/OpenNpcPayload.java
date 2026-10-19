package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: open the dialogue screen for a sorcerer NPC. {@code data} holds offers and state. */
public record OpenNpcPayload(int entityId, CompoundTag data) implements CustomPacketPayload {
    public static final Type<OpenNpcPayload> TYPE = new Type<>(JJKMod.id("open_npc"));
    public static final StreamCodec<ByteBuf, OpenNpcPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenNpcPayload::entityId,
            ByteBufCodecs.COMPOUND_TAG, OpenNpcPayload::data,
            OpenNpcPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
