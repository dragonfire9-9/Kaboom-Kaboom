package com.deadcraft.block;

import com.deadcraft.DeadCraft;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small plant that only grows on DeadCraft soils (and ordinary dirt). */
public class DeadPlantBlock extends BushBlock {
    public static final MapCodec<DeadPlantBlock> CODEC = simpleCodec(DeadPlantBlock::new);
    public static final TagKey<Block> SOIL = TagKey.create(Registries.BLOCK, DeadCraft.id("plant_soil"));
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public DeadPlantBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(SOIL) || super.mayPlaceOn(state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE.move(state.getOffset(level, pos).x, 0, state.getOffset(level, pos).z);
    }
}
