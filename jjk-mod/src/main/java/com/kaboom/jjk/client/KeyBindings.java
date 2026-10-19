package com.kaboom.jjk.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.jjk";

    public static final KeyMapping ACTIVATE = key("key.jjk.activate", GLFW.GLFW_KEY_R);
    public static final KeyMapping CYCLE = key("key.jjk.cycle", GLFW.GLFW_KEY_Z);
    public static final KeyMapping DOMAIN = key("key.jjk.domain", GLFW.GLFW_KEY_V);
    public static final KeyMapping REVERSE_CURSED_TECHNIQUE = key("key.jjk.rct", GLFW.GLFW_KEY_G);

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    private KeyBindings() {}
}
