package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DeadCraft.MODID);

    /** Current mana. */
    public static final Supplier<AttachmentType<Float>> MANA = ATTACHMENTS.register("mana",
            () -> AttachmentType.builder(() -> 0f).serialize(Codec.FLOAT).copyOnDeath().build());
    /** Set once the player reads a Grimoire of Awakening. Without it, magic items refuse to work. */
    public static final Supplier<AttachmentType<Boolean>> AWAKENED = ATTACHMENTS.register("awakened",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
    /** Where the player stood in the Overworld before travelling to a DeadCraft dimension. */
    public static final Supplier<AttachmentType<Long>> RETURN_POS = ATTACHMENTS.register("return_pos",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).serialize(Codec.LONG).copyOnDeath().build());

    private ModAttachments() {}
}
