package com.kaboom.jjk.particle;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, JJKMod.MODID);

    /** Blue-purple wisps of cursed energy. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CURSED_ENERGY =
            PARTICLES.register("cursed_energy", () -> new SimpleParticleType(false));

    /** Soft additive orb (Blue, Red, Purple, flames, domain sparkle). */
    public static final DeferredHolder<ParticleType<?>, ColorParticleOptions.Type> GLOW =
            PARTICLES.register("glow", () -> new ColorParticleOptions.Type(true));
    /** Sharp four-point spark (impacts, Black Flash). */
    public static final DeferredHolder<ParticleType<?>, ColorParticleOptions.Type> SPARK =
            PARTICLES.register("spark", () -> new ColorParticleOptions.Type(true));
    /** Crescent slash arc (Dismantle, Cleave, cursed tools). */
    public static final DeferredHolder<ParticleType<?>, ColorParticleOptions.Type> SLASH =
            PARTICLES.register("slash", () -> new ColorParticleOptions.Type(true));
    /** Expanding ring (shockwaves). */
    public static final DeferredHolder<ParticleType<?>, ColorParticleOptions.Type> RING =
            PARTICLES.register("ring", () -> new ColorParticleOptions.Type(true));
    /** Jagged lightning crack (Black Flash). */
    public static final DeferredHolder<ParticleType<?>, ColorParticleOptions.Type> LIGHTNING =
            PARTICLES.register("lightning", () -> new ColorParticleOptions.Type(true));

    public static ColorParticleOptions glow(int color, float scale, int lifetime) {
        return new ColorParticleOptions(GLOW.get(), color, scale, lifetime);
    }

    public static ColorParticleOptions spark(int color, float scale, int lifetime) {
        return new ColorParticleOptions(SPARK.get(), color, scale, lifetime);
    }

    public static ColorParticleOptions slash(int color, float scale, int lifetime) {
        return new ColorParticleOptions(SLASH.get(), color, scale, lifetime);
    }

    public static ColorParticleOptions ring(int color, float scale, int lifetime) {
        return new ColorParticleOptions(RING.get(), color, scale, lifetime);
    }

    public static ColorParticleOptions lightning(int color, float scale, int lifetime) {
        return new ColorParticleOptions(LIGHTNING.get(), color, scale, lifetime);
    }

    private ModParticles() {}
}
