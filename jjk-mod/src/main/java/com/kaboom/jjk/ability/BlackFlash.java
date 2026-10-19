package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CENature;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.event.JJKAdvancements;
import com.kaboom.jjk.network.ClientFxPayload;
import com.kaboom.jjk.network.FxSender;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Black Flash: when cursed energy lands within 0.000001s of a physical hit, the impact is distorted
 * and the damage is raised to the power of 2.5. Landing one puts the sorcerer "in the zone".
 */
public final class BlackFlash {
    public static final float MULTIPLIER = 2.5f;
    private static final int ZONE_TICKS = 20 * 10;
    private static final Map<UUID, Long> ZONE_UNTIL = new HashMap<>();

    public static double chance(ServerPlayer player, CursedEnergyData data, double bonus) {
        double c = 0.02 + bonus + 0.01 * data.getGrade().ordinal();
        if (data.getCeNature() == CENature.VOLATILE) c += 0.04;
        if (data.getTechnique() == Technique.BLACK_FLASH_AFFINITY) c += 0.10;
        if (data.isBlackFlashUnlocked()) c += 0.03;
        if (inZone(player)) c += 0.20;
        return Math.min(c, 0.6);
    }

    public static boolean inZone(ServerPlayer player) {
        Long until = ZONE_UNTIL.get(player.getUUID());
        return until != null && until > player.level().getGameTime();
    }

    /** Rolls for a Black Flash and returns the (possibly multiplied) damage. */
    public static float tryTrigger(ServerPlayer player, CursedEnergyData data, LivingEntity target, float amount, double bonus) {
        if (!data.canUseCursedEnergy() || data.getCurrentCE() < 5) return amount;
        if (player.getRandom().nextDouble() >= chance(player, data, bonus)) return amount;

        data.trySpend(5);
        data.incrementBlackFlash();
        data.setCurrentCE(data.getCurrentCE() + data.getMaxCE() * 0.15); // the zone: CE flows freely
        ZONE_UNTIL.put(player.getUUID(), player.level().getGameTime() + ZONE_TICKS);

        ServerLevel level = player.serverLevel();
        double x = target.getX(), y = target.getY() + target.getBbHeight() / 2, z = target.getZ();
        level.sendParticles(ModParticles.lightning(0xFF000000, 5f, 8), x, y, z, 6, 0.6, 0.6, 0.6, 0);
        level.sendParticles(ModParticles.lightning(0xFFE0102A, 4f, 8), x, y, z, 6, 0.6, 0.6, 0.6, 0);
        level.sendParticles(ModParticles.spark(0xFFFF2040, 2f, 10), x, y, z, 30, 0.4, 0.4, 0.4, 0.3);
        level.sendParticles(ModParticles.ring(0xFF100008, 6f, 10), x, y, z, 1, 0, 0, 0, 0);
        level.playSound(null, target.blockPosition(), ModSounds.BLACK_FLASH.get(), SoundSource.PLAYERS, 2f, 1f);

        FxSender.send(player, new ClientFxPayload(ClientFxPayload.Fx.BLACK_FLASH, 1f, 30, 0xFFE0102A, ""));
        FxSender.shakeAround(level, target.position(), 20, 0.8f, 10);
        JJKAdvancements.award(player, "black_flash");
        SyncJJKDataPayload.sync(player);
        return amount * MULTIPLIER;
    }

    private BlackFlash() {}
}
