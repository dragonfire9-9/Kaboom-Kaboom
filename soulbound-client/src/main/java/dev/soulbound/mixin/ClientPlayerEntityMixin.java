package dev.soulbound.mixin;

import dev.soulbound.module.movement.NoSlow;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "getActiveItemSpeedMultiplier", at = @At("RETURN"), cancellable = true)
    private void soulbound$noSlow(CallbackInfoReturnable<Float> cir) {
        if (NoSlow.active()) cir.setReturnValue(Math.max(cir.getReturnValueF(), NoSlow.INSTANCE.speed()));
    }
}
