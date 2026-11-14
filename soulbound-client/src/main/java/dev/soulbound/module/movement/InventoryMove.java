package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.util.KeyState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.option.KeyBinding;

/** Keep walking, jumping and sprinting while an inventory (or this menu) is open. */
public final class InventoryMove extends Module {
    private final BoolSetting jump, sprint, sneak, inMenus;

    public InventoryMove() {
        super("Inventory Move", "Move while inventories are open.", Category.MOVEMENT);
        jump = add(new BoolSetting("Jump", "Allow jumping", true));
        sprint = add(new BoolSetting("Sprint", "Allow sprinting", true));
        sneak = add(new BoolSetting("Sneak", "Allow sneaking", false));
        inMenus = add(new BoolSetting("In Menus", "Also in the SoulBound menu and the pause menu", true));
    }

    @Override
    public void onTick() {
        var s = mc.currentScreen;
        if (s == null || mc.player == null) return;
        if (s instanceof ChatScreen || s instanceof AbstractSignEditScreen || s instanceof AnvilScreen || s instanceof BookEditScreen) return;
        if (!inMenus.isOn() && !(s instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)) return;
        var o = mc.options;
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey}) k.setPressed(KeyState.physical(k));
        if (jump.isOn()) o.jumpKey.setPressed(KeyState.physical(o.jumpKey));
        if (sprint.isOn()) o.sprintKey.setPressed(KeyState.physical(o.sprintKey));
        if (sneak.isOn()) o.sneakKey.setPressed(KeyState.physical(o.sneakKey));
    }
}
