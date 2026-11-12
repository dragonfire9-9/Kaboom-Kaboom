package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.entity.DeadMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Skeleton: a crowned giant built on the skeleton frame. */
public class SkeletonBossRenderer extends HumanoidMobRenderer<DeadMonster, SkeletonModel<DeadMonster>> {
    private static final ResourceLocation TEXTURE = DeadCraft.id("textures/entity/skeleton.png");
    private static final ResourceLocation EYES = DeadCraft.id("textures/entity/skeleton_eyes.png");

    public SkeletonBossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SkeletonModel<>(ctx.bakeLayer(ModelLayers.SKELETON)), 1.2f);
        addLayer(new GlowLayer<>(this, e -> EYES));
    }

    @Override
    public ResourceLocation getTextureLocation(DeadMonster entity) { return TEXTURE; }

    @Override
    protected void scale(DeadMonster entity, PoseStack pose, float partialTick) {
        float s = DeadMob.SKELETON.renderScale;
        pose.scale(s, s, s);
    }
}
