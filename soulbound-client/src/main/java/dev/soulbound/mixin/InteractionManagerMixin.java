package dev.soulbound.mixin;

import dev.soulbound.event.Events;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class InteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void soulbound$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        Events.attack(target);
    }
}
