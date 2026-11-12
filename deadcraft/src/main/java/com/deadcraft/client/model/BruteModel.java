package com.deadcraft.client.model;

import com.deadcraft.entity.DeadMonster;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Hulking knuckle-walkers: Cracked, Blood Demon, Collapse, and the bosses Fracture, Dim Light Demon and Petrified. */
public class BruteModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, jaw, leftHorn, rightHorn, rightArm, leftArm, rightLeg, leftLeg;

    public BruteModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        leftHorn = head.getChild("left_horn");
        rightHorn = head.getChild("right_horn");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    @Override
    public ModelPart root() { return root; }

    public void setHorns(boolean visible) {
        leftHorn.visible = visible;
        rightHorn.visible = visible;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = yaw * Mth.DEG_TO_RAD * 0.8f;
        head.xRot = pitch * Mth.DEG_TO_RAD * 0.6f;
        float heave = Anim.breathe(age, 0.06f, 0.04f);
        body.xRot += heave;
        jaw.xRot += 0.15f + Mth.abs(Mth.sin(age * 0.05f)) * 0.15f;
        // Heavy, rolling gait.
        float sway = Mth.sin(limbSwing * 0.5f) * amount;
        body.zRot = sway * 0.08f;
        head.zRot = -sway * 0.06f;
        rightLeg.xRot = Anim.swing(limbSwing, amount, 0.5f, 0.9f, 0);
        leftLeg.xRot = Anim.swing(limbSwing, amount, 0.5f, 0.9f, Mth.PI);
        rightArm.xRot = Anim.swing(limbSwing, amount, 0.5f, 0.8f, Mth.PI) - 0.15f;
        leftArm.xRot = Anim.swing(limbSwing, amount, 0.5f, 0.8f, 0) - 0.15f;
        rightArm.zRot = 0.1f + sway * 0.05f;
        leftArm.zRot = -0.1f + sway * 0.05f;
        boolean slam = e instanceof DeadMonster m && m.getCastTicks() > 0;
        if (slam) {
            float t = ((DeadMonster) e).getCastTicks() / 15f;
            rightArm.xRot = -2.8f * t;
            leftArm.xRot = -2.8f * t;
            body.xRot -= 0.3f * t;
        }
        if (attackTime > 0) {
            float a = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= a * 2.0f;
            body.yRot = -a * 0.4f;
            jaw.xRot += a * 0.5f;
        }
    }
}
