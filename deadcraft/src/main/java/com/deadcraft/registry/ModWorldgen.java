package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.world.DeadPiece;
import com.deadcraft.world.DeadStructure;
import com.deadcraft.world.feature.BoneRibFeature;
import com.deadcraft.world.feature.DeadTreeFeature;
import com.deadcraft.world.feature.GhostwoodTreeFeature;
import com.deadcraft.world.feature.SpikeFeature;
import com.deadcraft.world.feature.GravePatchFeature;
import com.deadcraft.world.feature.RuinSpireFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, DeadCraft.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, DeadCraft.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, DeadCraft.MODID);

    public static final DeferredHolder<Feature<?>, DeadTreeFeature> DEAD_TREE = FEATURES.register("dead_tree", () -> new DeadTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, GravePatchFeature> GRAVE_PATCH = FEATURES.register("grave_patch", () -> new GravePatchFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, RuinSpireFeature> RUIN_SPIRE = FEATURES.register("ruin_spire", () -> new RuinSpireFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, BoneRibFeature> BONE_RIBS = FEATURES.register("bone_ribs", () -> new BoneRibFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, GhostwoodTreeFeature> GHOSTWOOD_TREE = FEATURES.register("ghostwood_tree", () -> new GhostwoodTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, SpikeFeature> VOID_CRYSTAL_SPIKE = FEATURES.register("void_crystal_spike",
            () -> new SpikeFeature(NoneFeatureConfiguration.CODEC, () -> ModBlocks.VOID_CRYSTAL.get().defaultBlockState(), false, 5, 14));
    public static final DeferredHolder<Feature<?>, SpikeFeature> SKYSTONE_STALACTITE = FEATURES.register("skystone_stalactite",
            () -> new SpikeFeature(NoneFeatureConfiguration.CODEC, () -> ModBlocks.SKYSTONE.get().defaultBlockState(), true, 4, 12));
    public static final DeferredHolder<Feature<?>, SpikeFeature> CHARNEL_STALACTITE = FEATURES.register("charnel_stalactite",
            () -> new SpikeFeature(NoneFeatureConfiguration.CODEC, () -> ModBlocks.CHARNEL_STONE.get().defaultBlockState(), true, 4, 16));

    public static final DeferredHolder<StructureType<?>, StructureType<DeadStructure>> DEAD_STRUCTURE =
            STRUCTURE_TYPES.register("dead_structure", () -> () -> DeadStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> DEAD_PIECE =
            STRUCTURE_PIECES.register("dead_piece", () -> DeadPiece::new);

    private ModWorldgen() {}
}
