package com.kaboom.jjk.client;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.client.hud.CursedEnergyHud;
import com.kaboom.jjk.particle.CursedEnergyParticle;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.particle.TintedParticle;
import com.kaboom.jjk.client.render.CurseUserRenderer;
import com.kaboom.jjk.client.render.JJKGeoRenderer;
import com.kaboom.jjk.client.render.SorcererNpcRenderer;
import com.kaboom.jjk.client.render.SukunaMarkingsLayer;
import com.kaboom.jjk.entity.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = JJKMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.ACTIVATE);
        event.register(KeyBindings.CYCLE);
        event.register(KeyBindings.DOMAIN);
        event.register(KeyBindings.REVERSE_CURSED_TECHNIQUE);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, JJKMod.id("cursed_energy_hud"), new CursedEnergyHud());
        event.registerAboveAll(JJKMod.id("cinematics"), ClientFx::renderOverlay);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CURSED_ENERGY.get(), CursedEnergyParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GLOW.get(), s -> new TintedParticle.Provider(s, TintedParticle.Style.GLOW));
        event.registerSpriteSet(ModParticles.SPARK.get(), s -> new TintedParticle.Provider(s, TintedParticle.Style.SPARK));
        event.registerSpriteSet(ModParticles.SLASH.get(), s -> new TintedParticle.Provider(s, TintedParticle.Style.SLASH));
        event.registerSpriteSet(ModParticles.RING.get(), s -> new TintedParticle.Provider(s, TintedParticle.Style.RING));
        event.registerSpriteSet(ModParticles.LIGHTNING.get(), s -> new TintedParticle.Provider(s, TintedParticle.Style.LIGHTNING));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TECHNIQUE_PROJECTILE.get(), NoopRenderer::new);
        ModEntities.CURSES.forEach((t, h) -> event.registerEntityRenderer(h.get(), ctx -> new JJKGeoRenderer<>(ctx, t.id, t.width * 0.5f)));
        ModEntities.SHIKIGAMI.forEach((t, h) -> event.registerEntityRenderer(h.get(), ctx -> new JJKGeoRenderer<>(ctx, t.id, t.width * 0.5f)));
        ModEntities.NPCS.forEach((t, h) -> event.registerEntityRenderer(h.get(), SorcererNpcRenderer::new));
        event.registerEntityRenderer(ModEntities.CURSE_USER.get(), CurseUserRenderer::new);
        ModEntities.BOSSES.forEach((t, h) -> event.registerEntityRenderer(h.get(), ctx -> new JJKGeoRenderer<>(ctx, t.id, t.width * 0.6f)));
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new SukunaMarkingsLayer(renderer));
        }
    }

    private ClientModEvents() {}
}
