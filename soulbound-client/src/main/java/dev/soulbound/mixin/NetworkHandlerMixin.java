package dev.soulbound.mixin;

import dev.soulbound.module.combat.Velocity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class NetworkHandlerMixin {
    /** Velocity: scale the knockback the server sends us. */
    @Redirect(method = "onEntityVelocityUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocityClient(Lnet/minecraft/util/math/Vec3d;)V"))
    private void soulbound$velocity(Entity entity, Vec3d velocity) {
        if (entity == MinecraftClient.getInstance().player && Velocity.active()) velocity = Velocity.INSTANCE.apply(entity.getVelocity(), velocity, false);
        if (velocity != null) entity.setVelocityClient(velocity);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "onExplosion", at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private void soulbound$explosion(Optional optional, Consumer consumer) {
        if (Velocity.active() && optional.isPresent() && optional.get() instanceof Vec3d kb) {
            Vec3d scaled = Velocity.INSTANCE.apply(Vec3d.ZERO, kb, true);
            if (scaled != null) consumer.accept(scaled);
            return;
        }
        optional.ifPresent(consumer);
    }
}
