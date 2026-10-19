package com.kaboom.jjk.entity;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, JJKMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TechniqueProjectile>> TECHNIQUE_PROJECTILE =
            ENTITIES.register("technique_projectile", () -> EntityType.Builder.<TechniqueProjectile>of(TechniqueProjectile::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(10).updateInterval(1).noSave().build("technique_projectile"));

    private ModEntities() {}
}
