package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.ability.BlackFlash;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.particle.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Infinity, Black Flash on melee, and CE upkeep for sustained techniques. */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class CombatEvents {
    private static final double INFINITY_DRAIN_PER_TICK = 0.08;

    /** Infinity: nothing reaches you unless it carries a way through. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInfinity(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.hasEffect(ModEffects.INFINITY)) return;
        var source = event.getSource();
        if (source.is(JJKDamage.DOMAIN_SURE_HIT) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (source.getEntity() instanceof LivingEntity attacker) {
            if (attacker.getMainHandItem().is(ModItems.INVERTED_SPEAR_OF_HEAVEN.get())) return;
            if (attacker.hasEffect(ModEffects.DOMAIN_AMPLIFICATION)) return;
        }
        event.setCanceled(true);
        if (victim.level() instanceof ServerLevel level) {
            AbilityEffects.burstAt(level, victim.position().add(0, victim.getBbHeight() / 2, 0), ModParticles.glow(0xFFBFF4FF, 1f, 8), 6, 0.6);
        }
    }

    /** Melee hits from cursed energy users can spark a Black Flash. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMelee(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (!(source.getDirectEntity() instanceof ServerPlayer player) || source.getDirectEntity() != source.getEntity()) return;
        if (source.is(JJKDamage.CURSED_TECHNIQUE) || source.is(JJKDamage.DOMAIN_SURE_HIT)) return; // abilities roll on their own
        if (player.getAttackStrengthScale(0.5f) < 0.9f) return; // spam-clicking never sparks
        CursedEnergyData data = ModAttachments.get(player);
        event.setAmount(BlackFlash.tryTrigger(player, data, event.getEntity(), event.getAmount(), 0));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasEffect(ModEffects.INFINITY)) return;
        CursedEnergyData data = ModAttachments.get(player);
        if (!data.trySpend(INFINITY_DRAIN_PER_TICK * data.getCostMultiplier())) {
            player.removeEffect(ModEffects.INFINITY);
            player.displayClientMessage(Component.translatable("jjk.message.infinity_down").withStyle(ChatFormatting.AQUA), true);
        }
    }

    private CombatEvents() {}
}
