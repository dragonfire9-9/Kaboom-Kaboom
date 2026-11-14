package dev.soulbound.mixin;

import dev.soulbound.module.combat.Hitboxes;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMarginMixin {
    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true)
    private void soulbound$hitboxes(CallbackInfoReturnable<Float> cir) {
        float extra = Hitboxes.extra((Entity) (Object) this);
        if (extra > 0) cir.setReturnValue(cir.getReturnValueF() + extra);
    }
}
