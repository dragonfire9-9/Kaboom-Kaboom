package com.kaboom.jjk.client;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.network.KeyActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JJKMod.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        CursedEnergyData data = ModAttachments.get(mc.player);
        data.clientTickCooldowns();

        while (KeyBindings.ACTIVATE.consumeClick()) send(KeyActionPayload.Action.ACTIVATE);
        while (KeyBindings.CYCLE.consumeClick()) {
            send(Screen.hasShiftDown() ? KeyActionPayload.Action.CYCLE_PREVIOUS : KeyActionPayload.Action.CYCLE_NEXT);
        }
        while (KeyBindings.DOMAIN.consumeClick()) send(KeyActionPayload.Action.DOMAIN);
        while (KeyBindings.REVERSE_CURSED_TECHNIQUE.consumeClick()) {
            if (data.isRctUnlocked()) send(KeyActionPayload.Action.REVERSE_CURSED_TECHNIQUE);
            else mc.player.displayClientMessage(Component.translatable("jjk.message.rct_locked").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHooks.clearMarked();
    }

    private static void send(KeyActionPayload.Action action) {
        PacketDistributor.sendToServer(new KeyActionPayload(action));
    }

    private ClientGameEvents() {}
}
