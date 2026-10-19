package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/** Applies attribute modifiers that come from jujutsu data (Heavenly Restrictions, CE nature). */
public final class PlayerStats {
    private record Mod(Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {}

    private static final List<Mod> PHYSICAL = List.of(
            new Mod(Attributes.ATTACK_DAMAGE, JJKMod.id("hr_strength"), 6.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.ATTACK_SPEED, JJKMod.id("hr_attack_speed"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Mod(Attributes.MOVEMENT_SPEED, JJKMod.id("hr_speed"), 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Mod(Attributes.ARMOR, JJKMod.id("hr_durability"), 8.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.ARMOR_TOUGHNESS, JJKMod.id("hr_toughness"), 4.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.MAX_HEALTH, JJKMod.id("hr_health"), 12.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.JUMP_STRENGTH, JJKMod.id("hr_jump"), 0.25, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.SAFE_FALL_DISTANCE, JJKMod.id("hr_fall"), 10.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.KNOCKBACK_RESISTANCE, JJKMod.id("hr_knockback"), 0.4, AttributeModifier.Operation.ADD_VALUE));

    private static final List<Mod> CURSED_ENERGY = List.of(
            new Mod(Attributes.MAX_HEALTH, JJKMod.id("hr_frail_health"), -8.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.MOVEMENT_SPEED, JJKMod.id("hr_frail_speed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Mod(Attributes.ATTACK_DAMAGE, JJKMod.id("hr_frail_strength"), -1.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.BLOCK_INTERACTION_RANGE, JJKMod.id("hr_range"), 1.5, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.ENTITY_INTERACTION_RANGE, JJKMod.id("hr_entity_range"), 1.5, AttributeModifier.Operation.ADD_VALUE));

    public static void applyAttributes(ServerPlayer player, CursedEnergyData data) {
        for (Mod m : PHYSICAL) remove(player, m);
        for (Mod m : CURSED_ENERGY) remove(player, m);

        List<Mod> active = data.getRestriction() == HeavenlyRestriction.PHYSICAL ? PHYSICAL
                : data.getRestriction() == HeavenlyRestriction.CURSED_ENERGY ? CURSED_ENERGY : List.of();
        for (Mod m : active) {
            AttributeInstance inst = player.getAttribute(m.attribute());
            if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(m.id(), m.amount(), m.op()));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void remove(ServerPlayer player, Mod m) {
        AttributeInstance inst = player.getAttribute(m.attribute());
        if (inst != null) inst.removeModifier(m.id());
    }

    private PlayerStats() {}
}
