package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.concurrent.ThreadLocalRandom;

/** Shift-clicks everything out of a chest you open, at a human pace. */
public final class ChestStealer extends Module {
    private final NumberSetting minDelay = add(new NumberSetting("Min Delay", "Shortest wait between items", 60, 0, 500, 10, "ms"));
    private final NumberSetting maxDelay = add(new NumberSetting("Max Delay", "Longest wait between items", 140, 0, 600, 10, "ms"));
    private final BoolSetting autoClose = add(new BoolSetting("Auto Close", "Close the chest once it's empty", true));
    private final BoolSetting shulkers = add(new BoolSetting("Shulker Boxes", "Also take from shulker boxes", true));
    private long next;

    public ChestStealer() {
        super("Chest Stealer", "Takes everything out of chests you open.", Category.UTIL);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen) && !(shulkers.isOn() && mc.currentScreen instanceof ShulkerBoxScreen)) {
            next = 0;
            return;
        }
        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        ScreenHandler h = screen.getScreenHandler();
        long now = System.currentTimeMillis();
        if (next == 0) next = now + delay();
        if (now < next) return;
        int containerSlots = h.slots.size() - 36;
        for (int i = 0; i < containerSlots; i++) {
            Slot s = h.slots.get(i);
            if (!s.hasStack()) continue;
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            next = now + delay();
            return;
        }
        if (autoClose.isOn()) mc.player.closeHandledScreen();
    }

    private long delay() {
        long lo = minDelay.getInt(), hi = Math.max(lo, maxDelay.getInt());
        return lo + (hi > lo ? ThreadLocalRandom.current().nextLong(hi - lo + 1) : 0);
    }
}
