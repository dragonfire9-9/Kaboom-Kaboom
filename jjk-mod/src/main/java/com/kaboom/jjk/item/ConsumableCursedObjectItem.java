package com.kaboom.jjk.item;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Cursed objects you can absorb: Death Paintings, Special Grade objects and CE shards. */
public class ConsumableCursedObjectItem extends Item {
    public enum Kind { DEATH_PAINTING, SPECIAL_GRADE_OBJECT, CE_SHARD }

    private final Kind kind;

    public ConsumableCursedObjectItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            CursedEnergyData data = ModAttachments.get(player);
            boolean canAbsorb = data.getRestriction() != HeavenlyRestriction.PHYSICAL;
            switch (kind) {
                case DEATH_PAINTING -> {
                    if (canAbsorb) data.addBonusMaxCE(data.getTechnique() == Technique.BLOOD_MANIPULATION ? 60 : 30);
                    data.addExperience(data.getTechnique() == Technique.BLOOD_MANIPULATION ? 300 : 120);
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                }
                case SPECIAL_GRADE_OBJECT -> {
                    if (canAbsorb) data.addBonusMaxCE(25);
                    data.addExperience(200);
                }
                case CE_SHARD -> {
                    if (canAbsorb) data.setCurrentCE(data.getCurrentCE() + 20);
                }
            }
            player.displayClientMessage(Component.translatable("item.jjk." + kind.name().toLowerCase() + ".absorbed")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            SyncJJKDataPayload.sync(player);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jjk." + kind.name().toLowerCase() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
