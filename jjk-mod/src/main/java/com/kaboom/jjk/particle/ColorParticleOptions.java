package com.kaboom.jjk.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Colour + size + lifetime for the mod's tintable particles (glow, spark, slash, ring, lightning).
 * The same options class backs several particle types, so it carries the type it was made for.
 */
public record ColorParticleOptions(ParticleType<ColorParticleOptions> type, int color, float scale, int lifetime)
        implements ParticleOptions {

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    public static MapCodec<ColorParticleOptions> codec(ParticleType<ColorParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("color").forGetter(ColorParticleOptions::color),
                Codec.FLOAT.fieldOf("scale").forGetter(ColorParticleOptions::scale),
                Codec.INT.fieldOf("lifetime").forGetter(ColorParticleOptions::lifetime)
        ).apply(i, (c, s, l) -> new ColorParticleOptions(type, c, s, l)));
    }

    public static StreamCodec<ByteBuf, ColorParticleOptions> streamCodec(ParticleType<ColorParticleOptions> type) {
        return StreamCodec.composite(
                ByteBufCodecs.INT, ColorParticleOptions::color,
                ByteBufCodecs.FLOAT, ColorParticleOptions::scale,
                ByteBufCodecs.VAR_INT, ColorParticleOptions::lifetime,
                (c, s, l) -> new ColorParticleOptions(type, c, s, l));
    }

    /** A ParticleType whose codecs build options pointing back at itself. */
    public static final class Type extends ParticleType<ColorParticleOptions> {
        private final MapCodec<ColorParticleOptions> codec;
        private final StreamCodec<ByteBuf, ColorParticleOptions> streamCodec;

        public Type(boolean overrideLimiter) {
            super(overrideLimiter);
            this.codec = ColorParticleOptions.codec(this);
            this.streamCodec = ColorParticleOptions.streamCodec(this);
        }

        @Override
        public MapCodec<ColorParticleOptions> codec() {
            return codec;
        }

        @Override
        public StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, ColorParticleOptions> streamCodec() {
            return streamCodec;
        }
    }
}
