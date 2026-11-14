package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Tops up hotbar stacks from your inventory when they run low. */
public final class HotbarRefill extends Module {
    private final NumberSetting threshold, delay;
    private final BoolSetting onlyBlocks, inCombat;
    private int timer;

    public HotbarRefill() {
        super("Hotbar Refill", "Refill hotbar stacks from your inventory.", Category.UTIL);
        threshold = add(new NumberSetting("Threshold", "Refill when a stack falls to this % of full", 25, 5, 90, 5, "%"));
        delay = add(new NumberSetting("Delay", "Ticks between refills", 4, 0, 20, 1, "t"));
        onlyBlocks = add(new BoolSetting("Only Blocks", "Only refill block stacks", false));
        inCombat = add(new BoolSetting("While Fighting", "Keep refilling while you're being hit", true));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (timer > 0 && --timer > 0) return;
        if (!inCombat.isOn() && p.hurtTime > 0) return;
        var inv = p.getInventory();
        for (int h = 0; h < 9; h++) {
            ItemStack s = inv.getStack(h);
            if (s.isEmpty() || !s.isStackable()) continue;
            if (onlyBlocks.isOn() && !(s.getItem() instanceof net.minecraft.item.BlockItem)) continue;
            if (s.getCount() * 100.0 / s.getMaxCount() > threshold.get()) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack o = inv.getStack(i);
                if (!ItemStack.areItemsAndComponentsEqual(s, o)) continue;
                // pick up the inventory stack and drop it onto the hotbar stack (merges), returning any rest
                int sid = p.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(sid, i, 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(sid, 36 + h, 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(sid, i, 0, SlotActionType.PICKUP, p);
                timer = delay.getInt() + 1;
                return;
            }
        }
    }
}
