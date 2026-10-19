package com.kaboom.jjk.item;

import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Right-click a creature or player to seal it for a while. Bosses break free sooner. */
public class PrisonRealmItem extends Item {
    public static final int SEAL_TICKS = 20 * 30;
    public static final int BOSS_SEAL_TICKS = 20 * 12;
    public static final int COOLDOWN = 20 * 120;

    public PrisonRealmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        boolean boss = isBoss(target);
        target.addEffect(new MobEffectInstance(ModEffects.SEALED, boss ? BOSS_SEAL_TICKS : SEAL_TICKS, 0, false, true), player);
        player.level().playSound(null, target.blockPosition(), ModSounds.PRISON_REALM.get(), SoundSource.PLAYERS, 1.2f, 1f);
        player.displayClientMessage(Component.translatable("jjk.message.prison_realm", target.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), true);
        if (target instanceof Player sealed) {
            sealed.displayClientMessage(Component.translatable("jjk.message.prison_realm_sealed").withStyle(ChatFormatting.DARK_PURPLE), false);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResult.CONSUME;
    }

    /** Boss check that works for vanilla bosses and our own (which use the bosses tag). */
    public static boolean isBoss(LivingEntity e) {
        return e instanceof WitherBoss || e instanceof EnderDragon || e.getType().is(com.kaboom.jjk.data.ModTags.BOSSES);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jjk.prison_realm.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
