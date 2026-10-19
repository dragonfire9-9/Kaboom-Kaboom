package com.kaboom.jjk.ability;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.Technique;
import net.minecraft.network.chat.Component;

/**
 * @param technique    owning technique, or null for universal / physical abilities
 * @param cost         base CE cost (before clan / nature / config multipliers)
 * @param cooldown     base cooldown in ticks
 * @param requiredGrade grade needed before the ability unlocks
 * @param color        ARGB color used for the HUD icon and default particles
 */
public record Ability(String id, Kind kind, Technique technique, double cost, int cooldown,
                      Grade requiredGrade, int color, AbilityEffect effect) {

    public enum Kind {
        /** Belongs to an innate technique. */
        TECHNIQUE,
        /** Any sorcerer with cursed energy. */
        UNIVERSAL,
        /** Heavenly Restriction (physical) only; costs no CE. */
        PHYSICAL
    }

    public boolean usableBy(CursedEnergyData data) {
        return switch (kind) {
            case TECHNIQUE -> data.canUseCursedEnergy() && data.getTechnique() == technique;
            case UNIVERSAL -> data.canUseCursedEnergy();
            case PHYSICAL -> data.getRestriction() == HeavenlyRestriction.PHYSICAL;
        };
    }

    public double costFor(CursedEnergyData data) {
        return kind == Kind.PHYSICAL ? 0 : cost * data.getCostMultiplier();
    }

    public Component displayName() {
        return Component.translatable("jjk.ability." + id);
    }
}
