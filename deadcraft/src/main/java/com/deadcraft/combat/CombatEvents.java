package com.deadcraft.combat;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.entity.DeadMonster;
import com.deadcraft.entity.DeadProjectile;
import com.deadcraft.item.DeadWeaponItem;
import com.deadcraft.magic.Mana;
import com.deadcraft.registry.ModArmorMaterials;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.util.Fx;
import com.deadcraft.world.Hearing;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The combat layer: dodges with invulnerability frames, combo chains with finishers, custom
 * enchantments, armour set bonuses, and the noise players make.
 */
@EventBusSubscriber(modid = DeadCraft.MODID)
public final class CombatEvents {
    public static final int DODGE_COOLDOWN = 20;
    private static final int DODGE_IFRAMES = 8;
    private static final int COMBO_WINDOW = 30;
    private static final int COMBO_FINISHER = 5;

    private static final Map<UUID, Long> dodgeReady = new HashMap<>();
    private static final Map<UUID, Long> dodgeUntil = new HashMap<>();
    private static final Map<UUID, Combo> combos = new HashMap<>();

    private record Combo(int count, long lastTick, int targetId) {}

    private CombatEvents() {}

    // ---- Dodge -------------------------------------------------------------------------------
    public static int dodgeCooldown(Player player) {
        int phantom = DeadEnchantments.level(player.level(), DeadEnchantments.PHANTOM_STEP, player.getItemBySlot(EquipmentSlot.FEET));
        return Math.max(6, (int) (DODGE_COOLDOWN * (1f - 0.25f * phantom)));
    }

    public static void onDodge(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now < dodgeReady.getOrDefault(player.getUUID(), 0L)) return;
        dodgeReady.put(player.getUUID(), now + dodgeCooldown(player) - 2); // small grace for latency
        dodgeUntil.put(player.getUUID(), now + DODGE_IFRAMES);
        ServerLevel level = player.serverLevel();
        level.playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.DODGE.get(), SoundSource.PLAYERS, 0.8f, 1f);
        level.sendParticles(ModParticles.ASH.get(), player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
        player.causeFoodExhaustion(0.3f);
    }

    private static boolean isDodging(Player player) {
        return player.level().getGameTime() <= dodgeUntil.getOrDefault(player.getUUID(), Long.MIN_VALUE);
    }

    // ---- Damage ------------------------------------------------------------------------------
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!(victim.level() instanceof ServerLevel level)) return;

        // Dodge i-frames: attacks and projectiles miss.
        if (victim instanceof Player p && isDodging(p) && source.getEntity() != null
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EVADE.get(), SoundSource.PLAYERS, 1f, 1.2f);
            level.sendParticles(ModParticles.SOUL.get(), p.getX(), p.getY(1), p.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
            return;
        }

        // Static Ward: melee attackers get shocked.
        if (victim instanceof Player p && source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker) {
            int ward = DeadEnchantments.level(level, DeadEnchantments.STATIC_WARD, p.getItemBySlot(EquipmentSlot.CHEST));
            if (ward > 0 && p.getRandom().nextFloat() < 0.15f * ward) {
                attacker.hurt(level.damageSources().thorns(p), 2f + ward);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                level.sendParticles(ModParticles.SPARK.get(), attacker.getX(), attacker.getY(0.5), attacker.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
            }
        }

        if (!(source.getEntity() instanceof Player attacker)) return;
        float amount = event.getAmount();
        ItemStack weapon = attacker.getMainHandItem();

        // Exorcism: bonus against DeadCraft horrors and the undead.
        int exorcism = DeadEnchantments.level(level, DeadEnchantments.EXORCISM, weapon);
        if (exorcism > 0 && (victim instanceof DeadMonster || victim.isInvertedHealAndHarm())) {
            amount *= 1f + 0.2f * exorcism;
        }

        // Combo chain for DeadCraft melee weapons (direct hits only).
        if (weapon.getItem() instanceof DeadWeaponItem dw && source.getDirectEntity() == attacker) {
            long now = level.getGameTime();
            Combo prev = combos.get(attacker.getUUID());
            int count = prev != null && now - prev.lastTick() <= COMBO_WINDOW ? prev.count() + 1 : 1;
            amount *= 1f + 0.08f * (count - 1);
            if (dw.trait() == DeadWeaponItem.Trait.VOID) amount += victim.getArmorValue() * 0.3f;
            if (dw.trait() == DeadWeaponItem.Trait.BACKSTAB) {
                // from behind: the attacker stands in the half-space the victim is facing away from
                Vec3 facing = victim.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1, 0, 1).normalize();
                if (facing.dot(toAttacker) < -0.3) {
                    amount *= 2f;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, victim.getX(), victim.getY(0.7), victim.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                }
            }
            if (dw.trait() == DeadWeaponItem.Trait.SWEEP) {
                Vec3 look = attacker.getLookAngle().multiply(1, 0, 1).normalize();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(3.2, 1, 3.2),
                        e -> e != attacker && e != victim && e.isAlive() && !(e instanceof Player))) {
                    Vec3 to = e.position().subtract(attacker.position()).multiply(1, 0, 1).normalize();
                    if (look.dot(to) > 0.35) e.hurt(level.damageSources().playerAttack(attacker), amount * 0.5f);
                }
            }
            Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            level.sendParticles(ModParticles.SLASH.get(), at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (count >= COMBO_FINISHER) {
                amount *= 1.6f;
                finisher(level, attacker, victim, dw.trait());
                count = 0;
            }
            combos.put(attacker.getUUID(), new Combo(count, now, victim.getId()));
        }
        event.setAmount(amount);
    }

    private static void finisher(ServerLevel level, Player attacker, LivingEntity victim, DeadWeaponItem.Trait trait) {
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.FINISHER.get(), SoundSource.PLAYERS, 1.4f, 1f);
        level.sendParticles(ModParticles.SLASH.get(), victim.getX(), victim.getY(0.6), victim.getZ(), 3, 0.4, 0.3, 0.4, 0);
        level.sendParticles(ModParticles.BLOOD.get(), victim.getX(), victim.getY(0.6), victim.getZ(), 30, 0.4, 0.4, 0.4, 0.2);
        Fx.shake(level, victim.position(), 6, 0.35f);
        switch (trait) {
            case CLEAVE, SHATTER -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(3),
                        e -> e != attacker && e != victim && e.isAlive() && !(e instanceof Player))) {
                    e.hurt(level.damageSources().playerAttack(attacker), trait == DeadWeaponItem.Trait.SHATTER ? 8f : 5f);
                    Vec3 away = e.position().subtract(victim.position()).normalize();
                    e.push(away.x, 0.4, away.z);
                }
                level.sendParticles(ModParticles.SHOCKWAVE.get(), victim.getX(), victim.getY() + 0.1, victim.getZ(), 1, 0, 0, 0, 0);
            }
            case STARFALL -> {
                DeadProjectile star = new DeadProjectile(level, attacker, DeadProjectile.Kind.STAR, 10f);
                star.setPos(victim.getX(), victim.getY() + 12, victim.getZ());
                star.setDeltaMovement(0, -1.6, 0);
                level.addFreshEntity(star);
            }
            case TIME -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 5));
            case LIFESTEAL -> attacker.heal(4f);
            case REAP -> Mana.add(attacker, 10f);
            case VOID -> victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0));
            case BACKSTAB -> {
                victim.addEffect(new MobEffectInstance(com.deadcraft.registry.ModEffects.BLEEDING, 160, 2));
                attacker.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0));
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1));
            }
            case SWEEP -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(4),
                        e -> e != attacker && e.isAlive() && !(e instanceof Player))) {
                    Vec3 away = e.position().subtract(attacker.position()).normalize();
                    e.push(away.x * 1.2, 0.35, away.z * 1.2);
                    e.hurt(level.damageSources().playerAttack(attacker), 6f);
                }
                level.sendParticles(ModParticles.SHOCKWAVE.get(), attacker.getX(), attacker.getY() + 0.1, attacker.getZ(), 1, 0, 0, 0, 0);
            }
            case BLEED -> {
                victim.addEffect(new MobEffectInstance(com.deadcraft.registry.ModEffects.BLEEDING, 200, 3));
                level.sendParticles(ModParticles.BLOOD.get(), victim.getX(), victim.getY(0.6), victim.getZ(), 40, 0.6, 0.6, 0.6, 0.3);
            }
            case STORM -> {
                LivingEntity from = victim;
                java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
                hit.add(victim);
                for (int i = 0; i < 4; i++) {
                    final LivingEntity src = from;
                    LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, src.getBoundingBox().inflate(6),
                                    e -> e != attacker && e.isAlive() && !hit.contains(e) && !(e instanceof Player))
                            .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(src))).orElse(null);
                    var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(src.position());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    src.hurt(level.damageSources().lightningBolt(), 6f);
                    if (next == null) break;
                    hit.add(next);
                    from = next;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker.level().isClientSide) return;
        float dealt = event.getNewDamage();
        int thirst = DeadEnchantments.level(attacker.level(), DeadEnchantments.BLOODTHIRST, attacker.getMainHandItem());
        float heal = dealt * 0.06f * thirst;
        if (fullSet(attacker, ModArmorMaterials.BLOODSTONE)) heal += dealt * 0.05f;
        if (heal > 0 && event.getSource().getDirectEntity() == attacker) attacker.heal(heal);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer) || killer.level().isClientSide) return;
        int reaper = DeadEnchantments.level(killer.level(), DeadEnchantments.SOUL_REAPER, killer.getMainHandItem());
        float mana = reaper * 6f;
        if (killer.getMainHandItem().getItem() instanceof DeadWeaponItem dw && dw.trait() == DeadWeaponItem.Trait.REAP) mana += 5f;
        if (event.getEntity() instanceof DeadMonster dm && dm.spec().kind == DeadMob.Kind.BOSS) mana += 100f;
        if (mana > 0 && Mana.isAwakened(killer)) {
            Mana.add(killer, mana);
            if (killer.level() instanceof ServerLevel sl) {
                LivingEntity dead = event.getEntity();
                sl.sendParticles(ModParticles.SOUL.get(), dead.getX(), dead.getY(0.5), dead.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    // ---- Feedback sounds -------------------------------------------------------------------
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SWING.get(), SoundSource.PLAYERS,
                    0.7f, 0.9f + p.getRandom().nextFloat() * 0.2f);
        }
    }

    @SubscribeEvent
    public static void onCrit(CriticalHitEvent event) {
        if (!event.isCriticalHit()) return;
        Player p = event.getEntity();
        if (p.level() instanceof ServerLevel sl) {
            var t = event.getTarget();
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), ModSounds.CRIT.get(), SoundSource.PLAYERS, 1f, 1f);
            sl.sendParticles(ModParticles.SPARK.get(), t.getX(), t.getY(0.6), t.getZ(), 10, 0.2, 0.2, 0.2, 0.25);
        }
    }

    // ---- Player tick: mana, set bonuses, noise ---------------------------------------------
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Mana.tick(p);
        // Full stardust set: mana regenerates twice as fast.
        if (fullSet(p, ModArmorMaterials.STARDUST) && p.tickCount % 10 == 0) Mana.add(p, 1.5f);
        if (p.tickCount % 40 == 0) {
            if (fullSet(p, ModArmorMaterials.NECRITE)) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, true));
            }
            if (fullSet(p, ModArmorMaterials.VOIDSTEEL)) {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false, true));
            }
        }
        // Movement noise: creatures hear running far better than walking.
        if (p.onGround() && !p.isSpectator() && p.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
            if (p.isSprinting() && p.tickCount % 10 == 0) Hearing.noise(p.serverLevel(), p.position(), 12, p);
            else if (!p.isCrouching() && p.tickCount % 30 == 0) Hearing.noise(p.serverLevel(), p.position(), 5, p);
        }
    }

    private static boolean fullSet(Player p, Holder<ArmorMaterial> material) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(p.getItemBySlot(slot).getItem() instanceof ArmorItem a) || !a.getMaterial().is(material.unwrapKey().orElseThrow())) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        Hearing.noise(p.serverLevel(), p.position(), 16, p);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            Vec3 c = event.getExplosion().center();
            Hearing.noise(sl, c, 48, null);
        }
    }

    // ---- Keep the client's mana display in sync -------------------------------------------
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Mana.sync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Mana.sync(sp);
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Mana.sync(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        dodgeReady.remove(id);
        dodgeUntil.remove(id);
        combos.remove(id);
    }
}
