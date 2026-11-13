package dev.soulbound.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button in the parameter list. Not saved. */
public class ActionSetting extends Setting<Runnable> {
    private final String buttonLabel;

    public ActionSetting(String name, String description, String buttonLabel, Runnable action) {
        super(name, description, action);
        this.buttonLabel = buttonLabel;
    }

    public String getButtonLabel() { return buttonLabel; }
    public void run() { value.run(); }

    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement e) { }
}
