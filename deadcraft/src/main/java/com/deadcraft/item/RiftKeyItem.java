package com.deadcraft.item;

import com.deadcraft.world.DeadDimensions;
import com.deadcraft.world.Travel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Tears a rift to one dimension. Used inside any DeadCraft dimension, it brings you home. */
public class RiftKeyItem extends Item {
    private final int destination;

    public RiftKeyItem(int destination, Properties props) {
        super(props);
        this.destination = destination;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            ResourceKey<Level> here = server.dimension();
            ResourceKey<Level> dest = DeadDimensions.isDeadCraft(here) ? Level.OVERWORLD : DeadDimensions.byIndex(destination);
            if (Travel.go(server, player, dest)) {
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        player.getCooldowns().addCooldown(this, 100);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.deadcraft.rift_key").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
