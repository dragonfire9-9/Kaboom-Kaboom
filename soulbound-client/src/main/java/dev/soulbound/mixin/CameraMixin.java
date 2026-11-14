package dev.soulbound.mixin;

import dev.soulbound.module.visual.CameraTweaks;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
    private float soulbound$distance(float distance) {
        return CameraTweaks.active() ? CameraTweaks.INSTANCE.distance() : distance;
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void soulbound$noClip(float distance, CallbackInfoReturnable<Float> cir) {
        if (CameraTweaks.active() && CameraTweaks.INSTANCE.noClip()) cir.setReturnValue(CameraTweaks.INSTANCE.distance());
    }
}
