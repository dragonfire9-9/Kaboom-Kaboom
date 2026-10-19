package com.kaboom.jjk.entity;

import com.kaboom.jjk.JJKMod;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = JJKMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class EntityEvents {

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        ModEntities.CURSES.forEach((t, h) -> event.put(h.get(), CurseEntity.attributes(t).build()));
        ModEntities.SHIKIGAMI.forEach((t, h) -> event.put(h.get(), ShikigamiEntity.attributes(t).build()));
    }

    @SubscribeEvent
    public static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        ModEntities.CURSES.forEach((t, h) -> event.register(h.get(),
                t.flying ? SpawnPlacementTypes.NO_RESTRICTIONS : SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CurseEntity::checkCurseSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }

    private EntityEvents() {}
}
