package dev.soulbound.mixin;

import dev.soulbound.module.visual.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockRenderManager.class)
public abstract class XrayMixins {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void soulbound$xrayBlock(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices, VertexConsumer vc,
                                     boolean cull, List<BlockModelPart> parts, CallbackInfo ci) {
        if (Xray.active() && !Xray.shows(state)) ci.cancel();
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void soulbound$xrayFluid(BlockPos pos, BlockRenderView world, VertexConsumer vc, BlockState state, FluidState fluid, CallbackInfo ci) {
        if (Xray.active() && !Xray.fluids()) ci.cancel();
    }
}
