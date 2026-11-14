package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Tells you (privately) where you died, so you can get your things back. */
public final class DeathCoords extends Module {
    private final BoolSetting dimension, copy;
    private boolean shown;

    public DeathCoords() {
        super("Death Coords", "Show where you died in chat.", Category.MISC);
        dimension = add(new BoolSetting("Dimension", "Also say which dimension", true));
        copy = add(new BoolSetting("Copy", "Copy the coordinates to your clipboard", false));
    }

    @Override
    public void onTick() {
        if (!(mc.currentScreen instanceof DeathScreen) || mc.player == null) {
            shown = false;
            return;
        }
        if (shown) return;
        shown = true;
        var b = mc.player.getBlockPos();
        String coords = b.getX() + " " + b.getY() + " " + b.getZ();
        String dim = mc.world.getRegistryKey().getValue().getPath().replace('_', ' ');
        mc.inGameHud.getChatHud().addMessage(Text.literal("☠ You died at ").formatted(Formatting.GRAY)
                .append(Text.literal(coords).formatted(Formatting.WHITE))
                .append(dimension.isOn() ? Text.literal(" in the " + dim).formatted(Formatting.GRAY) : Text.empty()));
        if (copy.isOn()) mc.keyboard.setClipboard(coords);
    }
}
