package com.deadcraft.network;

import com.deadcraft.DeadCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the player's mana for the HUD. */
public record ManaSyncPayload(float mana, float max, boolean awakened) implements CustomPacketPayload {
    public static final Type<ManaSyncPayload> TYPE = new Type<>(DeadCraft.id("mana"));
    public static final StreamCodec<ByteBuf, ManaSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ManaSyncPayload::mana, ByteBufCodecs.FLOAT, ManaSyncPayload::max,
            ByteBufCodecs.BOOL, ManaSyncPayload::awakened, ManaSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
