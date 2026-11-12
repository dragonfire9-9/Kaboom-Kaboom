package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DeadCraft.MODID);

    /** Rounds currently loaded in a gun. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AMMO =
            COMPONENTS.registerComponentType("ammo", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
