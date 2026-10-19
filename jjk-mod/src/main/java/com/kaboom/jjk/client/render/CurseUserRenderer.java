package com.kaboom.jjk.client.render;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.npc.CurseUserEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CurseUserRenderer extends HumanoidMobRenderer<CurseUserEntity, PlayerModel<CurseUserEntity>> {
    private static final ResourceLocation TEXTURE = JJKMod.id("textures/entity/npc/curse_user.png");

    public CurseUserRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(CurseUserEntity entity) {
        return TEXTURE;
    }
}
