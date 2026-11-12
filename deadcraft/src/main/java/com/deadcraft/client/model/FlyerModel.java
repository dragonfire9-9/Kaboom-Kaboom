package com.deadcraft.client.model;

import com.deadcraft.entity.DeadMob;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Birds and moths. Wings beat fast in the air and fold on the ground. */
public class FlyerModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, ears, rightWing, leftWing, tail, rightLeg, leftLeg;

    public FlyerModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        ears = head.getChild("ears");
        rightWing = root.getChild("right_wing");
        leftWing = root.getChild("left_wing");
        tail = root.getChild("tail");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    @Override
    public ModelPart root() { return root; }

    public void configure(DeadMob mob) {
        ears.visible = mob.has(DeadMob.Features.EARS);
        tail.visible = mob.has(DeadMob.Features.TAIL);
    }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        boolean airborne = !e.onGround();
        if (airborne) {
            float flap = Mth.sin(age * 1.6f) * 1.1f;
            rightWing.zRot = flap;
            leftWing.zRot = -flap;
            rightLeg.xRot = 0.8f;
            leftLeg.xRot = 0.8f;
            body.xRot = 0.15f;
        } else {
            rightWing.zRot = 1.3f;
            leftWing.zRot = -1.3f;
            rightWing.yRot = -0.2f;
            leftWing.yRot = 0.2f;
            rightLeg.xRot = Anim.swing(limbSwing, amount, 1.3f, 1.2f, 0);
            leftLeg.xRot = Anim.swing(limbSwing, amount, 1.3f, 1.2f, Mth.PI);
            head.xRot += Math.max(0, Mth.sin(age * 0.2f + e.getId())) > 0.95f ? 0.7f : 0f; // pecking
        }
        tail.xRot += Mth.sin(age * 0.2f) * 0.1f;
    }
}
