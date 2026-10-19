package com.kaboom.jjk.client.render;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CurseSight;
import com.kaboom.jjk.entity.CurseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Generic GeckoLib renderer for every mod creature: model, texture and animations all come from
 * {@code <id>.geo.json}, {@code <id>.png} and {@code <id>.animation.json}.
 * Curses are skipped entirely for viewers who cannot perceive them.
 */
public class JJKGeoRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {

    public JJKGeoRenderer(EntityRendererProvider.Context context, String id, float shadow) {
        super(context, new DefaultedEntityGeoModel<>(JJKMod.id(id), false));
        this.shadowRadius = shadow;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        if (entity instanceof CurseEntity) {
            var viewer = Minecraft.getInstance().player;
            if (viewer != null && !viewer.isSpectator() && !CurseSight.canSeeCurses(viewer)) return false;
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
