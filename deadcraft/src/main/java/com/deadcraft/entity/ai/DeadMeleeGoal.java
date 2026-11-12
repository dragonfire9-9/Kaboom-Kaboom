package com.deadcraft.entity.ai;

import com.deadcraft.entity.DeadMonster;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Melee chase that respects The Hollow's "frozen while watched" rule. */
public class DeadMeleeGoal extends MeleeAttackGoal {
    private final DeadMonster monster;

    public DeadMeleeGoal(DeadMonster monster, double speed, boolean followWithoutSight) {
        super(monster, speed, followWithoutSight);
        this.monster = monster;
    }

    @Override
    public boolean canUse() {
        return !monster.isGazeFrozen() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !monster.isGazeFrozen() && super.canContinueToUse();
    }
}
