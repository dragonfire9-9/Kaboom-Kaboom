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

    private ModParticles() {}
}
