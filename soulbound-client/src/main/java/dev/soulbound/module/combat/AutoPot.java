package dev.soulbound.module.combat;

import dev.soulbound.aim.AimController;
import dev.soulbound.aim.AimSettings;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Throws splash healing potions at your feet when you're low (and optionally speed/strength when they run out). */
public final class AutoPot extends Module {
    private final NumberSetting health, delay;
    private final BoolSetting speed, strength, fireRes, onlyOnGround, restoreView;
    private final AimSettings aim;
    private int cooldown, restoreSlot = -1, phase;
    private float savedPitch;

    public AutoPot() {
        super("Auto Pot", "Throw healing potions at your feet when low.", Category.COMBAT);
        health = add(new NumberSetting("Health", "Throw healing at or below this", 9, 1, 20, 0.5, " HP"));
        delay = add(new NumberSetting("Delay", "Ticks between throws", 10, 2, 60, 1, "t"));
        speed = add(new BoolSetting("Speed", "Re-throw Speed when it wears off", false));
        strength = add(new BoolSetting("Strength", "Re-throw Strength when it wears off", false));
        fireRes = add(new BoolSetting("Fire Resistance", "Throw Fire Resistance when burning", false));
        onlyOnGround = add(new BoolSetting("Only On Ground", "Only throw with your feet on the ground", true));
        restoreView = add(new BoolSetting("Restore View", "Look back up after throwing", true));
        aim = new AimSettings(this, 900, 0.3, 30);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.currentScreen != null) return;
        if (cooldown > 0) cooldown--;
        if (phase == 2) {
            // look back up
            Vec3d up = p.getEyePos().add(Vec3d.fromPolar(savedPitch, p.getYaw()).multiply(5));
            AimController.request(this, up, aim);
            if (AimController.errorTo(up) < 3 || !restoreView.isOn()) phase = 0;
            return;
        }
        int slot = cooldown > 0 || (onlyOnGround.isOn() && !p.isOnGround()) ? -1 : wanted();
        if (slot < 0) {
            phase = 0;
            return;
        }
        if (phase == 0) {
            savedPitch = p.getPitch();
            phase = 1;
        }
        Vec3d feet = new Vec3d(p.getX() + p.getVelocity().x * 3, p.getY() - 1, p.getZ() + p.getVelocity().z * 3);
        AimController.request(this, feet, aim);
        if (p.getPitch() > 75) {
            restoreSlot = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(slot);
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.getInventory().setSelectedSlot(restoreSlot);
            cooldown = delay.getInt();
            phase = 2;
        }
    }

    private int wanted() {
        var p = mc.player;
        boolean heal = p.getHealth() <= health.get();
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.SPLASH_POTION)) continue;
            var contents = s.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) continue;
            for (var eff : contents.getEffects()) {
                var type = eff.getEffectType();
                if (heal && (type.equals(StatusEffects.INSTANT_HEALTH) || type.equals(StatusEffects.REGENERATION))) return i;
                if (speed.isOn() && type.equals(StatusEffects.SPEED) && !p.hasStatusEffect(StatusEffects.SPEED)) return i;
                if (strength.isOn() && type.equals(StatusEffects.STRENGTH) && !p.hasStatusEffect(StatusEffects.STRENGTH)) return i;
                if (fireRes.isOn() && type.equals(StatusEffects.FIRE_RESISTANCE) && p.isOnFire() && !p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) return i;
            }
        }
        return -1;
    }
}
