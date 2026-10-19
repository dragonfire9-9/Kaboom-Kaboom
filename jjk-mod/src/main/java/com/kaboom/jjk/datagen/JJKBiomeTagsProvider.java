package com.kaboom.jjk.datagen;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/** Where each structure can generate ({@code jjk:has_structure/<id>}). */
public class JJKBiomeTagsProvider extends BiomeTagsProvider {

    public JJKBiomeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
        super(output, lookup, JJKMod.MODID, files);
    }

    private static TagKey<Biome> has(String structure) {
        return TagKey.create(Registries.BIOME, JJKMod.id("has_structure/" + structure));
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(has("tokyo_jujutsu_high")).add(Biomes.PLAINS).add(Biomes.MEADOW).add(Biomes.CHERRY_GROVE).add(Biomes.FOREST).add(Biomes.BIRCH_FOREST);
        tag(has("kyoto_jujutsu_high")).add(Biomes.CHERRY_GROVE).add(Biomes.MEADOW).add(Biomes.DARK_FOREST).add(Biomes.TAIGA).add(Biomes.PLAINS);
        tag(has("shibuya_station_ruins")).add(Biomes.PLAINS).add(Biomes.SAVANNA).add(Biomes.DESERT).add(Biomes.SUNFLOWER_PLAINS).add(Biomes.MEADOW);
        tag(has("abandoned_school")).addTag(BiomeTags.IS_FOREST).add(Biomes.PLAINS).add(Biomes.TAIGA);
        tag(has("abandoned_hospital")).add(Biomes.PLAINS).add(Biomes.SAVANNA).add(Biomes.SWAMP).add(Biomes.TAIGA).add(Biomes.SNOWY_PLAINS);
        tag(has("cursed_tunnel")).addTag(BiomeTags.IS_MOUNTAIN).addTag(BiomeTags.IS_HILL).add(Biomes.STONY_SHORE);
        tag(has("sukuna_shrine")).add(Biomes.DARK_FOREST).add(Biomes.BAMBOO_JUNGLE).add(Biomes.OLD_GROWTH_SPRUCE_TAIGA).add(Biomes.CHERRY_GROVE);
        tag(has("curse_womb_detention_center")).add(Biomes.PLAINS).add(Biomes.DESERT).add(Biomes.SNOWY_PLAINS).add(Biomes.BADLANDS);
        tag(has("culling_game_colony")).addTag(Tags.Biomes.IS_PLAINS).add(Biomes.SAVANNA).add(Biomes.DESERT);
    }
}
