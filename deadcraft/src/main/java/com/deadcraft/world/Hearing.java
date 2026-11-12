package com.deadcraft.world;

import com.deadcraft.entity.DeadMonster;
import com.deadcraft.entity.Rotwalker;
import com.deadcraft.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Creatures react to sound. Every noise source (proximity voice chat, text chat, sprinting,
 * gunshots, engines, spells) funnels through {@link #noise}.
 */
public final class Hearing {
    private Hearing() {}

    /**
     * @param radius base distance in blocks at which the noise is still audible
     * @param source the player responsible, if any
     */
    public static void noise(ServerLevel level, Vec3 pos, double radius, @Nullable Player source) {
        if (source != null && source.hasEffect(ModEffects.DREAD)) radius *= 2.0;
        if (source != null && source.isCrouching()) radius *= 0.5;
        double search = radius * 2.0; // the keenest listeners hear twice as far
        AABB box = new AABB(pos, pos).inflate(search);
        for (Monster m : level.getEntitiesOfClass(Monster.class, box)) {
            double d = m.position().distanceTo(pos);
            if (m instanceof DeadMonster dm) {
                double reach = radius * dm.hearingMultiplier();
                if (d <= reach) dm.hearNoise(pos, source, 1.0 - d / reach);
            } else if (m instanceof Rotwalker r) {
                if (d <= radius) r.hearNoise(pos, source, 1.0 - d / radius);
            } else if (d <= radius * 0.6 && m.getTarget() == null) {
                // Vanilla monsters come to look as well.
                m.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.0);
            }
        }
    }
}
