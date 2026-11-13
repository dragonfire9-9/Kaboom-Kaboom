package dev.soulbound.mixin;

import dev.soulbound.gui.SoulTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** Replaces Minecraft's title screen with SoulBound's whenever it would be shown. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen soulbound$replaceTitle(Screen screen) {
        return SoulTitleScreen.replace(screen);
    }
}
