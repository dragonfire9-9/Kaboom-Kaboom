package com.deadcraft.network;

import com.deadcraft.DeadCraft;
import com.deadcraft.combat.CombatEvents;
import com.deadcraft.item.GunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = DeadCraft.MODID)
public final class DeadNetwork {
    private DeadNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(DodgePayload.TYPE, DodgePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) CombatEvents.onDodge(sp);
        }));
        r.playToServer(ReloadPayload.TYPE, ReloadPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack stack = sp.getItemInHand(hand);
                    if (stack.getItem() instanceof GunItem gun) {
                        gun.reload(sp, stack);
                        break;
                    }
                }
            }
        }));
        r.playToClient(ManaSyncPayload.TYPE, ManaSyncPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            ClientState.mana = p.mana();
            ClientState.maxMana = p.max();
            ClientState.awakened = p.awakened();
        }));
        r.playToClient(CameraShakePayload.TYPE, CameraShakePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                ClientState.shake = Math.min(2.0f, ClientState.shake + p.intensity())));
    }
}
