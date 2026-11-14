package dev.soulbound.mixin;

import dev.soulbound.module.visual.Xray;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class XrayAoMixin {
    /** X-Ray: no ambient-occlusion shadows from the (now invisible) stone around ores. */
    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
    private void soulbound$xrayAo(BlockState state, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (Xray.active()) cir.setReturnValue(1.0f);
    }
}
