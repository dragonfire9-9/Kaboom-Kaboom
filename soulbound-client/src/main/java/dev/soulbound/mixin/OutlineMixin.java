package dev.soulbound.mixin;

import dev.soulbound.module.visual.Esp;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class OutlineMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void soulbound$esp(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Esp.outline(entity)) cir.setReturnValue(true);
    }
}
