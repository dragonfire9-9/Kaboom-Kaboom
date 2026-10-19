package com.kaboom.jjk.sound;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.Abilities;
import com.kaboom.jjk.ability.DomainType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

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
    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_SHATTER = register("domain.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_FLASH = register("black_flash");

    // Items
    public static final DeferredHolder<SoundEvent, SoundEvent> FINGER_EAT = register("item.finger_eat");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUKUNA_LAUGH = register("item.sukuna_laugh");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRISON_REALM = register("item.prison_realm");
    public static final DeferredHolder<SoundEvent, SoundEvent> TALISMAN_BURN = register("item.talisman_burn");

    /** One sound per ability ({@code technique.<ability id>}) and per domain ({@code domain.<domain id>}). */
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> ABILITY_SOUNDS = new HashMap<>();
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> DOMAIN_SOUNDS = new HashMap<>();

    static {
        for (String id : Abilities.ids()) ABILITY_SOUNDS.put(id, register("technique." + id));
        for (DomainType t : DomainType.values()) DOMAIN_SOUNDS.put(t.id, register("domain." + t.id));
    }

    public static SoundEvent forAbility(String abilityId) {
        DeferredHolder<SoundEvent, SoundEvent> h = ABILITY_SOUNDS.get(abilityId);
        return h != null ? h.get() : ABILITY_CAST.get();
    }

    public static SoundEvent forDomain(String domainId) {
        DeferredHolder<SoundEvent, SoundEvent> h = DOMAIN_SOUNDS.get(domainId);
        return h != null ? h.get() : DOMAIN_EXPANSION.get();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(JJKMod.id(name)));
    }

    private ModSounds() {}
}
