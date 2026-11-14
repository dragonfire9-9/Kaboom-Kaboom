package dev.soulbound.mixin;

import dev.soulbound.module.visual.LowFire;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void soulbound$lowFireStart(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite, CallbackInfo ci) {
        if (!LowFire.active()) return;
        if (LowFire.INSTANCE.mode.is("Hide")) {
            ci.cancel();
            return;
        }
        matrices.push();
        matrices.translate(0, -LowFire.INSTANCE.height.get(), 0);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void soulbound$lowFireEnd(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite, CallbackInfo ci) {
        if (LowFire.active() && !LowFire.INSTANCE.mode.is("Hide")) matrices.pop();
    }
}
