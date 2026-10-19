package com.kaboom.jjk.network;

import com.kaboom.jjk.JJKMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server -> client: every active Domain Expansion in the player's dimension (for fog, tint and sky). */
public record DomainStatePayload(List<DomainInfo> domains) implements CustomPacketPayload {
    public record DomainInfo(double x, double y, double z, float radius, int color, String domain) {
        public static final StreamCodec<ByteBuf, DomainInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, DomainInfo::x,
                ByteBufCodecs.DOUBLE, DomainInfo::y,
                ByteBufCodecs.DOUBLE, DomainInfo::z,
                ByteBufCodecs.FLOAT, DomainInfo::radius,
                ByteBufCodecs.INT, DomainInfo::color,
                ByteBufCodecs.STRING_UTF8, DomainInfo::domain,
                DomainInfo::new);

        public boolean contains(double px, double py, double pz) {
            double dx = px - x, dy = py - y, dz = pz - z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }

    public static final Type<DomainStatePayload> TYPE = new Type<>(JJKMod.id("domain_state"));
    public static final StreamCodec<ByteBuf, DomainStatePayload> STREAM_CODEC =
            DomainInfo.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DomainStatePayload::new, DomainStatePayload::domains);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
