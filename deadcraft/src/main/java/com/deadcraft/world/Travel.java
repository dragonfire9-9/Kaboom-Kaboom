package com.deadcraft.world;

import com.deadcraft.registry.ModAttachments;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Moves entities between the Overworld and the DeadCraft dimensions. */
public final class Travel {
    private Travel() {}

    @Nullable
    public static DimensionTransition transition(ServerLevel from, Entity entity, ResourceKey<Level> destKey) {
        MinecraftServer server = from.getServer();
        ServerLevel dest = server.getLevel(destKey);
        if (dest == null) return null;
        BlockPos arrival;
        if (destKey == Level.OVERWORLD) {
            long packed = entity instanceof Player p ? p.getData(ModAttachments.RETURN_POS) : Long.MIN_VALUE;
            BlockPos base = packed != Long.MIN_VALUE ? BlockPos.of(packed) : dest.getSharedSpawnPos();
            arrival = findArrival(dest, base);
        } else {
            if (from.dimension() == Level.OVERWORLD && entity instanceof Player p) {
                p.setData(ModAttachments.RETURN_POS, p.blockPosition().asLong());
            }
            arrival = findArrival(dest, entity.blockPosition());
        }
        return new DimensionTransition(dest, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                DimensionTransition.PLACE_PORTAL_TICKET.then(Travel::arrivalEffects));
    }

    /** Teleport right now (used by rift keys). */
    public static boolean go(ServerLevel from, Entity entity, ResourceKey<Level> destKey) {
        DimensionTransition t = transition(from, entity, destKey);
        if (t == null) return false;
        from.sendParticles(ModParticles.RUNE.get(), entity.getX(), entity.getY(0.5), entity.getZ(), 60, 0.5, 1, 0.5, 0.1);
        entity.changeDimension(t);
        return true;
    }

    private static void arrivalEffects(Entity e) {
        if (e.level() instanceof ServerLevel level) {
            level.playSound(null, e.blockPosition(), ModSounds.PORTAL_TRAVEL.get(), SoundSource.PLAYERS, 1f, 1f);
            level.sendParticles(ModParticles.RUNE.get(), e.getX(), e.getY(0.5), e.getZ(), 60, 0.5, 1, 0.5, 0.1);
        }
    }

    /** Finds a safe place to stand near {@code near}; builds a small platform if there is none. */
    public static BlockPos findArrival(ServerLevel level, BlockPos near) {
        int x = near.getX();
        int z = near.getZ();
        level.getChunk(x >> 4, z >> 4);
        if (level.dimensionType().hasCeiling()) {
            int top = level.getMinBuildHeight() + level.getLogicalHeight() - 4;
            for (int r = 0; r <= 32; r += 8) {
                for (int[] o : new int[][]{{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                    int cx = x + o[0], cz = z + o[1];
                    level.getChunk(cx >> 4, cz >> 4);
                    for (int y = Math.max(level.getMinBuildHeight() + 32, 33); y < top; y++) {
                        BlockPos p = new BlockPos(cx, y, cz);
                        if (isSafe(level, p)) return p;
                    }
                }
            }
            return platform(level, new BlockPos(x, 70, z));
        }
        for (int r = 0; r <= 48; r += 16) {
            for (int[] o : new int[][]{{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                int cx = x + o[0], cz = z + o[1];
                level.getChunk(cx >> 4, cz >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                BlockPos p = new BlockPos(cx, y, cz);
                if (y > level.getMinBuildHeight() + 2 && isSafe(level, p)) return p;
            }
        }
        return platform(level, new BlockPos(x, Math.max(level.getSeaLevel(), 80), z));
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        return below.isSolid() && below.getFluidState().isEmpty()
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    private static BlockPos platform(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(center.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                for (int dy = 0; dy < 3; dy++) level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(center.offset(2, 0, 2), Blocks.SOUL_LANTERN.defaultBlockState());
        return center;
    }
}
