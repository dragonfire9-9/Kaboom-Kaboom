package dev.soulbound.module;

import dev.soulbound.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Anything with a name, a description and a list of settings that the GUI can show. */
public abstract class Configurable {
    private final String name;
    private final String description;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Configurable(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
}
