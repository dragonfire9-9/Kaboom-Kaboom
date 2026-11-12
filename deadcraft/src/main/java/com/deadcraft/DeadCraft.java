package com.deadcraft;

import com.deadcraft.config.DeadConfig;
import com.deadcraft.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * DeadCraft: a horror overhaul. Fifteen hostile creatures, a destroyed-city zombie, five bosses,
 * twenty peaceful creatures, four dimensions, magic, guns, vehicles and a cinematic client.
 */
@Mod(DeadCraft.MODID)
public class DeadCraft {
    public static final String MODID = "deadcraft";
    public static final Logger LOG = LogUtils.getLogger();

    public DeadCraft(IEventBus modBus, ModContainer container) {
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModArmorMaterials.MATERIALS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModWorldgen.FEATURES.register(modBus);
        ModWorldgen.STRUCTURE_TYPES.register(modBus);
        ModWorldgen.STRUCTURE_PIECES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        container.registerConfig(ModConfig.Type.CLIENT, DeadConfig.CLIENT_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, DeadConfig.SERVER_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
