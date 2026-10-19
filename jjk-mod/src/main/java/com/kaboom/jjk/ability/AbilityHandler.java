package com.kaboom.jjk.ability;

import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/** Server-side entry point for the ability keybinds. */
public final class AbilityHandler {
    public static final String RCT_ID = "reverse_cursed_technique";

    public static void activateSelected(ServerPlayer player) {
        CursedEnergyData data = ModAttachments.get(player);
        Ability ability = Abilities.get(data.getSelectedAbilityId());
        if (ability == null) {
            player.displayClientMessage(Component.translatable("jjk.message.no_ability").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        tryActivate(player, data, ability);
    }

    public static boolean tryActivate(ServerPlayer player, CursedEnergyData data, Ability ability) {
        if (!ability.usableBy(data)) return false;
        int cd = data.getCooldown(ability.id());
        if (cd > 0) {
            player.displayClientMessage(Component.translatable("jjk.message.cooldown", ability.displayName(),
                    String.format("%.1f", cd / 20.0)).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double cost = ability.costFor(data);
        if (data.getCurrentCE() < cost) {
            player.displayClientMessage(Component.translatable("jjk.message.not_enough_ce").withStyle(ChatFormatting.DARK_PURPLE), true);
            AbilityEffects.playAt(player, ModSounds.CE_DEPLETED, 0.6f, 1.0f);
            return false;
        }
        if (!ability.effect().execute(player, data, ability)) return false;
        data.trySpend(cost);
        data.setCooldown(ability.id(), (int) Math.round(ability.cooldown() * JJKConfig.COOLDOWN_MULTIPLIER.get()));
        SyncJJKDataPayload.sync(player);
        return true;
    }

    public static void cycle(ServerPlayer player, int direction) {
        CursedEnergyData data = ModAttachments.get(player);
        data.cycleAbility(direction);
        Ability a = Abilities.get(data.getSelectedAbilityId());
        if (a != null) player.displayClientMessage(a.displayName().copy().withColor(a.color() & 0xFFFFFF), true);
        SyncJJKDataPayload.sync(player);
    }

    /** Reverse Cursed Technique: converts CE into healing. Only available once unlocked. */
    public static void reverseCursedTechnique(ServerPlayer player) {
        CursedEnergyData data = ModAttachments.get(player);
        if (!data.isRctUnlocked() || !data.canUseCursedEnergy()) {
            player.displayClientMessage(Component.translatable("jjk.message.rct_locked").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (data.getCooldown(RCT_ID) > 0) return;
        double cost = 40 * data.getCostMultiplier();
        if (!data.trySpend(cost)) {
            player.displayClientMessage(Component.translatable("jjk.message.not_enough_ce").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        player.heal(8f);
        player.getActiveEffects().stream()
                .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .map(MobEffectInstance::getEffect).toList()
                .forEach(player::removeEffect);
        AbilityEffects.burstAt(player.serverLevel(), player.position().add(0, 1, 0), AbilityEffects.dust(0xFFFFFFFF, 1.2f), 40, 0.5);
        AbilityEffects.playAt(player, ModSounds.ABILITY_HEAL, 1.0f, 1.0f);
        data.setCooldown(RCT_ID, 60);
        SyncJJKDataPayload.sync(player);
    }

    private AbilityHandler() {}
}
