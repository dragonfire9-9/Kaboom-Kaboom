package com.kaboom.jjk.client;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.client.hud.CursedEnergyHud;
import com.kaboom.jjk.particle.CursedEnergyParticle;
import com.kaboom.jjk.particle.ModParticles;
import net.neoforged.api.distmarker.Dist;
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
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CURSED_ENERGY.get(), CursedEnergyParticle.Provider::new);
    }

    private ClientModEvents() {}
}
