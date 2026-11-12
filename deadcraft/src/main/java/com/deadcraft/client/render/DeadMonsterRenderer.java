package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.model.*;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.entity.DeadMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** Renderer for every custom-modelled hostile and boss. */
public class DeadMonsterRenderer extends MobRenderer<DeadMonster, EntityModel<DeadMonster>> {
    private final DeadMob mob;
    private final ResourceLocation texture;

    public DeadMonsterRenderer(EntityRendererProvider.Context ctx, DeadMob mob) {
        super(ctx, model(ctx, mob), shadow(mob));
        this.mob = mob;
        this.texture = DeadCraft.id("textures/entity/" + mob.id + ".png");
        if (mob.hasEyeLayer()) {
            ResourceLocation eyes = DeadCraft.id("textures/entity/" + mob.id + "_eyes.png");
            addLayer(new GlowLayer<>(this, e -> eyes));
        }
    }

    static float shadow(DeadMob mob) {
        return mob.flies() ? 0.3f * mob.renderScale : 0.5f * mob.width * mob.renderScale;
    }

    private static EntityModel<DeadMonster> model(EntityRendererProvider.Context ctx, DeadMob mob) {
        return switch (mob.archetype) {
            case GAUNT -> new GauntModel<>(ctx.bakeLayer(DeadLayers.GAUNT));
            case CRAWLER -> new CrawlerModel<>(ctx.bakeLayer(DeadLayers.CRAWLER));
            case WRAITH -> new WraithModel<>(ctx.bakeLayer(DeadLayers.WRAITH));
            case BRUTE -> {
                BruteModel<DeadMonster> m = new BruteModel<>(ctx.bakeLayer(DeadLayers.BRUTE));
                m.setHorns(mob.has(DeadMob.Features.HORNS));
                yield m;
            }
            default -> throw new IllegalArgumentException("No custom model for " + mob);
        };
    }

    @Override
    public ResourceLocation getTextureLocation(DeadMonster entity) {
        return texture;
    }

    @Override
    protected void scale(DeadMonster entity, PoseStack pose, float partialTick) {
        float s = mob.renderScale;
        if (entity.getPhase() > 0) s *= 1.0f + 0.03f * Mth.sin((entity.tickCount + partialTick) * 0.3f); // enraged pulsing
        pose.scale(s, s, s);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(DeadMonster entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (mob.has(DeadMob.Features.TRANSLUCENT) || entity.isDecoy()) return RenderType.entityTranslucent(texture);
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    protected float getFlipDegrees(DeadMonster entity) {
        return mob.isBoss() ? 0f : super.getFlipDegrees(entity);
    }
}
