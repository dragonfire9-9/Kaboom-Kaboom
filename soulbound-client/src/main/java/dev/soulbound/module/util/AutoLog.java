package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** Leaves the server when you're about to die, or when someone shows up. */
public final class AutoLog extends Module {
    private final NumberSetting health, playerRange;
    private final BoolSetting onHealth, onPlayer, noTotems, disableAfter;

    public AutoLog() {
        super("Auto Log", "Disconnect when low or when a player appears.", Category.UTIL);
        onHealth = add(new BoolSetting("On Low Health", "Leave at or below a health", true));
        health = add(new NumberSetting("Health", "Leave at or below this", 6, 1, 19, 1, " HP").visibleWhen(onHealth::isOn));
        noTotems = add(new BoolSetting("Only Without Totems", "Only if you have no totem left", true).visibleWhen(onHealth::isOn));
        onPlayer = add(new BoolSetting("On Player Nearby", "Leave when another player comes close", false));
        playerRange = add(new NumberSetting("Player Range", "How close", 32, 4, 128, 4, "m").visibleWhen(onPlayer::isOn));
        disableAfter = add(new BoolSetting("Turn Off After", "Turn Auto Log off once it fires, so you can rejoin", true));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.getNetworkHandler() == null || mc.isInSingleplayer()) return;
        String why = null;
        if (onHealth.isOn() && p.getHealth() <= health.get()) {
            boolean totem = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getInventory().contains(s -> s.isOf(Items.TOTEM_OF_UNDYING));
            if (!(noTotems.isOn() && totem)) why = "health was " + Math.round(p.getHealth());
        }
        if (why == null && onPlayer.isOn()) {
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o != p && p.distanceTo(o) <= playerRange.get()) { why = o.getName().getString() + " came within " + Math.round(p.distanceTo(o)) + "m"; break; }
            }
        }
        if (why == null) return;
        if (disableAfter.isOn()) setEnabled(false);
        mc.getNetworkHandler().getConnection().disconnect(Text.literal("[SoulBound] Auto Log: " + why));
    }
}
