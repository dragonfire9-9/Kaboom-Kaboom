package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.slot.SlotActionType;

/** Puts on the best armour in your inventory. */
public final class AutoArmor extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final ModeSetting prefer;
    private final NumberSetting delay;
    private final BoolSetting elytra, avoidBinding, onlyInventory, keepDurability;
    private int timer;

    public AutoArmor() {
        super("Auto Armor", "Equip the best armour you have.", Category.UTIL);
        prefer = add(new ModeSetting("Prefer", "Tie-breaker between equal armour", "Protection", "Protection", "Blast Protection", "Projectile Protection", "Fire Protection"));
        delay = add(new NumberSetting("Delay", "Ticks between swaps", 3, 0, 20, 1, "t"));
        elytra = add(new BoolSetting("Keep Elytra", "Don't swap a worn elytra for a chestplate", true));
        avoidBinding = add(new BoolSetting("Avoid Binding", "Never put on Curse of Binding gear", true));
        keepDurability = add(new BoolSetting("Skip Broken", "Ignore pieces about to break", true));
        onlyInventory = add(new BoolSetting("Only In Inventory", "Only swap with your inventory open", false));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (timer > 0 && --timer > 0) return;
        boolean inv = mc.currentScreen instanceof InventoryScreen;
        if ((onlyInventory.isOn() && !inv) || (mc.currentScreen != null && !inv)) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack worn = p.getEquippedStack(slot);
            if (slot == EquipmentSlot.CHEST && elytra.isOn() && worn.isOf(Items.ELYTRA)) continue;
            double bestScore = score(worn, slot);
            int best = -1;
            for (int i = 0; i < 36; i++) {
                ItemStack s = p.getInventory().getStack(i);
                var eq = s.get(DataComponentTypes.EQUIPPABLE);
                if (eq == null || eq.slot() != slot) continue;
                double sc = score(s, slot);
                if (sc > bestScore + 0.01) {
                    bestScore = sc;
                    best = i;
                }
            }
            if (best < 0) continue;
            // armour screen slots: 5 head .. 8 feet; swap the inventory piece straight into place
            int armorSlot = 5 + (3 - slot.getEntitySlotId());
            int invSlot = best < 9 ? 36 + best : best;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, armorSlot, 0, SlotActionType.PICKUP, p);
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, invSlot, 0, SlotActionType.PICKUP, p);
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, armorSlot, 0, SlotActionType.PICKUP, p);
            timer = delay.getInt() + 1;
            return;
        }
    }

    private double score(ItemStack s, EquipmentSlot slot) {
        if (s.isEmpty()) return -1;
        var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        if (avoidBinding.isOn() && reg.getOptional(Enchantments.BINDING_CURSE).map(e -> EnchantmentHelper.getLevel(e, s) > 0).orElse(false)) return -1;
        if (keepDurability.isOn() && s.isDamageable() && s.getMaxDamage() - s.getDamage() < 10) return -1;
        double[] v = {0};
        s.applyAttributeModifiers(slot, (attr, mod) -> {
            if (attr.equals(EntityAttributes.ARMOR)) v[0] += mod.value() * 10;
            if (attr.equals(EntityAttributes.ARMOR_TOUGHNESS)) v[0] += mod.value() * 4;
        });
        var want = switch (prefer.get()) {
            case "Blast Protection" -> Enchantments.BLAST_PROTECTION;
            case "Projectile Protection" -> Enchantments.PROJECTILE_PROTECTION;
            case "Fire Protection" -> Enchantments.FIRE_PROTECTION;
            default -> Enchantments.PROTECTION;
        };
        v[0] += reg.getOptional(want).map(e -> EnchantmentHelper.getLevel(e, s)).orElse(0) * 3;
        v[0] += reg.getOptional(Enchantments.PROTECTION).map(e -> EnchantmentHelper.getLevel(e, s)).orElse(0) * 2;
        return v[0];
    }
}
