package com.deadcraft.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.function.Supplier;

/**
 * A tapering, leaning spike of one block. Upward spikes grow from the ground (void crystal);
 * hanging ones search the column for the underside of a floating island (skystone stalactites).
 */
public class SpikeFeature extends Feature<NoneFeatureConfiguration> {
    private final Supplier<BlockState> block;
    private final boolean hanging;
    private final int minLen, maxLen;

    public SpikeFeature(Codec<NoneFeatureConfiguration> codec, Supplier<BlockState> block, boolean hanging, int minLen, int maxLen) {
        super(codec);
        this.block = block;
        this.hanging = hanging;
        this.minLen = minLen;
        this.maxLen = maxLen;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos start = ctx.origin();
        if (hanging) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(start.getX(), level.getMaxBuildHeight() - 8, start.getZ());
            BlockPos found = null;
            boolean solidAbove = false;
            for (; m.getY() > level.getMinBuildHeight() + 4; m.move(0, -1, 0)) {
                boolean solid = level.getBlockState(m).isSolid();
                if (solidAbove && !solid && r.nextInt(3) == 0) { found = m.immutable(); break; }
                solidAbove = solid;
            }
            if (found == null) return false;
            start = found;
        } else if (!level.getBlockState(start.below()).isSolid()) {
            return false;
        }
        int len = minLen + r.nextInt(maxLen - minLen + 1);
        int dir = hanging ? -1 : 1;
        float lx = (r.nextFloat() - 0.5f) * 0.8f, lz = (r.nextFloat() - 0.5f) * 0.8f;
        BlockState s = block.get();
        for (int i = 0; i < len; i++) {
            float radius = Mth.lerp(i / (float) len, len / 6f + 0.6f, 0.2f);
            int cx = Math.round(lx * i), cz = Math.round(lz * i);
            int ri = (int) Math.ceil(radius);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 0.3f) continue;
                    BlockPos p = start.offset(cx + dx, i * dir, cz + dz);
                    BlockState at = level.getBlockState(p);
                    if (at.isAir() || at.canBeReplaced()) level.setBlock(p, s, 2);
                }
            }
        }
        return true;
    }
}
