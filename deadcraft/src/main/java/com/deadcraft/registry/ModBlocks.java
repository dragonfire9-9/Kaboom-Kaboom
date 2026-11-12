package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.block.GravestoneBlock;
import com.deadcraft.block.RiftPortalBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DeadCraft.MODID);

    // Ores
    public static final DeferredBlock<Block> NECRITE_ORE = ore("necrite_ore", Blocks.IRON_ORE, 1, 3);
    public static final DeferredBlock<Block> DEEPSLATE_NECRITE_ORE = ore("deepslate_necrite_ore", Blocks.DEEPSLATE_IRON_ORE, 1, 3);
    public static final DeferredBlock<Block> BLOODSTONE_ORE = ore("bloodstone_ore", Blocks.DIAMOND_ORE, 3, 7);
    public static final DeferredBlock<Block> DEEPSLATE_BLOODSTONE_ORE = ore("deepslate_bloodstone_ore", Blocks.DEEPSLATE_DIAMOND_ORE, 3, 7);
    public static final DeferredBlock<Block> VOIDSTEEL_ORE = ore("voidsteel_ore", Blocks.ANCIENT_DEBRIS, 2, 5);
    public static final DeferredBlock<Block> STARDUST_ORE = BLOCKS.registerBlock("stardust_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).lightLevel(s -> 6));

    // Storage blocks
    public static final DeferredBlock<Block> NECRITE_BLOCK = storage("necrite_block", MapColor.TERRACOTTA_WHITE);
    public static final DeferredBlock<Block> BLOODSTONE_BLOCK = storage("bloodstone_block", MapColor.CRIMSON_STEM);
    public static final DeferredBlock<Block> VOIDSTEEL_BLOCK = storage("voidsteel_block", MapColor.COLOR_BLACK);
    public static final DeferredBlock<Block> STARDUST_BLOCK = BLOCKS.registerBlock("stardust_block", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_BLOCK).mapColor(MapColor.GOLD).lightLevel(s -> 12));

    // World decoration
    public static final DeferredBlock<RotatedPillarBlock> CHARRED_LOG = BLOCKS.registerBlock("charred_log", RotatedPillarBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_LOG).mapColor(MapColor.COLOR_BLACK));
    public static final DeferredBlock<GravestoneBlock> GRAVESTONE = BLOCKS.registerBlock("gravestone", GravestoneBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<CarpetBlock> BLOOD_PUDDLE = BLOCKS.registerBlock("blood_puddle", CarpetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.1f).noOcclusion().noCollission()
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<Block> DREAD_LANTERN = BLOCKS.registerBlock("dread_lantern", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_LANTERN).lightLevel(s -> 13).noOcclusion());

    // Travel
    public static final DeferredBlock<RiftPortalBlock> RIFT_PORTAL = BLOCKS.registerBlock("rift_portal", RiftPortalBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_PORTAL).lightLevel(s -> 12).noCollission().strength(-1f, 3600000f).noLootTable());

    private ModBlocks() {}

    private static DeferredBlock<Block> ore(String name, Block base, int minXp, int maxXp) {
        return BLOCKS.registerBlock(name, p -> new DropExperienceBlock(UniformInt.of(minXp, maxXp), p),
                BlockBehaviour.Properties.ofFullCopy(base));
    }

    private static DeferredBlock<Block> storage(String name, MapColor color) {
        return BLOCKS.registerBlock(name, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(color));
    }
}
