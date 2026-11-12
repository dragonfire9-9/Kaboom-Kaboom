package com.deadcraft.block;

import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.world.DeadDimensions;
import com.deadcraft.world.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The swirling rift inside Gateways and Portal shrines. DESTINATION picks the dimension
 * (0 Overworld, 1 Hell's Paradise, 2 Skyfall Torn, 3 Light's Descent, 4 Disposed Skies).
 * Inside a DeadCraft dimension every rift leads home to the Overworld.
 */
public class RiftPortalBlock extends Block implements Portal {
    public static final IntegerProperty DESTINATION = IntegerProperty.create("destination", 0, 4);

    public RiftPortalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(DESTINATION, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DESTINATION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player p && !p.getAbilities().invulnerable ? 40 : 1;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        ResourceKey<Level> here = level.dimension();
        ResourceKey<Level> dest = DeadDimensions.isDeadCraft(here)
                ? Level.OVERWORLD
                : DeadDimensions.byIndex(level.getBlockState(pos).getOptionalValue(DESTINATION).orElse(1));
        if (dest == here) return null;
        return Travel.transition(level, entity, dest);
    }

    @Override
    public Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(60) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.PORTAL_HUM.get(),
                    SoundSource.BLOCKS, 0.6f, 0.8f + random.nextFloat() * 0.4f, false);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ModParticles.RUNE.get(), pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
