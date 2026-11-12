package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, DeadCraft.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD = register("blood");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = register("ember");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOUL = register("soul");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = register("spark");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAR = register("star");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RUNE = register("rune");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SLASH = register("slash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH = register("ash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FROST = register("frost");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUZZLE_FLASH = register("muzzle_flash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CASING = register("casing");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUNSMOKE = register("gunsmoke");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCKWAVE = register("shockwave");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
