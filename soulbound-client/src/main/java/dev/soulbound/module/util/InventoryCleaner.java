package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Set;

/** Throws junk out of your inventory. */
public final class InventoryCleaner extends Module {
    private static final Set<Item> SEEDS = Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.TORCHFLOWER_SEEDS);
    private static final Set<Item> MOB_DROPS = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.STRING, Items.BONE, Items.GUNPOWDER);
    private static final Set<Item> STONES = Set.of(Items.DIORITE, Items.ANDESITE, Items.GRANITE, Items.TUFF, Items.GRAVEL, Items.FLINT);
    private final BoolSetting seeds, mobDrops, stones, dirt, onlyInventory, keepHotbar;
    private final NumberSetting keepDirtStacks, delay;
    private int timer;

    public InventoryCleaner() {
        super("Inventory Cleaner", "Drop junk items automatically.", Category.UTIL);
        seeds = add(new BoolSetting("Seeds", "Drop seeds", true));
        mobDrops = add(new BoolSetting("Mob Drops", "Drop rotten flesh, spider eyes, string, bones, gunpowder", false));
        stones = add(new BoolSetting("Stone Junk", "Drop diorite, andesite, granite, tuff, gravel and flint", true));
        dirt = add(new BoolSetting("Extra Dirt", "Drop dirt and cobblestone beyond a few stacks", false));
        keepDirtStacks = add(new NumberSetting("Keep Stacks", "Stacks of dirt/cobble to keep", 2, 0, 9, 1).visibleWhen(dirt::isOn));
        keepHotbar = add(new BoolSetting("Keep Hotbar", "Never drop from the hotbar", true));
        onlyInventory = add(new BoolSetting("Only In Inventory", "Only while your inventory is open", false));
        delay = add(new NumberSetting("Delay", "Ticks between drops", 3, 0, 20, 1, "t"));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (timer > 0 && --timer > 0) return;
        boolean inv = mc.currentScreen instanceof InventoryScreen;
        if ((onlyInventory.isOn() && !inv) || (mc.currentScreen != null && !inv)) return;
        int dirtStacks = 0, cobbleStacks = 0;
        for (int i = keepHotbar.isOn() ? 9 : 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            boolean junk = (seeds.isOn() && SEEDS.contains(s.getItem())) || (mobDrops.isOn() && MOB_DROPS.contains(s.getItem()))
                    || (stones.isOn() && STONES.contains(s.getItem()));
            if (dirt.isOn() && s.isOf(Items.DIRT) && ++dirtStacks > keepDirtStacks.getInt()) junk = true;
            if (dirt.isOn() && s.isOf(Items.COBBLESTONE) && ++cobbleStacks > keepDirtStacks.getInt()) junk = true;
            if (!junk) continue;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i < 9 ? 36 + i : i, 1, SlotActionType.THROW, p);
            timer = delay.getInt() + 1;
            return;
        }
    }
}
