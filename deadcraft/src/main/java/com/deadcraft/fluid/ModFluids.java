package com.deadcraft.fluid;

import com.deadcraft.DeadCraft;
import com.deadcraft.registry.ModBlocks;
import com.deadcraft.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Blood: the sea of Hell's Paradise. Thick, slow, and it makes you bleed. */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, DeadCraft.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, DeadCraft.MODID);

    public static final DeferredHolder<FluidType, FluidType> BLOOD_TYPE = TYPES.register("blood", () -> new FluidType(
            FluidType.Properties.create()
                    .descriptionId("block.deadcraft.blood")
                    .density(1800).viscosity(4000).temperature(310)
                    .motionScale(0.0045).fallDistanceModifier(0f)
                    .canSwim(true).canDrown(true).canExtinguish(true).supportsBoating(true)
                    .pathType(PathType.WATER).adjacentPathType(PathType.WATER_BORDER)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, FlowingFluid> BLOOD = FLUIDS.register("blood",
            () -> new BaseFlowingFluid.Source(props()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_BLOOD = FLUIDS.register("flowing_blood",
            () -> new BaseFlowingFluid.Flowing(props()));

    private static BaseFlowingFluid.Properties props() {
        return new BaseFlowingFluid.Properties(BLOOD_TYPE, BLOOD, FLOWING_BLOOD)
                .block(ModBlocks.BLOOD).bucket(ModItems.BLOOD_BUCKET)
                .slopeFindDistance(3).levelDecreasePerBlock(2).tickRate(20).explosionResistance(100f);
    }

    private ModFluids() {}
}
