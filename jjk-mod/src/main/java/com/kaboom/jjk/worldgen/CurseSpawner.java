package com.kaboom.jjk.worldgen;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.ModTags;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.entity.CurseType;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.roll.RollEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * Curses are born from negative human emotion. On top of natural (biome) spawns, this spawner seeds
 * extra curses near players, weighted by: night, darkness, villages (crowds), mod structures tagged
 * {@code jjk:negative_emotion_hotspots}, and the player's own grade (stronger sorcerers draw stronger curses).
 */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class CurseSpawner {
    private static final int INTERVAL = 200;
    private static final int LOCAL_CAP = 8;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % INTERVAL != 0) return;
        if (!level.dimensionType().natural() || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) return;
        double rate = JJKConfig.CURSE_SPAWN_RATE.get();
        if (rate <= 0) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            BlockPos pos = player.blockPosition();
            RandomSource random = level.getRandom();

            double chance = 0.12 * rate;
            if (level.isNight()) chance *= 2.0;
            if (level.getBrightness(LightLayer.BLOCK, pos) < 6 && level.getBrightness(LightLayer.SKY, pos) < 6) chance *= 1.6;
            boolean village = level.isVillage(pos);
            if (village) chance *= 2.5;
            boolean hotspot = inHotspot(level, pos);
            if (hotspot) chance *= 3.0;
            if (random.nextDouble() > chance) continue;

            List<CurseEntity> nearby = level.getEntitiesOfClass(CurseEntity.class, new AABB(pos).inflate(48), c -> c.getOwnerUUID() == null);
            if (nearby.size() >= LOCAL_CAP) continue;

            CurseType type = pickType(ModAttachments.get(player).getGrade().ordinal() + (hotspot ? 2 : 0) + (village ? 1 : 0), random);
            BlockPos spawn = findSpot(level, pos, random, type.flying);
            if (spawn == null) continue;
            CurseEntity curse = ModEntities.curse(type).create(level);
            if (curse == null) continue;
            curse.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, random.nextFloat() * 360, 0);
            curse.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
            level.addFreshEntity(curse);
        }
    }

    /** Stronger "pull" shifts the weights toward higher-grade curses. */
    static CurseType pickType(int pull, RandomSource random) {
        return RollEngine.pick(List.of(CurseType.values()),
                t -> t.weight * Math.pow(1.0 + 0.6 * pull, t.grade.ordinal() / 2.0), random);
    }

    private static boolean inHotspot(ServerLevel level, BlockPos pos) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var tag = registry.getTag(ModTags.HOTSPOTS);
        if (tag.isEmpty()) return false;
        for (var holder : tag.get()) {
            Structure s = holder.value();
            if (level.structureManager().getStructureAt(pos, s).isValid()) return true;
        }
        return false;
    }

    private static BlockPos findSpot(ServerLevel level, BlockPos around, RandomSource random, boolean flying) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 16 + random.nextInt(16);
            int x = around.getX() + (int) (Math.cos(angle) * dist);
            int z = around.getZ() + (int) (Math.sin(angle) * dist);
            boolean underground = !level.canSeeSky(around);
            int y = underground ? around.getY() + random.nextInt(9) - 4 : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (flying) p = p.above(2 + random.nextInt(4));
            if (!level.isLoaded(p) || !level.getWorldBorder().isWithinBounds(p)) continue;
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
            if (!flying && !level.getBlockState(p.below()).isSolid()) continue;
            if (level.getBrightness(LightLayer.BLOCK, p) > 10) continue;
            return p;
        }
        return null;
    }

    private CurseSpawner() {}
}
