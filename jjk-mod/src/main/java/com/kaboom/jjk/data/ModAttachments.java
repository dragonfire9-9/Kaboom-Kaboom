package com.kaboom.jjk.data;

import com.kaboom.jjk.JJKMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, JJKMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CursedEnergyData>> CURSED_ENERGY =
            ATTACHMENT_TYPES.register("cursed_energy",
                    () -> AttachmentType.serializable(CursedEnergyData::new).copyOnDeath().build());

    public static CursedEnergyData get(Player player) {
        return player.getData(CURSED_ENERGY);
    }

    private ModAttachments() {}
}
