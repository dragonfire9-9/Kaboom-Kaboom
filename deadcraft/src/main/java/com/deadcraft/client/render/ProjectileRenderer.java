package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/** Glowing camera-facing sprites with a soft additive halo. */
public class ProjectileRenderer extends EntityRenderer<DeadProjectile> {
    private static final Map<DeadProjectile.Kind, ResourceLocation> TEXTURES = new EnumMap<>(DeadProjectile.Kind.class);

    static {
        for (DeadProjectile.Kind k : DeadProjectile.Kind.values()) {
            TEXTURES.put(k, DeadCraft.id("textures/entity/projectile/" + k.texture() + ".png"));
        }
    }

    public ProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DeadProjectile entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        DeadProjectile.Kind kind = entity.getKind();
        ResourceLocation tex = TEXTURES.get(kind);
        if (kind == DeadProjectile.Kind.BULLET || kind == DeadProjectile.Kind.PELLET) {
            tracer(entity, partialTick, pose, buffers, kind == DeadProjectile.Kind.BULLET ? 2.6f : 1.4f);
            super.render(entity, yaw, partialTick, pose, buffers, light);
            return;
        }
        float age = entity.tickCount + partialTick;
        float s = kind.size * (1f + 0.12f * (float) Math.sin(age * 0.8f));
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() / 2, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(age * 12f));
        quad(pose, buffers.getBuffer(RenderType.eyes(tex)), s * 2.2f, 0x80);   // halo
        quad(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(tex)), s, 0xff); // core
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** A thin, hot streak trailing back along the bullet's path, always facing the camera. */
    private void tracer(DeadProjectile e, float pt, PoseStack pose, MultiBufferSource buffers, float length) {
        var vel = e.getDeltaMovement();
        if (vel.lengthSqr() < 1e-4) return;
        var dir = vel.normalize();
        var cam = entityRenderDispatcher.camera.getPosition();
        var pos = e.getPosition(pt).add(0, e.getBbHeight() / 2, 0);
        var toCam = cam.subtract(pos).normalize();
        var side = dir.cross(toCam).normalize();
        float len = (float) Math.min(length, vel.length() * 0.9);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose p = pose.last();
        pose.pushPose();
        pose.translate(0, e.getBbHeight() / 2, 0);
        p = pose.last();
        for (float[] layer : new float[][]{{0.06f, 255, 230, 160, 90}, {0.02f, 255, 255, 235, 230}}) {
            float w = layer[0];
            float hx = (float) side.x * w, hy = (float) side.y * w, hz = (float) side.z * w;
            float bx = (float) -dir.x * len, by = (float) -dir.y * len, bz = (float) -dir.z * len;
            int r = (int) layer[1], g = (int) layer[2], b = (int) layer[3], a = (int) layer[4];
            vc.addVertex(p, -hx, -hy, -hz).setColor(r, g, b, a);
            vc.addVertex(p, hx, hy, hz).setColor(r, g, b, a);
            vc.addVertex(p, bx + hx, by + hy, bz + hz).setColor(r, g, b, 0);
            vc.addVertex(p, bx - hx, by - hy, bz - hz).setColor(r, g, b, 0);
        }
        pose.popPose();
    }

    private static void quad(PoseStack pose, VertexConsumer vc, float size, int alpha) {
        PoseStack.Pose p = pose.last();
        float h = size / 2;
        vertex(vc, p, -h, -h, 0, 1, alpha);
        vertex(vc, p, h, -h, 1, 1, alpha);
        vertex(vc, p, h, h, 1, 0, alpha);
        vertex(vc, p, -h, h, 0, 0, alpha);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float u, float v, int alpha) {
        vc.addVertex(p, x, y, 0).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(p, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(DeadProjectile entity) {
        return TEXTURES.get(entity.getKind());
    }
}
