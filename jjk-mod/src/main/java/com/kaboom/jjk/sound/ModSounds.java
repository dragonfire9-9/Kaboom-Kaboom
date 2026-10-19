package com.kaboom.jjk.sound;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, JJKMod.MODID);

    // Roll scrolls
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_OPEN = register("roll.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_TICK = register("roll.tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_REVEAL = register("roll.reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_REVEAL_RARE = register("roll.reveal_rare");

    // Generic ability feedback
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_CAST = register("ability.cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_IMPACT = register("ability.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_DASH = register("ability.dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_CLAP = register("ability.clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_HEAL = register("ability.heal");
    public static final DeferredHolder<SoundEvent, SoundEvent> CE_DEPLETED = register("ce.depleted");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRADE_UP = register("grade.up");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_EXPANSION = register("domain.expansion");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(JJKMod.id(name)));
    }

    private ModSounds() {}
}
