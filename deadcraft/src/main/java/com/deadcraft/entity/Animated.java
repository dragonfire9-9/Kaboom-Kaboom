package com.deadcraft.entity;

import net.minecraft.world.entity.AnimationState;

/** Entities driven by DeadCraft keyframe animations (see DeadRigModel). */
public interface Animated {
    AnimationState idleAnimation();
    AnimationState attackAnimation();
    AnimationState castAnimation();
    AnimationState flyAnimation();
}
