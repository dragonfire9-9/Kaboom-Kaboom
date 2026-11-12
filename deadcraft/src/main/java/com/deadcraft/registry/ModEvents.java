package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadAnimal;
import com.deadcraft.entity.DeadMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = DeadCraft.MODID)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        for (DeadMob mob : DeadMob.values()) {
            event.put(ModEntities.type(mob), ModEntities.attributes(mob));
        }
        event.put(ModEntities.RUIN_RUNNER.get(), ModEntities.vehicleAttributes(60, 0.42));
        event.put(ModEntities.SKY_SKIFF.get(), ModEntities.vehicleAttributes(40, 0.5));
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (DeadMob mob : DeadMob.values()) {
            if (mob.isBoss()) continue;
            if (mob.isPeaceful()) {
                EntityType<DeadAnimal> type = ModEntities.type(mob);
                event.register(type, mob.flies() ? SpawnPlacementTypes.NO_RESTRICTIONS : SpawnPlacementTypes.ON_GROUND,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, DeadAnimal::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            } else {
                EntityType<Monster> type = ModEntities.type(mob);
                event.register(type, mob.flies() ? SpawnPlacementTypes.NO_RESTRICTIONS : SpawnPlacementTypes.ON_GROUND,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        mob.flies() ? Monster::checkAnyLightMonsterSpawnRules : Monster::checkMonsterSpawnRules,
                        RegisterSpawnPlacementsEvent.Operation.REPLACE);
            }
        }
    }

}
