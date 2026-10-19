package com.kaboom.jjk.ability;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public final class JJKDamage {
    public static final ResourceKey<DamageType> CURSED_TECHNIQUE =
            ResourceKey.create(Registries.DAMAGE_TYPE, JJKMod.id("cursed_technique"));
    /** Sure-hit damage inside a Domain Expansion; bypasses armor (see damage type tags). */
    public static final ResourceKey<DamageType> DOMAIN_SURE_HIT =
            ResourceKey.create(Registries.DAMAGE_TYPE, JJKMod.id("domain_sure_hit"));

    public static DamageSource technique(Entity attacker) {
        return source(attacker, CURSED_TECHNIQUE);
    }

    public static DamageSource sureHit(Entity attacker) {
        return source(attacker, DOMAIN_SURE_HIT);
    }

    private static DamageSource source(Entity attacker, ResourceKey<DamageType> key) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker);
    }

    private JJKDamage() {}
}
