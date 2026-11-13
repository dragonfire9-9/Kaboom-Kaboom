package dev.soulbound.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/** A dynamic texture sampled with linear filtering and clamped edges. */
public final class LinearTexture extends NativeImageBackedTexture {
    public LinearTexture(String name, NativeImage image) {
        super(() -> name, image);
        this.sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }
}
