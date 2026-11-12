package com.deadcraft.network;

import com.deadcraft.DeadCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: reload the held gun. */
public record ReloadPayload() implements CustomPacketPayload {
    public static final ReloadPayload INSTANCE = new ReloadPayload();
    public static final Type<ReloadPayload> TYPE = new Type<>(DeadCraft.id("reload"));
    public static final StreamCodec<ByteBuf, ReloadPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
