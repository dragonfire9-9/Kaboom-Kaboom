package com.kaboom.jjk.datagen;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@code ./gradlew runData} writes to {@code src/generated/resources}: chest loot tables for every structure
 * and the {@code has_structure} biome tags. (Most other assets are produced by the Python tools in {@code tools/}.)
 */
@EventBusSubscriber(modid = JJKMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        gen.addProvider(event.includeServer(), new LootTableProvider(out, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(JJKChestLoot::new, LootContextParamSets.CHEST)), lookup));
        gen.addProvider(event.includeServer(), new JJKBiomeTagsProvider(out, lookup, files));
    }

    private DataGenerators() {}
}
