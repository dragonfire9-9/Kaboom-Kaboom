package dev.soulbound.mixin;

import dev.soulbound.module.visual.NoOverlays;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void soulbound$vignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NoOverlays.hide(NoOverlays.Kind.VIGNETTE)) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void soulbound$portal(DrawContext context, float nausea, CallbackInfo ci) {
        if (NoOverlays.hide(NoOverlays.Kind.PORTAL)) ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void soulbound$spyglass(DrawContext context, float scale, CallbackInfo ci) {
        if (NoOverlays.hide(NoOverlays.Kind.SPYGLASS)) ci.cancel();
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void soulbound$overlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        if (NoOverlays.hide(NoOverlays.Kind.PUMPKIN)) ci.cancel(); // carved pumpkin and powder snow
    }
}
