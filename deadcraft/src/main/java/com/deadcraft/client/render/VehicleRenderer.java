package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.model.VehicleModel;
import com.deadcraft.entity.VehicleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class VehicleRenderer<T extends VehicleEntity> extends MobRenderer<T, VehicleModel<T>> {
    private final ResourceLocation texture;

    public VehicleRenderer(EntityRendererProvider.Context ctx, String name, boolean buggy) {
        super(ctx, new VehicleModel<>(ctx.bakeLayer(buggy ? DeadLayers.BUGGY : DeadLayers.SKIFF), buggy), 1.0f);
        this.texture = DeadCraft.id("textures/entity/" + name + ".png");
        ResourceLocation glow = DeadCraft.id("textures/entity/" + name + "_glow.png");
        addLayer(new GlowLayer<>(this, e -> glow));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return texture; }

    @Override
    protected boolean shouldShowName(T entity) { return false; }

    @Override
    protected float getFlipDegrees(T entity) { return 0f; }
}
