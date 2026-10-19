package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants advancements whose criteria are {@code minecraft:impossible}; the mod awards them directly
 * when the matching gameplay event happens.
 */
public final class JJKAdvancements {

    public static void onGradeChanged(ServerPlayer player, CursedEnergyData data) {
        if (data.getGrade() == Grade.SPECIAL_GRADE) award(player, "special_grade");
    }

    public static void award(ServerPlayer player, String name) {
        ResourceLocation id = JJKMod.id(name);
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) return;
        var progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return;
        for (String criterion : com.google.common.collect.Lists.newArrayList(progress.getRemainingCriteria())) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    private JJKAdvancements() {}
}
