package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, DeadCraft.MODID);

    private static final Map<DeadMob, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> MOBS = new EnumMap<>(DeadMob.class);
    private static volatile Map<EntityType<?>, DeadMob> byType;

    static {
        for (DeadMob mob : DeadMob.values()) {
            MOBS.put(mob, switch (mob.kind) {
                case PEACEFUL -> ENTITY_TYPES.register(mob.id, () -> EntityType.Builder.<DeadAnimal>of(DeadAnimal::new, MobCategory.CREATURE)
                        .sized(mob.width, mob.height).clientTrackingRange(10).build(mob.id));
                case ZOMBIE -> ENTITY_TYPES.register(mob.id, () -> EntityType.Builder.<Rotwalker>of(Rotwalker::new, MobCategory.MONSTER)
                        .sized(mob.width, mob.height).eyeHeight(1.74f).clientTrackingRange(8).build(mob.id));
                case HOSTILE, BOSS -> ENTITY_TYPES.register(mob.id, () -> {
                    EntityType.Builder<DeadMonster> b = EntityType.Builder.<DeadMonster>of(DeadMonster::new, MobCategory.MONSTER)
                            .sized(mob.width, mob.height).clientTrackingRange(mob.isBoss() ? 16 : 10);
                    if (fireImmune(mob)) b.fireImmune();
                    return b.build(mob.id);
                });
            });
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<DeadProjectile>> PROJECTILE = ENTITY_TYPES.register("projectile",
            () -> EntityType.Builder.<DeadProjectile>of(DeadProjectile::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(8).updateInterval(1).build("projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<RuinRunnerEntity>> RUIN_RUNNER = ENTITY_TYPES.register("ruin_runner",
            () -> EntityType.Builder.<RuinRunnerEntity>of(RuinRunnerEntity::new, MobCategory.MISC).sized(2.2f, 1.4f)
                    .clientTrackingRange(10).build("ruin_runner"));
    public static final DeferredHolder<EntityType<?>, EntityType<SkySkiffEntity>> SKY_SKIFF = ENTITY_TYPES.register("sky_skiff",
            () -> EntityType.Builder.<SkySkiffEntity>of(SkySkiffEntity::new, MobCategory.MISC).sized(2.0f, 1.0f)
                    .clientTrackingRange(10).build("sky_skiff"));

    private ModEntities() {}

    private static boolean fireImmune(DeadMob mob) {
        return switch (mob.ability) {
            case SCORCHED, BLOOD_DEMON, STAR_FALL, FRACTURE, DIM_LIGHT_DEMON, PETRIFIED, CRACKED, COLLAPSE -> true;
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mob> EntityType<T> type(DeadMob mob) {
        return (EntityType<T>) MOBS.get(mob).get();
    }

    public static DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> holder(DeadMob mob) {
        return MOBS.get(mob);
    }

    /** Looks up the creature definition for a registered entity type. */
    public static DeadMob mobOf(EntityType<?> type) {
        Map<EntityType<?>, DeadMob> map = byType;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (Map.Entry<DeadMob, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> e : MOBS.entrySet()) {
                map.put(e.getValue().get(), e.getKey());
            }
            byType = map;
        }
        DeadMob mob = map.get(type);
        if (mob == null) throw new IllegalArgumentException("Not a DeadCraft creature: " + type);
        return mob;
    }

    public static AttributeSupplier attributes(DeadMob mob) {
        AttributeSupplier.Builder b = switch (mob.kind) {
            case PEACEFUL -> Mob.createMobAttributes();
            case ZOMBIE -> Zombie.createAttributes();
            case HOSTILE, BOSS -> Monster.createMonsterAttributes();
        };
        b.add(Attributes.MAX_HEALTH, mob.health)
                .add(Attributes.MOVEMENT_SPEED, mob.speed)
                .add(Attributes.ARMOR, mob.armor)
                .add(Attributes.FLYING_SPEED, mob.speed * 1.6)
                .add(Attributes.FOLLOW_RANGE, mob.isBoss() ? 64 : mob.isPeaceful() ? 16 : 40);
        if (!mob.isPeaceful()) {
            b.add(Attributes.ATTACK_DAMAGE, mob.attack)
                    .add(Attributes.ATTACK_KNOCKBACK, mob.isBoss() ? 1.5 : mob.archetype == DeadMob.Archetype.BRUTE ? 1.0 : 0.2);
        }
        if (mob.isBoss()) {
            b.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 1.5).add(Attributes.SCALE, 1.0);
        } else if (mob.archetype == DeadMob.Archetype.BRUTE) {
            b.add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.STEP_HEIGHT, 1.0);
        }
        return b.build();
    }

    public static AttributeSupplier vehicleAttributes(double health, double speed) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.STEP_HEIGHT, 1.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10)
                .build();
    }
}
