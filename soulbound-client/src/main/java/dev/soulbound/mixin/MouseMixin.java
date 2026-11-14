package dev.soulbound.mixin;

import dev.soulbound.aim.AimController;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    /** Runs once per rendered frame, right after vanilla applies mouse movement. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void soulbound$aimFrame(CallbackInfo ci) {
        AimController.frame();
    }

    /** Zoom: the scroll wheel adjusts the zoom while the zoom key is held. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void soulbound$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen == null && dev.soulbound.module.visual.Zoom.onScroll(vertical)) ci.cancel();
    }
}
