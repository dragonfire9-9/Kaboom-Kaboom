package dev.soulbound.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Reads whether the physical key behind a key binding is held, so modules can hand control back cleanly. */
public final class KeyState {
    private KeyState() {}

    public static boolean physical(KeyBinding kb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
        if (k == null || k.getCode() == GLFW.GLFW_KEY_UNKNOWN) return false;
        long w = mc.getWindow().getHandle();
        return k.getCategory() == InputUtil.Type.MOUSE ? GLFW.glfwGetMouseButton(w, k.getCode()) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(mc.getWindow(), k.getCode());
    }

    /** Releases a binding we were holding, unless the player is actually holding it. */
    public static void release(KeyBinding kb) { kb.setPressed(physical(kb)); }
}
