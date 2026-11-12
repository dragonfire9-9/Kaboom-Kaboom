package com.deadcraft.mixin;

import com.deadcraft.config.DeadConfig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cinematic third-person walk and run: the torso counter-rotates against the legs, arms pump
 * harder at a sprint, the body leans into the run and the head bobs with each stride.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;
    @Shadow public boolean crouching;
    @Shadow public float swimAmount;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void deadcraft$cinematicGait(T entity, float limbSwing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player p) || !DeadConfig.PLAYER_ANIMATIONS.get()) return;
        if (crouching || swimAmount > 0 || p.isPassenger() || p.isFallFlying() || p.isSpectator() || amount < 0.01f) return;
        if (((HumanoidModel<?>) (Object) this).attackTime > 0) return; // keep vanilla attack swings crisp

        float sprint = p.isSprinting() ? 1f : 0f;
        float cycle = limbSwing * 0.6662f;
        float twist = Mth.cos(cycle) * amount * (0.10f + 0.12f * sprint);
        body.yRot += twist;
        head.yRot -= twist * 0.5f;
        rightArm.z = Mth.sin(body.yRot) * 5.0f;
        rightArm.x = -Mth.cos(body.yRot) * 5.0f;
        leftArm.z = -Mth.sin(body.yRot) * 5.0f;
        leftArm.x = Mth.cos(body.yRot) * 5.0f;
        rightArm.yRot += body.yRot;
        leftArm.yRot += body.yRot;

        // Pump the arms harder and bend them outward a touch when running.
        rightArm.xRot *= 1f + 0.4f * sprint;
        leftArm.xRot *= 1f + 0.4f * sprint;
        rightArm.zRot += 0.06f + 0.08f * sprint;
        leftArm.zRot -= 0.06f + 0.08f * sprint;

        // Stride bounce.
        float bounce = Mth.abs(Mth.cos(cycle)) * amount * (0.5f + 0.6f * sprint);
        head.y += bounce;
        body.y += bounce;
        rightArm.y += bounce;
        leftArm.y += bounce;
        head.xRot += 0.04f * Mth.sin(cycle * 2f) * amount;
    }
}
