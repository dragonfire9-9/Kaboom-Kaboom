package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.combat.DeadEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, DeadCraft.MODID);

    /** Damage over time with blood spray. Inflicted by Severed, Blood Demons and Bloodreaver. */
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING = EFFECTS.register("bleeding",
            () -> new DeadEffect(MobEffectCategory.HARMFUL, 0x8b0000, DeadEffect.Behaviour.BLEED));
    /** Parasite larvae: poison-like damage and hunger. */
    public static final DeferredHolder<MobEffect, MobEffect> INFESTED = EFFECTS.register("infested",
            () -> new DeadEffect(MobEffectCategory.HARMFUL, 0x6b7d2a, DeadEffect.Behaviour.INFEST));
    /** Every noise you make carries twice as far. Inflicted by The Forgotten. */
    public static final DeferredHolder<MobEffect, MobEffect> DREAD = EFFECTS.register("dread",
            () -> new DeadEffect(MobEffectCategory.HARMFUL, 0x2a2a3a, DeadEffect.Behaviour.NONE));

    private ModEffects() {}
}
