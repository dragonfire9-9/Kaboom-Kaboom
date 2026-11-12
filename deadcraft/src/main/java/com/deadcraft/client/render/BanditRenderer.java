package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.entity.DeadMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Bandits use a human body and actually aim the guns they carry. */
public class BanditRenderer extends HumanoidMobRenderer<DeadMonster, BanditRenderer.BanditModel> {
    private final DeadMob mob;
    private final ResourceLocation texture;

    public BanditRenderer(EntityRendererProvider.Context ctx, DeadMob mob) {
        super(ctx, new BanditModel(ctx.bakeLayer(DeadLayers.of(mob.id))), 0.5f);
        this.mob = mob;
        this.texture = DeadCraft.id("textures/entity/" + mob.id + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(DeadMonster entity) { return texture; }

    @Override
    protected void scale(DeadMonster entity, PoseStack pose, float partialTick) {
        pose.scale(mob.renderScale, mob.renderScale, mob.renderScale);
    }

    public static class BanditModel extends HumanoidModel<DeadMonster> {
        private final ModelPart coatTail;

        public BanditModel(ModelPart root) {
            super(root);
            this.coatTail = body.hasChild("coat_tail") ? body.getChild("coat_tail") : null;
        }

        @Override
        public void setupAnim(DeadMonster e, float limbSwing, float amount, float age, float yaw, float pitch) {
            boolean armed = !e.getMainHandItem().isEmpty();
            rightArmPose = armed && e.isAggressive() ? ArmPose.CROSSBOW_HOLD : armed ? ArmPose.ITEM : ArmPose.EMPTY;
            leftArmPose = ArmPose.EMPTY;
            super.setupAnim(e, limbSwing, amount, age, yaw, pitch);
            if (coatTail != null) {
                // the coat flaps behind them as they run
                coatTail.xRot = 0.08f + amount * 0.55f + net.minecraft.util.Mth.sin(age * 0.2f) * 0.04f
                        + net.minecraft.util.Mth.cos(limbSwing * 1.3f) * amount * 0.12f;
            }
        }
    }
}
