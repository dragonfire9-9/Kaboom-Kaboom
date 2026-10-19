package com.kaboom.jjk.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/** Target choice for summoned allies (shikigami and released curses): protect the owner. */
public final class AllyTargeting {

    public static LivingEntity pick(Mob ally, LivingEntity owner) {
        if (owner == null) return null;
        LivingEntity attacker = owner.getLastHurtByMob();
        if (valid(ally, owner, attacker)) return attacker;
        LivingEntity victim = owner.getLastHurtMob();
        if (valid(ally, owner, victim)) return victim;
        AABB box = ally.getBoundingBox().inflate(12);
        return ally.level().getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && valid(ally, owner, e)).stream()
                .min(Comparator.comparingDouble(ally::distanceToSqr)).orElse(null);
    }

    private static boolean valid(Mob ally, LivingEntity owner, LivingEntity t) {
        if (t == null || !t.isAlive() || t == owner || t == ally) return false;
        if (t instanceof ShikigamiEntity s && owner.getUUID().equals(s.getOwnerUUID())) return false;
        if (t instanceof CurseEntity c && owner.getUUID().equals(c.getOwnerUUID())) return false;
        return t.distanceToSqr(ally) < 32 * 32;
    }

    private AllyTargeting() {}
}
