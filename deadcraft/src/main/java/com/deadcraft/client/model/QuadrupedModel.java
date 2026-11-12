package com.deadcraft.client.model;

import com.deadcraft.entity.DeadMob;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Four-legged grazers and hunters. Ears, horns, antlers, tusks and tail are toggled per creature. */
public class QuadrupedModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, ears, horns, antlers, tusks, tail, rh, lh, rf, lf;

    public QuadrupedModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        ears = head.getChild("ears");
        horns = head.getChild("horns");
        antlers = head.getChild("antlers");
        tusks = head.getChild("tusks");
        tail = root.getChild("tail");
        rh = root.getChild("right_hind_leg");
        lh = root.getChild("left_hind_leg");
        rf = root.getChild("right_front_leg");
        lf = root.getChild("left_front_leg");
    }

    @Override
    public ModelPart root() { return root; }

    public void configure(DeadMob mob) {
        ears.visible = mob.has(DeadMob.Features.EARS);
        horns.visible = mob.has(DeadMob.Features.HORNS);
        antlers.visible = mob.has(DeadMob.Features.ANTLERS);
        tusks.visible = mob.has(DeadMob.Features.TUSKS);
        tail.visible = mob.has(DeadMob.Features.TAIL);
    }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        // Idle grazing: dip the head every few seconds while standing still.
        float graze = Math.max(0, Mth.sin(age * 0.03f + e.getId())) * (1 - Math.min(1, amount * 3));
        head.xRot += graze * graze * 0.9f;
        rh.xRot = Anim.swing(limbSwing, amount, 0.6662f, 1.3f, 0);
        lh.xRot = Anim.swing(limbSwing, amount, 0.6662f, 1.3f, Mth.PI);
        rf.xRot = Anim.swing(limbSwing, amount, 0.6662f, 1.3f, Mth.PI);
        lf.xRot = Anim.swing(limbSwing, amount, 0.6662f, 1.3f, 0);
        body.y += Mth.abs(Mth.cos(limbSwing * 0.6662f)) * amount * -0.6f;
        tail.zRot = Mth.sin(age * 0.15f) * 0.2f;
        tail.xRot += amount * 0.4f;
        ears.xRot = Math.max(0, Mth.sin(age * 0.2f + e.getId())) > 0.97f ? -0.4f : 0f;
    }
}
