package com.kaboom.jjk.effect;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, JJKMod.MODID);

    /** Marked by the King of Curses. Jujutsu sorcerers treat you as an enemy. */
    public static final DeferredHolder<MobEffect, MobEffect> EXILED = EFFECTS.register("exiled",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0xB0101A));

    /** Locked inside the Prison Realm: cannot move, act, or be harmed. */
    public static final DeferredHolder<MobEffect, MobEffect> SEALED = EFFECTS.register("sealed",
            () -> new SealedEffect(MobEffectCategory.HARMFUL, 0x3A2A4A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, JJKMod.id("sealed_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, JJKMod.id("sealed_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, JJKMod.id("sealed_damage"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Hit by the Inverted Spear of Heaven or Black Rope: cursed techniques fizzle. */
    public static final DeferredHolder<MobEffect, MobEffect> NULLIFIED = EFFECTS.register("nullified",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x6A6A6A));

    /** Simple Domain: a tiny barrier that neutralises a domain's sure-hit. */
    public static final DeferredHolder<MobEffect, MobEffect> SIMPLE_DOMAIN = EFFECTS.register("simple_domain",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x9AD8FF)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, JJKMod.id("simple_domain_speed"), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Domain Amplification: CE wrapped around the body neutralises techniques it touches (and sure-hits). */
    public static final DeferredHolder<MobEffect, MobEffect> DOMAIN_AMPLIFICATION = EFFECTS.register("domain_amplification",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x4B3AFF));

    /** Limitless Infinity: attacks never reach you. Drains CE every tick. */
    public static final DeferredHolder<MobEffect, MobEffect> INFINITY = EFFECTS.register("infinity",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xBFF4FF));

    private ModEffects() {}
}
