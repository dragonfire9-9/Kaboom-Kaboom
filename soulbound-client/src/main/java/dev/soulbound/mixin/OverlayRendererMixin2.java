package dev.soulbound.mixin;

import dev.soulbound.module.visual.NoOverlays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class OverlayRendererMixin2 {
    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void soulbound$water(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers, CallbackInfo ci) {
        if (NoOverlays.hide(NoOverlays.Kind.WATER)) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void soulbound$wall(Sprite sprite, MatrixStack matrices, VertexConsumerProvider consumers, CallbackInfo ci) {
        if (NoOverlays.hide(NoOverlays.Kind.WALL)) ci.cancel();
    }
}
