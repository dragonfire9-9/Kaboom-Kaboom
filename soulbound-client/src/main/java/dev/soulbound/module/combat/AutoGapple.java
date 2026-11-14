package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.util.KeyState;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Eats a golden apple when you get low, then puts your weapon back. */
public final class AutoGapple extends Module {
    private final NumberSetting health, absorption;
    private final ModeSetting prefer;
    private final BoolSetting onlyInCombat, refreshAbsorption, pauseClicks;
    private int restore = -1;
    private boolean eating;
    private long lastAttack;

    public AutoGapple() {
        super("Auto Gapple", "Eats golden apples when you're low.", Category.COMBAT);
        health = add(new NumberSetting("Health", "Eat at or below this health", 10, 1, 20, 0.5, " HP"));
        prefer = add(new ModeSetting("Prefer", "Which apple first", "Golden", "Golden", "Enchanted"));
        refreshAbsorption = add(new BoolSetting("Keep Absorption", "Also eat when absorption runs out mid-fight", false));
        absorption = add(new NumberSetting("Absorption Below", "Refresh when absorption drops under this", 2, 0, 16, 1, " HP").visibleWhen(refreshAbsorption::isOn));
        onlyInCombat = add(new BoolSetting("Only In Fights", "Only with an enemy player within 8 blocks or right after hitting something", false));
        pauseClicks = add(new BoolSetting("Pause Attacks", "Don't swing while eating", true));
        dev.soulbound.event.Events.ATTACK.add(e -> lastAttack = System.currentTimeMillis());
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.currentScreen != null) {
            stop();
            return;
        }
        boolean low = p.getHealth() <= health.get();
        boolean absorb = refreshAbsorption.isOn() && p.getAbsorptionAmount() < absorption.get() && inCombat();
        boolean want = (low || absorb) && (!onlyInCombat.isOn() || inCombat());
        int slot = want ? slot() : -1;
        if (slot < 0) {
            stop();
            return;
        }
        if (!eating) {
            restore = p.getInventory().getSelectedSlot();
            eating = true;
        }
        p.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        if (pauseClicks.isOn()) mc.options.attackKey.setPressed(false);
    }

    private boolean inCombat() {
        if (System.currentTimeMillis() - lastAttack < 4000) return true;
        for (PlayerEntity pl : mc.world.getPlayers()) if (pl != mc.player && pl.isAlive() && mc.player.distanceTo(pl) < 8) return true;
        return mc.player.hasStatusEffect(StatusEffects.ABSORPTION) && mc.player.hurtTime > 0;
    }

    private int slot() {
        Item[] order = prefer.is("Enchanted") ? new Item[]{Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE} : new Item[]{Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE};
        for (Item it : order) for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(it)) return i;
        return -1;
    }

    private void stop() {
        if (!eating) return;
        eating = false;
        KeyState.release(mc.options.useKey);
        if (mc.player != null && restore >= 0) mc.player.getInventory().setSelectedSlot(restore);
        restore = -1;
    }

    @Override
    protected void onDisable() { stop(); }
}
