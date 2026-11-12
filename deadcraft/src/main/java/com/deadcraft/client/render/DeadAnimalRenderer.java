package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.model.DeadRigModel;
import com.deadcraft.entity.DeadAnimal;
import com.deadcraft.entity.DeadMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class DeadAnimalRenderer extends MobRenderer<DeadAnimal, EntityModel<DeadAnimal>> {
    private final DeadMob mob;
    private final ResourceLocation texture;

    public DeadAnimalRenderer(EntityRendererProvider.Context ctx, DeadMob mob) {
        super(ctx, model(ctx, mob), 0.4f * mob.width * mob.renderScale + 0.1f);
        this.mob = mob;
        this.texture = DeadCraft.id("textures/entity/" + mob.id + ".png");
        if (mob.hasEyeLayer()) {
            ResourceLocation eyes = DeadCraft.id("textures/entity/" + mob.id + "_eyes.png");
            addLayer(new GlowLayer<>(this, e -> eyes));
        }
    }

    private static EntityModel<DeadAnimal> model(EntityRendererProvider.Context ctx, DeadMob mob) {
        float speed = mob.archetype == DeadMob.Archetype.CRITTER || mob.archetype == DeadMob.Archetype.FLYER ? 3.0f : 2.0f;
        return new DeadRigModel<>(ctx.bakeLayer(DeadLayers.of(mob.id)), mob.id, speed);
    }

    @Override
    public ResourceLocation getTextureLocation(DeadAnimal entity) { return texture; }

    @Override
    protected void scale(DeadAnimal entity, PoseStack pose, float partialTick) {
        float s = mob.renderScale * (entity.isBaby() ? 0.55f : 1f);
        pose.scale(s, s, s);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(DeadAnimal entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (mob.has(DeadMob.Features.TRANSLUCENT)) return RenderType.entityTranslucent(texture);
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }
}
