package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.network.MarkingsPayload;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Eating a finger can let Sukuna surface: for a while the player wields Shrine, bears his markings,
 * and is branded Exiled by jujutsu society.
 */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class SukunaTakeover {

    public static void begin(ServerPlayer player, CursedEnergyData data, int ticks) {
        data.setTakeoverTicks(ticks);
        data.setCurrentCE(data.getMaxCE());
        player.addEffect(new MobEffectInstance(ModEffects.EXILED, 20 * 60 * 20, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1, false, false));
        player.level().playSound(null, player.blockPosition(), ModSounds.SUKUNA_LAUGH.get(), SoundSource.PLAYERS, 1.5f, 0.9f);
        AbilityEffects.burstAt(player.serverLevel(), player.position().add(0, 1, 0), AbilityEffects.dust(0xFFB0101A, 2f), 80, 0.8);
        player.sendSystemMessage(Component.translatable("jjk.message.takeover_start").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        JJKAdvancements.award(player, "vessel");
        broadcast(player, true);
        SyncJJKDataPayload.sync(player);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CursedEnergyData data = ModAttachments.get(player);
        if (!data.isTakenOver()) return;
        data.setTakeoverTicks(data.getTakeoverTicks() - 1);
        if (!data.isTakenOver()) {
            player.sendSystemMessage(Component.translatable("jjk.message.takeover_end").withStyle(ChatFormatting.GRAY));
            broadcast(player, false);
            SyncJJKDataPayload.sync(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && ModAttachments.get(target).isTakenOver() && viewer.connection.hasChannel(MarkingsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(viewer, new MarkingsPayload(target.getId(), true));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ModAttachments.get(p).isTakenOver()) broadcast(p, true);
    }

    private static void broadcast(ServerPlayer player, boolean marked) {
        MarkingsPayload payload = new MarkingsPayload(player.getId(), marked);
        for (ServerPlayer p : player.serverLevel().players()) {
            if ((p == player || p.distanceToSqr(player) < 160 * 160) && p.connection.hasChannel(MarkingsPayload.TYPE)) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }

    private SukunaTakeover() {}
}
