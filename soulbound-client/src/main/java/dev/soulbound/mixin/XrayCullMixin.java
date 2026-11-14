package dev.soulbound.mixin;

import dev.soulbound.module.visual.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class XrayCullMixin {
    /** With X-Ray on, shown blocks draw every face (their neighbours are invisible now). */
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void soulbound$xraySides(BlockState state, BlockState other, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (Xray.active()) cir.setReturnValue(Xray.shows(state) && !Xray.shows(other));
    }
}
