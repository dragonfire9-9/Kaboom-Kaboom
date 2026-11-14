package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/** Don't slow down (as much) while eating, drinking, blocking or drawing a bow. */
public final class NoSlow extends Module {
    public static NoSlow INSTANCE;
    private final NumberSetting speed;
    private final BoolSetting food, shield, bow;

    public NoSlow() {
        super("No Slow", "Keep your speed while using items.", Category.MOVEMENT);
        INSTANCE = this;
        speed = add(new NumberSetting("Speed", "Movement while using items (vanilla 20%)", 100, 20, 100, 5, "%"));
        food = add(new BoolSetting("Food & Potions", "While eating and drinking", true));
        shield = add(new BoolSetting("Shield", "While blocking", true));
        bow = add(new BoolSetting("Bow & Crossbow", "While drawing", true));
    }

    public static boolean active() {
        NoSlow n = INSTANCE;
        if (n == null || !n.isEnabled() || mc.player == null) return false;
        var item = mc.player.getActiveItem();
        if (item.isOf(Items.SHIELD)) return n.shield.isOn();
        if (item.isOf(Items.BOW) || item.isOf(Items.CROSSBOW) || item.isOf(Items.TRIDENT)) return n.bow.isOn();
        return n.food.isOn();
    }

    public float speed() { return speed.getFloat() / 100f; }
}
