package dev.soulbound.mixin;

import dev.soulbound.module.visual.Esp;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void soulbound$espColor(CallbackInfoReturnable<Integer> cir) {
        int c = Esp.color((Entity) (Object) this);
        if (c >= 0) cir.setReturnValue(c);
    }
}
