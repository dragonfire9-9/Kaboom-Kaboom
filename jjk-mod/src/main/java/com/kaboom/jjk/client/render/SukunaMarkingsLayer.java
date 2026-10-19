package com.kaboom.jjk.client.render;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.client.ClientHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws Sukuna's red markings over the skin of a player he has taken over. */
public class SukunaMarkingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = JJKMod.id("textures/entity/sukuna_markings.png");

    public SukunaMarkingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !ClientHooks.isMarked(player.getId())) return;
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(TEXTURE)), light,
                LivingEntityRenderer.getOverlayCoords(player, 0));
    }
}
