package com.deadcraft.entity.ai;

import com.deadcraft.entity.DeadMonster;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Walk toward the last thing this creature heard. */
public class InvestigateNoiseGoal extends Goal {
    private final DeadMonster mob;
    private final double speed;
    private int repath;

    public InvestigateNoiseGoal(DeadMonster mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && mob.getNoisePos() != null && !mob.isGazeFrozen();
    }

    @Override
    public boolean canContinueToUse() {
        Vec3 p = mob.getNoisePos();
        return mob.getTarget() == null && p != null && !mob.isGazeFrozen() && mob.position().distanceToSqr(p) > 4.0;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        Vec3 p = mob.getNoisePos();
        if (p == null) return;
        if (--repath <= 0) {
            repath = 20;
            mob.getNavigation().moveTo(p.x, p.y, p.z, speed);
        }
        mob.getLookControl().setLookAt(p.x, p.y + 1, p.z);
    }

    @Override
    public void stop() {
        Vec3 p = mob.getNoisePos();
        if (p != null && mob.position().distanceToSqr(p) <= 4.0) mob.clearNoise();
        mob.getNavigation().stop();
    }
}
