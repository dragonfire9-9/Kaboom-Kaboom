package dev.soulbound.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.soulbound.util.Keys;
import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting<Integer> {
    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public boolean isBound() { return value != NONE; }
    public String display() { return Keys.name(value); }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e.isJsonPrimitive()) set(e.getAsInt()); }
}
