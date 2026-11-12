package com.deadcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/** Emissive layer: glowing eyes, lava cracks, headlights. Full-bright and additive, so it glows in the dark. */
public class GlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final Function<T, ResourceLocation> texture;

    public GlowLayer(RenderLayerParent<T, M> parent, Function<T, ResourceLocation> texture) {
        super(parent);
        this.texture = texture;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float amount,
                       float partialTick, float age, float yaw, float pitch) {
        ResourceLocation tex = texture.apply(entity);
        if (tex == null || entity.isInvisible()) return;
        // Eyes flare brighter while the creature is hunting.
        float flicker = 0.85f + 0.15f * (float) Math.sin(age * 0.35f + entity.getId());
        int a = 255;
        int c = Math.round(255 * flicker);
        int color = (a << 24) | (c << 16) | (c << 8) | c;
        var vc = buffers.getBuffer(RenderType.eyes(tex));
        if (getParentModel() instanceof com.deadcraft.client.model.DeadRigModel<?> rig) {
            rig.renderGlow(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        } else {
            getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        }
    }
}
