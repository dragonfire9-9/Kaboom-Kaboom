package com.deadcraft.combat;

import com.deadcraft.DeadCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * Custom enchantments. They are data-driven (data/deadcraft/enchantment/*.json, so they show up on
 * enchanting tables and anvils); their effects are applied in {@link CombatEvents} and the gun code.
 */
public final class DeadEnchantments {
    /** Weapons: heal for part of the melee damage dealt. */
    public static final ResourceKey<Enchantment> BLOODTHIRST = key("bloodthirst");
    /** Weapons: kills restore mana. */
    public static final ResourceKey<Enchantment> SOUL_REAPER = key("soul_reaper");
    /** Guns: more bullet damage. */
    public static final ResourceKey<Enchantment> HOLLOW_POINT = key("hollow_point");
    /** Guns: faster reloads. */
    public static final ResourceKey<Enchantment> QUICK_RELOAD = key("quick_reload");
    /** Boots: shorter dodge cooldown. */
    public static final ResourceKey<Enchantment> PHANTOM_STEP = key("phantom_step");
    /** Chestplates: chance to shock melee attackers. */
    public static final ResourceKey<Enchantment> STATIC_WARD = key("static_ward");
    /** Weapons: bonus damage against bosses and other undead horrors. */
    public static final ResourceKey<Enchantment> EXORCISM = key("exorcism");

    private DeadEnchantments() {}

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, DeadCraft.id(name));
    }

    public static int level(Level level, ResourceKey<Enchantment> key, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel((Holder<Enchantment>) h, stack))
                .orElse(0);
    }
}
