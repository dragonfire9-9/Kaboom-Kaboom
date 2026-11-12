package com.deadcraft.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Six-legged crawling things: Severed, Parasite, Wretched. */
public class CrawlerModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, jaw, abdomen;
    private final ModelPart[] right = new ModelPart[3], left = new ModelPart[3];

    public CrawlerModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        abdomen = root.getChild("abdomen");
        for (int i = 0; i < 3; i++) {
            right[i] = root.getChild("right_leg" + i);
            left[i] = root.getChild("left_leg" + i);
        }
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        int id = e.getId();
        head.yRot = yaw * Mth.DEG_TO_RAD * 0.7f + Anim.twitch(age, id, 0.6f);
        head.xRot = pitch * Mth.DEG_TO_RAD * 0.5f;
        head.zRot = Anim.twitch(age + 7, id, 0.4f);
        jaw.xRot += 0.3f * Mth.abs(Mth.sin(age * 0.4f));
        float bob = Mth.sin(limbSwing * 1.3f) * amount * 0.8f;
        body.y += bob;
        head.y += bob;
        abdomen.y += bob;
        abdomen.xRot = Anim.breathe(age, 0.12f, 0.08f);
        abdomen.yRot = Mth.sin(limbSwing * 0.65f) * amount * 0.3f;
        for (int i = 0; i < 3; i++) {
            float phase = i * 2.1f;
            float lift = Math.max(0, Mth.sin(limbSwing * 1.3f + phase)) * 0.45f * amount;
            float sweep = Mth.cos(limbSwing * 1.3f + phase) * 0.4f * amount;
            right[i].yRot += sweep;
            right[i].zRot -= lift;
            left[i].yRot -= sweep;
            left[i].zRot += lift;
            // idle skittering
            right[i].zRot += Anim.twitch(age + i * 5, id + i, 0.15f);
            left[i].zRot -= Anim.twitch(age + i * 3, id - i, 0.15f);
        }
        if (attackTime > 0) {
            float a = Mth.sin(attackTime * Mth.PI);
            head.xRot -= a * 0.6f;
            jaw.xRot += a * 0.8f;
            right[0].yRot += a * 0.9f;
            left[0].yRot -= a * 0.9f;
        }
    }
}
