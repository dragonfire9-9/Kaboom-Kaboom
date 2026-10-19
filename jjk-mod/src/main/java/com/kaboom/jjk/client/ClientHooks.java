package com.kaboom.jjk.client;

import com.kaboom.jjk.client.screen.RollScreen;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.network.RollResultPayload;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import net.minecraft.client.Minecraft;

public final class ClientHooks {

    public static void applySync(SyncJJKDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ModAttachments.get(mc.player).deserializeNBT(mc.player.registryAccess(), payload.data());
    }

    public static void showRoll(RollResultPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RollScreen screen && screen.type() == payload.result().type()) {
            screen.respin(payload.result(), payload.scrollsRemaining());
        } else {
            mc.setScreen(new RollScreen(payload.result(), payload.scrollsRemaining()));
        }
    }

    private ClientHooks() {}
}
