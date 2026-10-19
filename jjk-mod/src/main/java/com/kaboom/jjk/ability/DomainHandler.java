package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Domain Expansion key (V): validation and costs, then hands off to {@link DomainManager}. */
public final class DomainHandler {
    public static final String COOLDOWN_ID = "domain_expansion";

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
        double cost = Math.max(150, data.getMaxCE() * 0.5) * data.getCostMultiplier() * com.kaboom.jjk.event.ArmorBonuses.costMultiplier(player);
        if (!data.trySpend(cost)) {
            player.displayClientMessage(Component.translatable("jjk.message.not_enough_ce").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        DomainType type = data.getTechnique().hasDomain() ? DomainType.byId(data.getTechnique().domain) : DomainType.INCOMPLETE_DOMAIN;
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("jjk.message.domain_cast",
                player.getDisplayName(), Component.translatable("jjk.domain." + type.id)).withStyle(ChatFormatting.DARK_PURPLE), false);
        DomainManager.expand(player, data, type);
        data.setCooldown(COOLDOWN_ID, 20 * 90);
        SyncJJKDataPayload.sync(player);
    }

    private DomainHandler() {}
}
