package com.deadcraft.client;

import com.deadcraft.DeadCraft;
import com.deadcraft.client.hud.DeadHud;
import com.deadcraft.client.particle.DeadParticle;
import com.deadcraft.client.render.*;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.item.GunItem;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModItems;
import com.deadcraft.registry.ModParticles;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.function.Supplier;

/** Mod-bus client registration: renderers, models, particles, keys, HUD layers, gun poses. */
@EventBusSubscriber(modid = DeadCraft.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        DeadLayers.register(event);
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (DeadMob mob : DeadMob.values()) {
            switch (mob.kind) {
                case PEACEFUL -> event.registerEntityRenderer(ModEntities.type(mob), ctx -> new DeadAnimalRenderer(ctx, mob));
                case ZOMBIE -> event.registerEntityRenderer(ModEntities.type(mob), RotwalkerRenderer::new);
                default -> {
                    switch (mob.archetype) {
                        case HUMANOID -> event.registerEntityRenderer(ModEntities.type(mob), ctx -> new BanditRenderer(ctx, mob));
                        default -> event.registerEntityRenderer(ModEntities.type(mob), ctx -> new DeadMonsterRenderer(ctx, mob));
                    }
                }
            }
        }
        event.registerEntityRenderer(ModEntities.PROJECTILE.get(), ProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.RUIN_RUNNER.get(), ctx -> new VehicleRenderer<>(ctx, "ruin_runner", true));
        event.registerEntityRenderer(ModEntities.SKY_SKIFF.get(), ctx -> new VehicleRenderer<>(ctx, "sky_skiff", false));
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        sprite(event, ModParticles.BLOOD, DeadParticle.Style.BLOOD);
        sprite(event, ModParticles.EMBER, DeadParticle.Style.EMBER);
        sprite(event, ModParticles.SOUL, DeadParticle.Style.SOUL);
        sprite(event, ModParticles.SPARK, DeadParticle.Style.SPARK);
        sprite(event, ModParticles.STAR, DeadParticle.Style.STAR);
        sprite(event, ModParticles.RUNE, DeadParticle.Style.RUNE);
        sprite(event, ModParticles.SLASH, DeadParticle.Style.SLASH);
        sprite(event, ModParticles.ASH, DeadParticle.Style.ASH);
        sprite(event, ModParticles.FROST, DeadParticle.Style.FROST);
        sprite(event, ModParticles.MUZZLE_FLASH, DeadParticle.Style.MUZZLE_FLASH);
        sprite(event, ModParticles.SHOCKWAVE, DeadParticle.Style.SHOCKWAVE);
    }

    private static void sprite(RegisterParticleProvidersEvent event, Supplier<? extends ParticleType<SimpleParticleType>> type, DeadParticle.Style style) {
        event.registerSpriteSet(type.get(), sprites -> new DeadParticle.Provider(sprites, style));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(DeadKeys.DODGE);
        event.register(DeadKeys.RELOAD);
    }

    @SubscribeEvent
    public static void hud(RegisterGuiLayersEvent event) {
        event.registerBelowAll(DeadCraft.id("atmosphere"), DeadHud::renderAtmosphere);
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, DeadCraft.id("bars"), DeadHud::renderBars);
    }

    @SubscribeEvent
    public static void skies(RegisterDimensionSpecialEffectsEvent event) {
        com.deadcraft.client.sky.DeadSkies.register(event);
    }

    @SubscribeEvent
    public static void clientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions gunPose = new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return stack.getItem() instanceof GunItem ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }
        };
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private final ResourceLocation still = DeadCraft.id("block/blood_still");
            private final ResourceLocation flow = DeadCraft.id("block/blood_flow");

            @Override
            public ResourceLocation getStillTexture() { return still; }

            @Override
            public ResourceLocation getFlowingTexture() { return flow; }

            @Override
            public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera, float partialTick,
                                                    net.minecraft.client.multiplayer.ClientLevel level, int renderDistance,
                                                    float darkenWorldAmount, org.joml.Vector3f fluidFogColor) {
                return new org.joml.Vector3f(0.32f, 0.01f, 0.03f);
            }

            @Override
            public void modifyFogRender(net.minecraft.client.Camera camera, net.minecraft.client.renderer.FogRenderer.FogMode mode,
                                        float renderDistance, float partialTick, float nearDistance, float farDistance,
                                        com.mojang.blaze3d.shaders.FogShape shape) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(-2f);
                com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(4f);
            }
        }, com.deadcraft.fluid.ModFluids.BLOOD_TYPE.get());
        event.registerItem(gunPose, ModItems.RUSTED_REVOLVER.get(), ModItems.DOOMSDAY_SHOTGUN.get(),
                ModItems.MARKSMAN_RIFLE.get(), ModItems.SCRAP_SMG.get());
    }
}
