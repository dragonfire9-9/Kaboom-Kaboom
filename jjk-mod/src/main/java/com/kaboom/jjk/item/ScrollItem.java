package com.kaboom.jjk.item;

import com.kaboom.jjk.roll.RollService;
import com.kaboom.jjk.roll.RollType;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Right-click to unroll: the server rolls and opens the reel screen on the client. */
public class ScrollItem extends Item {
    private final RollType type;

    public ScrollItem(RollType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public RollType type() {
        return type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            level.playSound(null, player.blockPosition(), ModSounds.ROLL_OPEN.get(), SoundSource.PLAYERS, 1f, 1f);
            RollService.start(sp, stack, type);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jjk." + type.id() + "_scroll.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.jjk.scroll.tooltip.use").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
