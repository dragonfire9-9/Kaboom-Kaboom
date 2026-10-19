package com.kaboom.jjk.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Plain item with a one-line lore tooltip ({@code item.jjk.<id>.tooltip}). */
public class TooltipItem extends Item {
    private final String key;

    public TooltipItem(String id, Properties properties) {
        super(properties);
        this.key = "item.jjk." + id + ".tooltip";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
