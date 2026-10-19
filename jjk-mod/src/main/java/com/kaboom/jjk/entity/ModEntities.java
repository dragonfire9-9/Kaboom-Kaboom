package com.kaboom.jjk.entity;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, JJKMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TechniqueProjectile>> TECHNIQUE_PROJECTILE =
            ENTITIES.register("technique_projectile", () -> EntityType.Builder.<TechniqueProjectile>of(TechniqueProjectile::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(10).updateInterval(1).noSave().build("technique_projectile"));

    public static final Map<CurseType, DeferredHolder<EntityType<?>, EntityType<CurseEntity>>> CURSES = new EnumMap<>(CurseType.class);
    public static final Map<ShikigamiType, DeferredHolder<EntityType<?>, EntityType<ShikigamiEntity>>> SHIKIGAMI = new EnumMap<>(ShikigamiType.class);

    static {
        for (CurseType t : CurseType.values()) {
            CURSES.put(t, ENTITIES.register(t.id, () -> EntityType.Builder.<CurseEntity>of(CurseEntity::new, MobCategory.MONSTER)
                    .sized(t.width, t.height).clientTrackingRange(10).build(t.id)));
        }
        for (ShikigamiType t : ShikigamiType.values()) {
            SHIKIGAMI.put(t, ENTITIES.register(t.id, () -> EntityType.Builder.<ShikigamiEntity>of(ShikigamiEntity::new, MobCategory.CREATURE)
                    .sized(t.width, t.height).clientTrackingRange(10).build(t.id)));
        }
    }

    private static final Map<EntityType<?>, CurseType> CURSE_LOOKUP = new IdentityHashMap<>();
    private static final Map<EntityType<?>, ShikigamiType> SHIKIGAMI_LOOKUP = new IdentityHashMap<>();

    public static synchronized CurseType curseTypeOf(EntityType<?> type) {
        if (CURSE_LOOKUP.isEmpty()) CURSES.forEach((k, v) -> CURSE_LOOKUP.put(v.get(), k));
        return CURSE_LOOKUP.getOrDefault(type, CurseType.FLY_HEAD);
    }

    public static synchronized ShikigamiType shikigamiTypeOf(EntityType<?> type) {
        if (SHIKIGAMI_LOOKUP.isEmpty()) SHIKIGAMI.forEach((k, v) -> SHIKIGAMI_LOOKUP.put(v.get(), k));
        return SHIKIGAMI_LOOKUP.getOrDefault(type, ShikigamiType.DIVINE_DOG);
    }

    public static EntityType<CurseEntity> curse(CurseType t) {
        return CURSES.get(t).get();
    }

    public static EntityType<ShikigamiEntity> shikigami(ShikigamiType t) {
        return SHIKIGAMI.get(t).get();
    }

    private ModEntities() {}
}
