package com.kaboom.jjk.client.render;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.npc.NpcType;
import com.kaboom.jjk.npc.SorcererNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Sorcerers use the player model with a per-character skin ({@code textures/entity/npc/<id>.png}). */
public class SorcererNpcRenderer extends HumanoidMobRenderer<SorcererNpc, PlayerModel<SorcererNpc>> {

    public SorcererNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SorcererNpc entity) {
        return JJKMod.id("textures/entity/npc/" + entity.npcType().id + ".png");
    }

    @Override
    protected void scale(SorcererNpc entity, PoseStack pose, float partialTick) {
        float s = entity.npcType() == NpcType.PANDA ? 1.17f : 0.9375f;
        pose.scale(s, s, s);
    }
}
