package com.deadcraft.item;

import com.deadcraft.client.ClientHooks;
import com.deadcraft.magic.Mana;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Reading it unlocks magic (mana) permanently. Dropped by bosses or crafted from star shards. */
public class GrimoireItem extends Item {
    public GrimoireItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Mana.isAwakened(player)) {
            if (level.isClientSide) player.displayClientMessage(Component.translatable("message.deadcraft.already_awakened").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            ClientHooks.awakening(stack.copy());
        } else if (player instanceof ServerPlayer sp) {
            ServerLevel server = sp.serverLevel();
            Mana.awaken(sp);
            server.playSound(null, sp.blockPosition(), ModSounds.AWAKEN.get(), SoundSource.PLAYERS, 1.5f, 1f);
            server.sendParticles(ModParticles.RUNE.get(), sp.getX(), sp.getY(1), sp.getZ(), 150, 1.2, 1.5, 1.2, 0.08);
            server.sendParticles(ModParticles.SOUL.get(), sp.getX(), sp.getY(1), sp.getZ(), 80, 0.6, 1.0, 0.6, 0.1);
            Fx.shake(server, sp.position(), 8, 0.6f);
            sp.displayClientMessage(Component.translatable("message.deadcraft.awakened").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.deadcraft.grimoire").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
