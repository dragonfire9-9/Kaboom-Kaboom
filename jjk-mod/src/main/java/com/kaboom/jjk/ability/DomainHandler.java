package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Domain Expansion entry point (V key). Phase 1 performs the sure-hit pulse only;
 * the barrier dome, custom sky and terrain restoration are layered on in the abilities phase.
 */
public final class DomainHandler {
    public static final String COOLDOWN_ID = "domain_expansion";
    public static final double RADIUS = 14;

    public static void tryExpand(ServerPlayer player) {
        CursedEnergyData data = ModAttachments.get(player);
        if (!data.isDomainUnlocked()) {
            player.displayClientMessage(Component.translatable("jjk.message.domain_locked").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (AbilityHandler.isSuppressed(player)) return;
        int cd = data.getCooldown(COOLDOWN_ID);
        if (cd > 0) {
            player.displayClientMessage(Component.translatable("jjk.message.cooldown",
                    Component.translatable("jjk.ability.domain_expansion"), String.format("%.1f", cd / 20.0)).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        double cost = Math.max(150, data.getMaxCE() * 0.5) * data.getCostMultiplier();
        if (!data.trySpend(cost)) {
            player.displayClientMessage(Component.translatable("jjk.message.not_enough_ce").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        String domain = data.getTechnique().hasDomain() ? data.getTechnique().domain : "incomplete_domain";
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("jjk.message.domain_cast",
                player.getDisplayName(), Component.translatable("jjk.domain." + domain)).withStyle(ChatFormatting.DARK_PURPLE), false);

        int color = data.getTechnique().color;
        AbilityEffects.ring(player.serverLevel(), player.position().add(0, 0.1, 0), RADIUS, AbilityEffects.dust(color, 2.5f));
        AbilityEffects.sphere(player.serverLevel(), player.position(), RADIUS, AbilityEffects.dust(color, 3f), 400);
        AbilityEffects.playAt(player, ModSounds.DOMAIN_EXPANSION, 2f, 1f);

        float damage = (float) (12 * data.getTechniqueDamageMultiplier());
        for (LivingEntity e : AbilityEffects.livingAround(player, player.position(), RADIUS)) {
            e.hurt(JJKDamage.sureHit(player), damage);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3), player);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), player);
        }
        data.incrementDomainsCast();
        data.setCooldown(COOLDOWN_ID, 20 * 90);
        SyncJJKDataPayload.sync(player);
    }

    private DomainHandler() {}
}
