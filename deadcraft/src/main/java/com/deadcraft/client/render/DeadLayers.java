package com.deadcraft.client.render;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.model.ModelGeometry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.HashMap;
import java.util.Map;

/** One model layer per DeadCraft model (every creature, humanoid family and vehicle has its own rig). */
public final class DeadLayers {
    private static final Map<String, ModelLayerLocation> LAYERS = new HashMap<>();

    private DeadLayers() {}

    public static ModelLayerLocation of(String id) {
        return LAYERS.computeIfAbsent(id, k -> new ModelLayerLocation(DeadCraft.id(k), "main"));
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions e) {
        ModelGeometry.ALL.forEach((id, def) -> e.registerLayerDefinition(of(id), def));
    }
}
