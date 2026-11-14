package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/** Throws an ender pearl from your hotbar when you middle-click, then switches back. */
public final class MiddleClickPearl extends Module {
    private final dev.soulbound.setting.ModeSetting item = add(new dev.soulbound.setting.ModeSetting("Item", "What middle-click throws", "Ender Pearl",
            "Ender Pearl", "Wind Charge", "Snowball", "Egg", "Firework"));
    private final BoolSetting inventory = add(new BoolSetting("From Inventory", "Pull one from your inventory if the hotbar has none", true));
    private final BoolSetting notOnBlocks = add(new BoolSetting("Not On Blocks", "Keep vanilla pick-block when looking at a block", false));
    private final dev.soulbound.setting.NumberSetting switchDelay = add(new dev.soulbound.setting.NumberSetting("Switch Back", "Ticks before switching back", 2, 1, 10, 1, "t"));
    private boolean wasDown;
    private int restore = -1, restoreTicks;

    public MiddleClickPearl() {
        super("Middle Click Pearl", "Middle-click to throw a pearl, wind charge and more.", Category.MISC);
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
        net.minecraft.item.Item want = switch (item.get()) {
            case "Wind Charge" -> Items.WIND_CHARGE;
            case "Snowball" -> Items.SNOWBALL;
            case "Egg" -> Items.EGG;
            case "Firework" -> Items.FIREWORK_ROCKET;
            default -> Items.ENDER_PEARL;
        };
        if (!click || p.getItemCooldownManager().isCoolingDown(want.getDefaultStack())) return;
        if (notOnBlocks.isOn() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return;
        var inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9 && slot < 0; i++) if (inv.getStack(i).isOf(want)) slot = i;
        if (slot < 0 && inventory.isOn()) {
            for (int i = 9; i < 36; i++) {
                if (!inv.getStack(i).isOf(want)) continue;
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
        if (prev != slot) { restore = prev; restoreTicks = switchDelay.getInt(); }
    }
}
