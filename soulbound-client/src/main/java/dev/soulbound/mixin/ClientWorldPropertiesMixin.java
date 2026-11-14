package dev.soulbound.mixin;

import dev.soulbound.module.visual.WorldTweaks;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void soulbound$time(CallbackInfoReturnable<Long> cir) {
        if (WorldTweaks.active()) {
            Long t = WorldTweaks.INSTANCE.time();
            if (t != null) cir.setReturnValue(t);
        }
    }
}
