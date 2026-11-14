package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.concurrent.ThreadLocalRandom;

/** Keeps a totem of undying in your offhand, refilling it after it pops. */
public final class AutoTotem extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", "Only swap at or below this health (20 = always)", 20, 1, 20, 1, " HP"));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Wait before refilling, like a real swap", 80, 0, 500, 10, "ms"));
    private final BoolSetting hotbar = add(new BoolSetting("Use Hotbar", "Also take totems from the hotbar", true));
    private final BoolSetting onlyInventory = add(new BoolSetting("Only In Inventory", "Only swap with your inventory open", false));
    private long due = -1;

    public AutoTotem() {
        super("Auto Totem", "Keeps a totem in your offhand.", Category.MISC);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getHealth() > health.get()
                || (onlyInventory.isOn() && !(mc.currentScreen instanceof InventoryScreen))
                || (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen))) {
            due = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (due < 0) due = now + (delay.getInt() == 0 ? 0 : ThreadLocalRandom.current().nextLong(delay.getInt() / 2, delay.getInt() + 1));
        if (now < due) return;
        var inv = p.getInventory();
        for (int i = hotbar.isOn() ? 0 : 9; i < 36; i++) {
            if (!inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i < 9 ? 36 + i : i, 40, SlotActionType.SWAP, p);
            break;
        }
        due = -1;
    }
}
