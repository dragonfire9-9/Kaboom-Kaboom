package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server -> client: full copy of the player's {@link CursedEnergyData}. */
public record SyncJJKDataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncJJKDataPayload> TYPE = new Type<>(JJKMod.id("sync_data"));
    public static final StreamCodec<ByteBuf, SyncJJKDataPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SyncJJKDataPayload::new, SyncJJKDataPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sync(ServerPlayer player) {
        CursedEnergyData data = ModAttachments.get(player);
        // Fake players and vanilla clients have no channel for our payloads.
        if (player.connection == null || !player.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(player, new SyncJJKDataPayload(data.serializeNBT(player.registryAccess())));
        data.clearDirty();
    }
}
