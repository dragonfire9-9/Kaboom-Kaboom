package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.block.DeadPlantBlock;
import com.deadcraft.block.GravestoneBlock;
import com.deadcraft.fluid.ModFluids;
import com.deadcraft.block.RiftPortalBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.SoundType;
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

    // ---- Terrain of the DeadCraft dimensions ---------------------------------------------------
    // Hell's Paradise
    public static final DeferredBlock<Block> CHARNEL_STONE = terrain("charnel_stone", Blocks.NETHERRACK, MapColor.CRIMSON_NYLIUM);
    public static final DeferredBlock<Block> ASH_SOIL = terrain("ash_soil", Blocks.SOUL_SOIL, MapColor.COLOR_GRAY);
    public static final DeferredBlock<Block> SINEW_BLOCK = BLOCKS.registerBlock("sinew_block", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.MUD).mapColor(MapColor.COLOR_RED).sound(SoundType.HONEY_BLOCK));
    // Skyfall Torn
    public static final DeferredBlock<Block> SKYSTONE = terrain("skystone", Blocks.CALCITE, MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<Block> PALE_MOSS = terrain("pale_moss", Blocks.MOSS_BLOCK, MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredBlock<Block> SHROUD_MOLD = terrain("shroud_mold", Blocks.MOSS_BLOCK, MapColor.COLOR_PURPLE);
    public static final DeferredBlock<RotatedPillarBlock> GHOSTWOOD_LOG = BLOCKS.registerBlock("ghostwood_log", RotatedPillarBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LOG).mapColor(MapColor.SNOW));
    public static final DeferredBlock<LeavesBlock> GHOSTWOOD_LEAVES = BLOCKS.registerBlock("ghostwood_leaves", LeavesBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_LIGHT_GRAY));
    // Light's Descent
    public static final DeferredBlock<Block> UMBRAL_STONE = terrain("umbral_stone", Blocks.STONE, MapColor.COLOR_BLACK);
    public static final DeferredBlock<Block> GRAVE_SOIL = terrain("grave_soil", Blocks.DIRT, MapColor.DIRT);
    public static final DeferredBlock<Block> GLOOM_GRASS = terrain("gloom_grass", Blocks.GRASS_BLOCK, MapColor.WARPED_STEM);
    public static final DeferredBlock<Block> BONE_SAND = BLOCKS.registerBlock("bone_sand", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).mapColor(MapColor.SAND).sound(SoundType.SAND));
    public static final DeferredBlock<Block> DUSTROCK = terrain("dustrock", Blocks.SANDSTONE, MapColor.TERRACOTTA_ORANGE);
    // Disposed Skies
    public static final DeferredBlock<Block> VOIDROCK = terrain("voidrock", Blocks.DEEPSLATE, MapColor.COLOR_BLACK);
    public static final DeferredBlock<Block> STATIC_CRUST = BLOCKS.registerBlock("static_crust", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 4));
    public static final DeferredBlock<Block> VOID_CRYSTAL = BLOCKS.registerBlock("void_crystal", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 9).noOcclusion());
    // Plants
    public static final DeferredBlock<DeadPlantBlock> CINDER_SPROUT = plant("cinder_sprout", 6);
    public static final DeferredBlock<DeadPlantBlock> GLOOM_FERN = plant("gloom_fern", 0);
    public static final DeferredBlock<DeadPlantBlock> PALE_BLOOM = plant("pale_bloom", 4);
    public static final DeferredBlock<DeadPlantBlock> STATIC_THORN = plant("static_thorn", 5);
    // The blood sea (registered with BLOCKS.register so it gets no block item)
    public static final DeferredBlock<LiquidBlock> BLOOD = BLOCKS.register("blood", () -> new LiquidBlock(ModFluids.BLOOD.get(),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_RED)));

    // Travel
    public static final DeferredBlock<RiftPortalBlock> RIFT_PORTAL = BLOCKS.registerBlock("rift_portal", RiftPortalBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_PORTAL).lightLevel(s -> 12).noCollission().strength(-1f, 3600000f).noLootTable());

    private ModBlocks() {}

    private static DeferredBlock<Block> ore(String name, Block base, int minXp, int maxXp) {
        return BLOCKS.registerBlock(name, p -> new DropExperienceBlock(UniformInt.of(minXp, maxXp), p),
                BlockBehaviour.Properties.ofFullCopy(base));
    }

    private static DeferredBlock<Block> terrain(String name, Block base, MapColor color) {
        return BLOCKS.registerBlock(name, Block::new, BlockBehaviour.Properties.ofFullCopy(base).mapColor(color));
    }

    private static DeferredBlock<DeadPlantBlock> plant(String name, int light) {
        return BLOCKS.registerBlock(name, DeadPlantBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
                .mapColor(MapColor.PLANT).lightLevel(s -> light));
    }

    private static DeferredBlock<Block> storage(String name, MapColor color) {
        return BLOCKS.registerBlock(name, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(color));
    }
}
