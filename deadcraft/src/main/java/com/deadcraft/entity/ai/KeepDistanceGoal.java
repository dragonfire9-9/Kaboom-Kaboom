package com.deadcraft.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Ranged combat movement: close in, back off when crowded, strafe in between. */
public class KeepDistanceGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private final double min;
    private final double max;
    private int strafeTimer;
    private boolean strafeLeft;

    public KeepDistanceGoal(PathfinderMob mob, double speed, double min, double max) {
        this.mob = mob;
        this.speed = speed;
        this.min = min;
        this.max = max;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0, 0);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 30f, 30f);
        double d = mob.distanceTo(t);
        if (d > max || !mob.hasLineOfSight(t)) {
            if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(t, speed);
        } else if (d < min) {
            if (mob.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 8, 4, t.position());
                if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, speed * 1.25);
            }
        } else {
            mob.getNavigation().stop();
            if (++strafeTimer > 40 + mob.getRandom().nextInt(30)) {
                strafeLeft = !strafeLeft;
                strafeTimer = 0;
            }
            mob.getMoveControl().strafe(0f, strafeLeft ? 0.45f : -0.45f);
            mob.setYRot(mob.yHeadRot);
        }
    }
}
