package com.kaboom.jjk;

import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.block.ModBlocks;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.item.ModArmorMaterials;
import com.kaboom.jjk.item.ModCreativeTabs;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.network.ModNetwork;
import com.kaboom.jjk.particle.ModParticles;
import com.kaboom.jjk.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(JJKMod.MODID)
public class JJKMod {
    public static final String MODID = "jjk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JJKMod(IEventBus modBus, ModContainer container) {
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModArmorMaterials.MATERIALS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);

        modBus.addListener(ModNetwork::register);

        container.registerConfig(ModConfig.Type.COMMON, JJKConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
