package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

public class RotwalkerRenderer extends ZombieRenderer {
    private static final ResourceLocation TEXTURE = DeadCraft.id("textures/entity/rotwalker.png");
    private static final ResourceLocation EYES = DeadCraft.id("textures/entity/rotwalker_eyes.png");

    public RotwalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, DeadLayers.of("rotwalker"), net.minecraft.client.model.geom.ModelLayers.ZOMBIE_INNER_ARMOR,
                net.minecraft.client.model.geom.ModelLayers.ZOMBIE_OUTER_ARMOR);
        addLayer(new GlowLayer<>(this, e -> EYES));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) { return TEXTURE; }
}
