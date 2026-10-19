package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface AbilityEffect {
    /**
     * Runs the ability on the server. Return {@code false} if nothing happened (no target, invalid state),
     * in which case no cursed energy is spent and no cooldown is applied.
     */
    boolean execute(ServerPlayer player, CursedEnergyData data, Ability ability);
}
