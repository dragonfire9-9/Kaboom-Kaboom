package com.deadcraft.world.feature;

import com.deadcraft.block.GravestoneBlock;
import com.deadcraft.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A few lonely graves, bones and old blood. */
public class GravePatchFeature extends Feature<NoneFeatureConfiguration> {
    public GravePatchFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos o = ctx.origin();
        int placed = 0;
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n * 3 && placed < n; i++) {
            int x = o.getX() + r.nextInt(7) - 3, z = o.getZ() + r.nextInt(7) - 3;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p.below()).isSolid() || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, ModBlocks.GRAVESTONE.get().defaultBlockState()
                    .setValue(GravestoneBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)), 2);
            level.setBlock(p.below(), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            placed++;
        }
        for (int i = 0; i < 4; i++) {
            int x = o.getX() + r.nextInt(9) - 4, z = o.getZ() + r.nextInt(9) - 4;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
                level.setBlock(p, r.nextBoolean() ? ModBlocks.BLOOD_PUDDLE.get().defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState(), 2);
            }
        }
        return placed > 0;
    }
}
