package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Set;

/** Eats the best food in your hotbar when you get hungry. */
public final class AutoEat extends Module {
    private static final Set<Item> BAD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
            Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE);
    private final NumberSetting hunger = add(new NumberSetting("Hunger", "Eat when your food bar is at or below this", 14, 1, 19, 1));
    private final BoolSetting allowGapples = add(new BoolSetting("Allow Golden Apples", "Count golden apples as food", false));
    private final BoolSetting notInCombat = add(new BoolSetting("Pause In Combat", "Don't eat while holding attack", true));
    private int restore = -1;
    private boolean eating;

    public AutoEat() {
        super("Auto Eat", "Eats food from your hotbar when you're hungry.", Category.UTIL);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        boolean need = p.getHungerManager().getFoodLevel() <= hunger.getInt() && mc.currentScreen == null
                && !(notInCombat.isOn() && mc.options.attackKey.isPressed());
        int slot = need ? bestFood(p) : -1;
        if (slot < 0) {
            stop(p);
            return;
        }
        if (!eating) {
            restore = p.getInventory().getSelectedSlot();
            eating = true;
        }
        p.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
    }

    private int bestFood(ClientPlayerEntity p) {
        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            FoodComponent food = s.get(DataComponentTypes.FOOD);
            if (food == null) continue;
            boolean gapple = s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            if (BAD.contains(s.getItem()) && !(gapple && allowGapples.isOn())) continue;
            int score = food.nutrition() * 10 + (int) (food.saturation() * 10);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    private void stop(ClientPlayerEntity p) {
        if (!eating) return;
        eating = false;
        mc.options.useKey.setPressed(false);
        if (restore >= 0 && p != null) p.getInventory().setSelectedSlot(restore);
        restore = -1;
    }

    @Override
    protected void onDisable() { stop(mc.player); }
}
