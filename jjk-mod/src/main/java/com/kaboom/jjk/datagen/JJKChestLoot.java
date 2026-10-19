package com.kaboom.jjk.datagen;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.function.BiConsumer;

/** Chest loot for every structure ({@code jjk:chests/<name>}). */
public class JJKChestLoot implements LootTableSubProvider {

    public JJKChestLoot(HolderLookup.Provider registries) {
    }

    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> out) {
        out.accept(key("jujutsu_high"), LootTable.lootTable()
                .withPool(pool(2, 4)
                        .add(item(ModItems.CURSED_ENERGY_SCROLL.get(), 4, 1, 1))
                        .add(item(ModItems.CLAN_SCROLL.get(), 4, 1, 1))
                        .add(item(ModItems.STUDENT_ID.get(), 6, 1, 1))
                        .add(item(ModItems.JUJUTSU_UNIFORM_JACKET.get(), 3, 1, 1))
                        .add(item(ModItems.CURSE_GLASSES.get(), 3, 1, 1))
                        .add(item(ModItems.TALISMAN.get(), 8, 2, 5))
                        .add(item(ModItems.CE_BOTTLE.get(), 8, 1, 3))));

        out.accept(key("shibuya_station"), LootTable.lootTable()
                .withPool(pool(3, 6)
                        .add(item(ModItems.CURSED_FRAGMENT.get(), 10, 4, 12))
                        .add(item(ModItems.CE_SHARD.get(), 8, 2, 6))
                        .add(item(ModItems.SPECIAL_GRADE_OBJECT.get(), 4, 1, 2))
                        .add(item(ModItems.SUKUNA_FINGER.get(), 2, 1, 1))
                        .add(item(ModItems.CURSED_ENERGY_SCROLL.get(), 3, 1, 1))
                        .add(item(ModItems.HEAVENLY_RESTRICTION_SCROLL.get(), 1, 1, 1))
                        .add(item(ModItems.SPLIT_SOUL_KATANA.get(), 1, 1, 1))));

        out.accept(key("abandoned_ruins"), LootTable.lootTable()
                .withPool(pool(2, 5)
                        .add(item(ModItems.CURSED_FRAGMENT.get(), 10, 2, 6))
                        .add(item(ModItems.CE_SHARD.get(), 8, 1, 4))
                        .add(item(ModItems.TALISMAN.get(), 6, 1, 3))
                        .add(item(Items.BONE, 6, 1, 4))
                        .add(item(ModItems.SUKUNA_FINGER.get(), 1, 1, 1))
                        .add(item(ModItems.CLAN_SCROLL.get(), 2, 1, 1))));

        out.accept(key("sukuna_shrine"), LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(item(ModItems.SUKUNA_FINGER.get(), 1, 2, 3)))
                .withPool(pool(1, 3)
                        .add(item(ModItems.SPECIAL_GRADE_OBJECT.get(), 4, 1, 2))
                        .add(item(ModItems.CE_BOTTLE.get(), 4, 1, 2))
                        .add(item(ModItems.CURSED_ENERGY_SCROLL.get(), 2, 1, 1))));

        out.accept(key("detention_center"), LootTable.lootTable()
                .withPool(pool(2, 4)
                        .add(item(ModItems.DEATH_PAINTING.get(), 6, 1, 2))
                        .add(item(ModItems.SPECIAL_GRADE_OBJECT.get(), 4, 1, 1))
                        .add(item(ModItems.PRISON_REALM.get(), 1, 1, 1))
                        .add(item(ModItems.BLACK_ROPE.get(), 2, 1, 1))
                        .add(item(ModItems.CURSED_FRAGMENT.get(), 10, 3, 8))));

        out.accept(key("culling_game"), LootTable.lootTable()
                .withPool(pool(3, 5)
                        .add(item(ModItems.CURSED_ENERGY_SCROLL.get(), 4, 1, 1))
                        .add(item(ModItems.CLAN_SCROLL.get(), 4, 1, 1))
                        .add(item(ModItems.HEAVENLY_RESTRICTION_SCROLL.get(), 2, 1, 1))
                        .add(item(ModItems.CHAIN_OF_A_THOUSAND_MILES.get(), 2, 1, 1))
                        .add(item(ModItems.SPECIAL_GRADE_OBJECT.get(), 4, 1, 2))
                        .add(item(ModItems.CE_SHARD.get(), 8, 2, 8))));
    }

    private static ResourceKey<LootTable> key(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, JJKMod.id("chests/" + name));
    }

    private static LootPool.Builder pool(int min, int max) {
        return LootPool.lootPool().setRolls(UniformGenerator.between(min, max));
    }

    private static LootPoolSingletonContainer.Builder<?> item(Item item, int weight, int min, int max) {
        return LootItem.lootTableItem(item).setWeight(weight)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }
}
