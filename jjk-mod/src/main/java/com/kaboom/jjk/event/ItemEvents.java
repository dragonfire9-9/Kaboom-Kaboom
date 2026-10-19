package com.kaboom.jjk.event;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.data.ModTags;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.item.CursedToolItem;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Combat hooks for Phase 2 items: cursed tools, talismans, the Prison Realm seal and drops. */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class ItemEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        var source = event.getSource();

        // Sealed targets are untouchable, and sealed attackers cannot act.
        if (victim.hasEffect(ModEffects.SEALED)
                || source.getEntity() instanceof LivingEntity attacker && attacker.hasEffect(ModEffects.SEALED)) {
            event.setCanceled(true);
            return;
        }

        // Talismans absorb exactly one technique hit.
        if (victim instanceof Player player && (source.is(JJKDamage.CURSED_TECHNIQUE) || source.is(JJKDamage.DOMAIN_SURE_HIT))) {
            ItemStack talisman = find(player, ModItems.TALISMAN.get());
            if (!talisman.isEmpty()) {
                talisman.shrink(1);
                event.setCanceled(true);
                player.level().playSound(null, player.blockPosition(), ModSounds.TALISMAN_BURN.get(), SoundSource.PLAYERS, 1f, 1f);
                if (player.level() instanceof ServerLevel level) {
                    AbilityEffects.burstAt(level, player.position().add(0, 1, 0), AbilityEffects.dust(0xFFFFE070, 1.3f), 30, 0.5);
                }
                return;
            }
        }

        // Cursed tool bonuses (direct melee only).
        if (source.getDirectEntity() instanceof LivingEntity attacker && source.getDirectEntity() == source.getEntity()) {
            ItemStack held = attacker.getMainHandItem();
            if (held.getItem() instanceof CursedToolItem tool) {
                event.setAmount(tool.modifyDamage(held, attacker, victim, event.getAmount()));
            }
        }

        // Dragon Bone soaks up part of what its wielder takes.
        if (victim.getMainHandItem().getItem() instanceof CursedToolItem tool && tool.kind() == CursedToolItem.Kind.DRAGON_BONE) {
            CursedToolItem.storeDamage(victim.getMainHandItem(), event.getAmount());
        }
    }

    /** Cursed fragments, CE shards and (rarely) a Sukuna finger from curses and other monsters. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer) || !(victim instanceof Enemy)) return;
        boolean curse = victim.getType().is(ModTags.CURSES);
        var random = victim.getRandom();
        // Curses drop fragments and shards through their loot tables; ordinary monsters only rarely.
        if (!curse && random.nextFloat() < 0.04f) drop(event, new ItemStack(ModItems.CURSED_FRAGMENT.get()));
        if (!curse && random.nextFloat() < 0.06f) drop(event, new ItemStack(ModItems.CE_SHARD.get()));
        if (random.nextFloat() < (curse ? 0.015f : 0.002f)) drop(event, new ItemStack(ModItems.SUKUNA_FINGER.get()));
    }

    private static void drop(LivingDropsEvent event, ItemStack stack) {
        LivingEntity e = event.getEntity();
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), stack));
    }

    private static ItemStack find(Player player, net.minecraft.world.item.Item item) {
        if (player.getOffhandItem().is(item)) return player.getOffhandItem();
        for (ItemStack s : player.getInventory().items) if (s.is(item)) return s;
        return ItemStack.EMPTY;
    }

    private ItemEvents() {}
}
