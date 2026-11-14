package dev.soulbound.mixin;

import dev.soulbound.module.visual.Xray;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class XrayOcclusionMixin {
    /** Solid stone would otherwise hide whole chunk sections from view (occlusion culling). */
    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void soulbound$xrayOpen(BlockPos pos, CallbackInfo ci) {
        if (Xray.active()) ci.cancel();
    }
}
