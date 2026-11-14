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
    private final dev.soulbound.setting.ModeSetting filter = add(new dev.soulbound.setting.ModeSetting("Take", "Everything, or skip junk", "Everything", "Everything", "No Junk", "Valuables"));
    private final BoolSetting stopWhenFull = add(new BoolSetting("Stop When Full", "Close the chest when your inventory is full", true));
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
            if (!s.hasStack() || !wanted(s.getStack())) continue;
            if (stopWhenFull.isOn() && mc.player.getInventory().getEmptySlot() < 0 && !canMerge(s.getStack())) continue;
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            next = now + delay();
            return;
        }
        if (autoClose.isOn()) mc.player.closeHandledScreen();
        next = Long.MAX_VALUE; // done with this chest
    }

    private boolean wanted(net.minecraft.item.ItemStack st) {
        var it = st.getItem();
        boolean junk = it == net.minecraft.item.Items.ROTTEN_FLESH || it == net.minecraft.item.Items.SPIDER_EYE || it == net.minecraft.item.Items.STRING
                || it == net.minecraft.item.Items.BONE || it == net.minecraft.item.Items.WHEAT_SEEDS || it == net.minecraft.item.Items.POISONOUS_POTATO
                || it == net.minecraft.item.Items.DIRT || it == net.minecraft.item.Items.COBBLESTONE || it == net.minecraft.item.Items.GRAVEL;
        return switch (filter.get()) {
            case "No Junk" -> !junk;
            case "Valuables" -> st.hasEnchantments() || st.getRarity() != net.minecraft.util.Rarity.COMMON || it == net.minecraft.item.Items.DIAMOND
                    || it == net.minecraft.item.Items.EMERALD || it == net.minecraft.item.Items.GOLD_INGOT || it == net.minecraft.item.Items.IRON_INGOT
                    || it == net.minecraft.item.Items.NETHERITE_INGOT || it == net.minecraft.item.Items.NETHERITE_SCRAP || it == net.minecraft.item.Items.GOLDEN_APPLE
                    || it == net.minecraft.item.Items.TOTEM_OF_UNDYING || it == net.minecraft.item.Items.ENDER_PEARL;
            default -> true;
        };
    }

    private boolean canMerge(net.minecraft.item.ItemStack st) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            var o = inv.getStack(i);
            if (net.minecraft.item.ItemStack.areItemsAndComponentsEqual(o, st) && o.getCount() < o.getMaxCount()) return true;
        }
        return false;
    }

    private long delay() {
        long lo = minDelay.getInt(), hi = Math.max(lo, maxDelay.getInt());
        return lo + (hi > lo ? ThreadLocalRandom.current().nextLong(hi - lo + 1) : 0);
    }
}
