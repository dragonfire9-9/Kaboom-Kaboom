package com.deadcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** DeadCraft melee weapons. Each has a trait; combo hits and finishers are in CombatEvents. */
public class DeadWeaponItem extends SwordItem {
    public enum Trait { CLEAVE, LIFESTEAL, VOID, STARFALL, REAP, TIME, SHATTER, BACKSTAB, SWEEP, BLEED, STORM }

    /** Knives get a quick stab instead of a swing (see client/WeaponSwing). */
    public boolean isKnife() { return trait == Trait.BACKSTAB; }

    /** Heavy blades get an overhead cleave. */
    public boolean isHeavy() { return trait == Trait.SWEEP || trait == Trait.SHATTER || trait == Trait.CLEAVE || trait == Trait.REAP; }

    private final Trait trait;

    public DeadWeaponItem(Tier tier, Trait trait, Properties props) {
        super(tier, props);
        this.trait = trait;
    }

    public Trait trait() { return trait; }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        switch (trait) {
            case TIME -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            case LIFESTEAL -> attacker.heal(1.5f);
            case VOID -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            case BACKSTAB -> target.addEffect(new MobEffectInstance(com.deadcraft.registry.ModEffects.BLEEDING, 60, 0));
            case BLEED -> {
                MobEffectInstance cur = target.getEffect(com.deadcraft.registry.ModEffects.BLEEDING);
                int amp = cur == null ? 0 : Math.min(2, cur.getAmplifier() + 1);
                target.addEffect(new MobEffectInstance(com.deadcraft.registry.ModEffects.BLEEDING, 100, amp));
            }
            default -> {}
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        String key = "tooltip.deadcraft.trait." + trait.name().toLowerCase(Locale.ROOT);
        lines.add(Component.translatable(key).withStyle(ChatFormatting.DARK_RED));
        lines.add(Component.translatable("tooltip.deadcraft.combo").withStyle(ChatFormatting.GRAY));
    }
}
