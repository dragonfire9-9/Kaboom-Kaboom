package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.ModTags;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.roll.RollService;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = JJKMod.MODID)
public final class PlayerDataEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CursedEnergyData data = ModAttachments.get(player);
        data.tick();
        // CE changes almost every tick; batch syncs to 4 per second.
        if (data.isDirty() && player.tickCount % 5 == 0) SyncJJKDataPayload.sync(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CursedEnergyData data = ModAttachments.get(player);
        data.refreshUnlocks();
        if (!data.isStarterKitGiven()) {
            data.setStarterKitGiven(true);
            data.setCurrentCE(data.getMaxCE());
            player.getInventory().add(new ItemStack(ModItems.CURSED_ENERGY_SCROLL.get()));
            player.getInventory().add(new ItemStack(ModItems.CLAN_SCROLL.get()));
            player.sendSystemMessage(Component.translatable("jjk.message.welcome").withStyle(ChatFormatting.DARK_PURPLE));
        }
        refresh(player, data);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RollService.discard(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player, ModAttachments.get(player));
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player, ModAttachments.get(player));
    }

    private static void refresh(ServerPlayer player, CursedEnergyData data) {
        PlayerStats.applyAttributes(player, data);
        SyncJJKDataPayload.sync(player);
    }

    /** Killing curses (and, at a reduced rate, ordinary monsters) raises the player's grade. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        boolean curse = victim.getType().is(ModTags.CURSES);
        double fraction = curse ? 1.0 : victim instanceof Enemy ? JJKConfig.NON_CURSE_MONSTER_XP_FRACTION.get() : 0;
        if (fraction <= 0) return;

        if (victim instanceof CurseEntity owned && owned.getOwnerUUID() != null) return; // your own released curse
        double base = victim instanceof CurseEntity c ? c.curseType().xp() : 5 + victim.getMaxHealth() * 0.6;
        int xp = (int) Math.round(base * fraction * JJKConfig.XP_MULTIPLIER.get());
        CursedEnergyData data = ModAttachments.get(player);
        if (curse) data.addCurseKill();
        Grade before = data.getGrade();
        if (data.addExperience(xp)) {
            player.sendSystemMessage(Component.translatable("jjk.message.grade_up", before.displayName(), data.getGrade().displayName())
                    .withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), ModSounds.GRADE_UP.get(), SoundSource.PLAYERS, 1f, 1f);
            data.setCurrentCE(data.getMaxCE());
            JJKAdvancements.onGradeChanged(player, data);
        }
        SyncJJKDataPayload.sync(player);
    }

    /** Clan weapon mastery (Zenin) and other melee passives. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player)) return;
        CursedEnergyData data = ModAttachments.get(player);
        Clan clan = data.getClan();
        ItemStack held = player.getMainHandItem();
        boolean weapon = held.is(ModTags.WEAPONS) || held.getItem() instanceof SwordItem
                || held.getItem() instanceof AxeItem || held.getItem() instanceof TridentItem;
        if (weapon || clan == Clan.CLANLESS) {
            event.setAmount((float) (event.getAmount() * clan.weaponDamageMultiplier()));
        }
    }

    private PlayerDataEvents() {}
}
