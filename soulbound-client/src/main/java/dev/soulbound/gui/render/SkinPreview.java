package dev.soulbound.gui.render;

import dev.soulbound.SoulBound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * A rotatable 3D player model drawn from a skin texture (no entity or world needed, so it
 * works on the title screen too). Drag to spin it; it sways gently when left alone.
 */
public final class SkinPreview {
    /** The Sword PvP Bot's own skin. */
    public static final Identifier BOT_SKIN = Identifier.of(SoulBound.MOD_ID, "textures/entity/sword_bot.png");

    public static SkinTextures botSkin() {
        return SkinTextures.create(new AssetInfo.TextureAssetInfo(BOT_SKIN, BOT_SKIN), null, null, PlayerSkinType.WIDE);
    }

    private PlayerEntityModel wide, slim;
    private float xRot = -5, yRot = 30, spinVel;
    private long lastDrag;

    private void ensureModels() {
        if (wide != null) return;
        var models = MinecraftClient.getInstance().getLoadedEntityModels();
        wide = new PlayerEntityModel(models.getModelPart(EntityModelLayers.PLAYER), false);
        // two fields named PLAYER_SLIM exist in the mappings (model layer and equipment data), so build the layer directly
        slim = new PlayerEntityModel(models.getModelPart(new EntityModelLayer(Identifier.ofVanilla("player_slim"), "main")), true);
    }

    /**
     * Draws the model with its feet at {@code feetY}, centred on {@code cx}, {@code height} GUI units tall.
     */
    public void render(DrawContext ctx, SkinTextures skin, float cx, float feetY, float height) {
        ensureModels();
        boolean idle = System.currentTimeMillis() - lastDrag > 1500;
        float dt = Anim.dt();
        if (idle) {
            // ease back toward a gentle sway around the front-three-quarter view
            float target = 25 + 18 * (float) Math.sin(Anim.seconds() * 0.7f);
            yRot += spinVel * dt;
            spinVel *= (float) Math.exp(-3 * dt);
            if (Math.abs(spinVel) < 20) yRot += (wrap(target - yRot)) * Math.min(1, dt * 2.5f);
            xRot += (-5 - xRot) * Math.min(1, dt * 3);
        }
        float boxH = height / 0.97f;
        int y2 = Math.round(feetY + boxH * 0.015f), y1 = Math.round(y2 - boxH);
        int halfW = Math.round(boxH * 0.4f);
        float scale = 0.97f * boxH / 2.125f;
        PlayerEntityModel model = skin.model() == PlayerSkinType.SLIM ? slim : wide;
        ctx.addPlayerSkin(model, skin.body().texturePath(), scale, xRot, yRot, -1.0625f,
                Math.round(cx) - halfW, y1, Math.round(cx) + halfW, y2);
    }

    public void drag(double dx, double dy) {
        lastDrag = System.currentTimeMillis();
        xRot = MathHelper.clamp(xRot - (float) dy * 2.5f, -50, 50);
        yRot += (float) dx * 2.5f;
        spinVel = (float) dx * 2.5f / Math.max(0.008f, Anim.dt()) * 0.35f;
    }

    private static float wrap(float deg) { return MathHelper.wrapDegrees(deg); }
}
