package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a choice made in the NPC dialogue screen. */
public record NpcActionPayload(int entityId, Action action) implements CustomPacketPayload {
    public enum Action { ACCEPT_MISSION, TURN_IN, TRADE, TRAIN_RCT, TRAIN_BLACK_FLASH }

    public static final Type<NpcActionPayload> TYPE = new Type<>(JJKMod.id("npc_action"));
    public static final StreamCodec<ByteBuf, NpcActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcActionPayload::entityId,
            ByteBufCodecs.VAR_INT, p -> p.action().ordinal(),
            (id, a) -> new NpcActionPayload(id, Action.values()[Math.floorMod(a, Action.values().length)]));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
