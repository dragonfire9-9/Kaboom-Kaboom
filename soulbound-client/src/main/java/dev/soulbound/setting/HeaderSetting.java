package dev.soulbound.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A section heading in a parameter list (e.g. "AIM", "MOVEMENT"). Not saved. */
public class HeaderSetting extends Setting<String> {
    public HeaderSetting(String title) { super(title, "", title); }

    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement e) { }
}
