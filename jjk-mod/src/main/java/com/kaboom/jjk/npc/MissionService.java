package com.kaboom.jjk.npc;

import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.event.JJKAdvancements;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Mission offers, progress and rewards. Missions are kept on the player's {@link CursedEnergyData}. */
public final class MissionService {

    /** The mission a sorcerer offers this player right now (scaled to the player's grade). */
    public static Mission offerFor(NpcType npc, CursedEnergyData data) {
        int grade = data.getGrade().ordinal();
        if (npc.fingerMissions && grade >= Grade.GRADE_2.ordinal() && (data.getMissionsCompleted() % 3 == 2)) {
            return new Mission(Mission.Type.RETRIEVE_FINGER, 1, 0, 0, npc.id);
        }
        int minCurse = grade <= 1 ? 0 : grade <= 3 ? 1 : Grade.GRADE_1.ordinal();
        int count = minCurse == Grade.GRADE_1.ordinal() ? 1 + grade / 3 : 4 + grade * 2;
        return new Mission(Mission.Type.EXORCISE, count, 0, minCurse, npc.id);
    }

    public static void accept(ServerPlayer player, NpcType npc) {
        CursedEnergyData data = ModAttachments.get(player);
        if (data.getMission() != null) {
            player.displayClientMessage(Component.translatable("jjk.mission.already_active").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Mission m = offerFor(npc, data);
        data.setMission(m);
        player.sendSystemMessage(Component.translatable("jjk.mission.accepted", m.describe()).withStyle(ChatFormatting.AQUA));
        SyncJJKDataPayload.sync(player);
    }

    /** Called when a player exorcises a curse. */
    public static void onCurseKilled(ServerPlayer player, int curseGradeOrdinal) {
        CursedEnergyData data = ModAttachments.get(player);
        Mission m = data.getMission();
        if (m == null || m.type() != Mission.Type.EXORCISE || curseGradeOrdinal < m.minCurseGrade() || m.complete()) return;
        Mission next = m.withProgress(m.progress() + 1);
        data.setMission(next);
        player.displayClientMessage(next.describe().copy().withStyle(ChatFormatting.AQUA), true);
        if (next.complete()) {
            player.sendSystemMessage(Component.translatable("jjk.mission.ready").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Hand in the active mission to any sorcerer. Returns true if it was completed. */
    public static boolean turnIn(ServerPlayer player) {
        CursedEnergyData data = ModAttachments.get(player);
        Mission m = data.getMission();
        if (m == null) return false;
        if (m.type() == Mission.Type.RETRIEVE_FINGER) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.SUKUNA_FINGER.get()));
            if (slot < 0) {
                player.displayClientMessage(Component.translatable("jjk.mission.need_finger").withStyle(ChatFormatting.RED), true);
                return false;
            }
            player.getInventory().removeItem(slot, 1);
        } else if (!m.complete()) {
            player.displayClientMessage(Component.translatable("jjk.mission.not_done", m.describe()).withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        data.setMission(null);
        data.incrementMissionsCompleted();
        Grade before = data.getGrade();
        boolean promoted = data.addExperience(m.xpReward());
        give(player, new ItemStack(ModItems.CURSED_FRAGMENT.get(), 2 + m.target()));
        give(player, new ItemStack(ModItems.CE_BOTTLE.get()));
        if (m.type() == Mission.Type.RETRIEVE_FINGER || player.getRandom().nextFloat() < 0.25f) {
            give(player, new ItemStack(player.getRandom().nextBoolean() ? ModItems.CURSED_ENERGY_SCROLL.get() : ModItems.CLAN_SCROLL.get()));
        }
        player.sendSystemMessage(Component.translatable("jjk.mission.complete", m.xpReward()).withStyle(ChatFormatting.GOLD));
        if (promoted) {
            player.sendSystemMessage(Component.translatable("jjk.message.grade_up", before.displayName(), data.getGrade().displayName())
                    .withStyle(ChatFormatting.GOLD));
            JJKAdvancements.onGradeChanged(player, data);
        }
        player.level().playSound(null, player.blockPosition(), ModSounds.GRADE_UP.get(), SoundSource.PLAYERS, 1f, 1.2f);
        if (data.getMissionsCompleted() >= 10) JJKAdvancements.award(player, "ten_missions");
        SyncJJKDataPayload.sync(player);
        return true;
    }

    /** Teachers: Reverse Cursed Technique (Grade 1 + 3 special grade objects) and Black Flash (Grade 3 + 20 exorcisms). */
    public static void train(ServerPlayer player, boolean rct) {
        CursedEnergyData data = ModAttachments.get(player);
        if (rct) {
            if (data.isRctUnlocked()) return;
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.SPECIAL_GRADE_OBJECT.get()));
            int have = slot < 0 ? 0 : player.getInventory().countItem(ModItems.SPECIAL_GRADE_OBJECT.get());
            if (!data.getGrade().atLeast(Grade.GRADE_1) || have < 3 || !data.canUseCursedEnergy()) {
                player.displayClientMessage(Component.translatable("jjk.train.rct_requirements").withStyle(ChatFormatting.RED), false);
                return;
            }
            removeItems(player, ModItems.SPECIAL_GRADE_OBJECT.get(), 3);
            data.setRctUnlocked(true);
            player.sendSystemMessage(Component.translatable("jjk.train.rct_learned").withStyle(ChatFormatting.GOLD));
            JJKAdvancements.award(player, "reverse_cursed_technique");
        } else {
            if (data.isBlackFlashUnlocked()) return;
            if (!data.getGrade().atLeast(Grade.GRADE_3) || data.getCurseKills() < 20) {
                player.displayClientMessage(Component.translatable("jjk.train.black_flash_requirements").withStyle(ChatFormatting.RED), false);
                return;
            }
            data.setBlackFlashUnlocked(true);
            player.sendSystemMessage(Component.translatable("jjk.train.black_flash_learned").withStyle(ChatFormatting.GOLD));
        }
        player.level().playSound(null, player.blockPosition(), ModSounds.GRADE_UP.get(), SoundSource.PLAYERS, 1f, 0.8f);
        SyncJJKDataPayload.sync(player);
    }

    private static void removeItems(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        for (ItemStack s : player.getInventory().items) {
            if (count <= 0) break;
            if (s.is(item)) {
                int take = Math.min(count, s.getCount());
                s.shrink(take);
                count -= take;
            }
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private MissionService() {}
}
