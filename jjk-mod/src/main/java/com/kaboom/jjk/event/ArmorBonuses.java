package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.item.JJKArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Outfit passives:
 * <ul>
 *     <li>Jujutsu High Uniform: +5% CE regen per piece</li>
 *     <li>Gojo's outfit: blindfold -15% CE cost (Six Eyes), full set -25% and +15% regen</li>
 *     <li>Sukuna's Kimono: +10% technique damage per piece; only wearable after eating 10 fingers</li>
 *     <li>Toji's outfit: for physical Heavenly Restriction users, +6% speed and +1.5 damage per piece</li>
 *     <li>Nanami's Suit: full set gives +30% melee damage at night ("overtime")</li>
 *     <li>Mahito's Patchwork: slowly reshapes your body (regeneration)</li>
 *     <li>Geto's Kesa: curses don't target you (2 pieces) unless you strike first</li>
 * </ul>
 */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class ArmorBonuses {
    public static final int SUKUNA_FINGERS_REQUIRED = 10;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static int pieces(Player player, String set) {
        int n = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (player.getItemBySlot(slot).getItem() instanceof JJKArmorItem a && a.set().equals(set)) n++;
        }
        return n;
    }

    public static double costMultiplier(Player player) {
        int gojo = pieces(player, "gojo_outfit");
        boolean blindfold = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof JJKArmorItem a && a.set().equals("gojo_outfit");
        if (gojo >= 4) return 0.75;
        return blindfold ? 0.85 : 1.0;
    }

    public static double extraRegenFraction(Player player) {
        double bonus = 0.05 * pieces(player, "jujutsu_uniform");
        if (pieces(player, "gojo_outfit") >= 4) bonus += 0.15;
        return bonus;
    }

    public static boolean curseNeutral(Player player) {
        return pieces(player, "geto_kesa") >= 2;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CursedEnergyData data = ModAttachments.get(player);

        double extra = extraRegenFraction(player);
        if (extra > 0 && data.getCurrentCE() < data.getMaxCE()) {
            data.setCurrentCE(data.getCurrentCE() + data.getRegenPerSecond() / 20.0 * extra);
        }

        if (player.tickCount % 20 != 0) return;

        // Sukuna's kimono refuses anyone who isn't vessel enough.
        if (data.getFingersEaten() < SUKUNA_FINGERS_REQUIRED) {
            for (EquipmentSlot slot : SLOTS) {
                ItemStack worn = player.getItemBySlot(slot);
                if (worn.getItem() instanceof JJKArmorItem a && a.set().equals("sukuna_kimono")) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                    if (!player.getInventory().add(worn)) player.drop(worn, false);
                    player.displayClientMessage(Component.translatable("jjk.message.kimono_rejects", SUKUNA_FINGERS_REQUIRED)
                            .withStyle(ChatFormatting.DARK_RED), true);
                }
            }
        }

        int mahito = pieces(player, "mahito_patchwork");
        if (mahito > 0 && player.tickCount % (120 / mahito) == 0 && player.getHealth() < player.getMaxHealth()) player.heal(1f);

        applyToji(player, data);
    }

    private static void applyToji(ServerPlayer player, CursedEnergyData data) {
        int toji = data.getRestriction() == HeavenlyRestriction.PHYSICAL ? pieces(player, "toji_outfit") : 0;
        set(player, Attributes.MOVEMENT_SPEED, "toji_speed", 0.06 * toji, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_DAMAGE, "toji_damage", 1.5 * toji, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                            String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        if (amount == 0) inst.removeModifier(JJKMod.id(name));
        else inst.addOrUpdateTransientModifier(new AttributeModifier(JJKMod.id(name), amount, op));
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        if (source.is(JJKDamage.CURSED_TECHNIQUE) || source.is(JJKDamage.DOMAIN_SURE_HIT)) {
            int sukuna = pieces(player, "sukuna_kimono");
            if (sukuna > 0) event.setAmount(event.getAmount() * (1 + 0.1f * sukuna));
        } else if (source.getDirectEntity() == player && pieces(player, "nanami_suit") >= 3 && player.level().isNight()) {
            event.setAmount(event.getAmount() * 1.3f);
        }
    }

    private ArmorBonuses() {}
}
