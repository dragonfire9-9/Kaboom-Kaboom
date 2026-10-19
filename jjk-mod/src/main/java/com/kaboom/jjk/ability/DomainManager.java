package com.kaboom.jjk.ability;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.block.ModBlocks;
import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.event.JJKAdvancements;
import com.kaboom.jjk.network.ClientFxPayload;
import com.kaboom.jjk.network.DomainStatePayload;
import com.kaboom.jjk.network.FxSender;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Active Domain Expansions: builds the barrier dome from temporary blocks, applies the sure-hit,
 * resolves clashes (stronger cursed energy wins) and restores the original terrain afterwards.
 */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class DomainManager {
    public static final int RADIUS = 14;
    public static final int DURATION = 20 * 20;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final List<ActiveDomain> ACTIVE = new ArrayList<>();
    private static long slowMotionUntil;
    private static float previousTickRate = 20f;

    public static final class ActiveDomain {
        final UUID owner;
        final ServerLevel level;
        final BlockPos center;
        final DomainType type;
        final double strength;
        final float damageMultiplier;
        final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        int ticksLeft = DURATION;

        ActiveDomain(UUID owner, ServerLevel level, BlockPos center, DomainType type, double strength, float damageMultiplier) {
            this.owner = owner;
            this.level = level;
            this.center = center;
            this.type = type;
            this.strength = strength;
            this.damageMultiplier = damageMultiplier;
        }

        public boolean contains(Vec3 pos) {
            return pos.distanceToSqr(Vec3.atBottomCenterOf(center)) <= RADIUS * RADIUS;
        }

        public DomainType type() { return type; }
        public UUID owner() { return owner; }
    }

    /** Domain clash strength: the larger cursed energy reserve and higher grade wins. */
    public static double strengthOf(CursedEnergyData data) {
        return data.getMaxCE() * (1 + 0.25 * data.getGrade().ordinal()) + data.getCurrentCE() * 0.5;
    }

    /** Called after costs are paid. Returns false if the domain lost a clash and never formed. */
    public static boolean expand(ServerPlayer caster, CursedEnergyData data, DomainType type) {
        ServerLevel level = caster.serverLevel();
        double strength = strengthOf(data);

        // Simultaneous domains: the stronger cursed energy overwrites the weaker one.
        for (ActiveDomain other : List.copyOf(ACTIVE)) {
            if (other.level != level || other.owner.equals(caster.getUUID())) continue;
            if (other.center.distSqr(caster.blockPosition()) > (RADIUS * 2) * (RADIUS * 2)) continue;
            ServerPlayer rival = level.getServer().getPlayerList().getPlayer(other.owner);
            announceClash(level, caster, rival);
            if (other.strength >= strength) {
                caster.sendSystemMessage(Component.translatable("jjk.message.domain_clash_lost").withStyle(ChatFormatting.RED));
                burnout(caster, data);
                return false;
            }
            collapse(other, true);
            if (rival != null) rival.sendSystemMessage(Component.translatable("jjk.message.domain_clash_lost").withStyle(ChatFormatting.RED));
            caster.sendSystemMessage(Component.translatable("jjk.message.domain_clash_won").withStyle(ChatFormatting.GOLD));
        }
        collapseOwnedBy(caster.getUUID());

        ActiveDomain domain = new ActiveDomain(caster.getUUID(), level, caster.blockPosition(), type, strength,
                (float) data.getTechniqueDamageMultiplier());
        if (JJKConfig.DOMAINS_PLACE_BLOCKS.get()) build(domain);
        ACTIVE.add(domain);

        // Cinematics: flash, shake, title and a brief world-wide slow motion.
        Vec3 c = Vec3.atBottomCenterOf(domain.center);
        level.playSound(null, domain.center, ModSounds.DOMAIN_EXPANSION.get(), SoundSource.PLAYERS, 3f, 1f);
        level.playSound(null, domain.center, ModSounds.forDomain(type.id), SoundSource.PLAYERS, 3f, 1f);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(c) > RADIUS * 3) continue;
            FxSender.send(p, new ClientFxPayload(ClientFxPayload.Fx.DOMAIN_OPEN, 1f, 50, type.color, "jjk.domain." + type.id));
            FxSender.send(p, new ClientFxPayload(ClientFxPayload.Fx.SHAKE, 1.4f, 30, 0, ""));
        }
        AbilityEffects.sphere(level, c, RADIUS * 0.9, ModParticles.glow(type.color, 3f, 30), 500);
        if (JJKConfig.DOMAIN_SLOW_MOTION.get()) startSlowMotion(level.getServer());

        data.incrementDomainsCast();
        if (data.getDomainsCast() == 1) JJKAdvancements.award(caster, "first_domain");
        sync(level);
        return true;
    }

    private static void announceClash(ServerLevel level, ServerPlayer a, ServerPlayer b) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceTo(a) < RADIUS * 4) {
                FxSender.text(p, "jjk.fx.domain_clash", 0xFFFFFFFF, 40);
                FxSender.send(p, new ClientFxPayload(ClientFxPayload.Fx.SHAKE, 2f, 30, 0, ""));
            }
        }
        if (b != null) JJKAdvancements.award(b, "domain_clash");
        JJKAdvancements.award(a, "domain_clash");
    }

    private static void build(ActiveDomain d) {
        BlockState barrier = ModBlocks.DOMAIN_BARRIER.get().defaultBlockState();
        BlockState floor = ModBlocks.DOMAIN_FLOOR.get().defaultBlockState();
        BlockPos c = d.center;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -1; dy <= RADIUS; dy++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    BlockState target = null;
                    if (dy == -1 && Math.sqrt(dx * dx + dz * dz) <= RADIUS) target = floor;
                    else if (dy >= 0 && dist <= RADIUS && dist > RADIUS - 1) target = barrier;
                    if (target == null) continue;
                    BlockPos pos = c.offset(dx, dy, dz);
                    if (!d.level.isInWorldBounds(pos)) continue;
                    BlockState old = d.level.getBlockState(pos);
                    if (old.getDestroySpeed(d.level, pos) < 0 || d.level.getBlockEntity(pos) != null) continue;
                    d.saved.put(pos.immutable(), old);
                    d.level.setBlock(pos, target, FLAGS);
                }
            }
        }
    }

    private static void restore(ActiveDomain d) {
        for (Map.Entry<BlockPos, BlockState> e : d.saved.entrySet()) {
            BlockState now = d.level.getBlockState(e.getKey());
            if (now.is(ModBlocks.DOMAIN_BARRIER.get()) || now.is(ModBlocks.DOMAIN_FLOOR.get())) {
                d.level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            }
        }
        d.saved.clear();
    }

    public static void collapse(ActiveDomain d, boolean shattered) {
        restore(d);
        ACTIVE.remove(d);
        Vec3 c = Vec3.atBottomCenterOf(d.center);
        d.level.sendParticles(ModParticles.spark(d.type.color, 2.5f, 20), c.x, c.y + RADIUS / 2.0, c.z, 120, RADIUS * 0.5, RADIUS * 0.4, RADIUS * 0.5, 0.1);
        if (shattered) d.level.playSound(null, d.center, ModSounds.DOMAIN_SHATTER.get(), SoundSource.PLAYERS, 2.5f, 1f);
        ServerPlayer owner = d.level.getServer().getPlayerList().getPlayer(d.owner);
        if (owner != null) burnout(owner, ModAttachments.get(owner));
        sync(d.level);
    }

    private static void collapseOwnedBy(UUID owner) {
        for (ActiveDomain d : List.copyOf(ACTIVE)) if (d.owner.equals(owner)) collapse(d, false);
    }

    /** After a domain ends the caster's technique burns out for a while. */
    private static void burnout(ServerPlayer player, CursedEnergyData data) {
        for (String id : data.getUnlockedAbilities()) {
            Ability a = Abilities.get(id);
            if (a != null && a.kind() == Ability.Kind.TECHNIQUE) data.setCooldown(id, Math.max(data.getCooldown(id), 20 * 15));
        }
        player.displayClientMessage(Component.translatable("jjk.message.burnout").withStyle(ChatFormatting.GRAY), true);
        SyncJJKDataPayload.sync(player);
    }

    /** True if this entity is protected from sure-hit (Simple Domain, Domain Amplification). */
    public static boolean resistsSureHit(LivingEntity e) {
        return e.hasEffect(ModEffects.SIMPLE_DOMAIN) || e.hasEffect(ModEffects.DOMAIN_AMPLIFICATION);
    }

    public static List<ActiveDomain> active() {
        return List.copyOf(ACTIVE);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (slowMotionUntil != 0 && System.currentTimeMillis() > slowMotionUntil) {
            server.tickRateManager().setTickRate(previousTickRate);
            slowMotionUntil = 0;
        }
        if (ACTIVE.isEmpty()) return;

        Iterator<ActiveDomain> it = List.copyOf(ACTIVE).iterator();
        while (it.hasNext()) {
            ActiveDomain d = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(d.owner);
            if (owner == null || !owner.isAlive() || owner.level() != d.level || !d.contains(owner.position()) || --d.ticksLeft <= 0) {
                collapse(d, false);
                continue;
            }
            Vec3 c = Vec3.atBottomCenterOf(d.center);
            if (d.ticksLeft % 20 == 0) {
                for (LivingEntity e : AbilityEffects.livingAround(owner, c, RADIUS)) {
                    if (resistsSureHit(e)) continue;
                    d.type.sureHit(d.level, owner, e, d.damageMultiplier);
                }
            }
            if (d.ticksLeft % 5 == 0) {
                d.level.sendParticles(ModParticles.glow(d.type.color, 2.5f, 30), c.x, c.y + RADIUS * 0.4, c.z, 20, RADIUS * 0.5, RADIUS * 0.3, RADIUS * 0.5, 0.01);
            }
        }
    }

    private static void startSlowMotion(MinecraftServer server) {
        if (slowMotionUntil == 0) previousTickRate = server.tickRateManager().tickrate();
        server.tickRateManager().setTickRate(Math.min(previousTickRate, 7f));
        slowMotionUntil = System.currentTimeMillis() + 1600;
    }

    public static void sync(ServerLevel level) {
        List<DomainStatePayload.DomainInfo> infos = new ArrayList<>();
        for (ActiveDomain d : ACTIVE) {
            if (d.level != level) continue;
            Vec3 c = Vec3.atBottomCenterOf(d.center);
            infos.add(new DomainStatePayload.DomainInfo(c.x, c.y, c.z, RADIUS, d.type.color, d.type.id));
        }
        DomainStatePayload payload = new DomainStatePayload(infos);
        for (ServerPlayer p : level.players()) {
            if (p.connection.hasChannel(DomainStatePayload.TYPE)) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p.serverLevel());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p.serverLevel());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ActiveDomain d : List.copyOf(ACTIVE)) restore(d);
        ACTIVE.clear();
        if (slowMotionUntil != 0) {
            event.getServer().tickRateManager().setTickRate(previousTickRate);
            slowMotionUntil = 0;
        }
    }

    private DomainManager() {}
}
