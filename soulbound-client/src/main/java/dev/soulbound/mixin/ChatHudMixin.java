package dev.soulbound.mixin;

import dev.soulbound.module.misc.BetterChat;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void soulbound$antiSpam(Text message, MessageSignatureData sig, MessageIndicator indicator, CallbackInfo ci) {
        if (BetterChat.active() && BetterChat.INSTANCE.suppress(message)) ci.cancel();
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text soulbound$decorate(Text message) {
        return BetterChat.active() ? BetterChat.INSTANCE.decorate(message) : message;
    }

    @ModifyConstant(method = {"addVisibleMessage", "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V"}, constant = @Constant(intValue = 100))
    private int soulbound$history(int max) {
        return BetterChat.active() ? BetterChat.INSTANCE.history() : max;
    }
}
