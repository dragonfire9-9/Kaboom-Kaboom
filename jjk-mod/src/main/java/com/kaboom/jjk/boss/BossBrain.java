package com.kaboom.jjk.boss;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Per-boss attack logic. All methods run on the server. */
public interface BossBrain {
    /** Called every tick while the boss has a target. */
    void tick(BossEntity boss, LivingEntity target, int phase);

    /** Called once when the boss enters phase 2 or 3. */
    default void onPhaseChange(BossEntity boss, int newPhase) {}

    /** Lets a boss rescale incoming damage (Mahoraga's adaptation). */
    default float modifyIncomingDamage(BossEntity boss, DamageSource source, float amount) {
        return amount;
    }
}
