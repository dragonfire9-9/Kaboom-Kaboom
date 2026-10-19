package com.kaboom.jjk.network;

import com.kaboom.jjk.ability.AbilityHandler;
import com.kaboom.jjk.ability.DomainHandler;
import com.kaboom.jjk.roll.RollService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {

    public static void handleRollAction(RollActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            switch (payload.action()) {
                case ACCEPT -> RollService.accept(player);
                case REROLL -> RollService.reroll(player);
                case DISCARD -> RollService.discard(player);
            }
        });
    }

    public static void handleKeyAction(KeyActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.isSpectator()) return;
            switch (payload.action()) {
                case ACTIVATE -> AbilityHandler.activateSelected(player);
                case CYCLE_NEXT -> AbilityHandler.cycle(player, 1);
                case CYCLE_PREVIOUS -> AbilityHandler.cycle(player, -1);
                case DOMAIN -> DomainHandler.tryExpand(player);
                case REVERSE_CURSED_TECHNIQUE -> AbilityHandler.reverseCursedTechnique(player);
            }
        });
    }

    private ServerPayloadHandler() {}
}
