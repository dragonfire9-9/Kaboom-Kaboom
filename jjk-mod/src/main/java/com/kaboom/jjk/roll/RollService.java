package com.kaboom.jjk.roll;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.event.PlayerStats;
import com.kaboom.jjk.item.ScrollItem;
import com.kaboom.jjk.network.RollResultPayload;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative roll flow. The client never decides an outcome:
 * <ol>
 *     <li>Using a scroll consumes it, rolls on the server and stores a pending result.</li>
 *     <li>The client animates the reel toward that result and shows Accept / Reroll.</li>
 *     <li>Reroll consumes another scroll of the same type from the inventory and replaces the pending result.</li>
 *     <li>Accept applies the pending result. Closing / disconnecting discards it.</li>
 * </ol>
 */
public final class RollService {
    private static final Map<UUID, RollResult> PENDING = new ConcurrentHashMap<>();

    public static void start(ServerPlayer player, ItemStack stack, RollType type) {
        if (PENDING.containsKey(player.getUUID())) {
            // A previous roll is still open (e.g. the screen was closed by lag). Re-show it instead of eating a scroll.
            resend(player, PENDING.get(player.getUUID()));
            return;
        }
        CursedEnergyData data = ModAttachments.get(player);
        if (type == RollType.CURSED_ENERGY && data.getRestriction() == HeavenlyRestriction.PHYSICAL) {
            player.displayClientMessage(Component.translatable("jjk.message.restriction_rejects_scroll").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        roll(player, type, data);
    }

    public static void reroll(ServerPlayer player) {
        RollResult pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        ItemStack scroll = findScroll(player, pending.type());
        if (scroll.isEmpty() && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("jjk.message.no_scrolls").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.getAbilities().instabuild) scroll.shrink(1);
        roll(player, pending.type(), ModAttachments.get(player));
    }

    public static void accept(ServerPlayer player) {
        RollResult pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        CursedEnergyData data = ModAttachments.get(player);
        RollEngine.apply(pending, data);
        data.setCurrentCE(data.getMaxCE());
        PlayerStats.applyAttributes(player, data);
        SyncJJKDataPayload.sync(player);
        player.displayClientMessage(Component.translatable("jjk.message.roll_accepted", pending.title()), false);
        player.level().playSound(null, player.blockPosition(), ModSounds.GRADE_UP.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    public static void discard(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private static void roll(ServerPlayer player, RollType type, CursedEnergyData data) {
        RollResult result = RollEngine.roll(type, data, player.getRandom());
        PENDING.put(player.getUUID(), result);
        resend(player, result);
    }

    private static void resend(ServerPlayer player, RollResult result) {
        PacketDistributor.sendToPlayer(player, new RollResultPayload(result, countScrolls(player, result.type())));
    }

    private static ItemStack findScroll(ServerPlayer player, RollType type) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof ScrollItem scroll && scroll.type() == type) return s;
        }
        if (player.getOffhandItem().getItem() instanceof ScrollItem scroll && scroll.type() == type) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static int countScrolls(ServerPlayer player, RollType type) {
        if (player.getAbilities().instabuild) return 99;
        int count = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof ScrollItem scroll && scroll.type() == type) count += s.getCount();
        }
        if (player.getOffhandItem().getItem() instanceof ScrollItem scroll && scroll.type() == type) count += player.getOffhandItem().getCount();
        return count;
    }

    private RollService() {}
}
