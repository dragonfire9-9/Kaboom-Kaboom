package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadMob;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every sound event. tools/gen_assets.py reads the register("...") calls to build sounds.json and the audio. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, DeadCraft.MODID);

    // Player movement layer
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_GEAR = register("player.step_gear");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP_RUN = register("player.step_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH = register("player.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = register("player.heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = register("player.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> DODGE = register("player.dodge");
    // Interface
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_HOVER = register("ui.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = register("ui.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_STATIC = register("ui.static");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MENU = register("music.menu");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DIMENSION = register("music.dimension");
    // Melee combat
    public static final DeferredHolder<SoundEvent, SoundEvent> SWING = register("combat.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT = register("combat.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRIT = register("combat.crit");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER = register("combat.finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent> EVADE = register("combat.evade");
    // Guns
    public static final DeferredHolder<SoundEvent, SoundEvent> REVOLVER = register("gun.revolver");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN = register("gun.shotgun");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE = register("gun.rifle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMG = register("gun.smg");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD = register("gun.reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMPTY = register("gun.empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = register("gun.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_REVOLVER = register("gun.reload_revolver");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_SHOTGUN = register("gun.reload_shotgun");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_RIFLE = register("gun.reload_rifle");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_SMG = register("gun.reload_smg");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYCLE_RIFLE = register("gun.cycle_rifle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYCLE_REVOLVER = register("gun.cycle_revolver");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASING = register("gun.casing");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELL_DROP = register("gun.shell_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_SHOT = register("gun.distant");
    // Magic
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST = register("magic.cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUL_BOLT = register("magic.soul_bolt");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER = register("magic.ember");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_NOVA = register("magic.frost_nova");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLINK = register("magic.blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM = register("magic.storm");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAL = register("magic.heal");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKEN = register("magic.awaken");
    // Creatures
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = register("mob.whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_HURT = register("mob.flesh_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_DEATH = register("mob.flesh_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHITTER = register("mob.chitter");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAIL = register("mob.wail");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROWL = register("mob.growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> BRUTE_ROAR = register("mob.brute_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_STEP = register("mob.stone_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> STINGER = register("mob.stinger");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOUT = register("mob.shout");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRITTER_AMBIENT = register("critter.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRITTER_HURT = register("critter.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRITTER_DEATH = register("critter.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIRD_CHIRP = register("critter.chirp");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAST_CALL = register("critter.beast_call");
    // Bosses
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_ROAR = register("boss.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PHASE = register("boss.phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEATH = register("boss.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SLAM = register("boss.slam");
    // World
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_HUM = register("world.portal_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_TRAVEL = register("world.portal_travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_WIND = register("world.ambient_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_DRONE = register("world.ambient_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_FAR = register("world.thunder_far");
    // Vehicles
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE = register("vehicle.engine");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOVER = register("vehicle.hover");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(DeadCraft.id(name)));
    }

    public static Music menuMusic() {
        return new Music(MUSIC_MENU, 20, 600, true);
    }

    // ---- Per-creature sound selection ------------------------------------------------------
    public static Holder<SoundEvent> ambient(DeadMob mob) {
        return switch (mob.archetype) {
            case GAUNT -> WHISPER;
            case CRAWLER -> CHITTER;
            case WRAITH -> WAIL;
            case BRUTE -> GROWL;
            case HUMANOID -> SHOUT;
            case ZOMBIE -> GROWL;
            case QUADRUPED -> BEAST_CALL;
            case FLYER -> BIRD_CHIRP;
            case CRITTER -> CRITTER_AMBIENT;
        };
    }

    public static Holder<SoundEvent> hurt(DeadMob mob) {
        return mob.isPeaceful() ? CRITTER_HURT : FLESH_HURT;
    }

    public static Holder<SoundEvent> death(DeadMob mob) {
        if (mob.isBoss()) return BOSS_DEATH;
        return mob.isPeaceful() ? CRITTER_DEATH : FLESH_DEATH;
    }
}
