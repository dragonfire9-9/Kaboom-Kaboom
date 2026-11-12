package com.deadcraft.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The half-buried ribcage of something enormous: a spine with arching ribs on both sides. */
public class BoneRibFeature extends Feature<NoneFeatureConfiguration> {
    public BoneRibFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos origin = ctx.origin();
        if (!level.getBlockState(origin.below()).isSolid()) return false;
        Direction along = r.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        Direction side = along.getClockWise();
        int ribs = 4 + r.nextInt(4);
        int span = 5 + r.nextInt(4);
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState spine = bone.setValue(RotatedPillarBlock.AXIS, along.getAxis());
        int sink = 1 + r.nextInt(2);
        for (int i = 0; i < ribs * 3; i++) {
            put(level, origin.relative(along, i).below(sink), spine);
        }
        for (int k = 0; k < ribs; k++) {
            BlockPos base = origin.relative(along, k * 3 + 1).below(sink);
            float size = span * (1f - 0.08f * Math.abs(k - ribs / 2f));
            for (int s = -1; s <= 1; s += 2) {
                // a half-ellipse arc rising from the spine and curling back down
                for (int a = 0; a <= 16; a++) {
                    float t = a / 16f * Mth.PI;
                    int dx = Math.round(Mth.sin(t * 0.5f) * size * s);
                    int dy = Math.round(Mth.sin(t) * size * 1.3f);
                    put(level, base.relative(side, dx).above(dy), bone);
                }
            }
        }
        return true;
    }

    private static void put(WorldGenLevel level, BlockPos p, BlockState s) {
        BlockState at = level.getBlockState(p);
        if (at.isAir() || at.canBeReplaced() || !at.getFluidState().isEmpty()) level.setBlock(p, s, 2);
    }
}
