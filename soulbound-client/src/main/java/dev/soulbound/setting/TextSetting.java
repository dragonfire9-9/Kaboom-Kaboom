package dev.soulbound.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short line of text the user types in the menu (a chat message, a name, ...). */
public class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, 64);
    }

    public TextSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue);
        this.maxLength = maxLength;
    }

    public int getMaxLength() { return maxLength; }

    @Override
    public void set(String v) {
        if (v == null) v = "";
        super.set(v.length() > maxLength ? v.substring(0, maxLength) : v);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e.isJsonPrimitive()) set(e.getAsString()); }
}
