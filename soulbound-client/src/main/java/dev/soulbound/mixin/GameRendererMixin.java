package dev.soulbound.mixin;

import dev.soulbound.module.visual.NoHurtCam;
import dev.soulbound.module.visual.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.option.SimpleOption;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void soulbound$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) cir.setReturnValue(Zoom.apply(cir.getReturnValueF()));
    }

    /** Scales the damage-tilt strength option while No Hurt Cam is on. */
    @Redirect(method = "tiltViewWhenHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
    private Object soulbound$noHurtCam(SimpleOption<?> option) {
        Object v = option.getValue();
        if (NoHurtCam.active() && v instanceof Double d) return d * NoHurtCam.INSTANCE.strength.get() / 100.0;
        return v;
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void soulbound$zoomHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        if (Zoom.hideHand()) ci.cancel();
    }
}
