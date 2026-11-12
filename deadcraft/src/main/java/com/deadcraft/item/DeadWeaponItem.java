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
    public enum Trait { CLEAVE, LIFESTEAL, VOID, STARFALL, REAP, TIME, SHATTER }

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
