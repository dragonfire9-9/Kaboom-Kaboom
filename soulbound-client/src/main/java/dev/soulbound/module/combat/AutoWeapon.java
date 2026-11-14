package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/** Switches to your best weapon the moment you attack. */
public final class AutoWeapon extends Module {
    private final ModeSetting prefer;
    private final BoolSetting axeOnShield, switchBack, maceWhenFalling;
    private final NumberSetting backDelay;
    private int restore = -1, restoreIn;

    public AutoWeapon() {
        super("Auto Weapon", "Swap to your best weapon when you attack.", Category.COMBAT);
        prefer = add(new ModeSetting("Prefer", "What counts as best", "Damage", "Damage", "Sword", "Axe", "DPS"));
        axeOnShield = add(new BoolSetting("Axe On Shield", "Use an axe on players holding a shield up", true));
        maceWhenFalling = add(new BoolSetting("Mace When Falling", "Use a mace on hits while falling", true));
        switchBack = add(new BoolSetting("Switch Back", "Return to the slot you had", false));
        backDelay = add(new NumberSetting("Back Delay", "Ticks before switching back", 4, 1, 40, 1, "t").visibleWhen(switchBack::isOn));
        dev.soulbound.event.Events.ATTACK.add(this::onAttack);
    }

    private void onAttack(Entity target) {
        var p = mc.player;
        if (!isEnabled() || p == null || !(target instanceof LivingEntity)) return;
        var inv = p.getInventory();
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean shield = axeOnShield.isOn() && target instanceof PlayerEntity pe && pe.isBlocking();
        boolean falling = maceWhenFalling.isOn() && p.fallDistance > 1.5;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            boolean sword = s.isIn(ItemTags.SWORDS), axe = s.isIn(ItemTags.AXES), mace = s.isOf(Items.MACE);
            if (!sword && !axe && !mace) continue;
            double dmg = damage(s);
            double score = switch (prefer.get()) {
                case "Sword" -> sword ? 100 + dmg : dmg;
                case "Axe" -> axe ? 100 + dmg : dmg;
                case "DPS" -> dmg * (sword ? 1.6 : axe ? 1.0 : 0.6);
                default -> dmg;
            };
            if (shield && axe) score += 1000;
            if (falling && mace) score += 2000;
            if (mace && !falling) score -= 500;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0 || best == inv.getSelectedSlot()) return;
        if (switchBack.isOn() && restore < 0) restore = inv.getSelectedSlot();
        restoreIn = backDelay.getInt();
        inv.setSelectedSlot(best);
    }

    @Override
    public void onTick() {
        if (restore >= 0 && --restoreIn <= 0) {
            mc.player.getInventory().setSelectedSlot(restore);
            restore = -1;
        }
    }

    static double damage(ItemStack s) {
        double[] d = {1};
        s.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(EntityAttributes.ATTACK_DAMAGE)) d[0] += mod.value();
        });
        return d[0];
    }
}
