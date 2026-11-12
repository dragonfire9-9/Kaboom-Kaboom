package com.deadcraft.client.model;

import com.deadcraft.entity.DeadMonster;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Floating robed spirits: Depart, Illusion, Ghost, Star Fall and Chronos. */
public class WraithModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, head, body, robe, tail, rightArm, leftArm;

    public WraithModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        body = root.getChild("body");
        robe = body.getChild("robe");
        tail = robe.getChild("tail");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float hover = Mth.sin(age * 0.08f) * 1.5f;
        for (ModelPart p : new ModelPart[]{head, body, rightArm, leftArm}) p.y += hover;
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        head.zRot = Anim.twitch(age, e.getId(), 0.5f);
        robe.xRot += Mth.sin(age * 0.1f) * 0.1f + amount * 0.4f;
        tail.xRot += Mth.sin(age * 0.1f - 0.8f) * 0.18f + amount * 0.5f;
        tail.zRot = Mth.sin(age * 0.07f) * 0.12f;
        body.xRot = amount * 0.3f;
        boolean casting = e instanceof DeadMonster m && m.getCastTicks() > 0;
        boolean aggressive = e instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive();
        float reach = casting ? -2.4f : aggressive ? -1.4f : -0.2f;
        rightArm.xRot = reach + Mth.sin(age * 0.09f) * 0.15f;
        leftArm.xRot = reach + Mth.sin(age * 0.09f + 1.3f) * 0.15f;
        rightArm.zRot = casting ? 0.5f : 0.1f + Mth.sin(age * 0.05f) * 0.05f;
        leftArm.zRot = casting ? -0.5f : -0.1f - Mth.sin(age * 0.05f) * 0.05f;
        if (attackTime > 0) {
            float a = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= a * 1.2f;
            leftArm.xRot -= a * 1.2f;
        }
    }
}
