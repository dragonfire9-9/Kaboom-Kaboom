package com.deadcraft.mixin;

import com.deadcraft.client.anim.SwingStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person swings for vanilla swords and axes, matching DeadCraft's own blades. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void deadcraft$swing(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        SwingStyle style = SwingStyle.of(player.getItemInHand(hand));
        if (style == SwingStyle.ARC || style == SwingStyle.OVERHEAD || style == SwingStyle.STAB) {
            style.firstPerson(pose, arm == HumanoidArm.RIGHT ? 1f : -1f, swing);
            ci.cancel();
        }
    }
}
