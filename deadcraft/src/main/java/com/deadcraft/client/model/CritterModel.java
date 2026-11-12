package com.deadcraft.client.model;

import com.deadcraft.entity.DeadMob;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Small ground creatures: hares, toads, snails, tortoises, beetles and wisps. */
public class CritterModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, ears, shell, tail, rh, lh, rf, lf;
    private boolean hops;
    private boolean floats;

    public CritterModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        ears = head.getChild("ears");
        shell = root.getChild("shell");
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
        shell.visible = mob.has(DeadMob.Features.SHELL);
        tail.visible = mob.has(DeadMob.Features.TAIL);
        hops = mob == DeadMob.DUSKHARE || mob == DeadMob.CANDLEWICK_TOAD;
        floats = mob.flies();
    }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        float lift = 0;
        if (floats) {
            lift = -2f + Mth.sin(age * 0.15f) * 1.2f;
        } else if (hops) {
            lift = -Math.max(0, Mth.sin(limbSwing * 0.8f)) * 3f * amount;
        }
        for (ModelPart p : new ModelPart[]{body, head, shell, tail, rh, lh, rf, lf}) p.y += lift;
        rh.xRot = Anim.swing(limbSwing, amount, 1.2f, 1.2f, 0);
        lh.xRot = Anim.swing(limbSwing, amount, 1.2f, 1.2f, Mth.PI);
        rf.xRot = Anim.swing(limbSwing, amount, 1.2f, 1.2f, Mth.PI);
        lf.xRot = Anim.swing(limbSwing, amount, 1.2f, 1.2f, 0);
        ears.xRot += Mth.sin(age * 0.3f) * 0.1f;
        tail.yRot = Mth.sin(age * 0.25f) * 0.3f;
        body.xRot = Anim.breathe(age, 0.2f, 0.04f);
    }
}
