package dev.soulbound.mixin;

import dev.soulbound.module.visual.WorldTweaks;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void soulbound$rain(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof net.minecraft.client.world.ClientWorld && WorldTweaks.active()) {
            Float r = WorldTweaks.INSTANCE.rain();
            if (r != null) cir.setReturnValue(r);
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void soulbound$thunder(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof net.minecraft.client.world.ClientWorld && WorldTweaks.active()) {
            Float r = WorldTweaks.INSTANCE.thunder();
            if (r != null) cir.setReturnValue(r);
        }
    }
}
