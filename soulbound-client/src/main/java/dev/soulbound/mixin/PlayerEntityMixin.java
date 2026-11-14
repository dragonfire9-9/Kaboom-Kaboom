package dev.soulbound.mixin;

import dev.soulbound.module.movement.SafeWalk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void soulbound$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == MinecraftClient.getInstance().player && SafeWalk.clip()) cir.setReturnValue(true);
    }
}
