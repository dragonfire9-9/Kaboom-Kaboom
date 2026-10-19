package com.kaboom.jjk.item;

import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.event.JJKAdvancements;
import com.kaboom.jjk.event.SukunaTakeover;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One of Sukuna's twenty fingers. Eating it permanently raises max CE and may let Sukuna take over for a while.
 */
public class SukunaFingerItem extends Item {
    public static final int TOTAL_FINGERS = 20;
    public static final double CE_PER_FINGER = 40;

    public SukunaFingerItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            CursedEnergyData data = ModAttachments.get(player);
            if (data.getRestriction() == HeavenlyRestriction.PHYSICAL) {
                // No cursed energy to absorb into: the finger just poisons you.
                player.hurt(player.damageSources().magic(), 6f);
                player.displayClientMessage(Component.translatable("jjk.message.finger_rejected").withStyle(ChatFormatting.RED), true);
            } else {
                int eaten = data.getFingersEaten() + 1;
                data.setFingersEaten(eaten);
                if (eaten <= TOTAL_FINGERS) data.addBonusMaxCE(CE_PER_FINGER);
                data.setCurrentCE(data.getMaxCE());
                level.playSound(null, player.blockPosition(), ModSounds.FINGER_EAT.get(), SoundSource.PLAYERS, 1f, 0.8f);
                player.displayClientMessage(Component.translatable("jjk.message.finger_eaten", Math.min(eaten, TOTAL_FINGERS), TOTAL_FINGERS)
                        .withStyle(ChatFormatting.DARK_RED), true);

                double chance = 0.12 + 0.025 * eaten;
                if (data.getClan() == Clan.RYOMEN) chance *= 1.6;
                if (player.getRandom().nextDouble() < chance) SukunaTakeover.begin(player, data, 20 * (40 + 4 * eaten));

                JJKAdvancements.award(player, "first_finger");
                if (eaten >= 10) JJKAdvancements.award(player, "ten_fingers");
                if (eaten >= TOTAL_FINGERS) JJKAdvancements.award(player, "all_fingers");
                SyncJJKDataPayload.sync(player);
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jjk.sukuna_finger.tooltip").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
