package com.kaboom.jjk.item;

import com.kaboom.jjk.data.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Jujutsu High Student ID. Right-click to register it to yourself. Sorcerer NPCs only trade with
 * players carrying an ID registered to them.
 */
public class StudentIdItem extends Item {
    private static final String OWNER = "jjk_owner";
    private static final String OWNER_NAME = "jjk_owner_name";

    public StudentIdItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && !hasOwner(stack)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putUUID(OWNER, player.getUUID());
                tag.putString(OWNER_NAME, player.getGameProfile().getName());
            });
            player.displayClientMessage(Component.translatable("jjk.message.student_id_registered").withStyle(ChatFormatting.AQUA), true);
        } else if (!level.isClientSide()) {
            var data = ModAttachments.get(player);
            player.displayClientMessage(Component.translatable("jjk.message.student_id_info", data.getGrade().displayName(),
                    data.getCurseKills()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static boolean hasOwner(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID(OWNER);
    }

    public static boolean isOwnedBy(ItemStack stack, Player player) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(OWNER) && tag.getUUID(OWNER).equals(player.getUUID());
    }

    /** True if the player carries an ID registered to themselves. */
    public static boolean carriesValidId(Player player) {
        for (ItemStack s : player.getInventory().items) if (s.getItem() instanceof StudentIdItem && isOwnedBy(s, player)) return true;
        return player.getOffhandItem().getItem() instanceof StudentIdItem && isOwnedBy(player.getOffhandItem(), player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(OWNER_NAME)) {
            tooltip.add(Component.translatable("item.jjk.student_id.owner", tag.getString(OWNER_NAME)).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.jjk.student_id.tooltip").withStyle(ChatFormatting.GRAY));
        }
    }
}
