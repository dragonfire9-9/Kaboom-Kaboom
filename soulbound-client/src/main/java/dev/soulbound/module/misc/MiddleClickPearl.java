package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/** Throws an ender pearl from your hotbar when you middle-click, then switches back. */
public final class MiddleClickPearl extends Module {
    private final BoolSetting inventory = add(new BoolSetting("From Inventory", "Pull a pearl from your inventory if the hotbar has none", true));
    private boolean wasDown;
    private int restore = -1, restoreTicks;

    public MiddleClickPearl() {
        super("Middle Click Pearl", "Middle-click to throw an ender pearl.", Category.MISC);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (restoreTicks > 0 && --restoreTicks == 0 && restore >= 0) {
            p.getInventory().setSelectedSlot(restore);
            restore = -1;
        }
        boolean down = mc.currentScreen == null && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        boolean click = down && !wasDown;
        wasDown = down;
        if (!click || p.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) return;
        var inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9 && slot < 0; i++) if (inv.getStack(i).isOf(Items.ENDER_PEARL)) slot = i;
        if (slot < 0 && inventory.isOn()) {
            for (int i = 9; i < 36; i++) {
                if (!inv.getStack(i).isOf(Items.ENDER_PEARL)) continue;
                slot = inv.getSelectedSlot();
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, slot, net.minecraft.screen.slot.SlotActionType.SWAP, p);
                break;
            }
        }
        if (slot < 0) return;
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        if (prev != slot) { restore = prev; restoreTicks = 2; }
    }
}
