package com.kaboom.jjk.data;

import com.kaboom.jjk.item.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Who can perceive cursed spirits. Used by curse rendering and targeting. */
public final class CurseSight {

    public static boolean canSeeCurses(Player player) {
        return ModAttachments.get(player).canSeeCurses() || player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CURSE_GLASSES.get());
    }

    private CurseSight() {}
}
