package com.deadcraft.network;

import com.deadcraft.DeadCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: shake the camera (boss slams, explosions, phase changes). */
public record CameraShakePayload(float intensity) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE = new Type<>(DeadCraft.id("shake"));
    public static final StreamCodec<ByteBuf, CameraShakePayload> CODEC = ByteBufCodecs.FLOAT.map(CameraShakePayload::new, CameraShakePayload::intensity);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
