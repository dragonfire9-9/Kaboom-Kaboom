package com.deadcraft.world.feature;

import com.deadcraft.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** A charred, leafless tree with crooked branches. */
public class DeadTreeFeature extends Feature<NoneFeatureConfiguration> {
    public DeadTreeFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos pos = ctx.origin();
        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolid() || !level.getBlockState(pos).isAir()) return false;
        return place(level, pos, ctx.random().nextLong(), null);
    }

    public static boolean place(WorldGenLevel level, BlockPos base, long seed, @Nullable BoundingBox clip) {
        RandomSource r = RandomSource.create(seed ^ base.asLong());
        BlockState log = ModBlocks.CHARRED_LOG.get().defaultBlockState();
        int height = 4 + r.nextInt(4);
        for (int y = 0; y < height; y++) put(level, base.above(y), log, clip);
        int branches = 2 + r.nextInt(3);
        for (int i = 0; i < branches; i++) {
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            BlockPos p = base.above(height - 1 - r.nextInt(Math.max(1, height / 2)));
            int len = 2 + r.nextInt(3);
            BlockState axis = log.setValue(RotatedPillarBlock.AXIS, dir.getAxis());
            for (int j = 1; j <= len; j++) {
                p = p.relative(dir);
                if (j == len && r.nextBoolean()) p = p.above();
                put(level, p, j == len ? log : axis, clip);
            }
        }
        return true;
    }

    private static void put(WorldGenLevel level, BlockPos p, BlockState s, @Nullable BoundingBox clip) {
        if (clip != null && !clip.isInside(p)) return;
        if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).isAir()) level.setBlock(p, s, 2);
    }
}
