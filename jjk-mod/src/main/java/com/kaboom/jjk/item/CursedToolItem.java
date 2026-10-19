package com.kaboom.jjk.item;

import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.ModTags;
import com.kaboom.jjk.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Cursed tools. Bonus damage is applied in {@link #modifyDamage} (called from {@code ItemEvents} before armor),
 * on-hit effects in {@link #hurtEnemy}.
 */
public class CursedToolItem extends SwordItem {
    public enum Kind {
        INVERTED_SPEAR_OF_HEAVEN, PLAYFUL_CLOUD, SPLIT_SOUL_KATANA, DRAGON_BONE, SLAUGHTER_DEMON, CHAIN_OF_A_THOUSAND_MILES, BLACK_ROPE
    }

    private static final String STORED = "jjk_stored_damage";
    private final Kind kind;

    public CursedToolItem(Kind kind, Tier tier, Properties properties) {
        super(tier, properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Adjusts outgoing melee damage. */
    public float modifyDamage(ItemStack stack, LivingEntity attacker, LivingEntity target, float amount) {
        float out = amount;
        if (attacker instanceof Player p && ModAttachments.get(p).getRestriction() == HeavenlyRestriction.PHYSICAL) {
            out *= 1.25f; // cursed tools are a Heavenly Restriction user's technique
        }
        switch (kind) {
            case PLAYFUL_CLOUD -> out *= 1.3f;
            case SPLIT_SOUL_KATANA -> out += target.getArmorValue() * 0.6f; // cuts the soul, not the body
            case SLAUGHTER_DEMON -> { if (target.getType().is(ModTags.CURSES)) out *= 1.75f; }
            case DRAGON_BONE -> {
                float stored = getStored(stack);
                if (stored > 0) {
                    out += stored;
                    setStored(stack, 0);
                    if (target.level() instanceof ServerLevel level) {
                        AbilityEffects.burstAt(level, target.position().add(0, 1, 0), AbilityEffects.dust(0xFFFFE6A0, 1.6f), 25, 0.4);
                    }
                }
            }
            default -> {}
        }
        return out;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        switch (kind) {
            case INVERTED_SPEAR_OF_HEAVEN -> nullify(target, 80);
            case BLACK_ROPE -> {
                nullify(target, 50);
                target.getActiveEffects().stream()
                        .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL)
                        .map(MobEffectInstance::getEffect).toList()
                        .forEach(target::removeEffect);
            }
            case PLAYFUL_CLOUD -> AbilityEffects.knock(attacker, target, 1.5);
            default -> {}
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Cancels the target's techniques: active buffs from techniques drop and abilities go on cooldown. */
    private static void nullify(LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(ModEffects.NULLIFIED, ticks, 0));
        if (target instanceof ServerPlayer p) {
            CursedEnergyData data = ModAttachments.get(p);
            for (String id : data.getUnlockedAbilities()) data.setCooldown(id, Math.max(data.getCooldown(id), ticks));
        }
        if (target.level() instanceof ServerLevel level) {
            AbilityEffects.burstAt(level, target.position().add(0, 1, 0), AbilityEffects.dust(0xFFAAAAAA, 1.2f), 20, 0.4);
        }
    }

    /** Dragon Bone: stores part of the damage its wielder takes. */
    public static void storeDamage(ItemStack stack, float amount) {
        setStored(stack, Math.min(30f, getStored(stack) + amount * 0.5f));
    }

    public static float getStored(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat(STORED);
    }

    private static void setStored(ItemStack stack, float value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putFloat(STORED, value));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jjk." + kind.name().toLowerCase() + ".tooltip").withStyle(ChatFormatting.GRAY));
        if (kind == Kind.DRAGON_BONE && getStored(stack) > 0) {
            tooltip.add(Component.translatable("item.jjk.dragon_bone.stored", String.format("%.1f", getStored(stack))).withStyle(ChatFormatting.GOLD));
        }
    }
}
