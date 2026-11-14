package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Extra particles and a sound on every hit, so hits feel solid. */
public final class HitEffects extends Module {
    private final ModeSetting particle, sound;
    private final NumberSetting amount, volume, pitch;
    private final BoolSetting onlyPlayers, alwaysCrit;

    public HitEffects() {
        super("Hit Effects", "Particles and a sound when you land a hit.", Category.VISUAL);
        particle = add(new ModeSetting("Particle", "What bursts out on a hit", "Crit", "None", "Crit", "Magic", "Hearts", "Flame", "Soul", "Blood"));
        amount = add(new NumberSetting("Amount", "How many bursts", 2, 1, 6, 1, "x"));
        alwaysCrit = add(new BoolSetting("Always Crit Look", "Show crit sparks on every hit", false));
        sound = add(new ModeSetting("Sound", "What you hear on a hit", "None", "None", "Bell", "Click", "Stick", "Pling", "Bass"));
        volume = add(new NumberSetting("Volume", "Hit sound volume", 0.6, 0.1, 1, 0.05).visibleWhen(() -> !sound.is("None")));
        pitch = add(new NumberSetting("Pitch", "Hit sound pitch", 1.2, 0.5, 2, 0.05).visibleWhen(() -> !sound.is("None")));
        onlyPlayers = add(new BoolSetting("Only Players", "Only on hits against players", false));
        dev.soulbound.event.Events.ATTACK.add(this::onAttack);
    }

    private void onAttack(Entity e) {
        if (!isEnabled() || mc.world == null || !(e instanceof LivingEntity)) return;
        if (onlyPlayers.isOn() && !(e instanceof net.minecraft.entity.player.PlayerEntity)) return;
        ParticleEffect fx = switch (particle.get()) {
            case "Crit" -> ParticleTypes.CRIT;
            case "Magic" -> ParticleTypes.ENCHANTED_HIT;
            case "Hearts" -> ParticleTypes.HEART;
            case "Flame" -> ParticleTypes.FLAME;
            case "Soul" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Blood" -> ParticleTypes.DAMAGE_INDICATOR;
            default -> null;
        };
        for (int i = 0; i < amount.getInt(); i++) {
            if (fx != null) mc.particleManager.addEmitter(e, fx);
            if (alwaysCrit.isOn()) mc.particleManager.addEmitter(e, ParticleTypes.CRIT);
        }
        SoundEvent s = switch (sound.get()) {
            case "Bell" -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case "Click" -> SoundEvents.UI_BUTTON_CLICK.value();
            case "Stick" -> SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
            case "Pling" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "Bass" -> SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
            default -> null;
        };
        if (s != null) mc.world.playSoundClient(e.getX(), e.getY(), e.getZ(), s, SoundCategory.PLAYERS, volume.getFloat(), pitch.getFloat(), false);
    }
}
