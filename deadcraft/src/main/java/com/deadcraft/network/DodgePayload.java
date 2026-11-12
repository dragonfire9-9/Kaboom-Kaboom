package com.deadcraft.network;

import com.deadcraft.DeadCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player dodged (the client already applied the impulse). */
public record DodgePayload(float strafe, float forward) implements CustomPacketPayload {
    public static final Type<DodgePayload> TYPE = new Type<>(DeadCraft.id("dodge"));
    public static final StreamCodec<ByteBuf, DodgePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DodgePayload::strafe, ByteBufCodecs.FLOAT, DodgePayload::forward, DodgePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
