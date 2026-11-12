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

    /**
     * Third-person swings for everything in hand (see {@link com.deadcraft.client.anim.SwingStyle}):
     * knives stab, heavy blades and axes come down overhead with the whole body, swords cut a wide
     * horizontal arc, tools swing with a lean into the stroke, and an empty hand throws a jab.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void deadcraft$swing(T entity, float limbSwing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player) || !DeadConfig.PLAYER_ANIMATIONS.get()) return;
        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
        float t = self.attackTime;
        if (t <= 0) return;
        var style = com.deadcraft.client.anim.SwingStyle.of(entity.getMainHandItem());
        boolean right = entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        ModelPart arm = right ? rightArm : leftArm;
        ModelPart off = right ? leftArm : rightArm;
        float side = right ? 1f : -1f;
        float s = Mth.sin(t * Mth.PI);
        switch (style) {
            case STAB, JAB -> {
                // a quick thrust: shoulder turns in, arm drives straight out, off hand guards
                arm.xRot = -Mth.HALF_PI - 0.1f + head.xRot * 0.6f;
                arm.yRot = -0.12f * side + head.yRot;
                arm.zRot = 0;
                arm.z = -4f * s;
                body.yRot = 0.4f * s * side;
                off.xRot = -0.9f;
                off.yRot = 0.35f * side;
                if (style == com.deadcraft.client.anim.SwingStyle.JAB) off.xRot = -1.2f;
            }
            case OVERHEAD -> {
                float a = t < 0.4f ? Mth.lerp(t / 0.4f, 0f, -2.9f) : Mth.lerp((t - 0.4f) / 0.6f, -2.9f, -0.4f);
                arm.xRot = a;
                arm.yRot = 0.1f * side;
                arm.zRot = 0.05f * side;
                off.xRot = a * 0.85f;
                off.yRot = -0.25f * side;
                body.xRot = t < 0.4f ? -0.15f * (t / 0.4f) : 0.35f * Mth.sin((t - 0.4f) / 0.6f * Mth.PI);
                head.xRot += body.xRot * 0.5f;
            }
            case ARC -> {
                arm.xRot = -Mth.HALF_PI + 0.2f;
                arm.yRot = Mth.lerp(t, 1.1f, -1.0f) * side;
                arm.zRot = 0;
                body.yRot = Mth.lerp(t, 0.5f, -0.6f) * side;
                off.xRot = -0.4f;
                off.zRot = -0.3f * side;
            }
            case TOOL -> {
                // vanilla's pick swing plus a lean into the stroke
                body.xRot += 0.25f * s;
                head.xRot += 0.15f * s;
            }
            default -> {}
        }
    }

    /**
     * Cinematic third-person walk, run and idle: the pelvis bobs and sways, the torso counter-
     * rotates against the legs and leans into a sprint, arms swing as pendulums (pumping and
     * bending outward at a run) and the head stays steady. Standing still, the player breathes.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void deadcraft$cinematicGait(T entity, float limbSwing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player p) || !DeadConfig.PLAYER_ANIMATIONS.get()) return;
        if (crouching || swimAmount > 0 || p.isPassenger() || p.isFallFlying() || p.isSpectator()) return;
        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
        if (self.attackTime > 0) return; // the swing owns the body this frame

        if (amount < 0.05f) {
            // idle: slow breathing in the chest and shoulders, a gentle weight shift
            float breath = Mth.sin(age * 0.09f);
            body.xRot += 0.015f * breath;
            rightArm.zRot += 0.03f + 0.02f * breath;
            leftArm.zRot -= 0.03f + 0.02f * breath;
            rightArm.y += 0.25f * breath;
            leftArm.y += 0.25f * breath;
            head.xRot += 0.02f * Mth.sin(age * 0.09f - 0.6f);
            body.zRot += 0.012f * Mth.sin(age * 0.03f);
            return;
        }

        float sprint = p.isSprinting() ? 1f : 0f;
        float cycle = limbSwing * 0.6662f;
        // torso counter-twist and hip sway
        float twist = Mth.cos(cycle) * amount * (0.12f + 0.12f * sprint);
        body.yRot += twist;
        body.zRot += Mth.sin(cycle) * amount * 0.035f;
        head.yRot -= twist * 0.7f;                      // the head stays on target
        rightArm.z = Mth.sin(body.yRot) * 5.0f;
        rightArm.x = -Mth.cos(body.yRot) * 5.0f;
        leftArm.z = -Mth.sin(body.yRot) * 5.0f;
        leftArm.x = Mth.cos(body.yRot) * 5.0f;
        rightArm.yRot += body.yRot;
        leftArm.yRot += body.yRot;

        // arms: pendulum swing, pumping harder and held a little forward at a sprint
        rightArm.xRot = rightArm.xRot * (1f + 0.5f * sprint) - 0.25f * sprint;
        leftArm.xRot = leftArm.xRot * (1f + 0.5f * sprint) - 0.25f * sprint;
        rightArm.zRot += 0.06f + 0.1f * sprint;
        leftArm.zRot -= 0.06f + 0.1f * sprint;

        // legs: longer stride at a sprint
        rightLeg.xRot *= 1f + 0.35f * sprint;
        leftLeg.xRot *= 1f + 0.35f * sprint;

        // the whole body leans into a run
        float lean = 0.06f * amount + 0.22f * sprint;
        body.xRot += lean;
        head.xRot -= lean * 0.6f;
        // the torso pivots at the neck, so its hips swing back: keep the legs under them
        float hipBack = Mth.sin(lean) * 12f, hipUp = (1f - Mth.cos(lean)) * 12f;
        rightLeg.z += hipBack;
        leftLeg.z += hipBack;
        rightLeg.y -= hipUp;
        leftLeg.y -= hipUp;

        // stride bounce: the body drops on each footfall
        float bounce = Mth.abs(Mth.cos(cycle)) * amount * (0.6f + 0.7f * sprint);
        head.y += bounce;
        body.y += bounce;
        rightArm.y += bounce;
        leftArm.y += bounce;
        head.xRot += 0.04f * Mth.sin(cycle * 2f) * amount;
    }
}
