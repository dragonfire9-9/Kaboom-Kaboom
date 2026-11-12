package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DeadCraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WORLD = TABS.register("world", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.deadcraft.world"))
            .icon(() -> new ItemStack(ModItems.HOLLOW_ESSENCE.get()))
            .displayItems((params, out) -> {
                ModItems.BLOCK_ITEMS.forEach(i -> out.accept(i.get()));
                ModItems.MATERIALS.forEach(i -> out.accept(i.get()));
                out.accept(ModItems.BLOOD_BUCKET.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARSENAL = TABS.register("arsenal", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.deadcraft.arsenal"))
            .icon(() -> new ItemStack(ModItems.RUSTED_REVOLVER.get()))
            .withTabsBefore(WORLD.getKey())
            .displayItems((params, out) -> ModItems.ARSENAL.forEach(i -> out.accept(i.get()))).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATURES = TABS.register("creatures", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.deadcraft.creatures"))
            .icon(() -> new ItemStack(ModItems.SPAWN_EGGS.get(com.deadcraft.entity.DeadMob.THE_HOLLOW).get()))
            .withTabsBefore(ARSENAL.getKey())
            .displayItems((params, out) -> ModItems.SPAWN_EGGS.values().forEach(i -> out.accept(i.get()))).build());

    private ModCreativeTabs() {}
}
