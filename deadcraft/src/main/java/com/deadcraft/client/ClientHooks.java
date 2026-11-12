package com.deadcraft.client;

import com.deadcraft.client.camera.CinematicCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Client-side effects triggered from common item code (only ever called when level.isClientSide). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void gunFired(float recoil, int gun) {
        CinematicCamera.recoil(recoil);
        ViewModel.fired(gun);
    }

    public static void awakening(ItemStack stack) {
        Minecraft.getInstance().gameRenderer.displayItemActivation(stack);
        CinematicCamera.addShake(0.6f);
    }
}
