package com.deadcraft.world.feature;

import com.deadcraft.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Tapering spires of blackstone and obsidian, crowned with a lightning rod or a dread lantern. */
public class RuinSpireFeature extends Feature<NoneFeatureConfiguration> {
    public RuinSpireFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos base = ctx.origin();
        if (!level.getBlockState(base.below()).isSolid()) return false;
        int height = 6 + r.nextInt(10);
        int radius = 1 + r.nextInt(2);
        for (int y = -2; y < height; y++) {
            int rad = Math.max(0, radius - (y * (radius + 1)) / height);
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad + 1) continue;
                    BlockState s = r.nextFloat() < 0.2f ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                            : r.nextFloat() < 0.5f ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
                    level.setBlock(base.offset(dx, y, dz), s, 2);
                }
        }
        BlockPos top = base.above(height);
        level.setBlock(top, r.nextBoolean() ? Blocks.LIGHTNING_ROD.defaultBlockState() : ModBlocks.DREAD_LANTERN.get().defaultBlockState(), 2);
        for (int i = 1; i <= 2 + r.nextInt(3); i++) {
            BlockPos c = base.offset(radius + 1, height - 2 - i, 0);
            if (level.getBlockState(c).isAir()) level.setBlock(c, Blocks.CHAIN.defaultBlockState(), 2);
        }
        return true;
    }
}
