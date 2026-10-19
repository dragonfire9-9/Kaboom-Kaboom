package com.kaboom.jjk.block;

import com.kaboom.jjk.JJKMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(JJKMod.MODID);

    /** Temporary, unbreakable shell of a Domain Expansion. Always restored to the original terrain. */
    public static final DeferredBlock<Block> DOMAIN_BARRIER = BLOCKS.registerSimpleBlock("domain_barrier", domainProps().lightLevel(s -> 3));
    /** Temporary floor inside a Domain Expansion. */
    public static final DeferredBlock<Block> DOMAIN_FLOOR = BLOCKS.registerSimpleBlock("domain_floor", domainProps().lightLevel(s -> 6));

    private static BlockBehaviour.Properties domainProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1f, 3600000f)
                .noLootTable()
                .sound(SoundType.AMETHYST)
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    private ModBlocks() {}
}
