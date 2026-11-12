package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.model.ModelGeometry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Model layer ids for every DeadCraft model. */
public final class DeadLayers {
    public static final ModelLayerLocation GAUNT = layer("gaunt");
    public static final ModelLayerLocation CRAWLER = layer("crawler");
    public static final ModelLayerLocation WRAITH = layer("wraith");
    public static final ModelLayerLocation BRUTE = layer("brute");
    public static final ModelLayerLocation QUADRUPED = layer("quadruped");
    public static final ModelLayerLocation CRITTER = layer("critter");
    public static final ModelLayerLocation FLYER = layer("flyer");
    public static final ModelLayerLocation BUGGY = layer("buggy");
    public static final ModelLayerLocation SKIFF = layer("skiff");

    private DeadLayers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(DeadCraft.id(name), "main");
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(GAUNT, ModelGeometry::gaunt);
        e.registerLayerDefinition(CRAWLER, ModelGeometry::crawler);
        e.registerLayerDefinition(WRAITH, ModelGeometry::wraith);
        e.registerLayerDefinition(BRUTE, ModelGeometry::brute);
        e.registerLayerDefinition(QUADRUPED, ModelGeometry::quadruped);
        e.registerLayerDefinition(CRITTER, ModelGeometry::critter);
        e.registerLayerDefinition(FLYER, ModelGeometry::flyer);
        e.registerLayerDefinition(BUGGY, ModelGeometry::buggy);
        e.registerLayerDefinition(SKIFF, ModelGeometry::skiff);
    }
}
