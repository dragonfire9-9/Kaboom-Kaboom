package com.kaboom.jjk.item;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JJKMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.jjk"))
            .icon(() -> new ItemStack(ModItems.JJK_EMBLEM.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(entry -> {
                if (entry.get() != ModItems.JJK_EMBLEM.get()) output.accept(entry.get());
            }))
            .build());

    private ModCreativeTabs() {}
}
