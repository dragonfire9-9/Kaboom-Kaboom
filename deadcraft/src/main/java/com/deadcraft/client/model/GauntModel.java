package com.deadcraft.client.model;

import com.deadcraft.entity.DeadMonster;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Tall, starved figures: The Hollow, Scorched, The Forgotten. Arms drag, the head jerks. */
public class GauntModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, jaw, rightArm, leftArm, rightLeg, leftLeg;

    public GauntModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        boolean frozen = e instanceof DeadMonster m && m.isGazeFrozen();
        int id = e.getId();
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        if (!frozen) {
            head.zRot = 0.15f * Mth.sin(age * 0.05f + id) + Anim.twitch(age, id, 0.7f);
            head.yRot += Anim.twitch(age + 13, id, 0.5f);
        } else {
            head.zRot = 0.35f; // a frozen, sideways stare
        }
        jaw.xRot += 0.25f + 0.2f * Mth.sin(age * 0.09f) + (frozen ? 0.4f : 0f);
        body.xRot += Anim.breathe(age, 0.07f, 0.03f) + amount * 0.25f;
        rightLeg.xRot = Anim.swing(limbSwing, amount, 0.55f, 1.1f, 0);
        leftLeg.xRot = Anim.swing(limbSwing, amount, 0.55f, 1.1f, Mth.PI);
        // Long arms sway loosely and lag behind the body.
        rightArm.xRot += Anim.swing(limbSwing, amount, 0.55f, 0.7f, Mth.PI) - 0.1f * amount;
        leftArm.xRot += Anim.swing(limbSwing, amount, 0.55f, 0.7f, 0) - 0.1f * amount;
        rightArm.zRot += Mth.sin(age * 0.06f) * 0.05f;
        leftArm.zRot -= Mth.sin(age * 0.06f + 1) * 0.05f;
        if (attackTime > 0) {
            float a = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= a * 2.2f;
            leftArm.xRot -= a * 1.6f;
            jaw.xRot += a * 0.6f;
        }
    }
}
