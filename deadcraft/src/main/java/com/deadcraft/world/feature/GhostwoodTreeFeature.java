package com.deadcraft.world.feature;

import com.deadcraft.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A pale, twisting ghostwood tree with drooping veils of leaves. */
public class GhostwoodTreeFeature extends Feature<NoneFeatureConfiguration> {
    public GhostwoodTreeFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos pos = ctx.origin();
        if (!level.getBlockState(pos.below()).isSolid() || !level.getBlockState(pos).isAir()) return false;
        BlockState log = ModBlocks.GHOSTWOOD_LOG.get().defaultBlockState();
        BlockState leaves = ModBlocks.GHOSTWOOD_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 6 + r.nextInt(6);
        BlockPos p = pos;
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        for (int y = 0; y < height; y++) {
            put(level, p, log);
            p = p.above();
            if (y > 2 && r.nextInt(3) == 0) {
                put(level, p, log.setValue(RotatedPillarBlock.AXIS, lean.getAxis()));
                p = p.relative(lean);
            }
        }
        // a lopsided crown, with strands of leaves hanging down like hair
        int rad = 2 + r.nextInt(2);
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx * dx + dz * dz + dy * dy * 3 > rad * rad + 1 || r.nextInt(6) == 0) continue;
                    put(level, p.offset(dx, dy, dz), leaves);
                    if (dy == -1 && r.nextInt(3) == 0) {
                        int hang = 1 + r.nextInt(4);
                        for (int h = 1; h <= hang; h++) put(level, p.offset(dx, -1 - h, dz), leaves);
                    }
                }
            }
        }
        put(level, p, log);
        return true;
    }

    private static void put(WorldGenLevel level, BlockPos p, BlockState s) {
        BlockState at = level.getBlockState(p);
        if (at.isAir() || at.canBeReplaced()) level.setBlock(p, s, 2);
    }
}
